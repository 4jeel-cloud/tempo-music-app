
plugins {
    kotlin("jvm")
    @Suppress("DSL_SCOPE_VIOLATION")
    alias(libs.plugins.kotlin.serialization)
}

sourceSets.all {
    java.srcDir("src/$name/kotlin")
}

dependencies {
    implementation(projects.ktorClientBrotli)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.serialization.json)

    implementation(libs.kotlin.coroutines)

    // NewPipeExtractor replaces the dead InnerTube player path: it handles
    // YouTube client rotation, signature (n-param) deobfuscation and stream URLs.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5") {
        // Rhino 1.8.x is not dexable by AGP 7.4's D8 (NullPointerException in DexingWithClasspath
        // Transform). NewPipeExtractor only touches the stable Rhino core API, so pin 1.7.15.
        // rhino-engine (JSR-223) is not referenced by NewPipeExtractor at all.
        exclude(group = "org.mozilla", module = "rhino")
        exclude(group = "org.mozilla", module = "rhino-engine")
    }
    implementation("org.mozilla:rhino:1.7.15")
    // Keep okhttp on the version the rest of the project already uses (Coil/ktor). Newer
    // releases pull Kotlin stdlib 1.9 metadata, which the 1.7.20 compiler cannot read.
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    testImplementation(testLibs.junit)
}
