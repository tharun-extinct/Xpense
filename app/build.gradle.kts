plugins {
    alias(libs.plugins.android.application)
    // No kotlin-android plugin: AGP 9.0+ provides built-in Kotlin support, and applying
    // org.jetbrains.kotlin.android alongside it is rejected (blueprints/build-and-ci.md).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.xpensetracker.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.xpensetracker.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Signed with the debug key so release APKs are sideloadable. Android rejects
            // unsigned packages, and no upload key exists yet (blueprints/build-and-ci.md).
            signingConfig = signingConfigs.getByName("debug")

            // R8 shrinks, obfuscates and optimizes; `isShrinkResources` additionally opts into
            // AGP 9's optimized resource shrinking, which drops resources referenced only from
            // code R8 already removed. Full mode, class repackaging and access modification are
            // AGP 9.1 defaults and are deliberately not restated here — see
            // blueprints/build-and-ci.md for why the list of things NOT configured is the point.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                // -optimize, not the plain file: AGP 9 dropped proguard-android.txt because it
                // carries -dontoptimize.
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // With built-in Kotlin, kotlin.compilerOptions.jvmTarget defaults to
    // android.compileOptions.targetCompatibility above, so no explicit kotlinOptions block
    // is needed (architecture.md is unaffected; see blueprints/build-and-ci.md).

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true

            // Robolectric reaches into JDK internals to emulate Android's native layer, so on
            // Java 17+ it needs these opens or every test dies with "Failed to interact with raw
            // FileDescriptor internals" before reaching an assertion. This is Robolectric's own
            // published list; `jdk.internal.access` is the one that specific failure needs.
            // See blueprints/build-and-ci.md.
            all {
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                )
            }
        }
    }

    lint {
        // Plain-text report lets CI surface the exact findings in the job summary
        // (build-and-ci.md #diagnostics) without needing authenticated log downloads.
        textReport = true
        textOutput = file("$projectDir/build/reports/lint-results.txt")
        abortOnError = true
    }

    sourceSets {
        // Plain JVM parser tests load rule JSON via the classpath, not AssetManager, so they
        // stay Android-free (architecture.md #verification-boundaries). This exposes the same
        // assets shipped in the app without duplicating them under src/test/resources.
        getByName("test") {
            resources.srcDirs("src/main/assets")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
