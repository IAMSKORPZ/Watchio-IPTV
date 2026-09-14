import java.io.FileInputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

data class WatchioSigningIdentity(
    val name: String,
    val prefix: String,
)

fun normalizeFingerprint(value: String): String = value.replace(":", "").trim().lowercase()

fun verifySigningIdentity(identity: WatchioSigningIdentity, values: Map<String, String>) {
    val path = values.getValue("${identity.prefix}_KEYSTORE_PATH")
    val storePassword = values.getValue("${identity.prefix}_KEYSTORE_PASSWORD")
    val alias = values.getValue("${identity.prefix}_KEY_ALIAS")
    val expected = normalizeFingerprint(values.getValue("${identity.prefix}_CERT_SHA256"))
    val keyStore = KeyStore.getInstance("JKS")
    FileInputStream(File(path)).use { keyStore.load(it, storePassword.toCharArray()) }
    val certificate = keyStore.getCertificate(alias)
        ?: throw GradleException("${identity.name} signing alias '$alias' not found")
    val actual = MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString("") { "%02x".format(it) }
    if (actual != expected) {
        throw GradleException("${identity.name} signing certificate mismatch: got $actual expected $expected")
    }
}

android {
    namespace = "com.watchioiptv.nativeapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.watchioiptv.nativeapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 13
        versionName = "0.1.0-dev.12"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val tmdbApiKey = providers.gradleProperty("WATCHIO_TMDB_API_KEY")
            .orElse(providers.environmentVariable("WATCHIO_TMDB_API_KEY"))
            .orElse("")
            .get()
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    val requestedTasks = gradle.startParameter.taskNames.map {
        it.substringAfterLast(':').lowercase()
    }
    val requestsAllBuilds = gradle.startParameter.taskNames.any {
        it.substringAfterLast(':').lowercase() in setOf("assemble", "build")
    }
    val identities = listOf(
        WatchioSigningIdentity("DEV", "WATCHIO_DEV"),
        WatchioSigningIdentity("PUBLIC", "WATCHIO_PUBLIC"),
    )
    val required = mapOf(
        "WATCHIO_DEV" to (requestsAllBuilds || requestedTasks.any {
            it in setOf("assembledebug", "packagedebug", "bundledebug", "installdebug")
        }),
        "WATCHIO_PUBLIC" to (requestsAllBuilds || requestedTasks.any { "release" in it }),
    )
    val signingValues = identities.associate { identity ->
        val names = listOf(
            "${identity.prefix}_KEYSTORE_PATH",
            "${identity.prefix}_KEYSTORE_PASSWORD",
            "${identity.prefix}_KEY_ALIAS",
            "${identity.prefix}_KEY_PASSWORD",
            "${identity.prefix}_CERT_SHA256",
        )
        val values = names.associateWith { providers.environmentVariable(it).orNull.orEmpty() }
        if (required.getValue(identity.prefix)) {
            val missing = values.filterValues { it.isBlank() }.keys
            if (missing.isNotEmpty()) {
                throw GradleException("Missing ${identity.name} signing configuration: ${missing.joinToString()}")
            }
            verifySigningIdentity(identity, values)
        } else if (values.values.none { it.isBlank() }) {
            verifySigningIdentity(identity, values)
        }
        identity.prefix to values
    }.toMap()

    signingConfigs {
        create("watchioDev") {
            val values = signingValues.getValue("WATCHIO_DEV")
            if (values.values.none { it.isBlank() }) {
                storeFile = file(values.getValue("WATCHIO_DEV_KEYSTORE_PATH"))
                storePassword = values.getValue("WATCHIO_DEV_KEYSTORE_PASSWORD")
                keyAlias = values.getValue("WATCHIO_DEV_KEY_ALIAS")
                keyPassword = values.getValue("WATCHIO_DEV_KEY_PASSWORD")
            }
        }
        create("watchioPublic") {
            val values = signingValues.getValue("WATCHIO_PUBLIC")
            if (values.values.none { it.isBlank() }) {
                storeFile = file(values.getValue("WATCHIO_PUBLIC_KEYSTORE_PATH"))
                storePassword = values.getValue("WATCHIO_PUBLIC_KEYSTORE_PASSWORD")
                keyAlias = values.getValue("WATCHIO_PUBLIC_KEY_ALIAS")
                keyPassword = values.getValue("WATCHIO_PUBLIC_KEY_PASSWORD")
            }
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("watchioDev")
            resValue("string", "app_name", "Watchio IPTV Dev")
        }
        create("local") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".local"
            versionNameSuffix = "-local"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "Watchio IPTV Local")
        }
        create("uitest") {
            initWith(getByName("local"))
            applicationIdSuffix = ".uitest"
            versionNameSuffix = "-uitest"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "Watchio IPTV Test")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("watchioPublic")
        }
    }

    testBuildType = "uitest"

    testOptions {
        val runId = System.getenv("WATCHIO_ANDROID_TEST_RUN_ID") ?: System.currentTimeMillis().toString()
        resultsDir = layout.buildDirectory.dir("test-results-phase2/$runId").get().asFile.absolutePath
        reportDir = layout.buildDirectory.dir("reports-phase2/$runId").get().asFile.absolutePath
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.converter)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.work.runtime.ktx)
    implementation(libs.security.crypto)
    implementation(libs.zxing.core)
    implementation(libs.play.services.code.scanner)
    implementation(libs.brotli.dec)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.manifest)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    add("uitestImplementation", libs.compose.ui.tooling)
    add("uitestImplementation", libs.compose.ui.test.manifest)
}

tasks.matching { it.name == "connectedUitestAndroidTest" }.configureEach {
    dependsOn("installUitest")
}
