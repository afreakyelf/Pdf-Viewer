plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.rajat.sample.pdfviewer"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.rajat.sample.pdfviewer"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    kotlin {
        jvmToolchain(21)
    }

    buildTypes {
        buildTypes {
            release {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        managedDevices {
            localDevices {
                create("pixel2api30") {
                    device = "Pixel 2"
                    apiLevel = 30
                }
                create("api27Pixel") {
                    device = "Pixel 3a"
                    apiLevel = 27
                    systemImageSource = "aosp"
                }
                create("api35Pixel") {
                    device = "Pixel 5"
                    apiLevel = 35
                    systemImageSource = "aosp"
                }
            }
            // Create a group to test across all defined devices
            groups {
                create("allApis") {
                    targetDevices.addAll(localDevices)
                }
            }
        }
    }

}


dependencies {

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.test.espresso:espresso-contrib:3.7.0")
    val kotlin_version = "2.2.10"
    implementation(kotlin("stdlib"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    //noinspection GradleDependency
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.compose.ui:ui-graphics")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    implementation(project(":pdfViewer"))
//    implementation("io.github.afreakyelf:Pdf-Viewer:2.1.1")
    testImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.3.0")

    implementation("androidx.recyclerview:recyclerview:1.4.0") // Check for the latest version available

    // compose
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))

    // Choose one of the following:
    // Material Design 3
    implementation("androidx.compose.material3:material3")
    // or Material Design 2
    implementation("androidx.compose.material:material")
    // or skip Material Design and build directly on top of foundational components
    implementation("androidx.compose.foundation:foundation")
    // or only import the main APIs for the underlying toolkit systems,
    // such as input and measurement/layout
    implementation("androidx.compose.ui:ui")

    // Android Studio Preview support
    implementation("androidx.compose.ui:ui-tooling-preview")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
    debugImplementation("androidx.compose.ui:ui-tooling")

    // UI Tests
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")


    // Optional - Integration with activities
    implementation("androidx.activity:activity-compose:1.10.1")


}
