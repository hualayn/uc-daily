package com.ucdaily.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ucdaily.R

/**
 * 设计令牌（对应 docs/ui-mockup.html 的 CSS 变量）：
 * 品牌主色 #2563EB（浅）/ #5B9BFF（深），类型色：饮食绿 / 大便琥珀 / 服药蓝 / 感受紫。
 * 随应用主题（Light/Dark 令牌集）切换，通过 [UcDailyTheme] 注入 [LocalUcPalette]。
 */
data class UcPalette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val text2: Color,
    val ring: Color,
    val primary: Color,
    val primaryDeep: Color,
    val primarySoft: Color,
    val primaryText: Color,
    val green: Color,
    val greenSoft: Color,
    val greenText: Color,
    val amber: Color,
    val amberSoft: Color,
    val amberText: Color,
    val red: Color,
    val redSoft: Color,
    val redText: Color,
    val purple: Color,
    val purpleSoft: Color,
    val purpleText: Color,
    val orange: Color,
)

/** 浅色令牌（设计稿 :root） */
internal val LightUcPalette = UcPalette(
    bg = Color(0xFFF4F7FB),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEDF1F7),
    text = Color(0xFF1B2437),
    text2 = Color(0xFF66748C),
    ring = Color(0xFFD9E1EC),
    primary = Color(0xFF2563EB),
    primaryDeep = Color(0xFF1D4ED8),
    primarySoft = Color(0xFFE4EDFF),
    primaryText = Color(0xFF1E40AF),
    green = Color(0xFF16A34A),
    greenSoft = Color(0xFFE3F5EA),
    greenText = Color(0xFF15803D),
    amber = Color(0xFFD97706),
    amberSoft = Color(0xFFFCEFDC),
    amberText = Color(0xFFB45309),
    red = Color(0xFFDC2626),
    redSoft = Color(0xFFFDE9E9),
    redText = Color(0xFFB91C1C),
    purple = Color(0xFF7C3AED),
    purpleSoft = Color(0xFFF0EAFE),
    purpleText = Color(0xFF6D28D9),
    orange = Color(0xFFF97316),
)

/** 深色令牌（设计稿 html[data-theme="dark"]） */
internal val DarkUcPalette = UcPalette(
    bg = Color(0xFF0E1420),
    surface = Color(0xFF1A2334),
    surface2 = Color(0xFF232E44),
    text = Color(0xFFE9EEF8),
    text2 = Color(0xFF93A2BC),
    ring = Color(0xFF2C3A55),
    primary = Color(0xFF5B9BFF),
    primaryDeep = Color(0xFF7FB0FF),
    primarySoft = Color(0xFF1D2C49),
    primaryText = Color(0xFF9CC2FF),
    green = Color(0xFF34D399),
    greenSoft = Color(0xFF132B21),
    greenText = Color(0xFF5EE0A8),
    amber = Color(0xFFFBBF24),
    amberSoft = Color(0xFF2E2410),
    amberText = Color(0xFFFCD34D),
    red = Color(0xFFF87171),
    redSoft = Color(0xFF301518),
    redText = Color(0xFFFCA5A5),
    purple = Color(0xFFA78BFA),
    purpleSoft = Color(0xFF241A36),
    purpleText = Color(0xFFC4B0FB),
    orange = Color(0xFFFB923C),
)

internal val LocalUcPalette = staticCompositionLocalOf { LightUcPalette }

/** 当前生效的调色板（跟随应用主题） */
@Composable
fun ucPalette(): UcPalette = LocalUcPalette.current

/** Hero 渐变（欢迎卡 / 统计概览 / 服药次数卡），135° 对角（设计稿 0% / 55% / 100% 三段） */
@Composable
fun heroBrush(): Brush =
    if (LocalDarkTheme.current) {
        Brush.linearGradient(
            0f to Color(0xFF2C55C4), 0.55f to Color(0xFF1B3A99), 1f to Color(0xFF142C74),
            start = Offset(0f, 0f),
            end = Offset.Infinite
        )
    } else {
        Brush.linearGradient(
            0f to Color(0xFF4C8DFF), 0.55f to Color(0xFF2563EB), 1f to Color(0xFF1E4FD8),
            start = Offset(0f, 0f),
            end = Offset.Infinite
        )
    }

/** 主按钮渐变（#3B82F6 → #2563EB） */
@Composable
fun primaryBtnBrush(): Brush =
    Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF2563EB)))

/** 卡片柔阴影（深色主题加深，对应设计稿 --shadow） */
@Composable
fun Modifier.softShadow(
    elevation: androidx.compose.ui.unit.Dp = 3.dp,
    shape: Shape = RoundedCornerShape(18.dp)
): Modifier {
    val a = if (LocalDarkTheme.current) 0.35f else 0.07f
    return this.shadow(
        elevation,
        shape,
        ambientColor = Color.Black.copy(alpha = a),
        spotColor = Color.Black.copy(alpha = a)
    )
}

// ---------------------------------------------------------------------------
// 记录类型（左侧色条 / 图标 / 快捷添加入口共用）
// ---------------------------------------------------------------------------

/** 记录类型：决定色条、图标底色与快捷添加入口磁贴的颜色 */
enum class RecordKind { MEAL, BOWEL, MED, NOTE }

/** 类型三色组：main = 主色（色条/描边），soft = 浅色底（图标底/徽章底），text = 深色文字 */
data class TypeColors(val main: Color, val soft: Color, val text: Color)

/** 记录类型 → 类型色（饮食绿 / 大便琥珀 / 服药蓝 / 感受紫） */
@Composable
fun recordTypeColors(kind: RecordKind): TypeColors {
    val p = ucPalette()
    return when (kind) {
        RecordKind.MEAL -> TypeColors(p.green, p.greenSoft, p.greenText)
        RecordKind.BOWEL -> TypeColors(p.amber, p.amberSoft, p.amberText)
        RecordKind.MED -> TypeColors(p.primary, p.primarySoft, p.primaryText)
        RecordKind.NOTE -> TypeColors(p.purple, p.purpleSoft, p.purpleText)
    }
}

/** 记录类型 emoji（首页统计卡 / 记录卡图标 / 快捷添加共用） */
fun recordKindEmoji(kind: RecordKind): String = when (kind) {
    RecordKind.MEAL -> "🍚"
    RecordKind.BOWEL -> "💩"
    RecordKind.MED -> "💊"
    RecordKind.NOTE -> "📝"
}

// ---------------------------------------------------------------------------
// 基础组件
// ---------------------------------------------------------------------------

/** 标准卡片：surface 底 + 18dp 圆角 + 柔阴影（设计稿 .card） */
@Composable
fun UcCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = ucPalette()
    Box(
        modifier = modifier
            .softShadow()
            .clip(RoundedCornerShape(18.dp))
            .background(p.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** 渐变主按钮（设计稿 .btn.pri）：14dp 圆角 + 主色渐变 + 蓝色投影 */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String
) {
    Box(
        modifier = modifier
            .shadow(
                6.dp,
                RoundedCornerShape(14.dp),
                ambientColor = Color(0xFF2563EB).copy(alpha = 0.35f),
                spotColor = Color(0xFF2563EB).copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(primaryBtnBrush())
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 次级按钮（设计稿 .btn.out）：surface 底 + 描边 + 柔阴影 */
@Composable
fun OutlineButton2(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String
) {
    val p = ucPalette()
    Box(
        modifier = modifier
            .softShadow(elevation = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, p.ring, RoundedCornerShape(14.dp))
            .background(p.surface)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) p.text else p.text2,
            maxLines = 1
        )
    }
}

/** 条数徽章（primary-soft 胶囊，设计稿 .badge） */
@Composable
fun CountBadge(text: String, modifier: Modifier = Modifier) {
    val p = ucPalette()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(p.primarySoft)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = p.primaryText)
    }
}

/** 区块标题（设计稿 .sec-head）：加粗标题 + 可选条数徽章 */
@Composable
fun SectionHead(
    title: String,
    badge: String? = null,
    modifier: Modifier = Modifier,
    onClearBadge: (() -> Unit)? = null
) {
    val p = ucPalette()
    Row(
        modifier = modifier
            .padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.text)
        if (badge != null) {
            Spacer(modifier = Modifier.width(8.dp))
            CountBadge(text = badge)
        }
        if (onClearBadge != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(p.surface2)
                    .clickable(onClick = onClearBadge),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_restore),
                    modifier = Modifier.size(13.dp),
                    tint = p.text2
                )
            }
        }
    }
}

/** 二级页统一顶部栏（设计稿 .topbar）：圆形返回钮 + 标题 + 可选右侧计数 */
@Composable
fun SecondaryTopBar(
    onBack: () -> Unit,
    title: String,
    trailing: String? = null,
    modifier: Modifier = Modifier
) {
    val p = ucPalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .shadow(
                    2.dp,
                    CircleShape,
                    ambientColor = Color.Black.copy(alpha = if (LocalDarkTheme.current) 0.4f else 0.07f),
                    spotColor = Color.Black.copy(alpha = if (LocalDarkTheme.current) 0.4f else 0.07f)
                )
                .clip(CircleShape)
                .background(p.surface)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.common_back),
                modifier = Modifier.size(18.dp),
                tint = p.text
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = p.text
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .shadow(
                        2.dp,
                        CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.06f),
                        spotColor = Color.Black.copy(alpha = 0.06f)
                    )
                    .clip(RoundedCornerShape(50.dp))
                    .background(p.surface)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(text = trailing, fontSize = 11.sp, color = p.text2)
            }
        }
    }
}

/** 菜单行（"我的" / "设置" 页共用，设计稿 .mrow）：彩色图标底 + 标题 + 副标题 + 当前值 + 箭头 */
@Composable
fun MenuItemRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    label: String,
    sub: String? = null,
    value: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = ucPalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = iconTint
            )
        }
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 13.sp, color = p.text)
            if (sub != null) {
                Text(
                    text = sub,
                    fontSize = 10.sp,
                    color = p.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                fontSize = 11.sp,
                color = p.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = p.ring
        )
    }
}

// ---------------------------------------------------------------------------
// 记录卡片（设计稿 .rec：左侧类型色条 + 彩色图标头 + 选中描边）
// ---------------------------------------------------------------------------

/** 记录卡外壳：surface 底 + 16dp 圆角 + 柔阴影 + 左侧 4dp 类型色条；选中时 2dp 主色描边 */
@Composable
fun RecordCardShell(
    kind: RecordKind,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = ucPalette()
    val tc = recordTypeColors(kind)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                2.dp,
                shape,
                ambientColor = Color.Black.copy(alpha = if (LocalDarkTheme.current) 0.35f else 0.05f),
                spotColor = Color.Black.copy(alpha = if (LocalDarkTheme.current) 0.35f else 0.06f)
            )
            .clip(shape)
            .background(p.surface)
            .then(if (selected) Modifier.border(2.dp, p.primary, shape) else Modifier)
            .clickable(onClick = onSelect)
    ) {
        // 左侧类型色条（上下各留 10dp）
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(vertical = 10.dp)
                .width(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(tc.main)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            content = content
        )
    }
}

/** 记录卡头部行：类型图标 + 标题 + 时间 + 可选徽章 + 编辑/删除按钮 */
@Composable
fun RecordHeadRow(
    kind: RecordKind,
    title: String,
    time: String? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    editLabel: String,
    deleteLabel: String,
    badge: @Composable (() -> Unit)? = null
) {
    val p = ucPalette()
    val tc = recordTypeColors(kind)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(tc.soft),
            contentAlignment = Alignment.Center
        ) {
            Text(text = recordKindEmoji(kind), fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = title, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = p.text)
        if (time != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = time, fontSize = 11.sp, color = p.text2)
        }
        if (badge != null) {
            Spacer(modifier = Modifier.width(6.dp))
            badge()
        }
        Spacer(modifier = Modifier.weight(1f))
        RecordOpButton(Icons.Filled.Edit, editLabel, onEdit)
        Spacer(modifier = Modifier.width(2.dp))
        RecordOpButton(Icons.Filled.Delete, deleteLabel, onDelete)
    }
}

/** 卡片头部的小操作按钮（✎ / 🗑，24dp 圆角方块） */
@Composable
fun RecordOpButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val p = ucPalette()
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(p.surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(13.dp), tint = p.text2)
    }
}

// ---------------------------------------------------------------------------
// 记录汇总列表行（设计稿 .lr：surface 底 + 左侧色条 + 固定宽时间）
// ---------------------------------------------------------------------------

/** 汇总列表单行卡片：左侧 4dp 类型色条 + 时间列 + 内容 */
@Composable
fun ListRowCard(
    kind: RecordKind,
    time: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = ucPalette()
    val tc = recordTypeColors(kind)
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                1.5.dp,
                shape,
                ambientColor = Color.Black.copy(alpha = if (LocalDarkTheme.current) 0.3f else 0.04f),
                spotColor = Color.Black.copy(alpha = if (LocalDarkTheme.current) 0.3f else 0.05f)
            )
            .clip(shape)
            .background(p.surface)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(vertical = 9.dp)
                .width(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(tc.main)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 15.dp, end = 11.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (time != null) {
                Text(
                    text = time,
                    fontSize = 10.5.sp,
                    color = p.text2,
                    modifier = Modifier.width(38.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), content = content)
        }
    }
}

// ---------------------------------------------------------------------------
// 对话框（设计稿：圆角 20px + 顶部彩色图标底；危险操作红底浅字）
// ---------------------------------------------------------------------------

/** 对话框小按钮：primary = 渐变主按钮；danger = 红底浅字；outline = 次级 */
@Composable
private fun DialogButton(
    label: String,
    onClick: () -> Unit,
    style: Int,
    enabled: Boolean = true
) {
    val p = ucPalette()
    when (style) {
        1 -> {
            // danger
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.redSoft)
                    .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) p.redText else p.redText.copy(alpha = 0.5f)
                )
            }
        }
        2 -> {
            // outline
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, p.ring, RoundedCornerShape(10.dp))
                    .background(p.surface)
                    .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text)
            }
        }
        else -> {
            // primary 渐变
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .shadow(
                        4.dp,
                        RoundedCornerShape(10.dp),
                        ambientColor = Color(0xFF2563EB).copy(alpha = 0.3f),
                        spotColor = Color(0xFF2563EB).copy(alpha = 0.3f)
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .background(primaryBtnBrush())
                    .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * 统一对话框（设计稿 .mini-dlg / .dlg）：
 * 顶部彩色图标底 + 居中标题 + 居中说明 + 底部按钮（主 = 渐变 / 危险 = 红底 / 次 = 描边）。
 */
@Composable
fun UcDialog(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmIsDanger: Boolean = false,
    dismissLabel: String? = null,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true
) {
    val p = ucPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = p.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = iconTint
                )
            }
        },
        title = {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = p.text,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 12.sp,
                color = p.text2,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            DialogButton(
                label = confirmLabel,
                onClick = onConfirm,
                style = if (confirmIsDanger) 1 else 0,
                enabled = confirmEnabled
            )
        },
        dismissButton = {
            if (dismissLabel != null) {
                DialogButton(label = dismissLabel, onClick = onDismiss, style = 2)
            }
        }
    )
}

/** 活动度徽章（浅色底 + 同色文字，设计稿 .act-badge） */
@Composable
fun ActivityBadgeText(text: String, color: Color) {
    val p = ucPalette()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = if (p == LightUcPalette) 0.12f else 0.22f))
            .padding(horizontal = 8.dp, vertical = 2.5.dp)
    ) {
        Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** Hero 卡内的半透明白色图标按钮（🔔 / ⚙️，设计稿 .iconbtn） */
@Composable
fun HeroIconButton(
    emoji: String,
    onClick: () -> Unit,
    contentDescription: String,
    badge: Boolean = false
) {
    // 注意：不能用 clip(CircleShape) 裁剪整个按钮 —— 红点角标位于圆形外缘，
    // 会被圆裁剪直接切没；这里用 background 的 shape 参数画圆底，内容不被裁剪
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(Color.White.copy(alpha = 0.18f), CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 15.sp)
        if (badge) {
            // 9dp 红点：中心正好落在圆形右上 45° 边缘（align TopEnd + 零偏移）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF5A5A))
                    .border(1.5.dp, Color.White, CircleShape)
            )
        }
    }
}


