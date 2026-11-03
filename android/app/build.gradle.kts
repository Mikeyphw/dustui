import org.gradle.api.tasks.Exec
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val dustuiRepositoryRoot = rootProject.projectDir.parentFile.canonicalFile
val dustuiSigningPropertiesFile = File(
    providers.environmentVariable("DUSTUI_ANDROID_SIGNING_PROPERTIES").orNull
        ?: File(
            System.getProperty("user.home"),
            ".config/devtool/dustui/android-signing.properties",
        ).absolutePath,
).absoluteFile

val dustuiPersonalSigningConfigured = dustuiSigningPropertiesFile.isFile
val dustuiSigningProperties = Properties().apply {
    if (dustuiPersonalSigningConfigured) {
        dustuiSigningPropertiesFile.inputStream().use { load(it) }
    }
}

fun dustuiSigningValue(name: String): String {
    val value = dustuiSigningProperties.getProperty(name)?.trim().orEmpty()
    check(value.isNotEmpty() && value != "REPLACE_ME") {
        "DustUI personal signing property '$name' is missing in ${dustuiSigningPropertiesFile.absolutePath}"
    }
    return value
}

val dustuiPersonalSigningStoreFile = if (dustuiPersonalSigningConfigured) {
    check(!dustuiSigningPropertiesFile.canonicalFile.toPath().startsWith(dustuiRepositoryRoot.toPath())) {
        "DustUI signing properties must live outside the repository: ${dustuiSigningPropertiesFile.absolutePath}"
    }
    File(dustuiSigningValue("storeFile")).canonicalFile.also { store ->
        check(store.isAbsolute && store.isFile) {
            "DustUI personal signing keystore does not exist: ${store.absolutePath}"
        }
        check(!store.toPath().startsWith(dustuiRepositoryRoot.toPath())) {
            "DustUI personal signing keystore must live outside the repository: ${store.absolutePath}"
        }
    }
} else {
    null
}

android {
    // DustUI production runtime is intentionally arm64-v8a.
    // x86_64 is not packaged because the Go runtime contract binds production to arm64.
    lint {
        disable += "ChromeOsAbiSupport"
    }

    // Release-only instrumentation explicitly tests the signed production variant.
    // M04/default debug tasks remain unchanged and never select this test build type.
    if (providers.environmentVariable("DUSTUI_M05_INSTRUMENTATION_BUILD").orNull == "true") {
        testBuildType = "release"
    }

    namespace = "com.mikeyphw.dustui"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.mikeyphw.dustui"
        minSdk = 26
        targetSdk = 36
        versionCode = 1400
        versionName = "1.4.0-rc1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testApplicationId = "com.mikeyphw.dustui.test"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (dustuiPersonalSigningConfigured) {
            create("dustuiPersonal") {
                storeFile = dustuiPersonalSigningStoreFile
                storePassword = dustuiSigningValue("storePassword")
                keyAlias = dustuiSigningValue("keyAlias")
                keyPassword = dustuiSigningValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isJniDebuggable = true
        }
        release {
            testProguardFiles("proguard-androidTest-rules.pro")
            // The separate AndroidX test runner shares the release app classloader.
            // Only M05's explicit release-instrumentation build needs these classes.
            if (providers.environmentVariable("DUSTUI_M05_INSTRUMENTATION_BUILD").orNull == "true") {
                proguardFiles("proguard-m05-release-test-rules.pro")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            if (dustuiPersonalSigningConfigured) {
                signingConfig = signingConfigs.getByName("dustuiPersonal")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("main").jniLibs.directories.add(layout.buildDirectory.dir("generated/goRuntime/jniLibs").get().asFile.absolutePath)
    }


    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        lintConfig = file("lint.xml")
    }
}

val dustuiSigningStatus = tasks.register("dustuiSigningStatus") {
    group = "help"
    description = "Show whether DustUI permanent personal release signing is configured."

    // Resolve build-script-backed values during configuration so the task
    // action captures only configuration-cache-safe values.
    val signingConfigured = dustuiPersonalSigningConfigured
    val signingPropertiesPath = dustuiSigningPropertiesFile.absolutePath
    val signingStorePath = dustuiPersonalSigningStoreFile?.absolutePath.orEmpty()
    val signingAlias = if (signingConfigured) dustuiSigningValue("keyAlias") else ""

    doLast {
        if (signingConfigured) {
            println("DustUI personal signing: configured")
            println("Properties: $signingPropertiesPath")
            println("Keystore: $signingStorePath")
            println("Alias: $signingAlias")
            println("Release APKs use this permanent certificate.")
        } else {
            println("DustUI personal signing: NOT configured")
            println("Expected properties: $signingPropertiesPath")
            println("Run bash android/tools/setup-personal-signing.sh or set DUSTUI_ANDROID_SIGNING_PROPERTIES.")
        }
    }
}

val verifyDustuiPersonalSigningConfigured = tasks.register("verifyDustuiPersonalSigningConfigured") {
    group = "verification"
    description = "Fail unless DustUI permanent personal release signing is configured outside the repository."

    // Do not dereference Kotlin build-script properties from the execution
    // action: configuration cache cannot serialize the script object.
    val signingConfigured = dustuiPersonalSigningConfigured
    val signingStoreExists = dustuiPersonalSigningStoreFile?.isFile == true

    doLast {
        check(signingConfigured && signingStoreExists) {
            "DustUI permanent personal signing is not configured. " +
                "Run bash android/tools/setup-personal-signing.sh or set DUSTUI_ANDROID_SIGNING_PROPERTIES."
        }
        println("DustUI permanent personal signing configuration: PASS")
    }
}

// Fail before expensive release compilation/R8/package work.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyDustuiPersonalSigningConfigured)
}


kotlin {
    jvmToolchain(21)
}

val buildGoAndroidRuntime = tasks.register("buildGoAndroidRuntime", Exec::class) {
    group = "build"
    description = "Build the production arm64 Android Go runtime and privileged root worker packaged as libdustui_go.so"
    val goRoot = rootProject.projectDir.parentFile.resolve("go")
    val output = layout.buildDirectory.file("generated/goRuntime/jniLibs/arm64-v8a/libdustui_go.so")
    inputs.files(fileTree(goRoot) { include("**/*.go", "go.mod", "go.sum") })
    outputs.file(output)
    workingDir(goRoot)
    environment("GOOS", "android")
    environment("GOARCH", "arm64")
    environment("CGO_ENABLED", "0")
    doFirst { output.get().asFile.parentFile.mkdirs() }
    commandLine("go", "build", "-trimpath", "-buildmode=pie", "-o", output.get().asFile.absolutePath, "./cmd/dustui-go")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(buildGoAndroidRuntime)
    dependsOn(rootProject.tasks.named("verifyStableToolchainBaseline"))
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)
    implementation(libs.json)


    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.errorprone.annotations)
    if (providers.environmentVariable("DUSTUI_M05_INSTRUMENTATION_BUILD").orNull == "true") {
        // AndroidJUnitRunner 1.6.2 references androidx.tracing.Trace at startup.
        // M05's minified app and test APK are separate classloader participants.
        implementation("androidx.tracing:tracing:1.3.0")
        androidTestImplementation("androidx.tracing:tracing:1.3.0")
    }
}
