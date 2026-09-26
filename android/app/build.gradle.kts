import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    // DustUI production runtime is intentionally arm64-v8a.
    // x86_64 is not packaged because the Go runtime contract binds production to arm64.
    lint {
        disable += "ChromeOsAbiSupport"
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

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isJniDebuggable = true
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
}
