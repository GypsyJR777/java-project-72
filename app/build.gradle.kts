import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    application
    checkstyle
    jacoco
    id("com.gradleup.shadow") version "9.3.2"
    id("org.sonarqube") version "7.1.0.6387"
}

group = "hexlet.code"
version = "1.0-SNAPSHOT"

apply(plugin = "com.github.ben-manes.versions")

buildscript {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }

    dependencies {
        classpath("com.github.ben-manes:gradle-versions-plugin:0.53.0")
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("com.konghq:unirest-java:3.14.5")
    implementation("com.h2database:h2:2.3.232")
    implementation("gg.jte:jte:3.2.3")
    implementation("io.javalin:javalin:6.7.0")
    implementation("io.javalin:javalin-rendering:6.7.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("org.slf4j:slf4j-simple:2.0.17")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

application {
    mainClass = "hexlet.code.App"
}

tasks.named<Jar>("shadowJar") {
    archiveClassifier.set("all")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

sonar {
    properties {
        property("sonar.projectKey", "GypsyJR777_java-project-72")
        property("sonar.organization", "gypsyjr777")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/test/jacocoTestReport.xml")
    }
}
