package com.ucdaily.data

import android.content.Context
import com.ucdaily.R
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 从"导出记录 → 文件 → CSV"恢复记录。
 *
 * 解析本应用导出的 CSV（长表：日期,类型,时间,内容,备注；内容列为本地化复合文本）：
 * - 类型列、内容列按**当前语言**匹配导出时的文案（导出与恢复语言不一致时该行无法识别，计入失败行）；
 * - 同类型同日同字段的记录自动跳过（重复导入同一文件不会产生重复数据）；
 * - 每日感受按 date 唯一，重复导入时覆盖为文件内容。
 */
class RestoreImporter(
    private val context: Context,
    private val mealDao: MealRecordDao,
    private val medDao: MedRecordDao,
    private val symptomDao: DailySymptomDao,
    private val noteDao: DailyNoteDao,
    private val foodTagDao: FoodTagDao
) {

    /** 恢复结果统计 */
    data class Result(
        val meals: Int = 0,
        val meds: Int = 0,
        val symptoms: Int = 0,
        val notes: Int = 0,
        /** 食物耐受（新增或更新，按名称唯一） */
        val tags: Int = 0,
        /** 与现有记录完全相同的行（跳过，不重复插入） */
        val skipped: Int = 0,
        /** 解析失败/插入失败的行 */
        val failed: Int = 0,
        val totalRows: Int = 0
    ) {
        val restored: Int get() = meals + meds + symptoms + notes + tags
    }

    private val typeMeal = context.getString(R.string.type_meal)
    private val typeMed = context.getString(R.string.type_med)
    private val typeBowel = context.getString(R.string.type_bowel)
    private val typeNote = context.getString(R.string.type_note)
    private val typeTolerance = context.getString(R.string.type_tolerance)

    private val strCount = context.getString(R.string.export_txt_count)
    private val strNight = context.getString(R.string.export_txt_night)
    private val strBristol = context.getString(R.string.export_csv_bristol)
    private val strBlood = context.getString(R.string.export_csv_blood)
    private val strMucus = context.getString(R.string.export_csv_mucus)
    private val strPain = context.getString(R.string.export_csv_pain)
    private val strUrgency = context.getString(R.string.export_csv_urgency)
    private val strYes = context.getString(R.string.common_yes)
    private val strPoints = context.getString(R.string.common_points)
    private val strNotRecorded = context.getString(R.string.export_txt_not_recorded)

    private val mealLabels = MealType.entries.map { it to context.getString(it.labelRes) }
    private val bloodLabels = BLOOD_LABELS.map { context.getString(it) }
    private val painLocLabels = PAIN_LOCATION_LABELS.map { context.getString(it) }
    private val toleranceLabels = FoodTolerance.entries.map { it to context.getString(it.labelRes) }

    /**
     * 解析 CSV 文本并写入数据库。
     * @return 统计结果；文件无法识别（非本应用导出 CSV）时返回 null。
     */
    suspend fun restore(csv: String): Result? {
        val rows = parseCsv(csv)
        if (rows.isEmpty()) return null
        // 跳过表头/说明行：首列不是 yyyy-MM-dd 日期且类型也不是"耐受"的行（含"日期,类型,…"表头）
        val dataRows = rows.dropWhile { row ->
            !isDate(row.firstOrNull().orEmpty()) && row.getOrNull(1).orEmpty().trim() != typeTolerance
        }
        if (dataRows.isEmpty()) return null

        var meals = 0
        var meds = 0
        var symptoms = 0
        var notes = 0
        var tags = 0
        var skipped = 0
        var failed = 0

        // 食物耐受按名称唯一：已有名称 → 更新状态；新名称 → 追加到排序末尾
        var existingTagNames: Set<String>? = null
        var baseSortOrder = 0
        var nextTagOrder = 0
        val firstSeenTags = mutableSetOf<String>()

        dataRows.forEach { row ->
            if (row.all { it.isBlank() }) return@forEach // 空行跳过
            try {
                when (val parsed = parseRow(row)) {
                    is Parsed.Meal -> {
                        val r = parsed.record
                        val dup = mealDao.getRecordsByDate(r.date).any {
                            it.time == r.time && it.mealType == r.mealType &&
                                it.tags == r.tags && it.note == r.note
                        }
                        if (dup) skipped++ else { mealDao.insert(r); meals++ }
                    }
                    is Parsed.Med -> {
                        val r = parsed.record
                        val dup = medDao.getByDate(r.date).any {
                            it.time == r.time && it.name == r.name && it.dose == r.dose
                        }
                        if (dup) skipped++ else { medDao.insert(r); meds++ }
                    }
                    is Parsed.Symptom -> {
                        val r = parsed.record
                        val dup = symptomDao.getByDate(r.date).any {
                            it.time == r.time && it.bowelCount == r.bowelCount &&
                                it.nightDiarrhea == r.nightDiarrhea && it.bristolType == r.bristolType &&
                                it.blood == r.blood && it.mucus == r.mucus &&
                                it.painScore == r.painScore && it.painLocation == r.painLocation &&
                                it.urgency == r.urgency && it.note == r.note
                        }
                        if (dup) skipped++ else { symptomDao.insert(r); symptoms++ }
                    }
                    is Parsed.Note -> {
                        // 每日感受唯一：重复导入覆盖为文件内容
                        noteDao.upsert(DailyNote(date = parsed.date, text = parsed.text))
                        notes++
                    }
                    is Parsed.Tolerance -> {
                        val name = parsed.name
                        if (existingTagNames == null) {
                            existingTagNames = foodTagDao.getAll().map { it.name }.toSet()
                            baseSortOrder = foodTagDao.maxSortOrder()
                        }
                        if (firstSeenTags.add(name)) {
                            if (name in existingTagNames!!) {
                                // 已有同名食物：按文件更新耐受状态（保留原排序）
                                foodTagDao.setTolerance(name, parsed.tolerance.ordinal)
                            } else {
                                foodTagDao.insert(
                                    FoodTag(
                                        name = name,
                                        tolerance = parsed.tolerance.ordinal,
                                        sortOrder = baseSortOrder + ++nextTagOrder
                                    )
                                )
                            }
                        } else {
                            // 文件内重复的同名行：以文件为准覆盖状态
                            foodTagDao.setTolerance(name, parsed.tolerance.ordinal)
                        }
                        tags++
                    }
                }
            } catch (e: Exception) {
                failed++
            }
        }
        return Result(meals, meds, symptoms, notes, tags, skipped, failed, dataRows.size)
    }

    // region 行解析

    private sealed interface Parsed {
        data class Meal(val record: MealRecord) : Parsed
        data class Med(val record: MedRecord) : Parsed
        data class Symptom(val record: DailySymptom) : Parsed
        data class Note(val date: String, val text: String) : Parsed
        data class Tolerance(val name: String, val tolerance: FoodTolerance) : Parsed
    }

    private fun parseRow(row: List<String>): Parsed {
        if (row.size < 4) throw IllegalArgumentException("column count < 4")
        val date = row[0].trim()
        val type = row[1].trim()
        val time = row[2].trim()
        val content = row.getOrElse(3) { "" }.trim()
        val note = row.getOrElse(4) { "" }.trim()
        // 食物耐受行：日期列留空，时间列 = 耐受状态，内容列 = 食物名
        if (type == typeTolerance) {
            val name = content
            if (name.isBlank()) throw IllegalArgumentException("tolerance name empty")
            val tolerance = toleranceLabels.firstOrNull { it.second == time }?.first
                ?: throw IllegalArgumentException("unknown tolerance label: $time")
            return Parsed.Tolerance(name, tolerance)
        }
        validateDate(date)
        return when (type) {
            typeMeal -> {
                val (mealType, tags) = parseMealContent(content)
                Parsed.Meal(
                    MealRecord(
                        date = date,
                        time = time,
                        mealType = mealType,
                        tagsJson = MealRecord.tagsEncode(tags),
                        note = note
                    )
                )
            }
            typeMed -> {
                val (name, dose) = parseMedContent(content)
                if (name.isBlank()) throw IllegalArgumentException("med name empty")
                Parsed.Med(MedRecord(date = date, time = time, name = name, dose = dose))
            }
            typeBowel -> Parsed.Symptom(parseSymptomContent(date, time, content, note))
            typeNote -> Parsed.Note(date, content)
            else -> throw IllegalArgumentException("unknown type: $type")
        }
    }

    /** 饮食内容："早餐：牛奶、鸡蛋" → (餐次, 标签列表) */
    private fun parseMealContent(content: String): Pair<MealType, List<String>> {
        val sep = "："
        val idx = content.indexOf(sep)
        if (idx <= 0) throw IllegalArgumentException("meal type missing")
        val label = content.substring(0, idx)
        val mealType = mealLabels.firstOrNull { it.second == label }?.first
            ?: throw IllegalArgumentException("unknown meal label: $label")
        val tags = content.substring(idx + sep.length)
            .split("、")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return mealType to tags
    }

    /** 服药内容："药名 剂量" → (药名, 剂量)；无空格时整段为药名 */
    private fun parseMedContent(content: String): Pair<String, String> {
        val idx = content.indexOf(' ')
        return if (idx < 0) content.trim() to "" else
            content.substring(0, idx).trim() to content.substring(idx + 1).trim()
    }

    /** 便便内容："次数2（含夜间）；布里斯托4 光滑香肠；便血无；黏液有；腹痛3分 左下腹；急迫感无" */
    private fun parseSymptomContent(date: String, time: String, content: String, note: String): DailySymptom {
        var bowelCount = 0
        var nightDiarrhea = false
        var bristolType = 0
        var blood = 0
        var mucus = false
        var painScore = 0
        var painLocation = 0
        var urgency = false

        content.split("；").forEach { seg ->
            val s = seg.trim()
            when {
                s.startsWith(strCount) -> {
                    bowelCount = leadingDigits(s.removePrefix(strCount))
                    nightDiarrhea = s.contains(strNight)
                }
                s.startsWith(strBristol) -> {
                    val rest = s.removePrefix(strBristol).trim()
                    bristolType = if (rest == strNotRecorded) 0 else leadingDigits(rest)
                }
                s.startsWith(strBlood) -> {
                    val label = s.removePrefix(strBlood).trim()
                    blood = bloodLabels.indexOf(label).takeIf { it >= 0 } ?: 0
                }
                s.startsWith(strMucus) -> mucus = s.removePrefix(strMucus).trim() == strYes
                s.startsWith(strPain) -> {
                    val rest = s.removePrefix(strPain).trim()
                    painScore = leadingDigits(rest.substringBefore(strPoints))
                    val loc = rest.substringAfter(strPoints, "").trim()
                    if (loc.isNotEmpty()) {
                        painLocation = painLocLabels.indexOf(loc).takeIf { it >= 0 } ?: 0
                    }
                }
                s.startsWith(strUrgency) -> urgency = s.removePrefix(strUrgency).trim() == strYes
            }
        }
        return DailySymptom(
            date = date,
            time = time,
            bowelCount = bowelCount,
            nightDiarrhea = nightDiarrhea,
            bristolType = bristolType,
            blood = blood,
            mucus = mucus,
            painScore = painScore,
            painLocation = painLocation,
            urgency = urgency,
            note = note
        )
    }

    // endregion

    // region 工具

    /** 解析整个 CSV 文本为行（支持引号字段：内部 "" 转义、字段内逗号/换行） */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStarted = false
        val s = text.removePrefix("\uFEFF")
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' && !fieldStarted -> { inQuotes = true; fieldStarted = true }
                c == ',' -> { row.add(field.toString()); field.setLength(0); fieldStarted = false }
                c == '\r' -> if (i + 1 < s.length && s[i + 1] == '\n') i++
                c == '\n' -> {
                    row.add(field.toString()); field.setLength(0); fieldStarted = false
                    rows.add(row.toList()); row.clear()
                }
                else -> { field.append(c); fieldStarted = true }
            }
            i++
        }
        if (fieldStarted || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row.toList())
        }
        return rows
    }

    private fun isDate(s: String): Boolean = try {
        LocalDate.parse(s)
        true
    } catch (e: DateTimeParseException) {
        false
    }

    private fun validateDate(s: String) {
        if (!isDate(s)) throw IllegalArgumentException("invalid date: $s")
    }

    /** 取字符串开头的连续数字（无数字返回 0） */
    private fun leadingDigits(s: String): Int {
        var v = 0
        for (c in s) {
            if (!c.isDigit()) break
            v = v * 10 + (c - '0')
        }
        return v
    }

    // endregion
}
