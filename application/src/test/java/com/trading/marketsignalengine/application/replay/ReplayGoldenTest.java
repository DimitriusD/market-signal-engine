package com.trading.marketsignalengine.application.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.trading.marketsignalengine.application.domain.model.MarketSignalSnapshot;
import com.trading.marketsignalengine.application.domain.model.SignalConfiguration;
import com.trading.marketsignalengine.application.domain.model.feature.MarketFeaturesSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Golden replay suite: every fixture in {@link GoldenFixtures} is replayed through the canonical
 * production engine ({@code ReplayHarness.standard}) with a fixed evaluation instant, rendered with
 * {@link SnapshotCanonicalText} and compared byte-for-byte with
 * {@code src/test/resources/golden/<case>.txt}.
 *
 * <p>A failing golden means the engine's observable output changed. That is sometimes intended
 * (threshold change, new signal, new attribute) — then regenerate with the environment variable
 * {@code GOLDEN_UPDATE=true} (or system property {@code golden.update=true}), review the diff, and
 * commit the golden change in its own commit with an explanation and a {@code signalSetVersion}
 * bump where semantics changed. It must never be regenerated blindly.
 */
class ReplayGoldenTest {

    /** Fixed evaluation instant: 75 ms after the fixtures' computedAt, a plausible transport lag. */
    static final Instant EVALUATED_AT = Instant.parse("2026-03-01T10:00:00.100Z");

    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden");

    private final ReplayHarness harness = ReplayHarness.standard(SignalConfiguration.defaults());

    @TestFactory
    Stream<DynamicTest> goldenCases() {
        Map<String, MarketFeaturesSnapshot> cases = GoldenFixtures.all();
        return cases.entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                entry.getKey(),
                () -> assertGolden(entry.getKey(), entry.getValue())));
    }

    @Test
    void goldenDirectoryHasNoOrphanFiles() throws IOException {
        if (!Files.isDirectory(GOLDEN_DIR)) {
            return;
        }
        Set<String> expected = new TreeSet<>(GoldenFixtures.all().keySet());
        Set<String> present = new TreeSet<>();
        try (Stream<Path> files = Files.list(GOLDEN_DIR)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".txt"))
                    .map(n -> n.substring(0, n.length() - ".txt".length()))
                    .forEach(present::add);
        }
        present.removeAll(expected);
        assertTrue(present.isEmpty(), "golden files without a fixture (delete them): " + present);
    }

    @Test
    void replayIsDeterministicAcrossRuns() {
        List<MarketFeaturesSnapshot> inputs = new ArrayList<>(GoldenFixtures.all().values());

        List<MarketSignalSnapshot> first = harness.replay(inputs, ReplayHarness.fixed(EVALUATED_AT));
        List<MarketSignalSnapshot> second = ReplayHarness.standard(SignalConfiguration.defaults())
                .replay(inputs, ReplayHarness.fixed(EVALUATED_AT));

        assertEquals(inputs.size(), first.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(SnapshotCanonicalText.render(first.get(i)), SnapshotCanonicalText.render(second.get(i)),
                    "non-deterministic output for " + inputs.get(i).snapshotId());
        }
    }

    @Test
    void fixtureSnapshotIdsAreUnique() {
        Set<String> ids = new TreeSet<>();
        for (MarketFeaturesSnapshot f : GoldenFixtures.all().values()) {
            assertTrue(ids.add(f.snapshotId()), "duplicate fixture snapshotId " + f.snapshotId());
        }
    }

    @Test
    void replayPreservesOrderAndOneToOneMapping() {
        List<MarketFeaturesSnapshot> inputs = new ArrayList<>(GoldenFixtures.all().values());

        List<MarketSignalSnapshot> outputs = harness.replay(inputs, ReplayHarness.fixed(EVALUATED_AT));

        assertEquals(inputs.size(), outputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            assertEquals(inputs.get(i).snapshotId(), outputs.get(i).sourceFeatureSnapshotId());
            assertEquals(EVALUATED_AT, outputs.get(i).createdAt());
        }
    }

    @Test
    void defaultResolverPinsEvaluationToComputedAt() {
        MarketFeaturesSnapshot input = GoldenFixtures.all().get("neutral-dead-zone");

        MarketSignalSnapshot output = harness.replay(List.of(input)).get(0);

        assertEquals(GoldenFixtures.COMPUTED_AT, output.createdAt());
        assertFalse(output.validUntil().isBefore(GoldenFixtures.COMPUTED_AT));
    }

    private void assertGolden(String name, MarketFeaturesSnapshot input) throws IOException {
        MarketSignalSnapshot output = harness.replay(List.of(input), ReplayHarness.fixed(EVALUATED_AT)).get(0);
        String actual = SnapshotCanonicalText.render(output);
        Path file = GOLDEN_DIR.resolve(name + ".txt");

        if (updateMode()) {
            Files.createDirectories(GOLDEN_DIR);
            Files.writeString(file, actual, StandardCharsets.UTF_8);
            return;
        }

        if (!Files.exists(file)) {
            fail("missing golden file " + file + " — run with GOLDEN_UPDATE=true to create it, then review and commit");
        }
        String expected = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!expected.equals(actual)) {
            fail("golden mismatch for '" + name + "' (" + file + ")\n" + firstDifference(expected, actual)
                    + "\nIf the change is intended: GOLDEN_UPDATE=true, review the diff, commit separately.");
        }
    }

    private static boolean updateMode() {
        return Boolean.parseBoolean(System.getProperty("golden.update", ""))
                || Boolean.parseBoolean(System.getenv().getOrDefault("GOLDEN_UPDATE", ""));
    }

    private static String firstDifference(String expected, String actual) {
        String[] e = expected.split("\n", -1);
        String[] a = actual.split("\n", -1);
        int n = Math.max(e.length, a.length);
        for (int i = 0; i < n; i++) {
            String el = i < e.length ? e[i] : "<EOF>";
            String al = i < a.length ? a[i] : "<EOF>";
            if (!el.equals(al)) {
                return "first difference at line " + (i + 1) + "\n  expected: " + el + "\n  actual:   " + al;
            }
        }
        return "(no line-level difference found; check line endings)";
    }
}
