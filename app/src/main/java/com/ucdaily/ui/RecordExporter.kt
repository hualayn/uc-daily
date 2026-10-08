package com.ucdaily.ui

import android.app.Application
import androidx.annotation.StringRes
import com.ucdaily.AppLocale
import com.ucdaily.R
import com.ucdaily.data.BLOOD_LABELS
import com.ucdaily.data.BRISTOL_LABELS
import com.ucdaily.data.DailyNote
import com.ucdaily.data.DailySymptom
import com.ucdaily.data.DailySymptomDao
import com.ucdaily.data.DailyNoteDao
import com.ucdaily.data.FoodTag
import com.ucdaily.data.FoodTagDao
import com.ucdaily.data.FoodTolerance
import com.ucdaily.data.MealRecord
import com.ucdaily.data.MealRecordDao
import com.ucdaily.data.MedRecord
import com.ucdaily.data.MedRecordDao
import com.ucdaily.data.PAIN_LOCATION_LABELS
import java.time.LocalDate
import java.util.Locale

/** 可导出的记录类型（导出对话框复选；labelRes 为多语言文案资源） */
enum class ExportType(@StringRes val labelRes: Int) {
    MEAL(R.string.type_meal),
    MED(R.string.type_med),
    BOWEL(R.string.type_bowel),
    NOTE(R.string.type_note)
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

private val CJK_LANGUAGES = setOf("zh", "ja", "ko")

/**
 * 记录导出（TXT / CSV 长表）：纯"数据 → 文本"转换，不触碰 UI 状态。
 *
 * 标点跟随当前应用语言：CJK（简中/日/韩）用全角"、（）：；"，
 * 其他语言用西文标点，保证任一语言导出的文件在该语言下都自然可读。
 */
class RecordExporter(
    private val app: Application,
    private val dao: MealRecordDao,
    private val medDao: MedRecordDao,
    private val symptomDao: DailySymptomDao,
    private val noteDao: DailyNoteDao,
    private val foodTagDao: FoodTagDao
) {

    /** 当前应用语言是否 CJK（"跟随系统"时用设备语言） */
    private val isCjk: Boolean =
        (AppLocale.localeForTag(AppLocale.currentTag(app)) ?: Locale.getDefault()).language in CJK_LANGUAGES

    private val listSep: String get() = if (isCjk) "、" else ","
    private val colon: String get() = if (isCjk) "：" else ":"
    private val parenOpen: String get() = if (isCjk) "（" else "("
    private val parenClose: String get() = if (isCjk) "）" else ")"
    private val clauseSep: String get() = if (isCjk) "；" else "; "

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
        // 食物耐受不属于日期记录：CSV 导出时无条件附带（TXT 不含）
        val tags = foodTagDao.getAll()

        val hasRecords = meals.isNotEmpty() || meds.isNotEmpty() || symptoms.isNotEmpty() || notes.isNotEmpty()
        val hasTags = format == ExportFormat.CSV && tags.isNotEmpty()
        if (!hasRecords && !hasTags) return null

        val fileName = app.getString(R.string.export_file_name, sStr, eStr) + "." + format.ext
        val text = if (format == ExportFormat.TXT) {
            buildExportTxt(s, e, meals, meds, symptoms, notes)
        } else {
            // 加 BOM，避免 Excel 打开中文 CSV 乱码
            "\uFEFF" + buildExportCsv(meals, meds, symptoms, notes, tags)
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
            if (mealByDate.isNotEmpty()) add(app.getString(R.string.type_meal))
            if (medByDate.isNotEmpty()) add(app.getString(R.string.type_med))
            if (sympByDate.isNotEmpty()) add(app.getString(R.string.type_bowel))
            if (noteByDate.isNotEmpty()) add(app.getString(R.string.type_note))
        }

        val sb = StringBuilder()
        sb.append(app.getString(R.string.export_txt_title)).append('\n')
        sb.append(app.getString(R.string.export_txt_range, s.toString(), e.toString())).append('\n')
        sb.append(app.getString(R.string.export_txt_types, present.joinToString(listSep))).append("\n\n")

        dates.forEach { dateStr ->
            val date = LocalDate.parse(dateStr)
            sb.append(app.getString(R.string.export_txt_day_header, dateStr, app.getString(weekNameRes(date))))
                .append('\n')

            mealByDate[dateStr]?.forEach {
                sb.append("  ").append(app.getString(R.string.export_txt_meal, it.time, app.getString(it.mealType.labelRes)))
                if (it.tags.isNotEmpty()) sb.append(colon).append(it.tags.joinToString(listSep))
                if (it.note.isNotBlank()) sb.append(parenOpen).append(it.note.trim().replace(Regex("\\R"), " ")).append(parenClose)
                sb.append('\n')
            }
            medByDate[dateStr]?.forEach {
                sb.append("  ").append(app.getString(R.string.export_txt_med, it.time, it.name))
                if (it.dose.isNotBlank()) sb.append(' ').append(it.dose.trim())
                sb.append('\n')
            }
            sympByDate[dateStr]?.sortedBy { it.id }?.forEach {
                sb.append("  ").append(app.getString(R.string.export_txt_bowel))
                if (it.time.isNotBlank()) sb.append(' ').append(it.time)
                sb.append(' ').append(app.getString(R.string.export_txt_count)).append(it.bowelCount)
                if (it.nightDiarrhea) sb.append(app.getString(R.string.export_txt_night))
                sb.append(app.getString(R.string.export_txt_bristol))
                sb.append(if (it.bristolType in 1..7) "${it.bristolType} ${app.getString(BRISTOL_LABELS[it.bristolType - 1])}" else app.getString(R.string.export_txt_not_recorded))
                sb.append(app.getString(R.string.export_txt_blood)).append(if (it.blood in 0..3) app.getString(BLOOD_LABELS[it.blood]) else app.getString(R.string.export_txt_not_recorded))
                sb.append(app.getString(R.string.export_txt_mucus)).append(if (it.mucus) app.getString(R.string.common_yes) else app.getString(R.string.common_no))
                sb.append(app.getString(R.string.export_txt_pain)).append(it.painScore).append(app.getString(R.string.common_points))
                if (it.painLocation in 1..4) sb.append(' ').append(app.getString(PAIN_LOCATION_LABELS[it.painLocation]))
                sb.append(app.getString(R.string.export_txt_urgency)).append(if (it.urgency) app.getString(R.string.common_yes) else app.getString(R.string.common_no))
                if (it.note.isNotBlank()) sb.append(app.getString(R.string.export_txt_other)).append(it.note.trim().replace(Regex("\\R"), " "))
                sb.append('\n')
            }
            noteByDate[dateStr]?.forEach {
                sb.append("  ").append(app.getString(R.string.export_txt_note)).append(' ')
                    .append(it.text.trim().replace(Regex("\\R"), " ")).append('\n')
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    /** 星期 → 多语言文案资源 id（周一开头） */
    private fun weekNameRes(date: LocalDate): Int = when (date.dayOfWeek.value) {
        1 -> R.string.week_mon
        2 -> R.string.week_tue
        3 -> R.string.week_wed
        4 -> R.string.week_thu
        5 -> R.string.week_fri
        6 -> R.string.week_sat
        else -> R.string.week_sun
    }

    /** CSV 版：长表 日期,类型,时间,内容,备注（含逗号/引号/换行的字段自动加引号转义）；
     *  记录行之后追加食物耐受行（日期留空，类型=耐受，时间=耐受状态，内容=食物名） */
    private fun buildExportCsv(
        meals: List<MealRecord>,
        meds: List<MedRecord>,
        symptoms: List<DailySymptom>,
        notes: List<DailyNote>,
        tags: List<FoodTag>
    ): String {
        val mealByDate = meals.groupBy { it.date }
        val medByDate = meds.groupBy { it.date }
        val sympByDate = symptoms.groupBy { it.date }
        val noteByDate = notes.groupBy { it.date }
        val dates = sortedDatesOf(mealByDate, medByDate, sympByDate, noteByDate)

        val sb = StringBuilder()
        sb.append(app.getString(R.string.export_csv_header)).append('\n')
        dates.forEach { dateStr ->
            mealByDate[dateStr]?.forEach {
                val content = buildList {
                    add(app.getString(it.mealType.labelRes) + colon)
                    if (it.tags.isNotEmpty()) add(it.tags.joinToString(listSep))
                }.joinToString(" ")
                sb.append(csvLine(dateStr, app.getString(R.string.type_meal), it.time, content, it.note.trim()))
            }
            medByDate[dateStr]?.forEach {
                val content = buildList {
                    add(it.name)
                    if (it.dose.isNotBlank()) add(it.dose.trim())
                }.joinToString(" ")
                sb.append(csvLine(dateStr, app.getString(R.string.type_med), it.time, content, ""))
            }
            sympByDate[dateStr]?.sortedBy { it.id }?.forEach {
                val content = buildList {
                    add(app.getString(R.string.export_txt_count) + it.bowelCount + (if (it.nightDiarrhea) app.getString(R.string.export_txt_night) else ""))
                    add(app.getString(R.string.export_csv_bristol) + (if (it.bristolType in 1..7) "${it.bristolType} ${app.getString(BRISTOL_LABELS[it.bristolType - 1])}" else app.getString(R.string.export_txt_not_recorded)))
                    add(app.getString(R.string.export_csv_blood) + (if (it.blood in 0..3) app.getString(BLOOD_LABELS[it.blood]) else app.getString(R.string.export_txt_not_recorded)))
                    add(app.getString(R.string.export_csv_mucus) + (if (it.mucus) app.getString(R.string.common_yes) else app.getString(R.string.common_no)))
                    add(app.getString(R.string.export_csv_pain) + it.painScore + app.getString(R.string.common_points) + (if (it.painLocation in 1..4) " ${app.getString(PAIN_LOCATION_LABELS[it.painLocation])}" else ""))
                    add(app.getString(R.string.export_csv_urgency) + (if (it.urgency) app.getString(R.string.common_yes) else app.getString(R.string.common_no)))
                }.joinToString(clauseSep)
                sb.append(csvLine(dateStr, app.getString(R.string.type_bowel), it.time, content, it.note.trim()))
            }
            noteByDate[dateStr]?.forEach {
                sb.append(csvLine(dateStr, app.getString(R.string.type_note), "", it.text.trim().replace(Regex("\\R"), " "), ""))
            }
        }
        // 食物耐受：无日期记录，统一追加在最后（getAll 已按 sortOrder 升序）
        tags.forEach { tag ->
            sb.append(
                csvLine(
                    "",
                    app.getString(R.string.type_tolerance),
                    app.getString(FoodTolerance.fromValue(tag.tolerance).labelRes),
                    tag.name.trim(),
                    ""
                )
            )
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
}
