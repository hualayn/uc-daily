plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.study.checkin"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.study.checkin"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    // release 签名：GitHub Actions 把仓库 secret 里的 keystore 解码成文件，
    // 通过 -P 参数传入（RELEASE_KEYSTORE_FILE / RELEASE_KEYSTORE_PASSWORD /
    // RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD）；
    // 本地构建（无密钥）时 release 自动退回 debug 签名，仍可安装
    // val releaseSigningProps = listOf(
    //     (project.findProperty("RELEASE_KEYSTORE_FILE") as? String),
    //     (project.findProperty("RELEASE_KEYSTORE_PASSWORD") as? String),
    //     (project.findProperty("RELEASE_KEY_ALIAS") as? String),
    //     (project.findProperty("RELEASE_KEY_PASSWORD") as? String)
    // ).map { it?.takeIf { s -> s.isNotBlank() } }
    // val hasReleaseSigning = releaseSigningProps.none { it == null }

    // if (hasReleaseSigning) {
    //     signingConfigs {
    //         create("release") {
    //             storeFile = file(releaseSigningProps[0]!!)
    //             storePassword = releaseSigningProps[1]!!
    //             keyAlias = releaseSigningProps[2]!!
    //             keyPassword = releaseSigningProps[3]!!
    //         }
    //     }
    // }
    // val releaseSigningConfig = if (hasReleaseSigning) {
    //     signingConfigs.getByName("release")
    // } else {
    //     signingConfigs.getByName("debug")
    // }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {     
        create("release") {
            storeFile = file("/home/ll/app-key-store.jks")
            storePassword = "111111"
            keyAlias = "key0"
            keyPassword = "111111"
        }        
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // signingConfig = releaseSigningConfig
            signingConfig = signingConfigs.getByName("release")
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
}
