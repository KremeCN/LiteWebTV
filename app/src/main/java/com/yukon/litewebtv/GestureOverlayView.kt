package com.yukon.litewebtv

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * 透明手势触控层
 *
 * 覆盖在 WebView 之上，拦截并识别多种手势：
 *
 * ┌────────────┬──────────────┬────────────┐
 * │  左侧 40%  │   中央 20%   │  右侧 40%  │
 * │  上下拖拽   │   上下快扫    │  上下拖拽   │
 * │  ☀ 亮度     │   换台       │  🔊 音量    │
 * │            │              │            │
 * │  左右快扫 → 全局：呼出频道列表/节目单     │
 * │  单击 → 关闭侧边栏                     │
 * │  双击 → 播放/暂停                       │
 * └────────────┴──────────────┴────────────┘
 */
class GestureOverlayView : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    // ==============================
    // 回调接口
    // ==============================
    interface GestureCallback {
        fun onSwipeUp()
        fun onSwipeDown()
        fun onSwipeLeft()
        fun onSwipeRight()
        fun onSingleTap()
        fun onDoubleTap()

        /** 音量变化，deltaPercent 为归一化增量（正=增大） */
        fun onVolumeChange(deltaPercent: Float)

        /** 亮度变化，deltaPercent 为归一化增量（正=增大） */
        fun onBrightnessChange(deltaPercent: Float)

        /** 开始调节（isVolume=true 为音量，false 为亮度） */
        fun onAdjustStart(isVolume: Boolean)

        /** 结束调节 */
        fun onAdjustEnd()
    }

    var callback: GestureCallback? = null
    var isSidebarOpen: Boolean = false
    var passthroughViews: List<View> = emptyList()

    // ==============================
    // 拖拽模式状态机
    // ==============================
    private enum class DragMode {
        NONE,        // 等待 ACTION_DOWN
        UNDECIDED,   // 已按下，尚未判定手势类型
        BRIGHTNESS,  // 左侧纵向拖拽 → 亮度
        VOLUME,      // 右侧纵向拖拽 → 音量
        GESTURE      // 中央区域 / 横向 → 交给 GestureDetector
    }

    private var dragMode = DragMode.NONE
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastDragY = 0f

    companion object {
        /** 判定拖拽方向前需要超过的最小位移 (px) */
        private const val DRAG_DECIDE_THRESHOLD = 30f

        /** 滑动判定的最小距离 (px) — 用于 fling 检测 */
        private const val SWIPE_THRESHOLD = 100

        /** 滑动判定的最小速度 (px/s) */
        private const val SWIPE_VELOCITY_THRESHOLD = 200

        /** 亮度/音量拖拽灵敏度倍数 */
        private const val ADJUST_SENSITIVITY = 1.5f

        /** 屏幕左/右分区比例：左 40% / 中 20% / 右 40% */
        private const val ZONE_LEFT_END = 0.4f
        private const val ZONE_RIGHT_START = 0.6f
    }

    // ==============================
    // GestureDetector (处理单击 & 双击 & fling)
    // ==============================
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (isAdjusting()) return false
            callback?.onSingleTap()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isAdjusting()) return false
            callback?.onDoubleTap()
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null || isAdjusting()) return false

            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            val absDx = abs(dx)
            val absDy = abs(dy)

            // 水平滑动优先判定
            if (absDx > absDy && absDx > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (dx > 0) callback?.onSwipeRight() else callback?.onSwipeLeft()
                return true
            }

            // 垂直滑动判定 — 仅中央区域才触发换台
            if (absDy > absDx && absDy > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                if (dy > 0) callback?.onSwipeDown() else callback?.onSwipeUp()
                return true
            }

            return false
        }

        override fun onDown(e: MotionEvent): Boolean = true
    })

    private fun isAdjusting(): Boolean =
        dragMode == DragMode.BRIGHTNESS || dragMode == DragMode.VOLUME

    // ==============================
    // 触摸事件处理
    // ==============================

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isSidebarOpen) return true
        for (view in passthroughViews) {
            if (view.visibility == View.VISIBLE && isTouchInsideView(ev, view)) {
                return false
            }
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                lastDragY = event.y
                dragMode = DragMode.UNDECIDED
            }

            MotionEvent.ACTION_MOVE -> {
                // 尚未判定：等位移超过阈值后决定手势类型
                if (dragMode == DragMode.UNDECIDED) {
                    val dx = abs(event.x - touchStartX)
                    val dy = abs(event.y - touchStartY)

                    if (dx > DRAG_DECIDE_THRESHOLD || dy > DRAG_DECIDE_THRESHOLD) {
                        if (dy > dx * 1.2f) {
                            // 明确的纵向拖拽 → 按起始横坐标分区
                            val screenWidth = width.toFloat()
                            dragMode = when {
                                touchStartX < screenWidth * ZONE_LEFT_END -> DragMode.BRIGHTNESS
                                touchStartX > screenWidth * ZONE_RIGHT_START -> DragMode.VOLUME
                                else -> DragMode.GESTURE
                            }
                            if (isAdjusting()) {
                                callback?.onAdjustStart(dragMode == DragMode.VOLUME)
                            }
                        } else {
                            dragMode = DragMode.GESTURE
                        }
                    }
                }

                // 正在调节亮度/音量：计算增量
                if (isAdjusting()) {
                    val deltaY = lastDragY - event.y   // 向上拖 → 正值 → 增大
                    lastDragY = event.y
                    val deltaPercent = (deltaY / height) * ADJUST_SENSITIVITY

                    if (dragMode == DragMode.VOLUME) {
                        callback?.onVolumeChange(deltaPercent)
                    } else {
                        callback?.onBrightnessChange(deltaPercent)
                    }
                    return true   // 消费事件，不传给 GestureDetector
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasAdjusting = isAdjusting()
                if (wasAdjusting) {
                    callback?.onAdjustEnd()
                }
                dragMode = DragMode.NONE
                if (wasAdjusting) {
                    // 发送 CANCEL 给 GestureDetector 以清理内部状态
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    gestureDetector.onTouchEvent(cancel)
                    cancel.recycle()
                    return true
                }
            }
        }

        // 非亮度/音量调节时，交给 GestureDetector 处理点击/双击/滑动
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun isTouchInsideView(ev: MotionEvent, target: View): Boolean {
        val location = IntArray(2)
        target.getLocationOnScreen(location)
        val x = ev.rawX
        val y = ev.rawY
        return x >= location[0] && x <= location[0] + target.width &&
               y >= location[1] && y <= location[1] + target.height
    }
}
