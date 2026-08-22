plugins {
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
    java
}

dependencies {
    implementation(project(":application"))
    implementation(project(":infrastructure:event-adapter"))

    implementation(libs.springBootStarter)
    implementation(libs.springBootStarterActuator)
    implementation(libs.springBootStarterValidation)
    implementation("org.springframework.kafka:spring-kafka")

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.springBootStarterTest)
    // Kafka integration tests run on an in-JVM EmbeddedKafka (KRaft) + mock:// Schema Registry:
    // no Docker needed, deterministic in CI.
    testImplementation(libs.springKafkaTest)
    testImplementation(libs.schemas)
    testImplementation(libs.kafkaAvroSerializer)
    testImplementation("org.springframework.kafka:spring-kafka")

    // Version managed by the Boot BOM so the launcher matches the JUnit platform that
    // spring-boot-starter-test brings (a pinned 1.11 launcher + Boot's 1.10 platform fails to start).
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
