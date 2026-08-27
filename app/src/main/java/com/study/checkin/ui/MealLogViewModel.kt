package com.study.checkin.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.study.checkin.data.AppDatabase
import com.study.checkin.data.BLOOD_LABELS
import com.study.checkin.data.BRISTOL_LABELS
import com.study.checkin.data.DailyNote
import com.study.checkin.data.DailySymptom
import com.study.checkin.data.FoodTag
import com.study.checkin.data.FoodTolerance
import com.study.checkin.data.MealRecord
import com.study.checkin.data.MealType
import com.study.checkin.data.MedRecord
import com.study.checkin.data.PAIN_LOCATION_LABELS
import com.study.checkin.data.activityScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 添加记录时的草稿 */
data class DraftRecord(
    val mealType: MealType,
    val note: String = "",
    val photos: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    /** 记录时间 HH:mm（面板内可调） */
    val time: String = ""
)

/** 服药记录草稿 */
data class MedDraft(
    val name: String = "",
    val dose: String = "",
    /** 记录时间 HH:mm（面板内可调） */
    val time: String = ""
)

/** 每日感受草稿 */
data class NoteDraft(
    val text: String = ""
)

/** 排便/症状记录草稿（字段与 DailySymptom 对应，不含 id/date/createdAt） */
data class SymptomDraft(
    val bowelCount: Int = 1,
    val nightDiarrhea: Boolean = false,
    val bristolType: Int = 0,
    val blood: Int = 0,
    val mucus: Boolean = false,
    val painScore: Int = 0,
    val painLocation: Int = 0,
    val urgency: Boolean = false,
    val note: String = "",
    /** 记录时间 HH:mm（面板内可调；编辑旧记录时回退显示 createdAt 时间） */
    val time: String = ""
) {
    companion object {
        fun from(record: DailySymptom?): SymptomDraft = record?.let {
            SymptomDraft(
                bowelCount = it.bowelCount,
                nightDiarrhea = it.nightDiarrhea,
                bristolType = it.bristolType,
                blood = it.blood,
                mucus = it.mucus,
                painScore = it.painScore,
                painLocation = it.painLocation,
                urgency = it.urgency,
                note = it.note,
                time = it.time.ifEmpty { recordTime(it.createdAt) }
            )
        } ?: SymptomDraft()
    }
}

/** 依据草稿实时计算参考活动度评分（面板内即时预览） */
fun symptomDraftScore(d: SymptomDraft): Int =
    activityScore(DailySymptom(date = "", bowelCount = d.bowelCount, blood = d.blood))

/** 新记录的默认时间：今天 = 当前时间，补录历史日期 = 中午 12:00（面板内均可调整） */
private fun defaultTimeFor(date: LocalDate): String =
    if (date == LocalDate.now()) {
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        "12:00"
    }

/** "当天记录"筛选类别（点击首页统计卡切换） */
enum class DayFilter(val label: String) {
    MEAL("饮食"),
    BOWEL("便便"),
    MED("服药")
}

/** 可导出的记录类型（导出对话框复选） */
enum class ExportType(val label: String) {
    MEAL("饮食"),
    MED("服药"),
    BOWEL("便便"),
    NOTE("感受")
}

/** 导出文件格式 */
enum class ExportFormat(val ext: String) {
    TXT("txt"),
    CSV("csv")
}

/** 导出结果：文件名（含扩展名）与文件内容 */
data class ExportResult(
    val fileName: String,
    val text: String
)

/** 头像选项：default（通用人物）/ boy（男生）/ girl（女生），SharedPreferences 持久化 */
const val AVATAR_DEFAULT = "default"
const val AVATAR_BOY = "boy"
const val AVATAR_GIRL = "girl"

/** 常用药物列表：SharedPreferences 键（JSON 数组字符串）与最大条数 */
const val PREF_COMMON_MED_NAMES = "common_med_names"
const val MAX_COMMON_MED_NAMES = 12

/** 主题模式 / 服药提醒时间：SharedPreferences 键（逗号分隔的 HH:mm 列表） */
const val PREF_THEME_MODE = "theme_mode"
const val PREF_MED_REMINDER_TIMES = "med_reminder_times"

/** 服药提醒时间的默认值与扩充池（次数增加时按序补位） */
val DEFAULT_MED_REMINDER_TIMES = listOf("08:00", "14:00", "20:00")
val MED_REMINDER_TIME_POOL = listOf("08:00", "12:00", "16:00", "20:00", "22:00", "23:00")

data class MealUiState(
    val loading: Boolean = true,
    /** 当前底部 Tab：0 首页 1 耐受 2 日常管理 3 我的 */
    val selectedTab: Int = 0,
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    /** 首页周历当前展示的周（滑动换周只移动它，不改变选中日期；点选日期时与选中日期同步） */
    val homeWeekAnchor: LocalDate = LocalDate.now(),
    /** 有饮食记录的日期集合（yyyy-MM-dd），统计页"记录天数/覆盖日期"用 */
    val recordDates: Set<String> = emptySet(),
    val selectedDateRecords: List<MealRecord> = emptyList(),
    val selectedDateMeds: List<MedRecord> = emptyList(),
    /** 今天的服药时间（HH:mm；驱动首页服药提醒铃铛） */
    val todayMedTimes: List<String> = emptyList(),
    val selectedDateNote: DailyNote? = null,
    val totalRecordDays: Int = 0,
    val totalRecords: Int = 0,
    val totalMedRecords: Int = 0,
    /** 每天最新一条排便记录（date -> 记录），驱动日历热力圆点与日期头展示 */
    val symptomByDate: Map<String, DailySymptom> = emptyMap(),
    /** 选中日期的全部排便记录（新记录在前），驱动当日记录列表 */
    val selectedDateSymptoms: List<DailySymptom> = emptyList(),
    /** "当天记录"筛选（null = 不筛选；点击统计卡设置，再点一次或点"恢复"清除） */
    val dayRecordFilter: DayFilter? = null,
    /** 食物标签列表与"被饮食记录引用次数" */
    val foodTags: List<FoodTag> = emptyList(),
    val foodTagCounts: Map<String, Int> = emptyMap(),
    /** 常用药物（用户可管理、持久化；首次由历史服药记录初始化；服药面板长按标签删除） */
    val commonMedNames: List<String> = emptyList(),
    /** 全屏查看的照片集（空 = 未打开），多张时左右滑动切换 */
    val fullscreenPhotos: List<String> = emptyList(),
    /** 当前显示照片在 fullscreenPhotos 中的下标 */
    val fullscreenPhotoIndex: Int = 0,
    /** 排便记录面板是否打开 */
    val isSymptomPanelOpen: Boolean = false,
    /** 非 null 表示正在编辑该条排便记录（null = 新增一条） */
    val editingSymptomId: Int? = null,
    val symptomDraft: SymptomDraft = SymptomDraft(),
    /** 服药面板（添加或编辑） */
    val isMedPanelOpen: Boolean = false,
    val editingMedId: Int? = null,
    val medDraft: MedDraft = MedDraft(),
    /** 每日感受面板 */
    val isNotePanelOpen: Boolean = false,
    val noteDraft: NoteDraft = NoteDraft(),
    /** 面板是否打开（添加或编辑共用同一个面板） */
    val isAdding: Boolean = false,
    /** 非 null 表示正在编辑该记录（复用添加面板） */
    val editingRecordId: Int? = null,
    val draft: DraftRecord = DraftRecord(MealType.fromTime(LocalTime.now())),
    /** 我的：昵称（SharedPreferences 持久化） */
    val nickname: String = "记录者",
    /** 我的：头像（SharedPreferences 持久化）：default/boy/girl */
    val avatar: String = AVATAR_DEFAULT,
    /** 我的→主题：主题模式，默认跟随系统（SharedPreferences 持久化） */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 我的→服药设置：提醒时间（HH:mm 升序），列表长度 = 每天服药次数 */
    val medReminderTimes: List<String> = DEFAULT_MED_REMINDER_TIMES,
    /** 全部排便记录（未去重；统计页算总量/分布用，symptomByDate 仍为每天最新一条） */
    val allSymptoms: List<DailySymptom> = emptyList(),
    /** 有感受记录的天数（统计页用） */
    val totalNoteDays: Int = 0,
    /** 全部饮食记录（日期倒序；统计页"饮食记录"汇总列表用） */
    val allMeals: List<MealRecord> = emptyList(),
    /** 全部服药记录（日期倒序；统计页"服药记录"汇总列表用） */
    val allMeds: List<MedRecord> = emptyList(),
    /** 全部感受（日期倒序；统计页"感受记录"汇总列表用） */
    val allNotes: List<DailyNote> = emptyList()
)

/** 关闭所有记录面板（打开新面板前调用，保证面板互斥） */
private fun MealUiState.closeAllPanels(): MealUiState = copy(
    isAdding = false,
    editingRecordId = null,
    isSymptomPanelOpen = false,
    editingSymptomId = null,
    isMedPanelOpen = false,
    editingMedId = null,
    isNotePanelOpen = false
)

/** 从全部排便记录中取每天最新一条（id 最大），用于热力图与日期头 */
private fun latestSymptomByDate(all: List<DailySymptom>): Map<String, DailySymptom> =
    all.groupBy { it.date }.mapValues { (_, list) -> list.maxByOrNull { it.id }!! }

class MealLogViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val db = AppDatabase.getDatabase(app)
    private val dao = db.mealRecordDao()
    private val symptomDao = db.dailySymptomDao()
    private val medDao = db.medRecordDao()
    private val noteDao = db.dailyNoteDao()
    private val foodTagDao = db.foodTagDao()
    private val prefs = app.getSharedPreferences("app_prefs", Application.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(MealUiState())
    val uiState: StateFlow<MealUiState> = _uiState

    /** 相机拍照的待写入文件路径（拍摄成功后并入草稿） */
    private var pendingCameraPath: String? = null

    init {
        loadState()
        // 应用常驻内存跨日期时，在零点检查一次并刷新"今天"
        viewModelScope.launch {
            while (true) {
                val secsToMidnight = (86_400 - LocalTime.now().toSecondOfDay()) % 86_400
                delay(secsToMidnight * 1000L + 5_000L)
                checkDayChange()
            }
        }
    }

    // region 数据加载

    private fun loadState() {
        viewModelScope.launch {
            val s = _uiState.value
            val dateStr = s.selectedDate.toString()
            val allSymptoms = symptomDao.getAll()
            _uiState.value = s.copy(
                loading = false,
                recordDates = dao.getRecordDates().toSet(),
                totalRecordDays = dao.getRecordDays(),
                totalRecords = dao.getTotalCount(),
                totalMedRecords = medDao.getCount(),
                symptomByDate = latestSymptomByDate(allSymptoms),
                selectedDateSymptoms = allSymptoms
                    .filter { it.date == dateStr }
                    .sortedByDescending { it.id },
                selectedDateRecords = dao.getRecordsByDate(dateStr),
                selectedDateMeds = medDao.getByDate(dateStr),
                todayMedTimes = medDao.getByDate(s.today.toString()).map { it.time },
                selectedDateNote = noteDao.getByDate(dateStr),
                foodTags = foodTagDao.getAll(),
                commonMedNames = loadCommonMeds(),
                nickname = prefs.getString("nickname", "记录者") ?: "记录者",
                avatar = prefs.getString("avatar", AVATAR_DEFAULT) ?: AVATAR_DEFAULT,
                themeMode = ThemeMode.fromKey(prefs.getString(PREF_THEME_MODE, null)),
                medReminderTimes = loadMedReminderTimes(),
                allSymptoms = allSymptoms,
                totalNoteDays = noteDao.getCount(),
                allMeals = dao.getAllRecordsDesc(),
                allMeds = medDao.getAllMedsDesc(),
                allNotes = noteDao.getAllNotesDesc()
            )
            refreshFoodTagCounts()
        }
    }

    /** 重新加载排便记录（每天最新一条 + 选中日全部条目） */
    private fun refreshSymptoms() {
        viewModelScope.launch {
            val all = symptomDao.getAll()
            val dateStr = _uiState.value.selectedDate.toString()
            _uiState.value = _uiState.value.copy(
                symptomByDate = latestSymptomByDate(all),
                selectedDateSymptoms = all
                    .filter { it.date == dateStr }
                    .sortedByDescending { it.id },
                allSymptoms = all
            )
        }
    }

    /** 统计每个食物标签被饮食记录引用的次数（耐受页展示） */
    private fun refreshFoodTagCounts() {
        viewModelScope.launch {
            val counts = dao.getAllRecords()
                .flatMap { it.tags }
                .groupingBy { it }
                .eachCount()
            _uiState.value = _uiState.value.copy(foodTagCounts = counts)
        }
    }

    /** 重新加载当前选中日期的服药与感受（保存/删除后调用） */
    private fun refreshDayData() {
        viewModelScope.launch {
            val dateStr = _uiState.value.selectedDate.toString()
            _uiState.value = _uiState.value.copy(
                selectedDateMeds = medDao.getByDate(dateStr),
                selectedDateNote = noteDao.getByDate(dateStr),
                totalNoteDays = noteDao.getCount()
            )
        }
    }

    /** 重新加载服药总数、全部服药列表与今天的服药时间（保存/删除服药后调用） */
    private fun refreshMedStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                totalMedRecords = medDao.getCount(),
                allMeds = medDao.getAllMedsDesc(),
                todayMedTimes = medDao.getByDate(_uiState.value.today.toString()).map { it.time }
            )
        }
    }

    /** 应用常驻内存时日期变化（跨零点）：刷新"今天"；
     * 选中日期若为旧的"今天"则跟随到新一天并重载当天数据 */
    private fun checkDayChange() {
        val s = _uiState.value
        val now = LocalDate.now()
        if (now == s.today) return
        val followToday = s.selectedDate == s.today
        _uiState.value = s.copy(
            today = now,
            selectedDate = if (followToday) now else s.selectedDate,
            homeWeekAnchor = if (followToday) now else s.homeWeekAnchor
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                todayMedTimes = medDao.getByDate(now.toString()).map { it.time }
            )
            if (followToday) {
                val dateStr = now.toString()
                _uiState.value = _uiState.value.copy(
                    selectedDateSymptoms = symptomDao.getByDate(dateStr),
                    selectedDateRecords = dao.getRecordsByDate(dateStr),
                    selectedDateMeds = medDao.getByDate(dateStr),
                    selectedDateNote = noteDao.getByDate(dateStr)
                )
            }
        }
    }

    /** 读取常用药物：优先用户持久化列表（空列表 = 用户已清空）；未设置时由历史服药记录初始化 */
    private suspend fun loadCommonMeds(): List<String> {
        val raw = prefs.getString(PREF_COMMON_MED_NAMES, null)
        if (raw != null) {
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
            } catch (e: Exception) {
                emptyList()
            }
        }
        val seeded = medDao.getRecentNames()
        if (seeded.isNotEmpty()) persistCommonMeds(seeded)
        return seeded
    }

    /** 持久化常用药物列表（JSON 数组字符串） */
    private fun persistCommonMeds(names: List<String>) {
        prefs.edit().putString(PREF_COMMON_MED_NAMES, JSONArray(names).toString()).apply()
    }

    /** 保存服药后把药名加入常用列表（去重、追加到末尾、最多保留 MAX_COMMON_MED_NAMES 个） */
    private fun addToCommonMeds(current: List<String>, name: String): List<String> {
        if (name.isBlank() || name in current) return current
        val updated = (current + name).takeLast(MAX_COMMON_MED_NAMES)
        persistCommonMeds(updated)
        return updated
    }

    /** 重新加载食物标签（增删改后调用） */
    private fun refreshFoodTags() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(foodTags = foodTagDao.getAll())
        }
    }

    // endregion

    // region 日期选择

    fun selectDate(date: LocalDate) {
        viewModelScope.launch {
            val dateStr = date.toString()
            _uiState.value = _uiState.value.copy(
                selectedDate = date,
                // 点选日期时，周历展示周跟随到新日期所在周
                homeWeekAnchor = date,
                selectedDateSymptoms = symptomDao.getByDate(dateStr),
                selectedDateRecords = dao.getRecordsByDate(dateStr),
                selectedDateMeds = medDao.getByDate(dateStr),
                selectedDateNote = noteDao.getByDate(dateStr)
            )
        }
    }

    // endregion

    // region 记录导出

    /**
     * 导出记录：收集日期范围内（含首尾）所选类型的记录，生成导出文本。
     * 起止日期自动纠正顺序；范围内没有所选类型的记录时返回 null。
     */
    suspend fun exportRecords(
        start: LocalDate,
        end: LocalDate,
        types: Set<ExportType>,
        format: ExportFormat
    ): ExportResult? {
        if (types.isEmpty()) return null
        val s = minOf(start, end)
        val e = maxOf(start, end)
        val sStr = s.toString()
        val eStr = e.toString()

        val meals = if (ExportType.MEAL in types) dao.getRecordsBetween(sStr, eStr) else emptyList()
        val meds = if (ExportType.MED in types) medDao.getMedsBetween(sStr, eStr) else emptyList()
        val symptoms = if (ExportType.BOWEL in types) symptomDao.getBetween(sStr, eStr) else emptyList()
        val notes = if (ExportType.NOTE in types) noteDao.getBetween(sStr, eStr) else emptyList()

        if (meals.isEmpty() && meds.isEmpty() && symptoms.isEmpty() && notes.isEmpty()) return null

        val fileName = "记录导出_${sStr}_${eStr}.${format.ext}"
        val text = if (format == ExportFormat.TXT) {
            buildExportTxt(s, e, meals, meds, symptoms, notes)
        } else {
            // 加 BOM，避免 Excel 打开中文 CSV 乱码
            "\uFEFF" + buildExportCsv(meals, meds, symptoms, notes)
        }
        return ExportResult(fileName, text)
    }

    /** TXT 版：按日期分组（仅含有记录的日期），组内按 饮食 → 服药 → 便便 → 感受 逐条列出 */
    private fun buildExportTxt(
        s: LocalDate,
        e: LocalDate,
        meals: List<MealRecord>,
        meds: List<MedRecord>,
        symptoms: List<DailySymptom>,
        notes: List<DailyNote>
    ): String {
        val mealByDate = meals.groupBy { it.date }
        val medByDate = meds.groupBy { it.date }
        val sympByDate = symptoms.groupBy { it.date }
        val noteByDate = notes.groupBy { it.date }
        val dates = sortedDatesOf(mealByDate, medByDate, sympByDate, noteByDate)

        val present = buildList {
            if (mealByDate.isNotEmpty()) add("饮食")
            if (medByDate.isNotEmpty()) add("服药")
            if (sympByDate.isNotEmpty()) add("便便")
            if (noteByDate.isNotEmpty()) add("感受")
        }

        val sb = StringBuilder()
        sb.append("记录导出\n")
        sb.append("范围：${s} ~ ${e}\n")
        sb.append("类型：${present.joinToString("、")}\n\n")

        dates.forEach { dateStr ->
            val date = LocalDate.parse(dateStr)
            sb.append("====== ${dateStr} 周${"一二三四五六日"[date.dayOfWeek.value - 1]} ======\n")

            mealByDate[dateStr]?.forEach {
                sb.append("  [饮食] ${it.time} ${it.mealType.label}")
                if (it.tags.isNotEmpty()) sb.append("：").append(it.tags.joinToString("、"))
                if (it.note.isNotBlank()) sb.append("（").append(it.note.trim().replace(Regex("\\R"), " ")).append("）")
                sb.append('\n')
            }
            medByDate[dateStr]?.forEach {
                sb.append("  [服药] ${it.time} ${it.name}")
                if (it.dose.isNotBlank()) sb.append(' ').append(it.dose.trim())
                sb.append('\n')
            }
            sympByDate[dateStr]?.sortedBy { it.id }?.forEach {
                sb.append("  [便便] ")
                if (it.time.isNotBlank()) sb.append(it.time).append(' ')
                sb.append("次数").append(it.bowelCount)
                if (it.nightDiarrhea) sb.append("（含夜间）")
                sb.append(" | 布里斯托")
                sb.append(if (it.bristolType in 1..7) "${it.bristolType} ${BRISTOL_LABELS[it.bristolType - 1]}" else "未记录")
                sb.append(" | 便血").append(BLOOD_LABELS[it.blood])
                sb.append(" | 黏液").append(if (it.mucus) "有" else "无")
                sb.append(" | 腹痛").append(it.painScore).append("分")
                if (it.painLocation in 1..4) sb.append(' ').append(PAIN_LOCATION_LABELS[it.painLocation])
                sb.append(" | 急迫感").append(if (it.urgency) "有" else "无")
                if (it.note.isNotBlank()) sb.append(" | 其他：").append(it.note.trim().replace(Regex("\\R"), " "))
                sb.append('\n')
            }
            noteByDate[dateStr]?.forEach {
                sb.append("  [感受] ").append(it.text.trim().replace(Regex("\\R"), " ")).append('\n')
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    /** CSV 版：长表 日期,类型,时间,内容,备注（含逗号/引号/换行的字段自动加引号转义） */
    private fun buildExportCsv(
        meals: List<MealRecord>,
        meds: List<MedRecord>,
        symptoms: List<DailySymptom>,
        notes: List<DailyNote>
    ): String {
        val mealByDate = meals.groupBy { it.date }
        val medByDate = meds.groupBy { it.date }
        val sympByDate = symptoms.groupBy { it.date }
        val noteByDate = notes.groupBy { it.date }
        val dates = sortedDatesOf(mealByDate, medByDate, sympByDate, noteByDate)

        val sb = StringBuilder()
        sb.append("日期,类型,时间,内容,备注\n")
        dates.forEach { dateStr ->
            mealByDate[dateStr]?.forEach {
                val content = buildList {
                    add("${it.mealType.label}：")
                    if (it.tags.isNotEmpty()) add(it.tags.joinToString("、"))
                }.joinToString(" ")
                sb.append(csvLine(dateStr, "饮食", it.time, content, it.note.trim()))
            }
            medByDate[dateStr]?.forEach {
                val content = buildList {
                    add(it.name)
                    if (it.dose.isNotBlank()) add(it.dose.trim())
                }.joinToString(" ")
                sb.append(csvLine(dateStr, "服药", it.time, content, ""))
            }
            sympByDate[dateStr]?.sortedBy { it.id }?.forEach {
                val content = buildList {
                    add("次数${it.bowelCount}${if (it.nightDiarrhea) "（含夜间）" else ""}")
                    add("布里斯托" + (if (it.bristolType in 1..7) "${it.bristolType} ${BRISTOL_LABELS[it.bristolType - 1]}" else "未记录"))
                    add("便血${BLOOD_LABELS[it.blood]}")
                    add("黏液${if (it.mucus) "有" else "无"}")
                    add("腹痛${it.painScore}分" + (if (it.painLocation in 1..4) " ${PAIN_LOCATION_LABELS[it.painLocation]}" else ""))
                    add("急迫感${if (it.urgency) "有" else "无"}")
                }.joinToString("；")
                sb.append(csvLine(dateStr, "便便", it.time, content, it.note.trim()))
            }
            noteByDate[dateStr]?.forEach {
                sb.append(csvLine(dateStr, "感受", "", it.text.trim().replace(Regex("\\R"), " "), ""))
            }
        }
        return sb.toString()
    }

    /** 拼一行 CSV（需要转义的字段自动加引号） */
    private fun csvLine(date: String, type: String, time: String, content: String, note: String): String =
        "${csvEscape(date)},${type},${csvEscape(time)},${csvEscape(content)},${csvEscape(note)}\n"

    /** CSV 字段转义：含逗号/引号/换行时整体加引号，内部引号翻倍 */
    private fun csvEscape(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else {
            v
        }

    /** 合并各类型的日期并升序排列（yyyy-MM-dd 字符串可直接比较） */
    private fun sortedDatesOf(vararg maps: Map<String, *>): List<String> =
        maps.flatMap { it.keys }.toSortedSet().toList()

    // endregion

    // region 添加记录（草稿）

    fun startAdd() {
        val s = _uiState.value
        _uiState.value = s.closeAllPanels().copy(
            isAdding = true,
            draft = DraftRecord(
                mealType = MealType.fromTime(LocalTime.now()),
                time = defaultTimeFor(s.selectedDate)
            )
        )
    }

    /** 进入编辑模式：把记录载入草稿，复用添加面板 */
    fun startEdit(record: MealRecord) {
        _uiState.value = _uiState.value.closeAllPanels().copy(
            isAdding = true,
            editingRecordId = record.id,
            draft = DraftRecord(
                mealType = record.mealType,
                note = record.note,
                photos = record.photos,
                tags = record.tags,
                time = record.time
            )
        )
    }

    fun cancelAdd() {
        pendingCameraPath = null
        _uiState.value = _uiState.value.copy(
            isAdding = false,
            editingRecordId = null,
            draft = DraftRecord(mealType = MealType.fromTime(LocalTime.now()))
        )
    }

    fun setDraftMealType(type: MealType) {
        val s = _uiState.value
        _uiState.value = s.copy(draft = s.draft.copy(mealType = type))
    }

    fun setDraftNote(note: String) {
        val s = _uiState.value
        _uiState.value = s.copy(draft = s.draft.copy(note = note))
    }

    /** 调整饮食草稿的记录时间（补录/改时间） */
    fun setDraftTime(time: String) {
        val s = _uiState.value
        _uiState.value = s.copy(draft = s.draft.copy(time = time))
    }

    /** 切换草稿中的食物标签选中状态 */
    fun toggleDraftTag(name: String) {
        val s = _uiState.value
        val tags = if (s.draft.tags.contains(name)) {
            s.draft.tags - name
        } else {
            s.draft.tags + name
        }
        _uiState.value = s.copy(draft = s.draft.copy(tags = tags))
    }

    fun removeDraftPhoto(index: Int) {
        // 只从草稿移除，不动磁盘文件：若之后取消编辑，原记录仍引用该照片，
        // 不能提前删除（与删除记录"照片不会从设备中删除"的约定保持一致）
        val s = _uiState.value
        _uiState.value = s.copy(
            draft = s.draft.copy(photos = s.draft.photos.filterIndexed { i, _ -> i != index })
        )
    }

    /** 创建相机写入文件并返回 FileProvider Uri，供 Activity 启动相机 */
    fun prepareCameraFile(): Uri? {
        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        val ts = System.currentTimeMillis()
        val file = File(dir, "meal_$ts.jpg")
        return try {
            pendingCameraPath = file.absolutePath
            FileProvider.getUriForFile(app, app.packageName + ".fileprovider", file)
        } catch (e: IllegalArgumentException) {
            pendingCameraPath = null
            null
        }
    }

    /** 相机拍摄成功，将照片并入草稿 */
    fun onCameraPhotoTaken() {
        val path = pendingCameraPath
        pendingCameraPath = null
        if (path != null && File(path).exists()) {
            appendDraftPhoto(path)
        }
    }

    /** 相机取消或失败 */
    fun onCameraCancelled() {
        pendingCameraPath = null
    }

    /** 从相册选取的多张照片：逐张复制到应用私有目录后并入草稿 */
    fun addGalleryPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val savedPaths = withContext(Dispatchers.IO) {
                val dir = app.getExternalFilesDir(null) ?: app.filesDir
                // 同一毫秒内多张照片用下标错开文件名，避免互相覆盖
                uris.mapIndexed { index, uri ->
                    try {
                        val ts = System.currentTimeMillis() + index
                        val ext = queryExtension(uri)
                        val file = File(dir, "meal_$ts.$ext")
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (file.length() > 0) file.absolutePath else null
                    } catch (e: Exception) {
                        null
                    }
                }.filterNotNull()
            }
            if (savedPaths.isNotEmpty()) {
                val s = _uiState.value
                _uiState.value = s.copy(
                    draft = s.draft.copy(photos = s.draft.photos + savedPaths)
                )
            }
        }
    }

    private fun queryExtension(uri: Uri): String {
        val name = app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }
        val ext = name?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 }
        return ext ?: "jpg"
    }

    private fun appendDraftPhoto(path: String) {
        val s = _uiState.value
        _uiState.value = s.copy(draft = s.draft.copy(photos = s.draft.photos + path))
    }

    /** 保存当前草稿：编辑模式下更新原记录（日期/时间保持不变），否则按选中日期新建 */
    fun saveRecord() {
        viewModelScope.launch {
            val s = _uiState.value
            val editingId = s.editingRecordId
            if (editingId != null) {
                // 编辑：更新餐次/照片/标签/备注/时间（日期沿用原记录）
                dao.getRecordById(editingId)?.let { existing ->
                    dao.update(
                        existing.copy(
                            mealType = s.draft.mealType,
                            photos = s.draft.photos,
                            tagsJson = MealRecord.tagsEncode(s.draft.tags),
                            note = s.draft.note.trim(),
                            time = s.draft.time.ifEmpty { existing.time }
                        )
                    )
                }
            } else {
                val date = s.selectedDate
                dao.insert(
                    MealRecord(
                        date = date.toString(),
                        time = s.draft.time.ifEmpty { defaultTimeFor(date) },
                        mealType = s.draft.mealType,
                        photos = s.draft.photos,
                        tagsJson = MealRecord.tagsEncode(s.draft.tags),
                        note = s.draft.note.trim()
                    )
                )
            }
            pendingCameraPath = null
            _uiState.value = s.copy(
                isAdding = false,
                editingRecordId = null,
                draft = DraftRecord(mealType = MealType.fromTime(LocalTime.now())),
                recordDates = dao.getRecordDates().toSet(),
                totalRecordDays = dao.getRecordDays(),
                totalRecords = dao.getTotalCount(),
                selectedDateRecords = dao.getRecordsByDate(s.selectedDate.toString()),
                allMeals = dao.getAllRecordsDesc()
            )
            refreshFoodTagCounts()
        }
    }

    // endregion

    // region 删除记录

    fun deleteRecord(id: Int) {
        viewModelScope.launch {
            dao.deleteById(id)
            val s = _uiState.value
            _uiState.value = s.copy(
                recordDates = dao.getRecordDates().toSet(),
                totalRecordDays = dao.getRecordDays(),
                totalRecords = dao.getTotalCount(),
                selectedDateRecords = dao.getRecordsByDate(s.selectedDate.toString()),
                allMeals = dao.getAllRecordsDesc()
            )
            refreshFoodTagCounts()
        }
    }

    // endregion

    // region 排便/症状记录

    /** 打开排便记录面板（始终新增一条，记录到当前选中日期），并关闭其他面板 */
    fun startSymptomPanel() {
        val s = _uiState.value
        _uiState.value = s.closeAllPanels().copy(
            isSymptomPanelOpen = true,
            editingSymptomId = null,
            symptomDraft = SymptomDraft(time = defaultTimeFor(s.selectedDate))
        )
    }

    /** 编辑已有的一条排便记录（从当日记录列表点入） */
    fun startEditSymptom(record: DailySymptom) {
        val s = _uiState.value
        _uiState.value = s.closeAllPanels().copy(
            isSymptomPanelOpen = true,
            editingSymptomId = record.id,
            symptomDraft = SymptomDraft.from(record)
        )
    }

    fun cancelSymptomPanel() {
        _uiState.value = _uiState.value.copy(
            isSymptomPanelOpen = false,
            editingSymptomId = null,
            symptomDraft = SymptomDraft()
        )
    }

    fun setSymptomDraft(draft: SymptomDraft) {
        _uiState.value = _uiState.value.copy(symptomDraft = draft)
    }

    /** 保存排便记录：编辑模式更新原记录；新增模式插入新记录（同一天可多条） */
    fun saveSymptom() {
        viewModelScope.launch {
            val s = _uiState.value
            val date = s.selectedDate
            val d = s.symptomDraft
            val editingId = s.editingSymptomId
            if (editingId != null) {
                symptomDao.getById(editingId)?.let { existing ->
                    symptomDao.update(
                        existing.copy(
                            time = d.time.ifEmpty { existing.time },
                            bowelCount = d.bowelCount,
                            nightDiarrhea = d.nightDiarrhea,
                            bristolType = d.bristolType,
                            blood = d.blood,
                            mucus = d.mucus,
                            painScore = d.painScore,
                            painLocation = d.painLocation,
                            urgency = d.urgency,
                            note = d.note.trim()
                        )
                    )
                }
            } else {
                symptomDao.insert(
                    DailySymptom(
                        date = date.toString(),
                        time = d.time.ifEmpty { defaultTimeFor(date) },
                        bowelCount = d.bowelCount,
                        nightDiarrhea = d.nightDiarrhea,
                        bristolType = d.bristolType,
                        blood = d.blood,
                        mucus = d.mucus,
                        painScore = d.painScore,
                        painLocation = d.painLocation,
                        urgency = d.urgency,
                        note = d.note.trim()
                    )
                )
            }
            _uiState.value = _uiState.value.copy(
                isSymptomPanelOpen = false,
                editingSymptomId = null,
                symptomDraft = SymptomDraft()
            )
            refreshSymptoms()
        }
    }

    /** 删除指定的一条排便记录 */
    fun deleteSymptom(id: Int) {
        viewModelScope.launch {
            symptomDao.deleteById(id)
            refreshSymptoms()
        }
    }

    // endregion

    // region 当天记录筛选

    /** 点击统计卡切换筛选：点已选中的类别 = 取消筛选 */
    fun toggleDayRecordFilter(filter: DayFilter) {
        val s = _uiState.value
        _uiState.value = s.copy(
            dayRecordFilter = if (s.dayRecordFilter == filter) null else filter
        )
    }

    /** 清除筛选（"恢复"按钮） */
    fun clearDayRecordFilter() {
        _uiState.value = _uiState.value.copy(dayRecordFilter = null)
    }

    // endregion

    // region 首页导航 / 全屏照片

    fun selectTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    /** 首页周视图：只切换展示周，不改变选中日期（点选日期才会改变选中） */
    fun prevHomeWeek() {
        val s = _uiState.value
        _uiState.value = s.copy(homeWeekAnchor = s.homeWeekAnchor.minusWeeks(1))
    }

    fun nextHomeWeek() {
        val s = _uiState.value
        _uiState.value = s.copy(homeWeekAnchor = s.homeWeekAnchor.plusWeeks(1))
    }

    /** 首页月视图：只切换展示月（anchor 按整月移动，日保留、月末自动夹取），不改变选中日期 */
    fun prevHomeMonth() {
        val s = _uiState.value
        _uiState.value = s.copy(homeWeekAnchor = s.homeWeekAnchor.minusMonths(1))
    }

    fun nextHomeMonth() {
        val s = _uiState.value
        _uiState.value = s.copy(homeWeekAnchor = s.homeWeekAnchor.plusMonths(1))
    }

    /** 打开全屏照片查看：photos 为本次可滑动切换的照片集（默认单张） */
    fun showPhoto(path: String, photos: List<String> = listOf(path)) {
        _uiState.value = _uiState.value.copy(
            fullscreenPhotos = photos,
            fullscreenPhotoIndex = photos.indexOf(path).coerceAtLeast(0)
        )
    }

    fun hidePhoto() {
        _uiState.value = _uiState.value.copy(
            fullscreenPhotos = emptyList(),
            fullscreenPhotoIndex = 0
        )
    }

    // endregion

    // region 服药记录

    /** 当前时刻（HH:mm）：添加/补录服药的默认时间 */
    private fun nowTime(): String =
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

    /** 打开服药面板（添加），并关闭其他面板 */
    fun startAddMed() {
        val s = _uiState.value
        _uiState.value = s.closeAllPanels().copy(
            isMedPanelOpen = true,
            medDraft = MedDraft(time = nowTime())
        )
    }

    /** 首页服药提醒铃铛：若当前选中的不是今天，先切回今天（含数据加载）再打开添加服药面板 */
    fun startAddMedForToday() {
        val s = _uiState.value
        if (s.selectedDate == s.today) {
            startAddMed()
        } else {
            _uiState.value = s.copy(selectedDate = s.today, homeWeekAnchor = s.today)
            viewModelScope.launch {
                val dateStr = s.today.toString()
                _uiState.value = _uiState.value.copy(
                    selectedDateSymptoms = symptomDao.getByDate(dateStr),
                    selectedDateRecords = dao.getRecordsByDate(dateStr),
                    selectedDateMeds = medDao.getByDate(dateStr),
                    selectedDateNote = noteDao.getByDate(dateStr)
                )
                startAddMed()
            }
        }
    }

    /** 进入服药编辑 */
    fun startEditMed(record: MedRecord) {
        _uiState.value = _uiState.value.closeAllPanels().copy(
            isMedPanelOpen = true,
            editingMedId = record.id,
            medDraft = MedDraft(name = record.name, dose = record.dose, time = record.time)
        )
    }

    fun cancelMedPanel() {
        _uiState.value = _uiState.value.copy(
            isMedPanelOpen = false,
            editingMedId = null,
            medDraft = MedDraft()
        )
    }

    fun setMedDraft(draft: MedDraft) {
        _uiState.value = _uiState.value.copy(medDraft = draft)
    }

    /** 保存服药：编辑时更新原记录，否则按选中日期新建（时间默认当前时刻） */
    fun saveMed() {
        viewModelScope.launch {
            val s = _uiState.value
            val d = s.medDraft
            if (d.name.isBlank()) return@launch
            val date = s.selectedDate
            if (s.editingMedId != null) {
                medDao.getById(s.editingMedId)?.let { existing ->
                    medDao.update(
                        existing.copy(
                            name = d.name.trim(),
                            dose = d.dose.trim(),
                            time = d.time.ifEmpty { existing.time }
                        )
                    )
                }
            } else {
                medDao.insert(
                    MedRecord(
                        date = date.toString(),
                        time = d.time.ifEmpty { nowTime() },
                        name = d.name.trim(),
                        dose = d.dose.trim()
                    )
                )
            }
            _uiState.value = s.copy(
                isMedPanelOpen = false,
                editingMedId = null,
                medDraft = MedDraft(),
                selectedDateMeds = medDao.getByDate(date.toString()),
                totalMedRecords = medDao.getCount(),
                commonMedNames = addToCommonMeds(s.commonMedNames, d.name.trim())
            )
            refreshMedStats()
        }
    }

    fun deleteMed(id: Int) {
        viewModelScope.launch {
            medDao.deleteById(id)
            val s = _uiState.value
            _uiState.value = s.copy(
                selectedDateMeds = medDao.getByDate(s.selectedDate.toString()),
                totalMedRecords = medDao.getCount()
            )
            refreshMedStats()
        }
    }

    /** 删除常用药物标签（只移除快捷标签，不影响已保存的服药记录） */
    fun removeCommonMed(name: String) {
        viewModelScope.launch {
            val s = _uiState.value
            val updated = s.commonMedNames.filterNot { it == name }
            if (updated.size == s.commonMedNames.size) return@launch
            persistCommonMeds(updated)
            _uiState.value = s.copy(commonMedNames = updated)
        }
    }

    // endregion

    // region 每日感受

    /** 打开感受面板（当天已有则载入），并关闭其他面板 */
    fun openNotePanel() {
        val s = _uiState.value
        val existing = s.selectedDateNote
        _uiState.value = s.closeAllPanels().copy(
            isNotePanelOpen = true,
            noteDraft = NoteDraft(text = existing?.text ?: "")
        )
    }

    fun cancelNotePanel() {
        _uiState.value = _uiState.value.copy(
            isNotePanelOpen = false,
            noteDraft = NoteDraft()
        )
    }

    fun setNoteDraft(draft: NoteDraft) {
        _uiState.value = _uiState.value.copy(noteDraft = draft)
    }

    /** 保存（当天重复保存则覆盖）每日感受 */
    fun saveNote() {
        viewModelScope.launch {
            val s = _uiState.value
            val date = s.selectedDate
            val text = s.noteDraft.text.trim()
            if (text.isEmpty()) return@launch
            val existing = s.selectedDateNote
            noteDao.upsert(
                DailyNote(
                    id = existing?.id ?: 0,
                    date = date.toString(),
                    text = text,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
            )
            _uiState.value = s.copy(
                isNotePanelOpen = false,
                noteDraft = NoteDraft(),
                selectedDateNote = noteDao.getByDate(date.toString()),
                allNotes = noteDao.getAllNotesDesc(),
                totalNoteDays = noteDao.getCount()
            )
        }
    }

    /** 删除当前选中日期的感受 */
    fun deleteNote() {
        viewModelScope.launch {
            val s = _uiState.value
            s.selectedDateNote?.let { noteDao.deleteById(it.id) }
            _uiState.value = _uiState.value.copy(
                selectedDateNote = null,
                allNotes = noteDao.getAllNotesDesc(),
                totalNoteDays = noteDao.getCount()
            )
        }
    }

    // endregion

    // region 食物标签（耐受）

    /** 添加食物标签（指定初始耐受状态，默认可耐受）；饮食面板打开时添加后默认选中（已存在的食物也选中） */
    fun addFoodTag(name: String, tolerance: FoodTolerance = FoodTolerance.OK) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val exists = _uiState.value.foodTags.any { it.name == trimmed }
        viewModelScope.launch {
            if (!exists) {
                // sortOrder 追加到末尾，保证新食物出现在对应分区尾部
                val nextSortOrder = foodTagDao.maxSortOrder() + 1
                foodTagDao.insert(FoodTag(name = trimmed, tolerance = tolerance.ordinal, sortOrder = nextSortOrder))
            }
            // 一次原子写入：刷新标签列表 + 添加后默认选中该标签
            val s = _uiState.value
            val shouldSelect = s.isAdding && !s.draft.tags.contains(trimmed)
            _uiState.value = s.copy(
                foodTags = foodTagDao.getAll(),
                draft = if (shouldSelect) s.draft.copy(tags = s.draft.tags + trimmed) else s.draft
            )
        }
    }

    /** 点击切换耐受状态：可耐受 → 尝试 → 不耐受 → 可耐受 */
    fun cycleFoodTag(name: String) {
        viewModelScope.launch {
            val current = _uiState.value.foodTags.firstOrNull { it.name == name } ?: return@launch
            val next = (current.tolerance + 1) % 3
            foodTagDao.setTolerance(name, next)
            refreshFoodTags()
        }
    }

    /**
     * 拖动移动食物标签：调整前后位置（before = null 表示放到列表末尾），
     * 并可同时改变耐受状态（before 指向另一个分区内的标签时即跨分区移动）。
     * 实现：重建全局顺序 → 逐项写回 sortOrder → 刷新列表。
     */
    fun moveFoodTag(name: String, targetTolerance: FoodTolerance, before: String?) {
        if (before == name) return
        viewModelScope.launch {
            val current = foodTagDao.getAll()
            if (current.none { it.name == name }) return@launch
            val moved = current.first { it.name == name }.copy(tolerance = targetTolerance.ordinal)
            val rest = current.filter { it.name != name }
            val insertAt = if (before == null) rest.size
            else rest.indexOfFirst { it.name == before }.let { if (it == -1) rest.size else it }
            val ordered = rest.toMutableList().also { it.add(insertAt, moved) }
            ordered.forEachIndexed { index, tag ->
                if (tag.name == name) {
                    foodTagDao.updateTag(tag.name, tag.tolerance, index)
                } else if (tag.sortOrder != index) {
                    foodTagDao.setSortOrder(tag.name, index)
                }
            }
            refreshFoodTags()
        }
    }

    fun deleteFoodTag(name: String) {
        viewModelScope.launch {
            foodTagDao.deleteByName(name)
            refreshFoodTags()
        }
    }

    // endregion

    // region 我的

    fun setNickname(name: String) {
        val trimmed = name.trim().ifEmpty { "记录者" }
        prefs.edit().putString("nickname", trimmed).apply()
        _uiState.value = _uiState.value.copy(nickname = trimmed)
    }

    /** 修改头像（boy=男生 / girl=女生），持久化到 SharedPreferences */
    fun setAvatar(avatar: String) {
        prefs.edit().putString("avatar", avatar).apply()
        _uiState.value = _uiState.value.copy(avatar = avatar)
    }

    // region 主题设置

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(PREF_THEME_MODE, mode.key).apply()
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    // endregion

    // region 服药设置（每天次数 = 提醒时间条数，1~6 次）

    private fun loadMedReminderTimes(): List<String> {
        val raw = prefs.getString(PREF_MED_REMINDER_TIMES, null) ?: return DEFAULT_MED_REMINDER_TIMES
        val list = raw.split(",").map { it.trim() }.filter { it.matches(Regex("\\d{2}:\\d{2}")) }.sorted()
        return list.ifEmpty { DEFAULT_MED_REMINDER_TIMES }
    }

    /** 调整每天服药次数：收缩截断；扩充时按 MED_REMINDER_TIME_POOL 顺序补位 */
    fun setMedTimesPerDay(n: Int) {
        val count = n.coerceIn(1, 6)
        val current = _uiState.value.medReminderTimes
        val times = (current + MED_REMINDER_TIME_POOL.drop(current.size)).take(count)
        persistMedReminderTimes(times)
    }

    /** 修改第 index 个提醒时间（保存后整体升序） */
    fun setMedReminderTime(index: Int, time: String) {
        val current = _uiState.value.medReminderTimes.toMutableList()
        if (index in current.indices) {
            current[index] = time
            persistMedReminderTimes(current)
        }
    }

    private fun persistMedReminderTimes(times: List<String>) {
        val sorted = times.sorted()
        prefs.edit().putString(PREF_MED_REMINDER_TIMES, sorted.joinToString(",")).apply()
        _uiState.value = _uiState.value.copy(medReminderTimes = sorted)
    }

    // endregion
}
