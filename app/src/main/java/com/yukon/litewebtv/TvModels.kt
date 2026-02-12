package com.yukon.litewebtv

// 频道数据模型
data class ChannelItem(
    val index: Int,      // 索引，用于点击切换
    val name: String,    // 频道名称
    val isActive: Boolean // 是否是当前频道
)

// 节目单数据模型
data class ProgramItem(
    val time: String,    // 播出时间
    val title: String,   // 节目名称
    val isCurrent: Boolean // 是否正在播出
)