import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compiler.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Windows + Linux masaüstü (modüler JVM hedefi)
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // iOS
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("build/generated/compose/resourceGenerator/kotlin/commonMainResourceAccessors")
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:${libs.versions.composeVersion.get()}")
                implementation("org.jetbrains.compose.foundation:foundation:${libs.versions.composeVersion.get()}")
                implementation("org.jetbrains.compose.material3:material3:${libs.versions.composeVersion.get()}")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                implementation("org.jetbrains.compose.ui:ui:${libs.versions.composeVersion.get()}")
                implementation("org.jetbrains.compose.components:components-resources:${libs.versions.composeVersion.get()}")
                implementation("org.jetbrains.compose.animation:animation:${libs.versions.composeVersion.get()}")
                implementation(libs.okio)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.jetbrains.lifecycle.runtime.compose)
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
                implementation(libs.jetbrains.navigation.compose)
                implementation(libs.coil.compose)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.ktor.client.core)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.androidx.room.runtime)
            }
        }

        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.newpipe.extractor)
                implementation(libs.okhttp)
            }
        }

        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation("org.jetbrains.compose.ui:ui-tooling-preview:${libs.versions.composeVersion.get()}")
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.media3.exoplayer)
                implementation(libs.media3.session)
                implementation(libs.media3.ui)
                implementation(libs.androidx.work.runtime.ktx)
                implementation(libs.koin.android)
                implementation(libs.androidx.room.ktx)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.coil.network.okhttp)
                implementation(libs.jaudiotagger)
                implementation(libs.youtubedl.android)
                implementation(libs.youtubedl.ffmpeg)
                implementation(libs.unity.ads)
            }
        }

        val desktopMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation("org.jetbrains.compose.desktop:desktop-jvm:${libs.versions.composeVersion.get()}")
                implementation(libs.okhttp)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.coil.network.okhttp)
                implementation(libs.jaudiotagger)
                // skiko yerel kitaplığı: paketleme (jpackage) ve çalışma için gerekli
                val gradleOs = org.gradle.internal.os.OperatingSystem.current()
                val skikoV = "0.9.22.2"
                if (gradleOs.isWindows) {
                    val arch = if (System.getProperty("os.arch").contains("aarch64")) "aarch64" else "x64"
                    runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-windows-$arch:$skikoV")
                } else if (gradleOs.isMacOsX) {
                    val arch = if (System.getProperty("os.arch").contains("aarch64")) "arm64" else "x64"
                    runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-macos-$arch:$skikoV")
                } else {
                    val arch = if (System.getProperty("os.arch").contains("aarch64")) "arm64" else "x64"
                    runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-linux-$arch:$skikoV")
                }
                runtimeOnly(libs.kotlinx.coroutines.swing)
            }
        }

        val iosMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.coil.network.ktor)
            }
        }
    }
}

android {
    namespace = "com.be.music"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.be.music"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "2.0.0-kmp"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a"))
        }
    }

    signingConfigs {
        val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
        val keystoreFile = file(keystorePath)
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = "upload"
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            versionNameSuffix = ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
    buildToolsVersion = "37.0.0"
    ndkVersion = "28.2.13676358"
}

afterEvaluate {
    tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
        dependsOn(
            "generateComposeResClass",
            "generateResourceAccessorsForCommonMain",
            "generateExpectResourceCollectorsForCommonMain",
            "generateActualResourceCollectorsForDesktopMain"
        )
    }
}

compose.desktop {
    application {
        mainClass = "com.be.music.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
            packageName = "BE-Music"
            packageVersion = "1.0.0"
            vendor = "BE-Music"
            description = "BE-Music crossed-platform music app"

            linux {
                iconFile.set(project.file("src/desktopMain/resources/icon.png"))
            }
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                upgradeUuid = "e4a77c22-9be2-4b64-8e60-3f9a1d5bc2f1"
            }
        }
    }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

compose {
    resources {
        packageOfResClass = "com.be.music.res"
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
    // FIXME(ios-trial): Momentarily disabled so the genuine iOS compile errors become visible
    // add("kspIosArm64", libs.androidx.room.compiler)
    // add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
