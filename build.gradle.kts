plugins {
    id("com.android.application") version "9.3.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    // ✅ 修正：改为匹配 Kotlin 2.2.10 的 KSP 版本
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
    // ✅ 修正：与 Kotlin 版本一致
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
