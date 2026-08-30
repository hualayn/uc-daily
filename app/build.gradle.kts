plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.ucdaily"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ucdaily"
        minSdk = 26
        targetSdk = 35
        // 版本号可由 -PVERSION_CODE / -PVERSION_NAME 覆盖（GitHub Actions 按 tag 注入），本地构建用默认值
        versionCode = (project.findProperty("VERSION_CODE") as? String)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("VERSION_NAME") as? String) ?: "1.1.0"
    }

    buildFeatures {
        compose = true
    }

    // release 签名：GitHub Actions 把仓库 secret 里的 keystore 解码成文件，
    // 通过 -P 参数传入（RELEASE_KEYSTORE_FILE / RELEASE_KEYSTORE_PASSWORD /
    // RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD）；
    // 本地构建（无密钥）时 release 自动退回 debug 签名，仍可安装。
    // 本地要出正式签名包时：
    // ./gradlew assembleRelease -PRELEASE_KEYSTORE_FILE=<jks 路径> \
    //   -PRELEASE_KEYSTORE_PASSWORD=<库密码> -PRELEASE_KEY_ALIAS=<别名> -PRELEASE_KEY_PASSWORD=<密钥密码>
    val releaseSigningProps = listOf(
        (project.findProperty("RELEASE_KEYSTORE_FILE") as? String),
        (project.findProperty("RELEASE_KEYSTORE_PASSWORD") as? String),
        (project.findProperty("RELEASE_KEY_ALIAS") as? String),
        (project.findProperty("RELEASE_KEY_PASSWORD") as? String)
    ).map { it?.takeIf { s -> s.isNotBlank() } }
    val hasReleaseSigning = releaseSigningProps.none { it == null }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseSigningProps[0]!!)
                storePassword = releaseSigningProps[1]!!
                keyAlias = releaseSigningProps[2]!!
                keyPassword = releaseSigningProps[3]!!
            }
        }
    }
    val releaseSigningConfig = if (hasReleaseSigning) {
        signingConfigs.getByName("release")
    } else {
        signingConfigs.getByName("debug")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = releaseSigningConfig
        }
        debug {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")

    // ViewModel + Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    // Fragment（ActivityResult API 的 lint 校验要求 fragment >= 1.3.0）
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // Room
    val roomVersion = "2.7.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")

    // Coil - 图片加载
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Pager - 日历翻页
    implementation("androidx.compose.foundation:foundation")

    // Material 扩展图标（含男生/女生头像图标 Man / Woman）
    implementation("androidx.compose.material:material-icons-extended")

    // Google Play Core：应用内更新（多国语言版本随 AAB 通过 Play 分发，
    // 应用内 Flexible 更新；"我的 → 软件更新"手动检查）
    implementation("com.google.android.play:app-update:2.1.0")
}
