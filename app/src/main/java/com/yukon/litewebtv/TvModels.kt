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
    var isCurrent: Boolean // 是否正在播出
) {
    // 双时区显示：同日仅显示时间，跨日才显示日期
    val displayTime: String
        get() {
            val beijingTZ = java.util.TimeZone.getTimeZone("Asia/Shanghai")
            val localTZ = MainActivity.realLocalTimeZone

            val parts = time.split(":")
            if (parts.size != 2) return time
            val h = parts[0].toIntOrNull() ?: return time
            val m = parts[1].toIntOrNull() ?: return time
            if (h !in 0..23 || m !in 0..59) return time

            val now = System.currentTimeMillis()
            val beijingCal = java.util.Calendar.getInstance(beijingTZ)
            beijingCal.timeInMillis = now
            beijingCal.set(java.util.Calendar.HOUR_OF_DAY, h)
            beijingCal.set(java.util.Calendar.MINUTE, m)
            beijingCal.set(java.util.Calendar.SECOND, 0)
            beijingCal.set(java.util.Calendar.MILLISECOND, 0)

            val beijingDate = beijingCal.time

            val localCal = java.util.Calendar.getInstance(localTZ)
            localCal.time = beijingDate

            val beijingDay = beijingCal.get(java.util.Calendar.DAY_OF_MONTH)
            val localDay = localCal.get(java.util.Calendar.DAY_OF_MONTH)

            val beijingTime = String.format("%02d:%02d", h, m)
            val localTime = String.format(
                "%02d:%02d",
                localCal.get(java.util.Calendar.HOUR_OF_DAY),
                localCal.get(java.util.Calendar.MINUTE)
            )

            if (beijingTZ.getOffset(now) == localTZ.getOffset(now)) {
                return beijingTime
            }

            if (beijingDay == localDay) {
                return "$beijingTime（$localTime）"
            }

            return "${beijingDay}日 $beijingTime（${localDay}日 $localTime）"
        }
}
