import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun git(vararg args: String): String? = runCatching {
    val out = ByteArrayOutputStream()
    val result = project.exec {
        commandLine("git", *args)
        standardOutput = out
        errorOutput = ByteArrayOutputStream()
        isIgnoreExitValue = true
    }
    if (result.exitValue == 0) out.toString().trim().ifEmpty { null } else null
}.getOrNull()

// versionName dérive du dernier tag Git `v*` (jamais codé en dur).
val gitVersionName: String = git("describe", "--tags", "--match", "v*", "--abbrev=0")
    ?.removePrefix("v") ?: "0.0.0-dev"
val gitVersionCode: Int = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

android {
    namespace = "com.gallakk.pdfmanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gallakk.pdfmanager"
        minSdk = 26
        targetSdk = 35
        versionCode = gitVersionCode
        versionName = gitVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation(project(":pdf"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

// Échoue si la permission INTERNET apparaît dans le manifeste fusionné.
val verifyNoInternetPermission by tasks.registering {
    group = "verification"
    description = "Fails if the merged manifest declares android.permission.INTERNET."
    dependsOn("processDebugMainManifest")
    val mergedDir = layout.buildDirectory.dir("intermediates")
    doLast {
        val manifests = mergedDir.get().asFile.walkTopDown()
            .filter { it.name == "AndroidManifest.xml" && it.path.contains("merged_manifest") }
            .toList()
        check(manifests.isNotEmpty()) { "Merged manifest not found" }
        manifests.forEach {
            check(!it.readText().contains("android.permission.INTERNET")) {
                "INTERNET permission found in ${it.path}"
            }
        }
    }
}
tasks.matching { it.name == "check" }.configureEach { dependsOn(verifyNoInternetPermission) }
