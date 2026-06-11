// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}

tasks.register("copyOutputs") {
    doLast {
        val rootDir = project.rootDir
        val srcApk = File(rootDir, ".build-outputs/app-debug.apk")
        val destDir = File(rootDir, "build-outputs")
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        if (srcApk.exists()) {
            srcApk.copyTo(File(destDir, "app-debug.apk"), overwrite = true)
            println("Successfully copied APK to build-outputs/app-debug.apk")
        } else {
            println("Source APK not found at .build-outputs/app-debug.apk")
        }

        val srcKeystore = File(rootDir, "debug.keystore.base64")
        if (srcKeystore.exists()) {
            srcKeystore.copyTo(File(rootDir, "debug_keystore_base64.txt"), overwrite = true)
            println("Successfully copied keystore to debug_keystore_base64.txt")
        } else {
            println("Source keystore not found at debug.keystore.base64")
        }
    }
}


