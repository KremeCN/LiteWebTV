package com.yukon.litewebtv

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView

/**
 * 一个“哑巴”WebView
 * 核心功能：彻底禁止弹出软键盘
 * 原理：当系统询问“你需要输入法吗？”时，直接回答“不需要(null)”
 */
class NoImeWebView : WebView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onCreateInputConnection(outAttrs: EditorInfo?): InputConnection? {
        // 返回 null，告诉系统：我不需要输入连接，别给我弹键盘！
        return null
    }
}