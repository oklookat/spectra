import java.util.Properties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val secrets = Properties()
val secretsFile = file("secrets.properties")

if (secretsFile.exists()) {
    secretsFile.inputStream().use { secrets.load(it) }
}

extra["secrets"] = secrets