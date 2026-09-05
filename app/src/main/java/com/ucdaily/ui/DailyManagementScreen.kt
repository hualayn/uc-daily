package com.ucdaily.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ucdaily.R

/**
 * 日常管理 Tab：日常管理手册（手风琴卡片）+ 病情自评工具。
 *
 * 卡片互斥展开（点开一个会收起其他，再点当前卡片可全部收起，
 * 默认全部收起）。内容参考循证建议，仅供自我参考，不替代医生诊断。
 */
@Composable
fun DailyManagementScreen() {
    /** 当前展开的卡片下标；-1 = 全部收起（默认） */
    var expandedIndex by remember { mutableIntStateOf(-1) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") {
            ManualHeader()
        }
        items(HANDBOOK_CARDS, key = { it.id }) { card ->
            val index = HANDBOOK_CARDS.indexOf(card)
            ManualCard(
                card = card,
                expanded = expandedIndex == index,
                onHeaderClick = {
                    expandedIndex = if (expandedIndex == index) -1 else index
                }
            ) {
                when (card.id) {
                    "diet" -> DietContent()
                    "lifestyle" -> LifestyleContent()
                    "medical" -> MedicalContent()
                    "psych" -> PsychContent()
                    else -> AssessmentContent()
                }
            }
        }
        item(key = "footer") {
            ManualFooter()
        }
    }
}

/** 手册卡片定义：id 与 HTML 原型 data-card 一致，便于对照维护 */
private data class HandbookCard(
    val id: String,
    val icon: String,
    @StringRes val titleRes: Int
)

private val HANDBOOK_CARDS = listOf(
    HandbookCard("diet", "🍽️", R.string.dm_card_diet),
    HandbookCard("lifestyle", "💪", R.string.dm_card_lifestyle),
    HandbookCard("medical", "💊", R.string.dm_card_medical),
    HandbookCard("psych", "❤️", R.string.dm_card_psych),
    HandbookCard("assessment", "📊", R.string.dm_card_assessment)
)

/** 头部：📘 日常管理手册 [UC] + 副标题 + 分隔线 */
@Composable
private fun ManualHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "📘",
                fontSize = 22.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.dm_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            val p = ucPalette()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(40.dp))
                    .background(p.primarySoft)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "UC",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.primaryText
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.dm_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 21.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = ucPalette().ring)
    }
}

/** 底部重要提示 */
@Composable
private fun ManualFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        HorizontalDivider(color = ucPalette().ring)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(R.string.dm_disclaimer),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * 手风琴卡片：未展开 = 淡色底 + 淡描边；展开 = 强调底 + 主题色描边 + 阴影，
 * 箭头旋转 180°，内容区纵向展开动画（对应 HTML 的 max-height 过渡）。
 */
@Composable
private fun ManualCard(
    card: HandbookCard,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val p = ucPalette()
    val shape = RoundedCornerShape(18.dp)
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "arrow"
    )
    // 设计稿 .mcard：表面底 + 描边；展开 = 主色描边 + 阴影。
    // 注意：border 必须放在 softShadow 之外——shadow 会把包裹的内容裁剪到阴影 shape，
    // 居中绘制的描边外半段会被裁掉，框线只剩一半粗细。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                if (expanded) 1.5.dp else 1.dp,
                if (expanded) p.primary else p.ring,
                shape
            )
            .softShadow(
                elevation = if (expanded) 4.dp else 2.dp,
                shape = shape
            )
            .clip(shape)
            .background(p.surface)
    ) {
        Column {
            // 卡片头：emoji 图标底 + 标题 … 箭头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHeaderClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(p.primarySoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = card.icon, fontSize = 17.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(card.titleRes),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = p.text
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = if (expanded) p.primary else p.text2,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = arrowRotation }
                )
            }
            // 内容区（展开动画）
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(
                        color = p.surface2,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    content()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 内容排版小构件（对应 HTML 的 h4 / p / ul / .tag / .highlight-box）
// ---------------------------------------------------------------------------

/** 小标题（h4）：主题色加粗 */
@Composable
private fun SectionTitle(
    text: String,
    /** 内容区第一行时减小上边距（对应 HTML :first-of-type） */
    first: Boolean = false
) {
    Text(
        text = text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        color = ucPalette().primaryText,
        modifier = Modifier.padding(top = if (first) 6.dp else 14.dp)
    )
}

/** 解析 **加粗** 标记为 AnnotatedString（奇数段加粗） */
private fun boldMarkup(markup: String): AnnotatedString =
    AnnotatedString.Builder().apply {
        markup.split("**").forEachIndexed { index, part ->
            if (part.isEmpty()) return@forEachIndexed
            if (index % 2 == 1) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(part)
            if (index % 2 == 1) pop()
        }
    }.toAnnotatedString()

/** 正文段落（支持 **加粗** 标记） */
@Composable
private fun BodyText(markup: String) {
    Text(
        text = boldMarkup(markup),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 24.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

/** 列表项：lead 为加粗关键词（可为空），rest 为正文 */
private data class Bullet(val lead: String, val rest: String)

/** 项目符号列表（圆点主题色，lead 加粗 + 冒号） */
@Composable
private fun BulletList(items: List<Bullet>) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        items.forEach { bullet ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = ucPalette().primary,
                    modifier = Modifier.width(18.dp)
                )
                Text(
                    text = AnnotatedString.Builder().apply {
                        if (bullet.lead.isNotEmpty()) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(bullet.lead)
                            pop()
                            append("：")
                        }
                        append(bullet.rest)
                    }.toAnnotatedString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                )
            }
        }
    }
}

/** 重点提示框（.highlight-box）：primary-soft 底 + 左侧主题色竖条 */
@Composable
private fun HighlightBox(markup: String) {
    val p = ucPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.primarySoft)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(p.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = boldMarkup(markup),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 24.sp
        )
    }
}

// ---------------------------------------------------------------------------
// 五张卡片的内容
// ---------------------------------------------------------------------------

/** 卡片 1：饮食管理 */
@Composable
private fun DietContent() {
    // 核心原则（一行连续文本：** 标记段加粗 + 主题色强调，自然换行）
    val core = stringResource(R.string.dm_diet_core_principle)
    Text(
        text = AnnotatedString.Builder().apply {
            core.split("**").forEachIndexed { index, part ->
                if (part.isEmpty()) return@forEachIndexed
                if (index % 2 == 1) {
                    pushStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = ucPalette().primary
                        )
                    )
                }
                append(part)
                if (index % 2 == 1) pop()
            }
        }.toAnnotatedString(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 24.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )

    SectionTitle(stringResource(R.string.dm_diet_acute_title))
    BulletList(
        listOf(
            Bullet(stringResource(R.string.dm_diet_acute_b1_lead), stringResource(R.string.dm_diet_acute_b1)),
            Bullet(stringResource(R.string.dm_diet_acute_b2_lead), stringResource(R.string.dm_diet_acute_b2))
        )
    )

    SectionTitle(stringResource(R.string.dm_diet_remission_title))
    BulletList(
        listOf(
            Bullet(stringResource(R.string.dm_diet_remission_b1_lead), stringResource(R.string.dm_diet_remission_b1)),
            Bullet("", stringResource(R.string.dm_diet_remission_b2))
        )
    )

    SectionTitle(stringResource(R.string.dm_diet_issues_title))
    BulletList(
        listOf(
            Bullet(stringResource(R.string.dm_diet_fiber_lead), stringResource(R.string.dm_diet_fiber)),
            Bullet(stringResource(R.string.dm_diet_dairy_lead), stringResource(R.string.dm_diet_dairy)),
            Bullet(stringResource(R.string.dm_diet_drinks_lead), stringResource(R.string.dm_diet_drinks)),
            Bullet(stringResource(R.string.dm_diet_greasy_lead), stringResource(R.string.dm_diet_greasy))
        )
    )

    SectionTitle(stringResource(R.string.dm_diet_tips_title))
    BulletList(
        listOf(
            Bullet(stringResource(R.string.dm_diet_cooking_lead), stringResource(R.string.dm_diet_cooking)),
            Bullet(stringResource(R.string.dm_diet_soluble_fiber_lead), stringResource(R.string.dm_diet_soluble_fiber)),
            Bullet(stringResource(R.string.dm_diet_small_meals_lead), stringResource(R.string.dm_diet_small_meals)),
            Bullet(stringResource(R.string.dm_diet_diary_lead), stringResource(R.string.dm_diet_diary))
        )
    )
}

/** 卡片 2：生活方式管理 */
@Composable
private fun LifestyleContent() {
    SectionTitle(stringResource(R.string.dm_lifestyle_sleep_title), first = true)
    BodyText(stringResource(R.string.dm_lifestyle_sleep))

    SectionTitle(stringResource(R.string.dm_lifestyle_exercise_title))
    BulletList(
        listOf(
            Bullet(
                stringResource(R.string.dm_lifestyle_exercise_acute_lead),
                stringResource(R.string.dm_lifestyle_exercise_acute)
            ),
            Bullet(
                stringResource(R.string.dm_lifestyle_exercise_remission_lead),
                stringResource(R.string.dm_lifestyle_exercise_remission)
            )
        )
    )

    SectionTitle(stringResource(R.string.dm_lifestyle_stress_title))
    BodyText(stringResource(R.string.dm_lifestyle_stress))
}

/** 卡片 3：药物与医疗管理 */
@Composable
private fun MedicalContent() {
    SectionTitle(stringResource(R.string.dm_med_meds_title), first = true)
    BodyText(stringResource(R.string.dm_med_meds))

    SectionTitle(stringResource(R.string.dm_med_pain_title))
    BodyText(stringResource(R.string.dm_med_pain))

    SectionTitle(stringResource(R.string.dm_med_nutrition_title))
    BodyText(stringResource(R.string.dm_med_nutrition))

    SectionTitle(stringResource(R.string.dm_med_checkup_title))
    BodyText(stringResource(R.string.dm_med_checkup))

    SectionTitle(stringResource(R.string.dm_med_vaccine_title))
    BodyText(stringResource(R.string.dm_med_vaccine))
}

/** 卡片 4：心理调适 */
@Composable
private fun PsychContent() {
    BodyText(stringResource(R.string.dm_psych_p1))
    HighlightBox(stringResource(R.string.dm_psych_tip))
    BodyText(stringResource(R.string.dm_psych_p2))
}

// ---------------------------------------------------------------------------
// 卡片 5：病情自评（症状对照 + 简易评分工具）
// ---------------------------------------------------------------------------

/** 评分选项：labelRes 为多语言文案资源，score 为分值 */
private data class ScoreOption(@StringRes val labelRes: Int, val score: Int)

/** 评分题的选项（与 HTML 原型一致） */
private val SCORE_QUESTIONS = listOf(
    listOf(
        ScoreOption(R.string.dm_assess_opt_freq_0, 0),
        ScoreOption(R.string.dm_assess_opt_freq_1, 1),
        ScoreOption(R.string.dm_assess_opt_freq_2, 2),
        ScoreOption(R.string.dm_assess_opt_freq_3, 3)
    ),
    listOf(
        ScoreOption(R.string.dm_assess_opt_blood_0, 0),
        ScoreOption(R.string.dm_assess_opt_blood_1, 1),
        ScoreOption(R.string.dm_assess_opt_blood_2, 2),
        ScoreOption(R.string.dm_assess_opt_blood_3, 3)
    ),
    listOf(
        ScoreOption(R.string.dm_assess_opt_pain_0, 0),
        ScoreOption(R.string.dm_assess_opt_pain_1, 1),
        ScoreOption(R.string.dm_assess_opt_pain_2, 2),
        ScoreOption(R.string.dm_assess_opt_pain_3, 3)
    ),
    listOf(
        ScoreOption(R.string.dm_assess_opt_temp_0, 0),
        ScoreOption(R.string.dm_assess_opt_temp_1, 1),
        ScoreOption(R.string.dm_assess_opt_temp_2, 2)
    )
)

private val SCORE_QUESTION_LABELS = listOf(
    R.string.dm_assess_q1,
    R.string.dm_assess_q2,
    R.string.dm_assess_q3,
    R.string.dm_assess_q4
)

/** 总分 → 阶段结论（与 HTML 原型一致） */
@Composable
private fun scoreStatus(total: Int): String = when {
    total <= 2 -> stringResource(R.string.dm_assess_result_0)
    total <= 5 -> stringResource(R.string.dm_assess_result_1)
    total <= 8 -> stringResource(R.string.dm_assess_result_2)
    else -> stringResource(R.string.dm_assess_result_3)
}

/** 病情自评 · 症状对照：发作期/缓解期表现对照 + 四题简易评分 */
@Composable
private fun AssessmentContent() {
    val scheme = MaterialTheme.colorScheme
    /** 四题选中下标（默认全 0 分） */
    var selections by remember { mutableStateOf(IntArray(SCORE_QUESTIONS.size) { 0 }) }
    /** 评分结果（选项变化后清除，与 HTML 原型一致） */
    var resultTotal by remember { mutableIntStateOf(0) }
    var showResult by remember { mutableStateOf(false) }

    BodyText(
        stringResource(R.string.dm_assess_intro)
    )

    SectionTitle(stringResource(R.string.dm_assess_active_title))
    BulletList(
        listOf(
            Bullet(stringResource(R.string.dm_assess_diarrhea_lead), stringResource(R.string.dm_assess_diarrhea)),
            Bullet(stringResource(R.string.dm_assess_blood_lead), stringResource(R.string.dm_assess_blood)),
            Bullet(stringResource(R.string.dm_assess_blood_color_lead), stringResource(R.string.dm_assess_blood_color)),
            Bullet(stringResource(R.string.dm_assess_pain_lead), stringResource(R.string.dm_assess_pain)),
            Bullet(stringResource(R.string.dm_assess_urgency_lead), stringResource(R.string.dm_assess_urgency)),
            Bullet(stringResource(R.string.dm_assess_systemic_lead), stringResource(R.string.dm_assess_systemic))
        )
    )

    SectionTitle(stringResource(R.string.dm_assess_remission_title))
    BulletList(
        listOf(
            Bullet(stringResource(R.string.dm_assess_normal_bowel_lead), stringResource(R.string.dm_assess_normal_bowel)),
            Bullet(stringResource(R.string.dm_assess_no_pain_lead), stringResource(R.string.dm_assess_no_pain)),
            Bullet(stringResource(R.string.dm_assess_condition_lead), stringResource(R.string.dm_assess_condition)),
            Bullet("", stringResource(R.string.dm_assess_mucosa))
        )
    )

    HorizontalDivider(
        color = scheme.outlineVariant,
        modifier = Modifier.padding(vertical = 14.dp)
    )

    SectionTitle(stringResource(R.string.dm_assess_score_title))
    BodyText(stringResource(R.string.dm_assess_score_intro))

    SCORE_QUESTIONS.forEachIndexed { index, options ->
        ScoreSelect(
            label = stringResource(SCORE_QUESTION_LABELS[index]),
            options = options,
            selectedIndex = selections[index],
            onSelect = { picked ->
                val updated = selections.copyOf()
                updated[index] = picked
                selections = updated
                showResult = false
            }
        )
    }

    // 计算评分按钮（设计稿 .btn：渐变）
    GradientButton(
        onClick = {
            resultTotal = selections.mapIndexed { index, picked -> SCORE_QUESTIONS[index][picked].score }.sum()
            showResult = true
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        text = stringResource(R.string.dm_assess_score_button)
    )

    // 评分结果
    val p2 = ucPalette()
    AnimatedVisibility(visible = showResult) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(p2.primarySoft),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(p2.primary)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.dm_assess_total),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "$resultTotal",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = p2.text
                    )
                    Text(
                        text = " ${stringResource(R.string.common_points)}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text(
                    text = scoreStatus(resultTotal),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = p2.primaryText
                )
            }
        }
    }

    Text(
        text = stringResource(R.string.dm_assess_legend),
        style = MaterialTheme.typography.labelMedium,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/** 评分题：题目 + 下拉选择（胶囊按钮 + DropdownMenu，对应 HTML 的 <select>） */
@Composable
private fun ScoreSelect(
    label: String,
    options: List<ScoreOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val p = ucPalette()
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.text
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        if (menuOpen) p.primary else p.ring,
                        RoundedCornerShape(14.dp)
                    )
                    .background(p.surface)
                    .clickable { menuOpen = true }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(options[selectedIndex].labelRes),
                    fontSize = 12.5.sp,
                    color = p.text
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = p.text2,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(text = stringResource(option.labelRes)) },
                        leadingIcon = if (index == selectedIndex) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = p.primary
                                )
                            }
                        } else null,
                        onClick = {
                            menuOpen = false
                            onSelect(index)
                        }
                    )
                }
            }
        }
    }
}
