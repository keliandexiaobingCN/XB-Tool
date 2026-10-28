import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) {
        file.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.xb.tool"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xb.tool"
        minSdk = 26
        targetSdk = 35
        versionCode = 137
        versionName = "1.3.7"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreProperties.getProperty("storeFile", "keystore/xb-release.jks")
            val store = rootProject.file(storePath)
            // 仓库里不含签名材料（.gitignore 排除 keystore/ 与 keystore.properties）。
            // 别人 clone 下来没有这两个文件时，回退到 debug 签名，保证能直接构建；
            // 正式发布包必须提供 keystore.properties 才会用正式证书签名。
            if (store.isFile && !keystoreProperties.getProperty("storePassword").isNullOrBlank()) {
                storeFile = store
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias", "xbtool")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseStore = signingConfigs.getByName("release").storeFile
            signingConfig = if (releaseStore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }
    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*"
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Native LAME (MP3) + pure-Java FLAC encoder
    implementation("com.github.axet:lame:1.0.9")
    implementation("com.github.axet:java-flac-encoder:0.3.8")

    // QR code generation
    implementation("com.google.zxing:core:3.5.3")

    // Loopable video background
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // ADB 无线调试配对（SPAKE2）: libadb-android (Apache-2.0)
    implementation(files("libs/libadb-android-3.1.1.aar"))
    implementation(files("libs/spake2.aar"))
    implementation(files("libs/bcprov.jar"))
    // 配对需要 exportKeyingMaterial，Android 15 平台 Conscrypt 已移除该方法，使用自定义 Conscrypt 走 TLS1.3
    implementation(files("libs/conscrypt-android-2.5.2.aar"))
}
