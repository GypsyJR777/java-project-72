plugins {
    application
    checkstyle
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
    implementation("com.h2database:h2:2.3.232")
    implementation("io.javalin:javalin:6.7.0")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("org.slf4j:slf4j-simple:2.0.17")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
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
    }
}
