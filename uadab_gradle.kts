import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.0"
    id("com.github.johnrengelman.shadow") version "4.0.4"
}

group = "com.gt22"
version = "2.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
    maven { url = uri("http://52.48.142.75/maven") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://dl.bintray.com/kotlin/ktor") }
}
val jdaVersion = "3.8.3_462"
val gsonVersion = "2.8.5"
val ktorVersion = "1.0.1"
dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.0.1")
    compile("net.dv8tion:JDA:$jdaVersion")
    compile("com.google.code.gson:gson:$gsonVersion")
    compile("org.slf4j:slf4j-simple:1.7.25")
    compile("de.codecentric.centerdevice:javafxsvg:1.3.0")
    compile("io.ktor:ktor-client-core:$ktorVersion")
    compile("io.ktor:ktor-client-apache:$ktorVersion")
    compile("org.jetbrains.exposed:exposed:0.10.4")
    compile("mysql:mysql-connector-java:6.0.6")
    compile("com.github.kizitonwose.time:time:1.0.2")
    compile("com.uadaf:uadamusic:2.5")
    compile("com.uadaf:quoter-api:1.1")
    compile("com.sedmelluq:lavaplayer:1.3.12")
    compile("pl.droidsonroids:jspoon:1.3.2")
    testCompile("junit:junit:4.12")
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "UADAB"
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}