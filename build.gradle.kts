import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.50"
    id("com.github.johnrengelman.shadow") version "4.0.4"
}

group = "com.uadaf.quoter"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
    maven("http://52.48.142.75/maven")
    maven("https://kotlin.bintray.com/kotlinx")
    maven("https://jitpack.io")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("rocks.waffle.telekt:telekt:0.6.7")
    implementation("com.uadaf:quoter-api:1.3.1")
    implementation("io.ktor:ktor-client-apache:1.2.0")
    implementation("org.slf4j:slf4j-simple:1.7.25")
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "quoterbot.MainKt"
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}