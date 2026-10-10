# uc-daily 代码审计报告

> 审计日期：2026-10-08
> 审计范围：`app/src/main`（Kotlin/Compose Android 应用）、`app/build.gradle.kts`、`settings.gradle.kts`、`.github/workflows/build-apk.yml`、`gradle/wrapper`
> 应用性质：溃疡性结肠炎（UC）日常健康记录应用，**纯本地存储**（Room + 应用私有照片目录），无账号、无网络请求权限。

---

## 总体结论

这是一个典型的「本地单机健康记录」应用：攻击面很小（无 `INTERNET` 权限、无 WebView、DAO 全部参数化查询），整体安全基线尚可。
发现 **0 个可直接被远程利用的漏洞**，但存在 **2 个中等风险问题**（备份泄露、数据升级时被销毁）和多个值得改进的健壮性/架构问题。
由于数据属于**敏感医疗健康信息**，下面「隐私与数据安全」一节的问题应按较高优先级处理。

---

## 一、安全 / 隐私问题

### 🔴 [中] 1. `android:allowBackup="true"` 允许健康数据被备份导出

- **位置**：`app/src/main/AndroidManifest.xml:22`
- **问题**：应用所有数据（症状、便血、用药、照片）存于 Room 数据库与应用私有目录。`allowBackup="true"` 时，通过 `adb backup`（旧设备）或厂商云备份，可在无 root 情况下把整库与照片取出。README 承诺「数据完全私密」，但备份通道与此承诺相悖。
- **建议**：
  - 直接 `android:allowBackup="false"`（数据导出/恢复已有显式 CSV 功能，无需依赖系统备份）；
  - 或保留备份但加 `android:fullBackupContent`/`dataExtractionRules`，排除 `uc_daily_db` 与照片目录；
  - 同时建议为数据库启用 **SQLCipher**（如 `androidx.sqlite` + sqlcipher），文件层加密后即使导出也无法读取。

### 🔴 [中] 2. `fallbackToDestructiveMigration` 升级会静默清空用户全部病历

- **位置**：`app/src/main/java/com/ucdaily/data/AppDatabase.kt:62`（注释自述「正式发布前建议移除」）
- **问题**：当前 `version = 1`。一旦未来改 schema 升到 v2，旧用户升级后**全部数据被销毁且无任何提示**。对医疗记录应用这是灾难性的数据丢失，且注释表明团队已知道这是临时方案但尚未处理。
- **建议**：立即补上 Room `Migration(1, 2)` 流程与 `exportSchema = true`（把 schema JSON 存入版本库供迁移测试）；删除 destructive fallback，或至少改为仅 debug 构建保留。

### 🟡 [中低] 3. 敏感健康信息经剪贴板明文暴露

- **位置**：`app/src/main/java/com/ucdaily/ui/MealLogScreen.kt:203`（导出复制到剪贴板）
- **问题**：CSV/TXT 含便血、腹痛、用药等敏感内容，直接写入系统剪贴板。Android 12+ 会提示「已复制」，但其他应用/输入法/剪贴板管理器仍可读取，且剪贴板历史会持久化。
- **建议**：复制后用 `ClipboardManager.addPrimaryClipChangedListener` 或定时（如 60s）清空剪贴板；或在 UI 上提示用户「剪贴板内容包含健康信息，注意粘贴对象」。

### 🟡 [中低] 4. 照片存于 `getExternalFilesDir()`，对同用户其它应用可读

- **位置**：`app/src/main/java/com/ucdaily/ui/MealLogViewModel.kt:814、848`；`res/xml/file_paths.xml`
- **问题**：`getExternalFilesDir(null)`（`/sdcard/Android/data/com.ucdaily/files`）在多数国产 ROM 上对同设备其它应用可直接读取（旧 Android 更甚），饮食照片含健康线索。相较之下 `filesDir`（内部存储）严格受沙箱保护。
- **建议**：改为 `app.filesDir` 存储（`FileProvider` 的 `files-path` 已配置好兜底），或至少默认内部存储、外置仅作缓存。

### 🟢 [低] 5. 备份通道之外的数据库明文可被 root/调试提取

- Room 默认未加密；在已 root 设备或 USB 调试下可直接拷走 `uc_daily_db`。
- **建议**：同问题 1 的 SQLCipher 方案；并可在 manifest 加 `android:allowBackup="false"` + 禁用 `debuggable`（release 已由构建体系保证）后，配合应用锁（如指纹）进一步降险。

### 🟢 [低] 6. 导出/恢复格式按「当前语言」解析，存在数据不可恢复风险

- **位置**：`app/src/main/java/com/ucdaily/data/RestoreImporter.kt`（类型列/内容列均按当前 locale 文案匹配）
- **问题**：导出后若切换语言（或换设备后系统语言不同），恢复时所有行都会判为 `failed`，用户数据实际丢失。这属于「数据完整性」漏洞，界面提示也只是小字注释（`restore_error_invalid`）。
- **建议**：CSV 增加语言/版本标记行（如 `#uc-daily,v1,lang=zh`），恢复时按标记行选择解析规则；或改成与语言无关的枚举列（`meal/med/bowel/note/tolerance`）。

### ✅ 未发现的问题（已检查）

- **SQL 注入**：DAO 全部使用 Room `@Query` 参数绑定，无 `rawQuery`/拼接 SQL。
- **导出路径**：走 SAF `CreateDocument`，无路径穿越风险。
- **网络攻击面**：manifest 无 `INTERNET` 权限，代码无 HTTP/WebView；更新走 Google Play Core（官方 API）。
- **PendingIntent**：`MedReminder` 统一使用 `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`，广播接收器 `exported="false"`（除 BOOT_COMPLETED 必需）。
- **FileProvider**：`exported="false"` + `grantUriPermissions="true"`，authority 配置正确。
- **反射改 `Notification.mMessageCount`**：仅用于桌面角标降级，有 `try/catch` 兜底，失败不影响功能（非安全问题，但依赖隐藏 API，见改进 #13）。

---

## 二、数据完整性 / 正确性

### 🟡 7. CSV 导入的字段与时间格式无校验

- **位置**：`RestoreImporter.parseRow()`（`time` 原样入库、`leadingDigits` 无溢出保护）
- **问题**：`time` 未按 `HH:mm` 校验（可写入任意字符串）；`leadingDigits("99999999999")` 会整型溢出。虽然输入是用户自选文件、危害有限，但会造成统计/排序异常。
- **建议**：`time` 用 `LocalTime.parse` 校验，非法值兜底为空串；数字解析加 `coerceIn` 上限。

### 🟡 8. 记录删除后照片文件成为孤儿，永占存储

- **位置**：`MealLogViewModel.deleteRecord()`（只删 DB 行）；`RecordPanels/RecordListScreen` 读取时 `filter { File(it).exists() }`
- **问题**：删除记录、或草稿里移除照片后，磁盘上的 `meal_*.jpg` 不会被删除，长期累积占用数百 MB。
- **建议**：删除记录/移除照片时同步删除文件（注意编辑取消场景不能删，当前注释已说明该约束）；提供「清理未引用照片」维护入口。

### 🟢 9. 导入不做事务化处理

- **位置**：`RestoreImporter.restore()`（逐行 insert/upsert）
- **问题**：几千行导入中途失败（如进程被杀）会留下半套数据，重导时靠逐行去重兜底，但去重逻辑是「同日同字段全等」，语义脆弱。
- **建议**：整体 `db.withTransaction { ... }`；去重改用唯一索引（如 `(date,time,mealType)`）+ `OnConflictStrategy.IGNORE`。

### 🟢 10. `DailyNote` upsert 会覆盖旧记录的 `createdAt`

- **位置**：`MealLogViewModel.saveNote()`（已处理：保留 `existing.createdAt`）；但 `RestoreImporter` 的 `noteDao.upsert(DailyNote(date=..., text=...))` **未带 id/createdAt**，重复导入同一文件会把 createdAt 重置为当前时间。
- **建议**：恢复时先 `getByDate` 取回旧 id/createdAt 再 upsert。

---

## 三、架构 / 代码质量改进

### 11. `MealLogViewModel` 巨型 God Object（1568 行、5 个 DAO + prefs + 导出 + 导入 + 提醒全部耦合）

- **问题**：单个 ViewModel 承担 8 类职责，状态字段 40+；`loadState()` 一次性全表加载 `allMeals/allSymptoms/allMeds/allNotes` 进 `MealUiState`，**数据量随使用时间线性增长，内存与重组开销持续上升**。
- **建议**：
  - 拆成 `RecordsRepository`（DAO 聚合）+ 按页拆分的 UseCase；UI 状态按 `Home/Tolerance/Stats/Profile` 拆成多个 ViewModel；
  - 统计页改为 SQL 聚合查询（`COUNT`/`GROUP BY`）替代把全表读进内存再 `groupBy`；
  - 全量列表改 `PagingSource`/分页。

### 12. 状态更新非原子，存在竞态

- **位置**：多处 `_uiState.value = _uiState.value.copy(...)`（`saveRecord`/`moveFoodTag`/`refreshXxx` 等）
- **问题**：协程并发写 `MutableStateFlow` 时可能出现「读后写覆盖」（例如 `refreshMedStats()` 与 `saveMed()` 并发时，各自基于旧快照 copy，后写覆盖先写）。`moveFoodTag` 里的「乐观更新 + 异步落库 + refreshFoodTags」尤其容易回闪。
- **建议**：改用 `MutableStateFlow.update { it.copy(...) }`（CAS 语义）；或把写操作收敛到单一 Mutex/actor。

### 13. 依赖隐藏 API 反射设置角标

- **位置**：`MedReminder.applyLauncherBadgeCount()`（`Notification.mMessageCount`）
- **问题**：字段名/机制可能随 Android 版本或厂商 ROM 变化（目前有 try/catch 降级）。此外这是「非公开接口」，未来 targetSdk 收紧可能有风险。
- **建议**：优先使用 `ShortcutBadger`/厂商角标协议或 `Notification.Builder#setNumber` + 文档化兼容矩阵；把这段隔离到单独的 `LauncherBadge` 工具类并写好降级日志。

### 14. `MedReminder.sync` 全量取消通知的副作用

- **位置**：`MedReminder.sync()`（`nm.activeNotifications.forEach { nm.cancel(it.id) }`）
- **问题**：取消的是**本应用全部通知**，未来若应用新增其它通知（如导出完成）会被误杀。当前无影响，属于隐患。
- **建议**：只 cancel 本应用 reminder 相关 id（保存 `lastPostedId` 并精确取消）。

### 15. 零点定时器与每分钟循环的实现脆弱

- **位置**：`MealLogViewModel.init`（`while(true) { delay(...) }`）
- **问题**：两个无限循环（跨零点检查、每分钟 `MedReminder.sync`）依赖进程存活；注释已承认 Doze/后台不可靠并用 `checkDayChange()` 兜底，但每分钟唤醒在前台持续耗电。可改用 `WorkManager` 周期任务 + `AlarmManager` 精确闹钟（现状已用 `setAndAllowWhileIdle`）组合。
- **建议**：每分钟循环改 `Handler.postDelayed` + 生命周期感知（进入后台即停）；跨零点用 `WorkManager` 唯一周期任务。

### 16. 构建产物与日志文件混入仓库

- **位置**：`build-verify.log`（根目录，已跟踪）；`app/build/intermediates/**`（未跟踪但存在）
- **问题**：构建日志（含 WARNING）被提交；`.gitignore` 虽然写了 `build/`，但 `build-verify.log` 未忽略。
- **建议**：`git rm --cached build-verify.log` 并把 `*.log` 之外加 `build-verify.log`；`.gitignore` 补 `*.log` 例外规则确认。

### 17. 依赖与构建配置

- `compileSdk = 37` 但 `targetSdk = 35`：建议升 targetSdk（Android 商店要求逐步提高）；注意 `MedReminderService` 普通后台服务在 API 26+ 受限，`startService` 启动普通服务在后台可能被拒绝——当前已有 `try/catch` 兜底，但可改 `WorkManager`/`JobIntentService` 语义。
- `fallbackToDestructiveMigration`（见问题 2）。
- `gradle.properties` 里多条 AGP 弃用告警（`android.usesSdkInManifest.disallowed` 等）：建议清理或加 `android.sync.suppressAgpWarnings`，避免未来 AGP 10 直接报错。
- 无单元测试目录（`app/src/test`、`androidTest` 均缺失）：`RestoreImporter` 的 CSV 解析、`activityScore` 评分算法是最值得补测试的地方。

---

## 四、优先级行动清单

| 优先级 | 问题 | 建议动作 |
|---|---|---|
| P0 | #2 destructive migration | 补 Room Migration + exportSchema，移除 destructive fallback |
| P0 | #1 allowBackup | `allowBackup="false"` 或备份排除 DB/照片 |
| P1 | #4 照片目录 | 移到 `filesDir` 内部存储 |
| P1 | #6 语言耦合的 CSV | 导出加语言/版本标记或改语言无关枚举 |
| P1 | #11/#12 ViewModel 拆分与状态竞态 | Repository + 分页 + `StateFlow.update` |
| P2 | #3 剪贴板敏感信息 | 复制后清空或加提示 |
| P2 | #8 孤儿照片 | 删除记录/移除照片时删文件 + 清理入口 |
| P2 | #7 CSV 字段校验 | 时间格式 + 数字上限校验 |
| P3 | #9/#10 导入事务与 createdAt | withTransaction + 保留旧 id |
| P3 | #13/#14/#15/#16/#17 | 角标反射隔离、通知精确取消、WorkManager、清理构建产物、补测试、升 targetSdk |

---

## 附：审计方法

1. 通读 `app/src/main/java` 全部 Kotlin 源文件（数据层、ViewModel、UI、MedReminder、MainActivity、PhotoCompressor、AppLocale）。
2. 检查 manifest 权限、组件导出属性、FileProvider 配置。
3. 检查 Room DAO 是否存在原始 SQL 拼接；检查导出/导入数据流与路径处理。
4. 检查 CI（`.github/workflows/build-apk.yml`）密钥管理与签名流程（✅ 密钥走 GitHub Secrets，未入库；`local.properties` 已在 `.gitignore` 且未被跟踪）。
5. 检查网络与更新通道（无 INTERNET 权限；Play Core 更新流程异常处理完善）。
