plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

val innertubeSources = rootProject.file("../Metrolists-main/innertube/src/main/kotlin")
val stubSources = layout.projectDirectory.dir("src/main/kotlin").asFile

kotlin.sourceSets.named("main") {
    kotlin.setSrcDirs(listOf(innertubeSources, stubSources))
}

dependencies {
    api("com.github.MetrolistGroup.innertubex:innertubex:v0.11.0")
    implementation("io.ktor:ktor-client-core:3.6.0")
    implementation("io.ktor:ktor-client-okhttp:3.6.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.6.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
    implementation("io.ktor:ktor-client-encoding:3.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
