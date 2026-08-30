package com.ucdaily.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ucdaily.R
import com.ucdaily.data.FoodTag
import com.ucdaily.data.FoodTolerance
import kotlin.math.abs
import kotlin.math.roundToInt

/** 耐受状态对应颜色（绿=可耐受 红=不耐受 黄=尝试）；首页饮食标签也复用 */
internal fun toleranceColor(t: FoodTolerance): Color = when (t) {
    FoodTolerance.OK -> Color(0xFF4CAF50)
    FoodTolerance.CAUTION -> Color(0xFFF9A825)
    FoodTolerance.BAD -> Color(0xFFE53935)
}

/** 耐受说明（页面顶部提示卡，多语言文案资源） */
private val TOLERANCE_NOTE_RES: Int = R.string.tolerance_note

/** 分组展示顺序：可耐受 → 不耐受 → 尝试 */
private val TOLERANCE_ORDER = listOf(FoodTolerance.OK, FoodTolerance.BAD, FoodTolerance.CAUTION)

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
    val translation: Offset
) {
    /** 当前手指位置（根坐标系，px） */
    val fingerRoot: Offset get() = natTopLeft + grabOffset + translation
}

/**
 * 耐受页：食物以 tag 样式展示（框色即状态：绿=可耐受 红=不耐受 黄=尝试），
 * 点 tag 右上角出现 X 角标可删除。
 * 长按 tag（按住 400ms）进入拖动：标签跟随手指移动，
 * 同分区内松开 = 调整前后顺序，拖到另一分区松开 = 改变耐受状态。
 * 添加入口在「添加饮食」页面（照片区下方）。
 */
@OptIn(ExperimentalLayoutApi::class)
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
    /** 各标签的自然位置（根坐标系）——落点计算用 */
    val chipRects = remember { mutableMapOf<String, Rect>() }
    /** 各分区的位置（根坐标系）——判断手指悬停在哪一个框 */
    val sectionRects = remember { mutableMapOf<FoodTolerance, Rect>() }

    // 手势协程持有稳定的 lambda 引用，始终读取最新的状态与回调
    val curState = rememberUpdatedState(state)
    val curOnDelete = rememberUpdatedState(onDeleteFood)
    val curOnMove = rememberUpdatedState(onMoveFood)

    fun rectOf(coords: LayoutCoordinates): Rect {
        val p = coords.localToRoot(Offset.Zero)
        return Rect(p.x, p.y, p.x + coords.size.width, p.y + coords.size.height)
    }

    // ---- 拖动开始 / 移动 / 结束 ----

    fun startDrag(name: String, pos: Offset) {
        val rect = chipRects[name] ?: return
        val tag = curState.value.foodTags.firstOrNull { it.name == name } ?: return
        dragInfo = DragInfo(
            name = name,
            tolerance = FoodTolerance.fromValue(tag.tolerance),
            count = curState.value.foodTagCounts[name] ?: 0,
            natTopLeft = rect.topLeft,
            grabOffset = pos,
            translation = Offset.Zero
        )
        armedName = null
    }

    fun updateDrag(name: String, pos: Offset) {
        val d = dragInfo ?: return
        if (d.name != name) return
        dragInfo = d.copy(translation = pos - d.grabOffset)
    }

    fun endDrag(name: String, pos: Offset) {
        val d = dragInfo
        dragInfo = null
        val rect = chipRects[name] ?: return
        val finger = rect.topLeft + pos
        // 手指落在哪个分区，就移入哪个分区（落点不在任何分区内则不动）
        val target = sectionRects.entries.firstOrNull { it.value.contains(finger) }?.key ?: return
        // 插入位置：目标分区内按阅读顺序（先上行、再从左到右）排在手指后面的第一个标签
        val others = curState.value.foodTags.filter {
            it.name != name && FoodTolerance.fromValue(it.tolerance) == target
        }
        val beforeIdx = others.indexOfFirst { t ->
            val r = chipRects[t.name] ?: return@indexOfFirst false
            r.top > finger.y + r.height * 0.5f ||
                (abs(r.center.y - finger.y) < r.height * 0.5f && r.left > finger.x)
        }
        val before = if (beforeIdx == -1) null else others[beforeIdx].name
        curOnMove.value(name, target, before)
    }

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "note") {
                // 耐受说明卡（琥珀色提示）：默认折叠，点击展开/收起
                var expanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFF9A825).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable { expanded = !expanded },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF9A825).copy(alpha = 0.10f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row {
                            Text(
                                text = stringResource(R.string.tolerance_note_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB26A00),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(
                                    if (expanded) R.string.tolerance_note_collapse
                                    else R.string.tolerance_note_expand
                                ),
                                tint = Color(0xFFB26A00)
                            )
                        }
                        if (expanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(TOLERANCE_NOTE_RES),
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (state.foodTags.isEmpty()) {
                item(key = "empty") {
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
                }
            } else {
                // 三个分区：可耐受 / 不耐受 / 尝试（状态色分区卡片，区内为对应 tag）
                val finger = dragInfo?.fingerRoot
                TOLERANCE_ORDER.forEach { tol ->
                    item(key = "section_${tol.name}") {
                        ToleranceSection(
                            tolerance = tol,
                            tags = state.foodTags.filter {
                                FoodTolerance.fromValue(it.tolerance) == tol
                            },
                            counts = state.foodTagCounts,
                            armedName = armedName,
                            dragName = dragInfo?.name,
                            isDropTarget = finger != null && sectionRects[tol]?.contains(finger) == true,
                            onTagTap = { name ->
                                armedName = if (armedName == name) null else name
                            },
                            onTagDelete = { name ->
                                armedName = null
                                curOnDelete.value(name)
                            },
                            onChipPositioned = { name, coords -> chipRects[name] = rectOf(coords) },
                            onSectionPositioned = { coords -> sectionRects[tol] = rectOf(coords) },
                            onDragStart = { name, pos -> startDrag(name, pos) },
                            onDrag = { name, pos -> updateDrag(name, pos) },
                            onDragEnd = { name, pos -> endDrag(name, pos) }
                        )
                    }
                }
                // 底部提示（左对齐）
                item(key = "drag_hint") {
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
    DragOverlay(dragInfo = dragInfo, chipRects = chipRects)
}
}

/** 耐受分区：状态色边框 + 淡色底的卡片，区内为对应状态的食物 tag；拖动时手指悬停的分区高亮 */
@OptIn(ExperimentalLayoutApi::class)
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
    onDragStart: (String, Offset) -> Unit,
    onDrag: (String, Offset) -> Unit,
    onDragEnd: (String, Offset) -> Unit
) {
    val color = toleranceColor(tolerance)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onSectionPositioned(it) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = if (isDropTarget) 0.14f else 0.06f)
        ),
        border = BorderStroke(1.dp, color.copy(alpha = if (isDropTarget) 0.9f else 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
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
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.tolerance_types_count, tags.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (tags.isEmpty()) {
                Text(
                    text = stringResource(R.string.tolerance_empty_section),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        FoodTagChip(
                            tag = tag,
                            count = counts[tag.name] ?: 0,
                            armed = armedName == tag.name,
                            dragging = dragName == tag.name,
                            onPositioned = { onChipPositioned(tag.name, it) },
                            onTap = { onTagTap(tag.name) },
                            onDelete = { onTagDelete(tag.name) },
                            onDragStart = { onDragStart(tag.name, it) },
                            onDrag = { onDrag(tag.name, it) },
                            onDragEnd = { onDragEnd(tag.name, it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 食物 tag 手势：轻点 = onTap（标记删除）；
 * 按住 400ms 且未移动超过触摸阈值 = 长按，进入拖动（位置为 tag 本地坐标）；
 * 长按前若手指移动超过阈值则不拦截（让 LazyColumn 滚动）；释放 = onDragEnd。
 */
private fun Modifier.foodTagGesture(
    key: Any,
    touchSlop: Float,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit
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
                    // 不消费事件：列表滚动由 LazyColumn 处理，只等待手指抬起
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == id } ?: break
                        if (!c.pressed) break
                    }
                    continue
                }
                else -> {
                    onDragStart(lastPos)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == id } ?: break
                        if (!c.pressed) {
                            c.consume()
                            onDragEnd(c.position)
                            break
                        }
                        c.consume()
                        onDrag(c.position)
                    }
                }
            }
        }
    }
}

/** 食物 tag 视觉（药丸 + 名称 + 计数）；列表与拖动悬浮层共用 */
@Composable
private fun FoodTagChipVisual(
    name: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .border(1.5.dp, color, shape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyMedium)
        if (count > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "·$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 食物 tag：框色即状态；点名称右上角出现 X 角标（点 X 删除）；长按拖动换序/跨分区移动。
 * 拖动中：原位保留半透明占位，真正的 tag 由根级 [DragOverlay] 悬浮绘制（避免被其他分区卡片遮挡）。
 */
@Composable
private fun FoodTagChip(
    tag: FoodTag,
    count: Int,
    armed: Boolean,
    dragging: Boolean,
    onPositioned: (LayoutCoordinates) -> Unit,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit
) {
    val color = toleranceColor(FoodTolerance.fromValue(tag.tolerance))
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val haptics = LocalHapticFeedback.current

    Box {
        FoodTagChipVisual(
            name = tag.name,
            count = count,
            color = color,
            modifier = Modifier
                // 拖动中：原位半透明占位（真实 tag 在根级悬浮层绘制）
                .graphicsLayer { if (dragging) alpha = 0.35f }
                .onGloballyPositioned(onPositioned)
                .foodTagGesture(
                    key = tag.name,
                    touchSlop = touchSlop,
                    onTap = onTap,
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDragStart(it)
                    },
                    onDrag = onDrag,
                    onDragEnd = onDragEnd
                )
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

/**
 * 拖动悬浮层：拖动 tag 时，把它绘制在所有分区之上（跟随手指）。
 * 整层 fillMaxSize 但不带任何指针处理器，不会拦截触摸（原 tag 的手势仍在持有拖动）。
 */
@Composable
private fun DragOverlay(dragInfo: DragInfo?, chipRects: Map<String, Rect>) {
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
            val floatShape = RoundedCornerShape(50.dp)
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
 * 输入框 + 添加按钮（小圆角）；点按钮弹出下拉菜单选耐受状态，选中后添加
 */
@Composable
internal fun AddFoodSection(onAddFood: (String, FoodTolerance) -> Unit) {
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
            placeholder = { Text(stringResource(R.string.tolerance_add_placeholder)) },
            singleLine = true
        )
        // 添加按钮：点击弹出下拉菜单选择耐受状态，选中后才添加
        Box {
            Button(
                onClick = { showMenu = true },
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
