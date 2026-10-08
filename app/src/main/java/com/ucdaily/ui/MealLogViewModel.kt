package com.ucdaily.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ucdaily.AppLocale
import com.ucdaily.MedReminder
import com.ucdaily.R
import com.ucdaily.data.AppDatabase
import com.ucdaily.data.DailyNote
import com.ucdaily.data.DailySymptom
import com.ucdaily.data.FoodTag
import com.ucdaily.data.FoodTolerance
import com.ucdaily.data.MealRecord
import com.ucdaily.data.MealType
import com.ucdaily.data.MedRecord
import com.ucdaily.data.RestoreImporter
import com.ucdaily.data.activityScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** "当天记录"筛选类别（点击首页统计卡切换；labelRes 为多语言文案资源） */
enum class DayFilter(@StringRes val labelRes: Int) {
    MEAL(R.string.type_meal),
    BOWEL(R.string.type_bowel),
    MED(R.string.type_med)
}

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
    /** 当前打开面板对应的日期（打开面板那一刻固定；保存草稿写入该日期，
     *  面板开着跨零点时不会把草稿静默写进新的一天） */
    val panelDate: LocalDate = LocalDate.now(),
    val draft: DraftRecord = DraftRecord(MealType.fromTime(LocalTime.now())),
    /** 我的：昵称（SharedPreferences 持久化；默认文案按当前语言解析） */
    val nickname: String = "",
    /** 我的：头像（SharedPreferences 持久化）：default/boy/girl */
    val avatar: String = AVATAR_DEFAULT,
    /** 我的→主题：主题模式，默认跟随系统（SharedPreferences 持久化） */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 我的→字体大小：字体档位，默认标准（SharedPreferences 持久化） */
    val fontLevel: FontSizeLevel = FontSizeLevel.STANDARD,
    /** 我的→语言：所选语言 tag（"system" = 跟随系统；SharedPreferences 持久化，切换后 recreate 生效） */
    val languageTag: String = AppLocale.TAG_SYSTEM,
    /** 我的→首页寄语：首页顶部横幅轮播列表（列表为空时首页回退轮播内置默认；SharedPreferences 持久化） */
    val homeSlogans: List<String> = emptyList(),
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
    /** 设置持久化（昵称/头像/主题/字体/语言/寄语/提醒时间/常用药物） */
    private val settingsStore = AppSettingsStore(app, medDao)
    /** 照片管线（相机/相册/压缩） */
    private val photoStore = MealPhotoStore(app)
    /** 记录导出（TXT/CSV） */
    private val exporter = RecordExporter(app, dao, medDao, symptomDao, noteDao, foodTagDao)

    private val _uiState = MutableStateFlow(MealUiState())
    val uiState: StateFlow<MealUiState> = _uiState

    init {
        loadState()
        // 应用开着跨零点时，在零点检查一次并刷新"今天"（后台不保证触发，
        // 回到前台由 MainActivity 生命周期再次调用 checkDayChange 兜底）
        viewModelScope.launch {
            while (true) {
                val secsToMidnight = (86_400 - LocalTime.now().toSecondOfDay()) % 86_400
                delay(secsToMidnight * 1000L + 5_000L)
                checkDayChange()
            }
        }
        // 服药提醒：到点由系统闹钟唤醒应用检查/发出通知（不依赖应用驻留，
        // 见 MedReminder + MedAlarmReceiver）；应用存活期间再每分钟同步一次
        // （服药记录刚保存/删除后立刻刷新或取消通知）
        MedReminder.scheduleNext(app)
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                MedReminder.sync(app)
            }
        }
    }

    // region 数据加载

    private fun loadState() {
        viewModelScope.launch {
            val dateStr = _uiState.value.selectedDate.toString()
            val allSymptoms = symptomDao.getAll()
            val recordDates = dao.getRecordDates().toSet()
            val recordDays = dao.getRecordDays()
            val totalCount = dao.getTotalCount()
            val medCount = medDao.getCount()
            val records = dao.getRecordsByDate(dateStr)
            val meds = medDao.getByDate(dateStr)
            val note = noteDao.getByDate(dateStr)
            val todayTimes = medDao.getByDate(_uiState.value.today.toString()).map { it.time }
            val foodTags = foodTagDao.getAll()
            val commonMeds = settingsStore.loadCommonMeds()
            val nickname = settingsStore.loadNickname()
            val avatar = settingsStore.loadAvatar()
            val themeMode = settingsStore.loadThemeMode()
            val fontLevel = settingsStore.loadFontLevel()
            val languageTag = settingsStore.loadLanguageTag()
            val homeSlogans = settingsStore.loadHomeSlogans()
            val medTimes = settingsStore.loadMedReminderTimes()
            val noteDays = noteDao.getCount()
            val allMeals = dao.getAllRecordsDesc()
            val allMeds = medDao.getAllMedsDesc()
            val allNotes = noteDao.getAllNotesDesc()
            // 基于最新状态合并写入（update），避免协程开头的过期快照把期间
            // 发生的并发更新（如零点检查已推进 today）整体覆盖回去；
            // 加载期间选中日期变化时，日期维度的字段保持当前值不覆盖
            _uiState.update { cur ->
                val dateChanged = cur.selectedDate.toString() != dateStr
                cur.copy(
                    loading = false,
                    recordDates = recordDates,
                    totalRecordDays = recordDays,
                    totalRecords = totalCount,
                    totalMedRecords = medCount,
                    symptomByDate = latestSymptomByDate(allSymptoms),
                    selectedDateSymptoms = if (dateChanged) cur.selectedDateSymptoms
                        else allSymptoms
                            .filter { it.date == dateStr }
                            .sortedByDescending { it.id },
                    selectedDateRecords = if (dateChanged) cur.selectedDateRecords else records,
                    selectedDateMeds = if (dateChanged) cur.selectedDateMeds else meds,
                    todayMedTimes = todayTimes,
                    selectedDateNote = if (dateChanged) cur.selectedDateNote else note,
                    foodTags = foodTags,
                    commonMedNames = commonMeds,
                    nickname = nickname,
                    avatar = avatar,
                    themeMode = themeMode,
                    fontLevel = fontLevel,
                    languageTag = languageTag,
                    homeSlogans = homeSlogans,
                    medReminderTimes = medTimes,
                    allSymptoms = allSymptoms,
                    totalNoteDays = noteDays,
                    allMeals = allMeals,
                    allMeds = allMeds,
                    allNotes = allNotes
                )
            }
            refreshFoodTagCounts()
            syncMedReminderNotification()
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

    /** 重新加载服药总数、全部服药列表与今天的服药时间（保存/删除服药后调用） */
    private fun refreshMedStats() {
        viewModelScope.launch {
            val count = medDao.getCount()
            val allMeds = medDao.getAllMedsDesc()
            val todayTimes = medDao.getByDate(_uiState.value.today.toString()).map { it.time }
            _uiState.update { it.copy(
                totalMedRecords = count,
                allMeds = allMeds,
                todayMedTimes = todayTimes
            ) }
            syncMedReminderNotification()
        }
    }

    /** 日期变化（跨零点）检查：刷新"今天"；
     * 选中日期若为旧的"今天"则跟随到新一天并重载当天数据。
     * 两个调用点：① 应用开着跨零点时的零点定时器；
     * ② 每次应用回到前台（ON_START）——后台期间进程可能被冻结/Doze，
     * 零点定时器不一定触发，回到前台必须主动补查一次 */
    fun checkDayChange() {
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
            val todayTimes = medDao.getByDate(now.toString()).map { it.time }
            val dateStr = now.toString()
            val symptoms = if (followToday) symptomDao.getByDate(dateStr) else null
            val records = if (followToday) dao.getRecordsByDate(dateStr) else null
            val meds = if (followToday) medDao.getByDate(dateStr) else null
            val note = if (followToday) noteDao.getByDate(dateStr) else null
            _uiState.update { cur ->
                cur.copy(
                    todayMedTimes = todayTimes,
                    selectedDateSymptoms = symptoms ?: cur.selectedDateSymptoms,
                    selectedDateRecords = records ?: cur.selectedDateRecords,
                    selectedDateMeds = meds ?: cur.selectedDateMeds,
                    selectedDateNote = note ?: cur.selectedDateNote
                )
            }
            syncMedReminderNotification()
        }
    }

    /** 重新加载食物标签（增删改后调用） */
    private fun refreshFoodTags() {
        viewModelScope.launch {
            val tags = foodTagDao.getAll()
            _uiState.update { it.copy(foodTags = tags) }
        }
    }

    // endregion

    // region 日期选择

    fun selectDate(date: LocalDate) {
        viewModelScope.launch {
            val dateStr = date.toString()
            val symptoms = symptomDao.getByDate(dateStr)
            val records = dao.getRecordsByDate(dateStr)
            val meds = medDao.getByDate(dateStr)
            val note = noteDao.getByDate(dateStr)
            _uiState.update { it.copy(
                selectedDate = date,
                // 点选日期时，周历展示周跟随到新日期所在周
                homeWeekAnchor = date,
                selectedDateSymptoms = symptoms,
                selectedDateRecords = records,
                selectedDateMeds = meds,
                selectedDateNote = note
            ) }
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
    ): ExportResult? = exporter.exportRecords(start, end, types, format)

    // endregion

    // region 记录恢复

    /**
     * 从导出的 CSV 恢复记录（"我的 → 恢复记录"）。
     * @return 恢复统计；文件无法识别（非本应用导出的 CSV）时返回 null。
     * 恢复成功后重载全部界面数据。
     */
    suspend fun restoreRecords(csv: String): RestoreImporter.Result? {
        // 恢复期间显示加载态（复用首页的 loading 遮罩），避免用户误以为卡死
        _uiState.update { it.copy(loading = true) }
        val result = withContext(Dispatchers.IO) {
            RestoreImporter(app, dao, medDao, symptomDao, noteDao, foodTagDao).restore(csv)
        }
        if (result != null) {
            // 恢复成功：重载全部界面数据（loadState 结束时会把 loading 置回 false）
            loadState()
        } else {
            // 文件无法识别：不重载，手动解除加载态
            _uiState.update { it.copy(loading = false) }
        }
        return result
    }

    // endregion

    // region 添加记录（草稿）

    fun startAdd() {
        val s = _uiState.value
        _uiState.value = s.closeAllPanels().copy(
            isAdding = true,
            panelDate = s.selectedDate,
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
            panelDate = LocalDate.parse(record.date),
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
        photoStore.onCameraCancelled()
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

    /**
     * 直接选中已存在的食物标签（幂等：已选中则不变，不会取消选中）。
     * 用于"添加饮食"页"添加"按钮输入已存在标签时——直接选中，不弹耐受状态菜单。
     */
    fun selectDraftTag(name: String) {
        val s = _uiState.value
        if (s.draft.tags.contains(name)) return
        _uiState.value = s.copy(draft = s.draft.copy(tags = s.draft.tags + name))
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
    fun prepareCameraFile(): Uri? = photoStore.prepareCameraFile()

    /** 相机拍摄成功：压缩到 300KB 以下后再并入草稿（IO 线程压缩，不阻塞 UI） */
    fun onCameraPhotoTaken() {
        viewModelScope.launch {
            photoStore.onCameraPhotoTaken()?.let { appendDraftPhoto(it) }
        }
    }

    /** 相机取消或失败（清理可能的孤儿文件） */
    fun onCameraCancelled() {
        photoStore.onCameraCancelled()
    }

    /** 从相册选取的多张照片：逐张复制到应用私有目录（含压缩）后并入草稿 */
    fun addGalleryPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val savedPaths = photoStore.addGalleryPhotos(uris)
            if (savedPaths.isNotEmpty()) {
                _uiState.update { it.copy(draft = it.draft.copy(photos = it.draft.photos + savedPaths)) }
            }
        }
    }

    private fun appendDraftPhoto(path: String) {
        _uiState.update { it.copy(draft = it.draft.copy(photos = it.draft.photos + path)) }
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
                // 落面板打开时固定的日期（跨零点时 selectedDate 已变，但草稿属于原日期）
                val date = s.panelDate
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
            photoStore.onCameraCancelled()
            val recordDates = dao.getRecordDates().toSet()
            val recordDays = dao.getRecordDays()
            val totalCount = dao.getTotalCount()
            val records = dao.getRecordsByDate(s.selectedDate.toString())
            val allMeals = dao.getAllRecordsDesc()
            val newDraft = DraftRecord(mealType = MealType.fromTime(LocalTime.now()))
            _uiState.update { it.copy(
                isAdding = false,
                editingRecordId = null,
                draft = newDraft,
                recordDates = recordDates,
                totalRecordDays = recordDays,
                totalRecords = totalCount,
                selectedDateRecords = records,
                allMeals = allMeals
            ) }
            refreshFoodTagCounts()
        }
    }

    // endregion

    // region 删除记录

    fun deleteRecord(id: Int) {
        viewModelScope.launch {
            dao.deleteById(id)
            val recordDates = dao.getRecordDates().toSet()
            val recordDays = dao.getRecordDays()
            val totalCount = dao.getTotalCount()
            val records = dao.getRecordsByDate(_uiState.value.selectedDate.toString())
            val allMeals = dao.getAllRecordsDesc()
            _uiState.update { it.copy(
                recordDates = recordDates,
                totalRecordDays = recordDays,
                totalRecords = totalCount,
                selectedDateRecords = records,
                allMeals = allMeals
            ) }
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
            panelDate = s.selectedDate,
            symptomDraft = SymptomDraft(time = defaultTimeFor(s.selectedDate))
        )
    }

    /** 编辑已有的一条排便记录（从当日记录列表点入） */
    fun startEditSymptom(record: DailySymptom) {
        val s = _uiState.value
        _uiState.value = s.closeAllPanels().copy(
            isSymptomPanelOpen = true,
            editingSymptomId = record.id,
            panelDate = LocalDate.parse(record.date),
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
            // 落面板打开时固定的日期（跨零点时 selectedDate 已变，但草稿属于原日期）
            val date = s.panelDate
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
            panelDate = s.selectedDate,
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
                val symptoms = symptomDao.getByDate(dateStr)
                val records = dao.getRecordsByDate(dateStr)
                val meds = medDao.getByDate(dateStr)
                val note = noteDao.getByDate(dateStr)
                _uiState.update { it.copy(
                    selectedDateSymptoms = symptoms,
                    selectedDateRecords = records,
                    selectedDateMeds = meds,
                    selectedDateNote = note
                ) }
                startAddMed()
            }
        }
    }

    /** 进入服药编辑 */
    fun startEditMed(record: MedRecord) {
        _uiState.value = _uiState.value.closeAllPanels().copy(
            isMedPanelOpen = true,
            editingMedId = record.id,
            panelDate = LocalDate.parse(record.date),
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
            // 落面板打开时固定的日期（跨零点时 selectedDate 已变，但草稿属于原日期）
            val date = s.panelDate
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
            val meds = medDao.getByDate(date.toString())
            val count = medDao.getCount()
            val common = settingsStore.addToCommonMeds(s.commonMedNames, d.name.trim())
            _uiState.update { it.copy(
                isMedPanelOpen = false,
                editingMedId = null,
                medDraft = MedDraft(),
                // 面板日期 == 当前选中日期时才刷列表；跨零点后保存，UI 显示的是新日期，
                // 旧日期的 meds 不应覆盖，等新日期被选中时再加载
                selectedDateMeds = if (date == it.selectedDate) meds else it.selectedDateMeds,
                totalMedRecords = count,
                commonMedNames = common
            ) }
            refreshMedStats()
        }
    }

    fun deleteMed(id: Int) {
        viewModelScope.launch {
            medDao.deleteById(id)
            val meds = medDao.getByDate(_uiState.value.selectedDate.toString())
            val count = medDao.getCount()
            _uiState.update { it.copy(
                selectedDateMeds = meds,
                totalMedRecords = count
            ) }
            refreshMedStats()
        }
    }

    /** 删除常用药物标签（只移除快捷标签，不影响已保存的服药记录） */
    fun removeCommonMed(name: String) {
        viewModelScope.launch {
            val s = _uiState.value
            val updated = s.commonMedNames.filterNot { it == name }
            if (updated.size == s.commonMedNames.size) return@launch
            settingsStore.persistCommonMeds(updated)
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
            panelDate = s.selectedDate,
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
            // 落面板打开时固定的日期（跨零点时 selectedDate 已变，但草稿属于原日期）
            val date = s.panelDate
            val text = s.noteDraft.text.trim()
            if (text.isEmpty()) return@launch
            val existing = if (date == s.selectedDate) s.selectedDateNote else noteDao.getByDate(date.toString())
            noteDao.upsert(
                DailyNote(
                    id = existing?.id ?: 0,
                    date = date.toString(),
                    text = text,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
            )
            val note = noteDao.getByDate(date.toString())
            val allNotes = noteDao.getAllNotesDesc()
            val noteDays = noteDao.getCount()
            _uiState.update { it.copy(
                isNotePanelOpen = false,
                noteDraft = NoteDraft(),
                // 面板日期 == 当前选中日期时才直接刷 UI，否则等下次选中该日期时加载
                selectedDateNote = if (date == it.selectedDate) note else it.selectedDateNote,
                allNotes = allNotes,
                totalNoteDays = noteDays
            ) }
        }
    }

    /** 删除当前选中日期的感受 */
    fun deleteNote() {
        viewModelScope.launch {
            _uiState.value.selectedDateNote?.let { noteDao.deleteById(it.id) }
            val allNotes = noteDao.getAllNotesDesc()
            val noteDays = noteDao.getCount()
            _uiState.update { it.copy(
                selectedDateNote = null,
                allNotes = allNotes,
                totalNoteDays = noteDays
            ) }
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
            // 一次原子写入：刷新标签列表 + 添加后默认选中该标签（基于最新状态合并）
            val tags = foodTagDao.getAll()
            _uiState.update { cur ->
                val shouldSelect = cur.isAdding && !cur.draft.tags.contains(trimmed)
                cur.copy(
                    foodTags = tags,
                    draft = if (shouldSelect) cur.draft.copy(tags = cur.draft.tags + trimmed) else cur.draft
                )
            }
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
        // 乐观更新：先【同步】更新内存顺序——与拖动松手的 dragInfo 清空同帧生效，
        // 展示顺序直接落到最终顺序，避免落点后瞬间闪回旧顺序再弹回
        val current = _uiState.value.foodTags
        if (current.none { it.name == name }) return
        val moved = current.first { it.name == name }.copy(tolerance = targetTolerance.ordinal)
        val rest = current.filter { it.name != name }
        val insertAt = if (before == null) rest.size
        else rest.indexOfFirst { it.name == before }.let { if (it == -1) rest.size else it }
        val ordered = rest.toMutableList().also { it.add(insertAt, moved) }
        _uiState.value = _uiState.value.copy(foodTags = ordered)
        // 异步落库
        viewModelScope.launch {
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

    /**
     * 分区一键排序：按标签次数对指定分区内的标签排序。
     * reverse = false → 正向（次数从少到多）；reverse = true → 逆向（次数从多到少）。
     * 次数相同的标签保持原有相对顺序（稳定排序）。
     * 实现：只改变本分区内相对顺序——分区标签排序后回填全局顺序中本分区原先占位的位置
     * （其它分区位置不动）→ 逐项写回 sortOrder → 刷新列表。
     */
    fun sortFoodTagsByCount(tolerance: FoodTolerance, reverse: Boolean) {
        val s = _uiState.value
        val current = s.foodTags
        val section = current.filter { FoodTolerance.fromValue(it.tolerance) == tolerance }
        if (section.size < 2) return
        val counts = s.foodTagCounts
        val sorted = if (reverse) section.sortedByDescending { counts[it.name] ?: 0 }
        else section.sortedBy { counts[it.name] ?: 0 }
        if (sorted.map { it.name } == section.map { it.name }) return
        // 排序后的标签按原顺序回填全局中本分区占位（其它分区相对位置不变）
        val sortedIt = sorted.iterator()
        val ordered = current.map {
            if (FoodTolerance.fromValue(it.tolerance) == tolerance) sortedIt.next() else it
        }
        // 乐观更新：先同步更新内存顺序，展示顺序直接落到最终顺序
        _uiState.value = s.copy(foodTags = ordered)
        // 异步落库
        viewModelScope.launch {
            ordered.forEachIndexed { index, tag ->
                if (tag.sortOrder != index) foodTagDao.setSortOrder(tag.name, index)
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
        val trimmed = name.trim().ifEmpty { app.getString(R.string.profile_default_nickname) }
        settingsStore.saveNickname(trimmed)
        _uiState.value = _uiState.value.copy(nickname = trimmed)
    }

    /** 修改头像（boy=男生 / girl=女生），持久化到 SharedPreferences */
    fun setAvatar(avatar: String) {
        settingsStore.saveAvatar(avatar)
        _uiState.value = _uiState.value.copy(avatar = avatar)
    }

    // region 首页寄语（横幅轮播列表：我的→首页寄语页管理，首页顶部欢迎卡每天按序取一条）

    /** 修改第 index 条寄语（内容为空时不生效） */
    fun updateHomeSlogan(index: Int, text: String) {
        val trimmed = text.trim().take(MAX_HOME_SLOGAN_LEN)
        if (trimmed.isEmpty()) return
        val list = _uiState.value.homeSlogans.toMutableList()
        if (index !in list.indices) return
        list[index] = trimmed
        settingsStore.persistHomeSlogans(list)
        _uiState.update { it.copy(homeSlogans = list) }
    }

    /** 添加一条寄语（追加到末尾；与已有条目完全相同时不重复添加） */
    fun addHomeSlogan(text: String) {
        val trimmed = text.trim().take(MAX_HOME_SLOGAN_LEN)
        if (trimmed.isEmpty() || trimmed in _uiState.value.homeSlogans) return
        val list = _uiState.value.homeSlogans + trimmed
        settingsStore.persistHomeSlogans(list)
        _uiState.update { it.copy(homeSlogans = list) }
    }

    /** 删除第 index 条寄语（删空后首页回退轮播内置默认寄语） */
    fun deleteHomeSlogan(index: Int) {
        val list = _uiState.value.homeSlogans
        if (index !in list.indices) return
        val updated = list.filterIndexed { i, _ -> i != index }
        settingsStore.persistHomeSlogans(updated)
        _uiState.update { it.copy(homeSlogans = updated) }
    }

    /** 恢复内置默认寄语列表：清空固化的副本，默认寄语按当前语言实时解析（切换语言后自动跟随，不会残留其他语言的文案） */
    fun resetHomeSlogans() {
        settingsStore.resetHomeSlogans()
        _uiState.update { it.copy(homeSlogans = defaultHomeSlogans(app)) }
    }

    // endregion

    // region 主题设置

    fun setThemeMode(mode: ThemeMode) {
        settingsStore.saveThemeMode(mode)
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    // endregion

    // region 字体大小（我的→字体大小：影响首页/耐受/日常管理三个 Tab 的文字，SharedPreferences 持久化）

    fun setFontSize(level: FontSizeLevel) {
        settingsStore.saveFontLevel(level)
        _uiState.value = _uiState.value.copy(fontLevel = level)
    }

    // endregion

    // region 语言设置（我的→语言：持久化所选语言；切换后立即生效需调用方 Activity.recreate()）

    fun setLanguage(tag: String) {
        settingsStore.saveLanguageTag(tag)
        _uiState.value = _uiState.value.copy(languageTag = tag)
    }

    // endregion

    // region 服药设置（每天次数 = 提醒时间条数，1~9 次）

    /** 调整每天服药次数：收缩截断；扩充时按 MED_REMINDER_TIME_POOL 顺序补位 */
    fun setMedTimesPerDay(n: Int) {
        val count = n.coerceIn(1, 9)
        val current = _uiState.value.medReminderTimes
        val times = (current + MED_REMINDER_TIME_POOL.drop(current.size)).take(count)
        applyMedReminderTimes(times)
    }

    /** 修改第 index 个提醒时间（保存后整体升序） */
    fun setMedReminderTime(index: Int, time: String) {
        val current = _uiState.value.medReminderTimes.toMutableList()
        if (index in current.indices) {
            current[index] = time
            applyMedReminderTimes(current)
        }
    }

    /** 持久化提醒时间并同步 UI / 重新安排系统闹钟 / 刷新通知 */
    private fun applyMedReminderTimes(times: List<String>) {
        val sorted = times.sorted()
        settingsStore.persistMedReminderTimes(sorted)
        _uiState.update { it.copy(medReminderTimes = sorted) }
        // 提醒时间变化：重新安排系统闹钟
        MedReminder.scheduleNext(app)
        syncMedReminderNotification()
    }

    // region 服药提醒（统一在 MedReminder：通知发出 / 取消 + 系统闹钟安排，与首页铃铛同一判定口径）

    /**
     * 同步服药提醒系统通知：
     * 应服药总数 = 已到点（<= 当前时刻）的提醒时间个数；实际服药总数 = 今天服药记录总条数。
     * 实际 < 应服 → 发出 / 刷新通知（状态栏常驻图标 / 应用角标）；实际 >= 应服 → 取消。
     * 应用在前台（首页已打开）时不发出通知、只撤掉后台残留通知，
     * 未服药由首页右上角铃铛提醒（见 MedReminder.setAppInForeground）。
     * 由以下时机触发：应用启动、每分钟定时、服药记录增删、提醒时间 / 次数修改、跨零点，
     * 以及 Android 13+ 通知权限授予后（MainActivity 回调）。
     */
    fun syncMedReminderNotification() {
        viewModelScope.launch { MedReminder.sync(app) }
    }

    /**
     * 应用退到后台时刷新桌面角标：换新通知 id 静默重发一次。
     * 真机验证：MIUI 桌面在应用前台期间不处理角标重新显示（应用打开时重发
     * 无效果且多一次通知事件，已移除），应用退到后台时（onStop）的这次重发
     * 即可让角标恢复；延迟补发反而造成角标闪动，不再使用。
     */
    fun refreshMedReminderBadgeToBackground() {
        viewModelScope.launch { MedReminder.refreshLauncherBadge(app, "退后台") }
    }

    // endregion
}
