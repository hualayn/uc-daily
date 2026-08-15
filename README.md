# Study Checkin App

学习打卡 Android 应用 — 每日打卡，持续学习。

## 功能

- **每日打卡**：一键记录当天学习情况
- **拍照打卡**：拍照记录学习内容，照片与打卡关联
- **日历查看**：滑动日历查看过去 30 天的打卡历史
- **打卡统计**：累计打卡天数与最近记录列表

## 技术栈

- **Kotlin** 2.2.10
- **Jetpack Compose** + Material3
- **Room** 2.7.1（本地 SQLite）
- **Coil** 2.7.0（图片加载）
- **MVVM** 架构（ViewModel + StateFlow）

## 系统要求

- Android SDK 26+（Android 8.0）
- JDK 17
- Android Studio Iguana+

## 构建

```bash
cd study-checkin-app
./gradlew assembleDebug
```

构建产物位于 `app/build/outputs/apk/debug/`。

## 项目结构

```
app/src/main/java/com/study/checkin/
├── MainActivity.kt        # 入口，相机权限与 Activity 编排
├── ui/
│   ├── CheckinViewModel.kt   # ViewModel，状态管理与数据操作
│   └── CheckinScreen.kt      # Compose UI
└── data/
    ├── CheckinEntity.kt      # Room 实体
    ├── CheckinDao.kt         # DAO 接口
    └── AppDatabase.kt        # 数据库配置
```
