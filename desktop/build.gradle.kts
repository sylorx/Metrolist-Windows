import org.gradle.api.tasks.testing.Test
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":innertube-jvm"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.bytedeco:javacv:1.5.11")
    implementation("org.bytedeco:ffmpeg:7.1-1.5.11:windows-x86_64")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.metrolist.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "Metrolist"
            packageVersion = "1.0.0"
            description = "Metrolist for Windows"
            vendor = "Metrolist"
            windows {
                menuGroup = "Metrolist"
                upgradeUuid = "6f1c1b2e-7a4d-4e5b-9c1a-2d8e4f6a0b11"
            }
        }
    }
}
