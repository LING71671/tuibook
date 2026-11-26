plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("dev.detekt") version "2.0.0-alpha.3"
    application
    jacoco
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test-junit"))
}

dependencyLocking {
    lockAllConfigurations()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "tuibook.MainKt"
}

jacoco {
    toolVersion = "0.8.16"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport, "detekt")
}
