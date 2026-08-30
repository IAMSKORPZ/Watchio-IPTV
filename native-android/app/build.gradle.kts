plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.watchioiptv.nativeapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.watchioiptv.nativeapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.0-dev.3"
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

    signingConfigs {
        val devKeystorePath = providers.environmentVariable("WATCHIO_DEV_KEYSTORE_PATH")
        val devKeystorePassword = providers.environmentVariable("WATCHIO_DEV_KEYSTORE_PASSWORD")
        val devKeyAlias = providers.environmentVariable("WATCHIO_DEV_KEY_ALIAS")
        val devKeyPassword = providers.environmentVariable("WATCHIO_DEV_KEY_PASSWORD")
        if (
            devKeystorePath.isPresent &&
            devKeystorePassword.isPresent &&
            devKeyAlias.isPresent &&
            devKeyPassword.isPresent
        ) {
            getByName("debug") {
                storeFile = file(devKeystorePath.get())
                storePassword = devKeystorePassword.get()
                keyAlias = devKeyAlias.get()
                keyPassword = devKeyPassword.get()
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
        }
        create("uitest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uitest"
            versionNameSuffix = "-uitest"
            matchingFallbacks += listOf("debug")
            resValue("string", "app_name", "Watchio Test")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
