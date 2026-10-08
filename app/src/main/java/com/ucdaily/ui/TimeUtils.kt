package com.ucdaily.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 记录创建时间（HH:mm）：排便卡片展示 + 当日列表排序共用（RecordPanels 也引用） */
fun recordTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
