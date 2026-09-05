package com.ucdaily.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ucdaily.R
import com.ucdaily.data.FoodTag
import com.ucdaily.data.FoodTolerance
import kotlin.math.abs
import kotlin.math.roundToInt

/** 耐受状态对应颜色（绿=可耐受 琥珀=尝试 红=不耐受，随主题深浅令牌）；首页饮食标签也复用 */
@Composable
internal fun toleranceColor(t: FoodTolerance): Color = when (t) {
    FoodTolerance.OK -> ucPalette().green
    FoodTolerance.CAUTION -> ucPalette().amber
    FoodTolerance.BAD -> ucPalette().red
}

/** 耐受状态浅色底（分区卡背景 / 落点预览槽，设计稿 .tsec） */
@Composable
internal fun toleranceSoft(t: FoodTolerance): Color = when (t) {
    FoodTolerance.OK -> ucPalette().greenSoft
    FoodTolerance.CAUTION -> ucPalette().amberSoft
    FoodTolerance.BAD -> ucPalette().redSoft
}

/** 耐受说明（页面顶部提示卡，多语言文案资源） */
private val TOLERANCE_NOTE_RES: Int = R.string.tolerance_note

/** 分组展示顺序：可耐受 → 不耐受 → 尝试（统计页耐受情况分布复用） */
internal val TOLERANCE_ORDER = listOf(FoodTolerance.OK, FoodTolerance.BAD, FoodTolerance.CAUTION)

/** 长按进入拖动的等待时长（ms） */
private const val DRAG_LONG_PRESS_MS = 400L

/**
 * 拖动中的食物标签信息（坐标均为页面根坐标系，px）：
 * natTopLeft 为标签自然位置左上角，grabOffset 为长按时手指在标签内的位置，
 * translation 为标签相对自然位置的位移（让标签跟随手指）。
 */
private data class DragInfo(
    val name: String,
    val tolerance: FoodTolerance,
    val count: Int,
    val natTopLeft: Offset,
    val grabOffset: Offset,
    val translation: Offset,
    val pointerId: PointerId
) {
    /** 当前手指位置（根坐标系，px） */
    val fingerRoot: Offset get() = natTopLeft + grabOffset + translation
}

/**
 * 拖动落点预览：标签将落入的分区 + 在该分区内的下标。
 * 下标相对于"该分区其它标签"（不含被拖标签）按阅读顺序。
 * 拖动中随手指实时刷新，让用户在松手前就能看到落点位置。
 */
private data class DragPreview(val section: FoodTolerance, val index: Int)

/**
 * 耐受页：食物以 tag 样式展示（框色即状态：绿=可耐受 红=不耐受 黄=尝试），
 * 点 tag 右上角出现 X 角标可删除。
 * 长按 tag（按住 400ms）进入拖动：
 *  - 标签以"落点预览槽"（虚线框）形态实时插入手指所在分区，
 *    右侧标签被挤开滑动（重排动画），所见即所落；
 *  - 同分区内松开 = 调整前后顺序，拖到另一分区松开 = 改变耐受状态。
 * 拖动中的手指由根级 pointerInput 统一跟踪（标签节点可在分区间自由重排）。
 */
@Composable
fun ToleranceScreen(
    state: MealUiState,
    onCycleTolerance: (String) -> Unit,
    onDeleteFood: (String) -> Unit,
    onMoveFood: (String, FoodTolerance, String?) -> Unit
) {
    /** 当前"待删除"tag（点名称后右上角出现 X） */
    var armedName by remember { mutableStateOf<String?>(null) }
    /** 正在拖动的标签（null = 未拖动） */
    var dragInfo by remember { mutableStateOf<DragInfo?>(null) }
    /** 拖动落点预览（将落入哪个分区 + 哪个位置），拖动中实时刷新 */
    var dragPreview by remember { mutableStateOf<DragPreview?>(null) }
    /** 各标签的坐标（页面根坐标系）——落点计算用 */
    val chipCoords = remember { mutableMapOf<String, LayoutCoordinates>() }
    /** 各分区的位置（页面根坐标系）——判断手指悬停在哪一个框 */
    val sectionRects = remember { mutableMapOf<FoodTolerance, Rect>() }
    /** 根容器坐标——把拖动跟踪事件的位置换算到根坐标系 */
    var rootBoxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    /** 根 View——落点槽切换时用平台分段轻震（Compose 1.7 的 HapticFeedbackType 仅暴露 LongPress/TextHandleMove） */
    val root = LocalView.current
    /** 分段控件式轻震：API 34+ 用 SEGMENT_TICK，更早用 CLOCK_TICK */
    val tickHaptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        HapticFeedbackConstants.SEGMENT_TICK
    else
        HapticFeedbackConstants.CLOCK_TICK

    // 手势协程持有稳定的 lambda 引用，始终读取最新的状态与回调
    val curState = rememberUpdatedState(state)
    val curDragInfo = rememberUpdatedState(dragInfo)
    val curRootCoords = rememberUpdatedState(rootBoxCoords)
    val curOnDelete = rememberUpdatedState(onDeleteFood)
    val curOnMove = rememberUpdatedState(onMoveFood)

    fun rectOf(coords: LayoutCoordinates): Rect {
        val p = coords.localToRoot(Offset.Zero)
        return Rect(p.x, p.y, p.x + coords.size.width, p.y + coords.size.height)
    }

    // ---- 拖动开始 / 移动 / 结束 ----

    /** 按手指根坐标计算落点预览：手指所在分区 + 该分区内"手指之后第一个标签"的下标（阅读顺序） */
    fun previewAt(finger: Offset): DragPreview? {
        val dragName = dragInfo?.name ?: return null
        val target = sectionRects.entries.firstOrNull { it.value.contains(finger) }?.key ?: return null
        val others = curState.value.foodTags.filter {
            it.name != dragName && FoodTolerance.fromValue(it.tolerance) == target
        }
        val beforeIdx = others.indexOfFirst { t ->
            val r = chipCoords[t.name]?.let { rectOf(it) } ?: return@indexOfFirst false
            r.top > finger.y + r.height * 0.5f ||
                (abs(r.center.y - finger.y) < r.height * 0.5f && r.left > finger.x)
        }
        return DragPreview(target, if (beforeIdx == -1) others.size else beforeIdx)
    }

    fun startDrag(name: String, localPos: Offset, pointerId: PointerId) {
        if (dragInfo != null) {
            return
        }
        val coords = chipCoords[name] ?: return
        val rect = rectOf(coords)
        val tag = curState.value.foodTags.firstOrNull { it.name == name } ?: return
        dragInfo = DragInfo(
            name = name,
            tolerance = FoodTolerance.fromValue(tag.tolerance),
            count = curState.value.foodTagCounts[name] ?: 0,
            natTopLeft = rect.topLeft,
            grabOffset = localPos,
            translation = Offset.Zero,
            pointerId = pointerId
        )
        armedName = null
        dragPreview = previewAt(rect.topLeft + localPos)
    }

    fun updateDrag(finger: Offset) {
        val d = dragInfo ?: return
        dragInfo = d.copy(translation = finger - d.natTopLeft - d.grabOffset)
        val preview = previewAt(finger)
        if (preview != dragPreview) {
            dragPreview = preview
            // 落点槽每换一个位置轻震一下
            if (preview != null) root.performHapticFeedback(tickHaptic)
        }
    }

    fun endDrag(finger: Offset) {
        val d = dragInfo ?: return
        // 以实时预览为落点（所见即所落）——必须在清空 dragInfo 之前读取
        val preview = previewAt(finger)
        if (preview == null) return
        dragInfo = null
        dragPreview = null
        val others = curState.value.foodTags.filter {
            it.name != d.name && FoodTolerance.fromValue(it.tolerance) == preview.section
        }
        val before = if (preview.index in 0 until others.size) others[preview.index].name else null
        // 落点与当前位置一致则不写库
        val all = curState.value.foodTags
        val current = all.firstOrNull { it.name == d.name } ?: return
        val curIdx = all.indexOfFirst { it.name == d.name }
        val rest = all.filter { it.name != d.name }
        val insertIdx = if (before == null) rest.size
        else rest.indexOfFirst { it.name == before }.let { if (it == -1) rest.size else it }
        if (FoodTolerance.fromValue(current.tolerance) == preview.section && insertIdx == curIdx) return
        curOnMove.value(d.name, preview.section, before)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .onGloballyPositioned { rootBoxCoords = it }
            // 拖动跟踪：长按进入拖动后，该指针的后续事件始终沿"按下时的命中路径"派发
            // （Compose 只在按下/悬停时做命中测试，移动事件不重新命中），
            // 根容器在所有 tag 的命中路径上，因此在这里统一跟踪手指。
            // 这样被拖 tag 的节点可以自由在分区间重排/重建，不会丢失手势。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val d = curDragInfo.value ?: continue
                        val c = ev.changes.firstOrNull { it.id == d.pointerId } ?: continue
                        c.consume()
                        val finger = curRootCoords.value?.localToRoot(c.position)
                        if (finger != null) {
                            if (!c.pressed) endDrag(finger) else updateDrag(finger)
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
        ) {
            // 标题（风格与日常管理手册页一致：图标 + 标题、副标题、分隔线）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🥗", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.tolerance_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.tolerance_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Spacer(modifier = Modifier.height(14.dp))

            // 说明 + 三个分区（滚动区）
            // 注意：这里用普通 Column + verticalScroll 而不是 LazyColumn——
            // LazyColumn 的 item 内容在子组合（subcomposition）中，拖动重排时
            // FlowRow 内 key 节点的 move 无法触发布局重排（实测布局停留在旧顺序），
            // 普通组合树里重排可以正常生效。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 耐受说明卡（琥珀色提示）：默认折叠，点击展开/收起
                var expanded by remember { mutableStateOf(false) }
                // 耐受说明卡（设计稿 .note-card）：琥珀色描边 + 浅琥珀底，默认折叠
                val p = ucPalette()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .softShadow(elevation = 2.dp, shape = RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, p.amber, RoundedCornerShape(14.dp))
                        .background(p.amberSoft)
                        .clickable { expanded = !expanded }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row {
                            Text(
                                text = stringResource(R.string.tolerance_note_title),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = p.amberText,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(
                                    if (expanded) R.string.tolerance_note_collapse
                                    else R.string.tolerance_note_expand
                                ),
                                modifier = Modifier.size(16.dp),
                                tint = p.amberText
                            )
                        }
                        if (expanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(TOLERANCE_NOTE_RES),
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 24.sp,
                                color = p.text
                            )
                        }
                    }
                }

            if (state.foodTags.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🥗", style = MaterialTheme.typography.headlineMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.tolerance_empty_title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.tolerance_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
            } else {
                // 三个分区：可耐受 / 不耐受 / 尝试（状态色分区卡片，区内为对应 tag）
                val drag = dragInfo
                val preview = dragPreview
                TOLERANCE_ORDER.forEach { tol ->
                        val baseTags = state.foodTags.filter {
                            FoodTolerance.fromValue(it.tolerance) == tol
                        }
                        // 拖动中的展示顺序：被拖 tag 插入落点预览位置（虚线预览槽），
                        // 其它分区暂时不显示被拖 tag
                        val displayTags: List<FoodTag> = when {
                            drag == null -> baseTags
                            preview != null && preview.section == tol -> {
                                val others = baseTags.filter { it.name != drag.name }
                                val dragTag = curState.value.foodTags.firstOrNull { it.name == drag.name }
                                if (dragTag != null) {
                                    val list = others.toMutableList()
                                    list.add(preview.index.coerceIn(0, list.size), dragTag)
                                    list
                                } else {
                                    others
                                }
                            }
                            else -> baseTags.filter { it.name != drag.name }
                        }
                        ToleranceSection(
                            tolerance = tol,
                            tags = displayTags,
                            counts = state.foodTagCounts,
                            armedName = armedName,
                            dragName = drag?.name,
                            isDropTarget = preview != null && preview.section == tol,
                            onTagTap = { name ->
                                // 拖动中不响应轻点（避免点到预览槽标记删除）
                                if (dragInfo == null) {
                                    armedName = if (armedName == name) null else name
                                }
                            },
                            onTagDelete = { name ->
                                armedName = null
                                curOnDelete.value(name)
                            },
                            onChipPositioned = { name, coords -> chipCoords[name] = coords },
                            onSectionPositioned = { coords -> sectionRects[tol] = rectOf(coords) },
                            onDragStart = { name, pos, id -> startDrag(name, pos, id) }
                        )
                }
                // 底部提示（左对齐）
                    Text(
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                        text = stringResource(R.string.tolerance_drag_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
            }
            }
        }
    }

    // 拖动悬浮层：把正在拖动的 tag 画在所有分区之上（根坐标定位，不参与布局）
    DragOverlay(dragInfo = dragInfo)
}

/**
 * 流式排列（FlowRow 等价）：子项按行从左到右排列，超宽换行。
 *
 * 为什么不用 androidx 的 [androidx.compose.foundation.layout.FlowRow]：
 * 在 Compose 1.7.0 中，FlowRow 是"多内容"布局（multi-content / virtual layouts），
 * 区内 key 子项重排（拖动换序）时不会触发重新测量/布局——子项停留在旧位置
 * （同场景下普通 Row 可正常重排，已实测确认）。这里用单内容 [Layout] 自行实现
 * 相同的换行布局，子项重排能正确传播为重新布局。
 *
 * 换行规则：子项自然宽度（受容器宽度约束）从左到右累加，放不下则换行；
 * 行首/行内水平间距 [hSpacing]，行间垂直间距 [vSpacing]；行左对齐、行内顶对齐。
 */
@Composable
private fun TagFlowRow(
    modifier: Modifier = Modifier,
    hSpacing: Dp = 8.dp,
    vSpacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    val hPx = LocalDensity.current.run { hSpacing.toPx().roundToInt() }
    val vPx = LocalDensity.current.run { vSpacing.toPx().roundToInt() }
    val policy = MeasurePolicy { measurables, constraints ->
        val bounded = constraints.hasBoundedWidth
        val maxWidth = constraints.maxWidth
        // 每个子项按容器宽度约束测量（自然宽度，不拉伸）
        val measured = measurables.map { it.measure(constraints) }
        // 贪心换行
        val lines = mutableListOf<MutableList<Placeable>>()
        var line = mutableListOf<Placeable>()
        var lineWidth = 0
        for (p in measured) {
            val needed = if (line.isEmpty()) p.width else lineWidth + hPx + p.width
            if (line.isNotEmpty() && bounded && needed > maxWidth) {
                // 当前行放不下 → 换行（p 成为新行首项，不能跳过）
                lines.add(line)
                line = mutableListOf()
                lineWidth = 0
            }
            line.add(p)
            lineWidth = if (line.size == 1) p.width else lineWidth + hPx + p.width
        }
        if (line.isNotEmpty()) lines.add(line)
        // 整体尺寸 = 最宽行 x 总高
        val naturalWidth = lines.maxOfOrNull { it.sumOf { pl -> pl.width } + (it.size - 1) * hPx } ?: 0
        val width = if (bounded) naturalWidth.coerceAtMost(maxWidth) else naturalWidth
        val height = lines.sumOf { it.maxOf { pl -> pl.height } } + (lines.size - 1) * vPx
        layout(width.coerceAtLeast(constraints.minWidth), height.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            for (l in lines) {
                var x = 0
                val lineHeight = l.maxOf { pl -> pl.height }
                for (p in l) {
                    p.placeRelative(x, y)
                    x += p.width + hPx
                }
                y += lineHeight + vPx
            }
        }
    }
    Layout(
        content = { content() },
        measurePolicy = policy,
        modifier = modifier
    )
}

/**
 * 耐受分区：状态色边框 + 淡色底的卡片，区内为对应状态的食物 tag；
 * 拖动中手指悬停的分区高亮，区内 tag 实时重排，为被拖 tag 让出落点位置。
 */
@Composable
private fun ToleranceSection(
    tolerance: FoodTolerance,
    tags: List<FoodTag>,
    counts: Map<String, Int>,
    armedName: String?,
    dragName: String?,
    isDropTarget: Boolean,
    onTagTap: (String) -> Unit,
    onTagDelete: (String) -> Unit,
    onChipPositioned: (String, LayoutCoordinates) -> Unit,
    onSectionPositioned: (LayoutCoordinates) -> Unit,
    onDragStart: (String, Offset, PointerId) -> Unit
) {
    val color = toleranceColor(tolerance)
    val p = ucPalette()
    val shape = RoundedCornerShape(16.dp)
    /**
     * 本分区卡片的 LayoutCoordinates（实例稳定、位置就地更新）——
     * tag 重排动画以它为参照系（分区整体移动时 chip 相对位置不变，不误触发动画）。
     * 存实例而非坐标值：chip 在布局回调里直接读它的最新位置，不经过组合期状态。
     */
    val sectionCard = remember { mutableStateOf<LayoutCoordinates?>(null) }
    // 分区卡（设计稿 .tsec）：状态色浅色底 + 同色 1px 描边；拖动悬停时描边加粗
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .softShadow(elevation = 2.dp, shape = shape)
            .clip(shape)
            .background(toleranceSoft(tolerance))
            .border(if (isDropTarget) 1.5.dp else 1.dp, color, shape)
            .onGloballyPositioned { coords ->
                sectionCard.value = coords
                onSectionPositioned(coords)
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 分区标题：色点 + 名称 + 数量
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(tolerance.labelRes),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.tolerance_types_count, tags.size),
                    fontSize = 10.5.sp,
                    color = p.text2
                )
            }
            if (tags.isEmpty()) {
                Text(
                    text = stringResource(R.string.tolerance_empty_section),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TagFlowRow(
                    hSpacing = 8.dp,
                    vSpacing = 8.dp
                ) {
                    tags.forEach { tag ->
                        key(tag.name) {
                            FoodTagChip(
                                tag = tag,
                                count = counts[tag.name] ?: 0,
                                armed = armedName == tag.name,
                                dragging = dragName == tag.name,
                                sectionCard = sectionCard,
                                onPositioned = { onChipPositioned(tag.name, it) },
                                onTap = { onTagTap(tag.name) },
                                onDelete = { onTagDelete(tag.name) },
                                onDragStart = { pos, id -> onDragStart(tag.name, pos, id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 食物 tag：框色即状态；点名称右上角出现 X 角标（点 X 删除）；长按拖动换序/跨分区移动。
 * 拖动中：本节点变为"落点预览槽"（虚线框）出现在预览位置，随其它 tag 一起重排，
 * 真正的 tag 由根级 [DragOverlay] 悬浮绘制（避免被其他分区卡片遮挡）。
 */
@Composable
private fun FoodTagChip(
    tag: FoodTag,
    count: Int,
    armed: Boolean,
    dragging: Boolean,
    /** 本分区卡片的 LayoutCoordinates 持有者（实例稳定、位置就地更新）——重排动画的参照系 */
    sectionCard: MutableState<LayoutCoordinates?>,
    onPositioned: (LayoutCoordinates) -> Unit,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: (Offset, PointerId) -> Unit
) {
    val color = toleranceColor(FoodTolerance.fromValue(tag.tolerance))
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val haptics = LocalHapticFeedback.current

    // 重排动画：chip 在分区内的位置变化时（拖动让位/挤位），
    // 从当前视觉位置平滑滑到新位置（graphicsLayer 偏移，不触发重新布局）。
    //
    // 两个关键设计（均对照 Compose 1.7 源码验证）：
    // 1) 测量节点与动画节点分离：graphicsLayer 变化时框架会对该节点及其【后代】
    //    重新派发 onGloballyPositioned（且 localToRoot 含祖先变换）——若在同一节点上
    //    既动画又测位置，坐标会含动画偏移写回状态，每帧重启动画，形成自反馈震荡。
    //    因此：本节点只测位置（onGloballyPositioned，无 graphicsLayer），
    //    动画偏移放在【子】Box 的 graphicsLayer 上（该子节点无 positioned 回调）。
    // 2) 动画在布局回调内【同步】启动（绘制前就位）：不会出现"先到新位置闪一帧
    //    再滑回"的跳变；分区整体移动时相对位置不变，不误触发动画。
    val positionAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val animScope = rememberCoroutineScope()
    val lastRel = remember { mutableStateOf<Offset?>(null) }
    var animJob: Job? by remember { mutableStateOf(null) }

    Box(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                val root = coords.localToRoot(Offset.Zero)
                onPositioned(coords)
                sectionCard.value?.let { card ->
                    val rel = root - card.localToRoot(Offset.Zero)
                    lastRel.value?.let { prev ->
                        if (prev != rel) {
                            // 新动画起点 = 当前视觉位置（上次布局位置 + 进行中的偏移）；
                            // 同步写入（绘制前就位，无跳帧），再启动滑向新位置的弹簧动画
                            animJob?.cancel()
                            animJob = animScope.launch {
                                positionAnim.snapTo(prev + positionAnim.value - rel)
                                val r = positionAnim.animateTo(
                                    Offset.Zero,
                                    spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
                                )
                            }
                        }
                    }
                    lastRel.value = rel
                }
            }
            .foodTagGesture(
                key = tag.name,
                touchSlop = touchSlop,
                onTap = onTap,
                onDragStart = { downPos, id ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDragStart(downPos, id)
                }
            )
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = positionAnim.value.x
                    translationY = positionAnim.value.y
                }
        ) {
            FoodTagChipVisual(
                name = tag.name,
                count = count,
                color = color,
                placeholder = dragging,
                modifier = Modifier
            )
            // 右上角 X 角标（待删除状态）
            if (armed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-4).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .border(1.dp, MaterialTheme.colorScheme.surface)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_delete),
                        modifier = Modifier.size(10.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 食物 tag 视觉（药丸 + 名称 + 计数）；列表与拖动悬浮层共用。
 * placeholder=true 时渲染为"落点预览槽"（淡底 + 虚线框），标示拖动将落入的位置。
 */
@Composable
private fun FoodTagChipVisual(
    name: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false
) {
    // 设计稿 .tchip：12px 圆角 + 1.5px 状态色描边 + 白底 + 同色文字 + ×N 计数
    val p = ucPalette()
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .then(
                if (placeholder) {
                    // 落点预览槽：淡底色 + 虚线边框
                    Modifier.drawWithContent {
                        val r = 12.dp.toPx()
                        val pill = Path().apply {
                            addRoundRect(RoundRect(0f, 0f, size.width, size.height, r, r))
                        }
                        drawPath(pill, color = color.copy(alpha = 0.10f))
                        drawContent()
                        drawPath(
                            path = pill,
                            color = color.copy(alpha = 0.8f),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
                            )
                        )
                    }
                } else {
                    Modifier
                        .border(1.5.dp, color, shape)
                        .background(p.surface)
                }
            )
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
        if (count > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "×$count",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = color.copy(alpha = 0.65f)
            )
        }
    }
}

/**
 * 食物 tag 手势：
 *  - 轻点 = onTap（标记删除）；
 *  - 按住 400ms 且未移动超过触摸阈值 = 长按，进入拖动
 *    （回调 tag 本地坐标下的手指位置 + 该指针的 PointerId）；
 *  - 长按前若手指移动超过阈值则不拦截（让页面垂直滚动）。
 * 拖动开始后的手指跟踪由根级 pointerInput 接管（见 [ToleranceScreen]），
 * 因此本手势在长按确认后即退出，被拖 tag 节点可自由重排。
 */
private fun Modifier.foodTagGesture(
    key: Any,
    touchSlop: Float,
    onTap: () -> Unit,
    onDragStart: (Offset, PointerId) -> Unit
) = pointerInput(key) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            val id = down.id
            val downPos = down.position
            val downTime = System.currentTimeMillis()

            // 长按判定：400ms 内未松手且移动未超阈值；先松手 = 轻点；先超阈值 = 交给列表滚动
            var outcome: String? = null
            var lastPos = downPos
            while (outcome == null) {
                val elapsed = System.currentTimeMillis() - downTime
                if (elapsed >= DRAG_LONG_PRESS_MS) {
                    outcome = "longpress"
                    break
                }
                // 等待下一个事件或剩余长按时间耗尽（超时返回 null → 重新检查 elapsed）
                val ev = withTimeoutOrNull(DRAG_LONG_PRESS_MS - elapsed) { awaitPointerEvent() }
                    ?: continue
                val c = ev.changes.firstOrNull { it.id == id } ?: continue
                lastPos = c.position
                if (!c.pressed) {
                    outcome = "tap"
                    break
                }
                if ((c.position - downPos).getDistance() > touchSlop) {
                    outcome = "scroll"
                    break
                }
            }

            when (outcome) {
                "tap" -> {
                    onTap()
                    continue
                }
                "scroll" -> {
                    // 不消费事件：滚动由 verticalScroll 处理，只等待手指抬起
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == id } ?: break
                        if (!c.pressed) break
                    }
                    continue
                }
                else -> {
                    onDragStart(lastPos, id)
                }
            }
        }
    }
}

/**
 * 拖动悬浮层：拖动 tag 时，把它绘制在所有分区之上（跟随手指）。
 * 整层 fillMaxSize 但不带任何指针处理器，不会拦截触摸（手指跟踪由根级 pointerInput 负责）。
 */
@Composable
private fun DragOverlay(dragInfo: DragInfo?) {
    var overlayCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayCoords = it }
    ) {
        val d = dragInfo
        val oc = overlayCoords
        if (d != null && oc != null) {
            // 把手指的根坐标换算到本悬浮层局部坐标，使抓取点恰好落在手指下
            val overlayTopLeft = oc.localToRoot(Offset.Zero)
            val topLeft = d.fingerRoot - overlayTopLeft - d.grabOffset
            val shadowPx = with(LocalDensity.current) { 10.dp.toPx() }
            val floatShape = RoundedCornerShape(12.dp)
            FoodTagChipVisual(
                name = d.name,
                count = d.count,
                color = toleranceColor(d.tolerance),
                modifier = Modifier
                    .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                    .graphicsLayer {
                        scaleX = 1.08f
                        scaleY = 1.08f
                        shadowElevation = shadowPx
                        spotShadowColor = Color.Black
                        ambientShadowColor = Color.Black
                        shape = floatShape
                    }
            )
        }
    }
}

/**
 * 添加食物标签区块（「添加饮食」页面，照片区下方）：
 * 输入框 + 添加按钮（小圆角）。点"添加"：
 * - 输入的食物标签已存在 → 直接选中该标签（不弹菜单，耐受状态沿用标签自身）；
 * - 不存在 → 弹出下拉菜单选耐受状态，选中后添加新标签。
 */
@Composable
internal fun AddFoodSection(
    existingNames: Set<String>,
    onSelectFood: (String) -> Unit,
    onAddFood: (String, FoodTolerance) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            placeholder = { Text(stringResource(R.string.tolerance_add_placeholder)) },
            singleLine = true
        )
        // 添加按钮：标签已存在 → 直接选中；新标签 → 弹出下拉菜单选耐受状态后添加
        Box {
            Button(
                onClick = {
                    val name = input.trim()
                    if (name in existingNames) {
                        onSelectFood(name)
                        input = ""
                    } else {
                        showMenu = true
                    }
                },
                enabled = input.isNotBlank(),
                modifier = Modifier
                    .width(100.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.common_add), style = MaterialTheme.typography.labelMedium)
            }
            // 简化菜单：仅三个状态选项。
            // 菜单宽 112dp（最小宽度）> 按钮 100dp：x 偏移 -12dp 使菜单右缘与按钮右缘对齐；y 留 8dp 空隙
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = DpOffset((-12).dp, 8.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.background,
                shadowElevation = 8.dp
            ) {
                TOLERANCE_ORDER.forEach { tol ->
                    DropdownMenuItem(
                        text = {
                            // 文字横向居中，颜色即对应耐受状态色
                            Text(
                                stringResource(tol.labelRes),
                                color = toleranceColor(tol),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        onClick = {
                            showMenu = false
                            onAddFood(input, tol)
                            input = ""
                        }
                    )
                }
            }
        }
    }
}
