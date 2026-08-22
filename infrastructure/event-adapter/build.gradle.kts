plugins {
    `java-library`
}

dependencies {
    implementation(platform(libs.springBom))
    implementation(project(":application"))

    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation(libs.schemas)
    implementation(libs.kafkaAvroSerializer)
    implementation(libs.micrometerCore)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}
