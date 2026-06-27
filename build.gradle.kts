import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer

group = "com.trading"
version = "0.1.0-SNAPSHOT"

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://packages.confluent.io/maven/")
    }
}

val collectSources = tasks.register("collectSources") {
    group = "documentation"
    description = "Concatenates all production source code (excluding tests) into build/sources/all-sources.txt."

    val outputFile = layout.buildDirectory.file("sources/all-sources.txt")
    outputs.file(outputFile)

    doLast {
        val files = inputs.files.files.sortedBy { it.absolutePath }
        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        out.bufferedWriter().use { writer ->
            files.forEach { file ->
                val relativePath = file.relativeTo(rootDir).path.replace('\\', '/')
                writer.appendLine("// ===== $relativePath =====")
                writer.appendLine(file.readText())
                writer.appendLine()
            }
        }
        logger.lifecycle("Collected ${files.size} source files into ${out.relativeTo(rootDir).path.replace('\\', '/')}")
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        val mainSource = extensions.getByType(SourceSetContainer::class.java)
            .getByName("main").allSource
        collectSources.configure { inputs.files(mainSource) }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

tasks.wrapper {
    gradleVersion = "9.2.1"
    distributionType = Wrapper.DistributionType.BIN
}
