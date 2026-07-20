plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    
    jvm()
    
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            // pure kotlin dependencies
        }
        commonTest.dependencies {
            implementation("org.jetbrains.kotlin:kotlin-test")
        }
    }
}

android {
    namespace = "com.google.android.stardroid.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
}
