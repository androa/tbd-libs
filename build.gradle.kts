import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

val junitJupiterVersion = "5.11.3"
val mockkVersion = "1.13.13"
val jacksonVersion = "2.18.1"
val testcontainersVersion = "1.20.3"
val kotlinxCoroutinesVersion = "1.9.0"

plugins {
    kotlin("jvm") version "2.0.21" apply false
    `maven-publish`
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.gradle.maven-publish")

    ext.set("testcontainersVersion", testcontainersVersion)

    val api by configurations
    val testImplementation by configurations
    val testRuntimeOnly by configurations
    dependencies {
        constraints {
            api("com.fasterxml.jackson:jackson-bom:$jacksonVersion") {
                because("Alle moduler skal bruke samme versjon av jackson")
            }
            api("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion") {
                because("Alle moduler skal bruke samme versjon av jackson")
            }
            api("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion") {
                because("Alle moduler skal bruke samme versjon av jackson")
            }
            api("io.mockk:mockk:$mockkVersion") {
                because("Alle moduler skal bruke samme versjon av mockk")
            }
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinxCoroutinesVersion") {
                because("Alle moduler skal bruke samme versjon av coroutines")
            }
        }

        testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    configure<KotlinJvmProjectExtension> {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of("21"))
        }
    }

    configure<JavaPluginExtension> {
        withSourcesJar()
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                groupId = "com.github.navikt.tbd-libs"
                artifactId = project.name
                version = "${this@subprojects.version}"
            }
        }
        repositories {
            maven {
                url = uri("https://maven.pkg.github.com/navikt/tbd-libs")
                credentials {
                    username = System.getenv("GITHUB_USERNAME")
                    password = System.getenv("GITHUB_PASSWORD")
                }
            }
        }
    }

    tasks {
        withType<Test> {
            useJUnitPlatform()
            testLogging {
                events("skipped", "failed")
            }

            systemProperty("junit.jupiter.execution.parallel.enabled", "true")
            systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
            systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
            systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "8")
        }
    }
}
