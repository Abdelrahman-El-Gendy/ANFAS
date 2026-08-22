import com.anfas.buildlogic.appVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

/**
 * Thin Android launcher. AGP 9 no longer allows the KMP plugin in the same module as
 * com.android.application, so this module is Android-only and everything shared comes from
 * :composeApp. It cannot use a kmp.* convention plugin because it is not a KMP module.
 */
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    id("anfas.quality")
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":composeApp"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

/**
 * Release signing, read from a gitignored properties file.
 *
 * Returns null when the file is absent, and the release build type then falls back to the debug
 * signing config. That fallback is deliberate: a contributor without the keystore must still be
 * able to run `assembleRelease` locally, and CI supplies the real material through the same file.
 * An unsigned release APK cannot be uploaded, so the fallback is a convenience, never a shipping
 * path -- verify with `apksigner verify --print-certs`.
 *
 * Read through the provider API rather than at configuration time so the configuration cache
 * records the file as an input: rotating a secret then invalidates the cached configuration
 * instead of silently reusing a build configured with the old one.
 *
 * [Properties] is imported rather than fully qualified because inside a Gradle Kotlin DSL script
 * `java` resolves to the JavaPluginExtension, which shadows the package name.
 */
val keystoreProperties: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().also { props -> file.inputStream().use(props::load) } }

android {
    namespace = "com.anfas.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.anfas.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // From gradle/version.properties -- the single source of truth shared with iOS
        // (MARKETING_VERSION / CURRENT_PROJECT_VERSION) and desktop (packageVersion).
        versionCode = appVersion().get().versionCode
        versionName = appVersion().get().versionName

        // Only the languages the app actually ships. Without this every AndroidX translation
        // for ~70 locales rides along in the APK.
        androidResources {
            localeFilters += listOf("en", "ar")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        keystoreProperties?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                // Both schemes: v1 for the API 24 floor, v2+ for everything since.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }

        /**
         * Minified like release, but debuggable and debug-signed.
         *
         * This exists so R8 output can actually be tested. `connectedAndroidTest` needs a
         * debuggable APK and `release` must never be one, so without a third build type the
         * first time R8 runs is under release pressure -- and every R8 failure mode here is
         * release-only and silent.
         */
        create("stage") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = true
            applicationIdSuffix = ".stage"
            versionNameSuffix = "-stage"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    // Instrumented tests run against the minified APK, which is the whole point of `stage`.
    testBuildType = "stage"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}
