// Top-level build file where you can add configuration options common to all sub-projects/modules.
//buildscript {
//    repositories {
//        google()
//        mavenCentral()
//    }
//    dependencies {
//        classpath("com.spotify.ruler:ruler-gradle-plugin:2.0.0-alpha-13")
//    }
//}


plugins {
    id("com.android.application") version "8.1.4" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}



tasks.register("clean",Delete::class){
    delete(rootProject.buildDir)
}