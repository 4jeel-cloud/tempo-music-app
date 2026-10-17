
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
        // rhino-engine (JSR-223) is not referenced by NewPipeExtractor at all.
        exclude(group = "org.mozilla", module = "rhino")
        exclude(group = "org.mozilla", module = "rhino-engine")
    }
    // NewPipeExtractor v0.26.5 needs Rhino 1.8.1 at runtime to execute YouTube's player JS.
    // AGP 7.4.2's bundled D8 cannot dex Rhino 1.8.1 (StackOverflowError / NullPointerException
    // during the DexingWithClasspath transform), so the root build overrides
    // com.android.tools:r8 to 8.5.35, whose D8 dexes it correctly. Keep the two in sync.
    implementation("org.mozilla:rhino:1.8.1")
    // Keep okhttp on the version the rest of the project already uses (Coil/ktor). Newer
    // releases pull Kotlin stdlib 1.9 metadata, which the 1.7.20 compiler cannot read.
    implementation("com.squareup.okhttp3:okhttp:4.10.0")

    testImplementation(testLibs.junit)
}
