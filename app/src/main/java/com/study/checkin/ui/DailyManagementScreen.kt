package com.study.checkin.ui

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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 日常管理 Tab：日常管理手册（手风琴卡片）+ 病情自评工具。
 *
 * 卡片互斥展开（点开一个会收起其他，再点当前卡片可全部收起，
 * 默认展开第一张"饮食管理"）。内容参考循证建议，仅供自我参考，不替代医生诊断。
 */
@Composable
fun DailyManagementScreen() {
    /** 当前展开的卡片下标；-1 = 全部收起（默认展开第一张） */
    var expandedIndex by remember { mutableIntStateOf(0) }

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
    val title: String
)

private val HANDBOOK_CARDS = listOf(
    HandbookCard("diet", "🍽️", "饮食管理"),
    HandbookCard("lifestyle", "💪", "生活方式管理"),
    HandbookCard("medical", "💊", "药物与医疗管理"),
    HandbookCard("psych", "❤️", "心理调适"),
    HandbookCard("assessment", "📊", "病情自评 · 症状对照")
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
            Text(text = "📘", fontSize = 22.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "日常管理手册",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(40.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "UC",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "基于循证建议 · 根据病情阶段灵活调整\n与你的医生共同制定个体化方案",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 21.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "📌 重要提示\n以上信息仅供参考，不能替代专业医疗建议。\n具体方案请务必与你的主治医生共同制定。",
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
    val scheme = MaterialTheme.colorScheme
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "arrow"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (expanded) scheme.surfaceContainerLowest else scheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (expanded) scheme.primary else scheme.outlineVariant
        ),
        shadowElevation = if (expanded) 4.dp else 0.dp
    ) {
        Column {
            // 卡片头：emoji + 标题 … 箭头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHeaderClick)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = card.icon, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = if (expanded) scheme.primary else scheme.onSurfaceVariant,
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
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
                ) {
                    HorizontalDivider(
                        color = scheme.outlineVariant,
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
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.primary,
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

/** 重点提示框（.highlight-box）：浅色底 + 左侧主题色竖条 */
@Composable
private fun HighlightBox(markup: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
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

/** 卡片 1：饮食管理（默认展开） */
@Composable
private fun DietContent() {
    // 核心原则（一行连续文本：期别用行内加粗 + 主题色强调，自然换行）
    Text(
        text = AnnotatedString.Builder().apply {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append("核心原则：")
            pop()
            append("根据 ")
            pushStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            append("急性期")
            pop()
            append(" 或 ")
            pushStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            append("缓解期")
            pop()
            append(" 灵活调整，给肠道“减负”或“修复”。")
        }.toAnnotatedString(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 24.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )

    SectionTitle("🔴 急性发作期 · 严格减负")
    BulletList(
        listOf(
            Bullet("无渣、软烂流质/半流质", "米汤、藕粉、蒸蛋羹、烂面条、去油清汤。"),
            Bullet("目的", "减少粪便体积和排便次数，让肠道休息。")
        )
    )

    SectionTitle("🟢 缓解期 · 逐步修复")
    BulletList(
        listOf(
            Bullet("高营养、易消化、低渣", "清蒸鱼、鸡蛋羹、嫩豆腐、瘦肉糜。"),
            Bullet("", "补充优质蛋白，修复肠黏膜。")
        )
    )

    SectionTitle("⚠️ 需要留意的“问题食物”")
    BulletList(
        listOf(
            Bullet("高纤维", "（发作期避免）：坚果、种子、玉米、爆米花、生蔬菜。"),
            Bullet("乳制品", "部分患者不耐受，可暂停观察。"),
            Bullet("刺激性饮品", "酒精、咖啡因、碳酸饮料。"),
            Bullet("油腻、辛辣", "油炸、烧烤、辣椒。")
        )
    )

    SectionTitle("💡 实用技巧")
    BulletList(
        listOf(
            Bullet("改变烹饪", "蔬菜水果蒸、煮、烤、搅拌，做熟做烂。"),
            Bullet("可溶性纤维", "（耐受时）：燕麦、去皮红薯、香蕉、熟胡萝卜。"),
            Bullet("少食多餐", "一日五到六顿小餐，减轻单次负担。"),
            Bullet("饮食日记", "记录食物与反应，精准避开“问题食物”。")
        )
    )
}

/** 卡片 2：生活方式管理 */
@Composable
private fun LifestyleContent() {
    SectionTitle("😴 规律作息", first = true)
    BodyText("保证 **每晚 7~8 小时** 高质量睡眠，避免过度劳累，有助于免疫系统稳定。")

    SectionTitle("🚶 适度运动")
    BulletList(
        listOf(
            Bullet("急性期", "以休息为主，避免运动。"),
            Bullet("缓解期", "温和有氧（散步、慢跑、瑜伽、太极、游泳）。频率：**每周 3~5 次**，每次 20~60 分钟，以运动后不感到过度疲劳为宜。")
        )
    )

    SectionTitle("🧘 压力管理")
    BodyText("精神压力会加重症状，可通过 **冥想、深呼吸、与亲友交流、病友互助小组** 等方式排解。")
}

/** 卡片 3：药物与医疗管理 */
@Composable
private fun MedicalContent() {
    SectionTitle("💊 规范用药", first = true)
    BodyText("**严格遵医嘱**，切勿自行停药、减量或换药。常用药物包括氨基水杨酸类、免疫调节剂、生物制剂等。")

    SectionTitle("🚫 谨慎使用止痛药")
    BodyText("避免 **非甾体类抗炎药（NSAID）**（如布洛芬、萘普生），可能诱发疾病发作。")

    SectionTitle("🧪 补充营养素")
    BodyText("长期腹泻可能导致营养流失，可在医生指导下补充 **铁、钙和维生素 D**。")

    SectionTitle("📅 定期复查")
    BodyText("患病 **8~10 年** 以上者，建议遵医嘱 **每 1~2 年** 接受结肠镜检查，监测病情及筛查癌变风险。")

    SectionTitle("💉 接种疫苗")
    BodyText("咨询医生后，按时接种 **流感疫苗** 和 **肺炎球菌疫苗**（因疾病或药物可能影响免疫系统）。")
}

/** 卡片 4：心理调适 */
@Composable
private fun PsychContent() {
    BodyText(
        "认识到溃疡性结肠炎是一种慢性病，学会与它共存非常重要。" +
            "积极的心态和科学的疾病认知，本身就是一种强大的“药物”。"
    )
    HighlightBox(
        "**🌱 建议：** 如果感到焦虑或抑郁，不要犹豫，及时寻求 **专业心理咨询师** 的帮助。"
    )
    BodyText("与家人、朋友或病友分享感受，参与支持团体，有助于减轻心理负担。")
}

// ---------------------------------------------------------------------------
// 卡片 5：病情自评（症状对照 + 简易评分工具）
// ---------------------------------------------------------------------------

/** 评分选项：label 为展示文案，score 为分值 */
private data class ScoreOption(val label: String, val score: Int)

/** 评分题的选项（与 HTML 原型一致） */
private val SCORE_QUESTIONS = listOf(
    listOf(
        ScoreOption("＜3次 (0分)", 0),
        ScoreOption("3～5次 (1分)", 1),
        ScoreOption("6～10次 (2分)", 2),
        ScoreOption("＞10次 (3分)", 3)
    ),
    listOf(
        ScoreOption("无 (0分)", 0),
        ScoreOption("少量 (1分)", 1),
        ScoreOption("中等 (2分)", 2),
        ScoreOption("大量 (3分)", 3)
    ),
    listOf(
        ScoreOption("无 (0分)", 0),
        ScoreOption("轻度 (1分)", 1),
        ScoreOption("中度 (2分)", 2),
        ScoreOption("重度 (3分)", 3)
    ),
    listOf(
        ScoreOption("＜37.5℃ (0分)", 0),
        ScoreOption("37.5～38.5℃ (1分)", 1),
        ScoreOption("＞38.5℃ (2分)", 2)
    )
)

private val SCORE_QUESTION_LABELS = listOf(
    "1. 每日排便次数",
    "2. 便血情况",
    "3. 腹痛程度",
    "4. 体温（腋下）"
)

/** 总分 → 阶段结论（与 HTML 原型一致） */
private fun scoreStatus(total: Int): String = when {
    total <= 2 -> "缓解期 (症状控制良好)"
    total <= 5 -> "轻度活动期 (建议咨询医生)"
    total <= 8 -> "中度活动期 (需及时就诊)"
    else -> "重度活动期 (请立即就医)"
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
        "通过典型症状对照和简易评分，初步判断当前所处阶段。" +
            "⚠️ 本工具不能替代专业诊断，如有疑虑请及时就医。"
    )

    SectionTitle("🔄 发作期（活动期）常见表现")
    BulletList(
        listOf(
            Bullet("腹泻", "排便次数显著增多（每日 > 3次），常为稀便或水样便。"),
            Bullet("便血", "粪便中带血或黏液，颜色鲜红或暗红。"),
            Bullet("腹痛", "下腹部绞痛或持续性钝痛，排便后可能减轻。"),
            Bullet("里急后重", "总有便意，但排便不尽感。"),
            Bullet("全身症状", "发热、乏力、体重下降、食欲不振。")
        )
    )

    SectionTitle("🟢 缓解期常见表现")
    BulletList(
        listOf(
            Bullet("排便正常", "每日 1~2 次成形便，无血便。"),
            Bullet("腹痛消失", "腹部无不适或仅有轻微隐痛。"),
            Bullet("全身状态", "精力恢复，体重稳定，无发热。"),
            Bullet("", "内镜下黏膜愈合或显著改善。")
        )
    )

    HorizontalDivider(
        color = scheme.outlineVariant,
        modifier = Modifier.padding(vertical = 14.dp)
    )

    SectionTitle("📝 简易活动性评分（自评参考）")
    BodyText("请根据近3天的平均情况，选择最符合的选项，点击下方按钮评估。")

    SCORE_QUESTIONS.forEachIndexed { index, options ->
        ScoreSelect(
            label = SCORE_QUESTION_LABELS[index],
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

    // 计算评分按钮
    Button(
        onClick = {
            resultTotal = selections.mapIndexed { index, picked -> SCORE_QUESTIONS[index][picked].score }.sum()
            showResult = true
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        shape = RoundedCornerShape(40.dp)
    ) {
        Text(
            text = "📐 计算评分",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }

    // 评分结果
    AnimatedVisibility(visible = showResult) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.secondaryContainer.copy(alpha = 0.6f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.primary)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "总分：",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "$resultTotal",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface
                    )
                    Text(
                        text = " 分",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Text(
                    text = scoreStatus(resultTotal),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary
                )
            }
        }
    }

    Text(
        text = "* 0～2分：缓解期；3～5分：轻度活动；6～8分：中度活动；≥9分：重度活动。",
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
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .border(
                        1.dp,
                        if (menuOpen) scheme.primary else scheme.outlineVariant,
                        RoundedCornerShape(30.dp)
                    )
                    .background(scheme.surfaceContainerLowest)
                    .clickable { menuOpen = true }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = options[selectedIndex].label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(text = option.label) },
                        leadingIcon = if (index == selectedIndex) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = scheme.primary
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
