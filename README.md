# 饮食记录 App

日常饮食记录 Android 应用 — 每天把吃的东西拍下来，方便回溯。
为溃结（溃疡性结肠炎）日常管理设计：通过照片 + 文字备注，回顾每一餐吃了什么、吃完身体有什么反应。

## 功能

- **按餐记录**：早餐 / 午餐 / 晚餐 / 加餐，一天可记多条
- **拍照记录**：相机拍摄或从相册选取，一条记录可加多张照片
- **文字备注**：记录吃了什么、吃完的感觉（如排便情况）
- **日历查看**：滑动日历查看任意月份，有记录的日期有小圆点标记
- **日期回溯**：点选任意日期，查看当天全部记录与照片；支持补录历史日期
- **全屏查看**：点照片可放大查看
- **删除记录**：记录卡片右上角删除（二次确认）
- **统计**：累计有记录的天数与总记录条数

## 技术栈

- **Kotlin** 2.2.10
- **Jetpack Compose** + Material3
- **Room** 2.7.1（本地 SQLite，数据库版本 3）
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
├── MainActivity.kt        # 入口，相机/相册权限与 Activity 编排
├── ui/
│   ├── MealLogViewModel.kt  # ViewModel，状态管理、草稿、数据操作
│   └── MealLogScreen.kt     # Compose UI（日历、记录卡片、添加面板、全屏查看）
└── data/
    ├── MealRecord.kt      # Room 实体 + 餐次枚举
    ├── MealRecordDao.kt   # DAO 接口
    └── AppDatabase.kt     # 数据库配置（v3，含 1→2→3 迁移）
```

## 数据存储说明

- 记录存于本地 Room 数据库 `checkin_db`（表 `meal_records`）
- 照片存于应用私有目录（`getExternalFilesDir`），卸载应用时自动清理
- 相册选取的照片会复制到应用私有目录，保证卸载前一直可查看
- 数据库 v3 迁移会删除旧版"学习打卡"数据表（改造时一次性清理）
