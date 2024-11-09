val testcontainersVersion: String by project
val kotlinxCoroutinesVersion: String by project
val jacksonVersion: String by project
val kafkaVersion = "3.8.0"

val logbackClassicVersion = "1.4.14"
val logbackEncoderVersion = "7.4"

dependencies {
    api("org.testcontainers:kafka:$testcontainersVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion")
    // konsumenter av biblioteket må selv vurdere hvilken kafkaversjon de vil ha
    // (implementation 'lekker' ikke ut på compile-classpath til konsumentene
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    //testImplementation("ch.qos.logback:logback-classic:$logbackClassicVersion")
    //testImplementation("net.logstash.logback:logstash-logback-encoder:$logbackEncoderVersion")
}
