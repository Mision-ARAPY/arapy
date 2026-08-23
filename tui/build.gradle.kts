plugins {
    kotlin("jvm") version "2.4.0"
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.compose.compiler)
    application
}

group = "com.github.arapy.tui"
version = "unspecified"

dependencies {
    testImplementation(kotlin("test"))
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.mosaic.runtime)
    implementation(libs.orbit.core)
}

kotlin {
    jvmToolchain(23)
}

application {
    mainClass = "com.github.arapy.tui.MainKt"
    // Mosaic habla con la terminal via FFI (Libmosaic); sin esto, Java 23 solo tira warning
    // por ahora, pero "Restricted methods will be blocked in a future release" - mejor
    // habilitarlo ya. IMPORTANTE: esta app necesita una TTY real - correrla con
    // `build/install/tui/bin/tui` desde una terminal de verdad, no con `./gradlew run` ni
    // desde la consola capturada del IDE, porque ninguna de esas dos te da un TTY de verdad.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    useJUnitPlatform()
}