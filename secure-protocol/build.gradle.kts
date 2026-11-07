import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

dependencies {
    compileOnly("com.google.crypto.tink:tink:1.24.0")
    testImplementation("com.google.crypto.tink:tink:1.24.0")
    testImplementation(kotlin("test-junit"))
}

tasks.test { useJUnit() }
