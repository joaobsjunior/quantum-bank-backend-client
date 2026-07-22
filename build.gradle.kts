import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

group = "com.quantumbank"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Only produce the executable Spring Boot jar (not the extra `-plain` jar), so
// the Docker runtime stage can COPY a single `build/libs/*.jar`.
tasks.named<Jar>("jar") {
    enabled = false
}

// Coverage enforcement (test-coverage-enforcement capability).
// Generates XML + HTML reports and enforces a 100% line-coverage minimum,
// wired into the `check` lifecycle so `gradle check` fails below target.
kover {
    reports {
        filters {
            excludes {
                // Reviewed exclusions: Spring Boot bootstrap entrypoint has no
                // testable branching logic (only `runApplication`). Keep this
                // list minimal and justified so 100% stays honest.
                classes(
                    "com.quantumbank.backendclient.QuantumBankBackendClientApplication",
                    "com.quantumbank.backendclient.QuantumBankBackendClientApplicationKt",
                )
            }
        }
        total {
            xml { onCheck = true }
            html { onCheck = true }
            verify {
                onCheck = true
                rule {
                    bound {
                        minValue = 100
                        coverageUnits = CoverageUnit.LINE
                    }
                }
            }
        }
    }
}
