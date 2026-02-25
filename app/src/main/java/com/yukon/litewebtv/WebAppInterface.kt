package com.yukon.litewebtv

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONException

class WebAppInterface(private val context: Context) {

    @JavascriptInterface
    fun receiveChannelList(json: String) {
        try {
            val jsonArray = JSONArray(json)
            val list = ArrayList<ChannelItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(ChannelItem(
                    index = obj.getInt("index"),
                    name = obj.getString("name"),
                    isActive = obj.getBoolean("isActive")
                ))
            }
            if (context is MainActivity) {
                context.runOnUiThread {
                    context.updateChannelList(list)
                }
            }
        } catch (e: JSONException) {
            Log.e("LiteWebTV_Data", "频道解析错误", e)
        }
    }

    @JavascriptInterface
    fun receiveProgramList(json: String) {
        try {
            val jsonArray = JSONArray(json)
            val list = ArrayList<ProgramItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(ProgramItem(
                    time = obj.getString("time"),
                    title = obj.getString("title"),
                    isCurrent = obj.getBoolean("isCurrent")
                ))
            }
            if (context is MainActivity) {
                context.runOnUiThread {
                    context.updateProgramList(list)
                }
            }
        } catch (e: JSONException) {
            Log.e("LiteWebTV_Data", "节目单解析错误", e)
        }
    }

    @JavascriptInterface
    fun receiveTitle(title: String) {
        if (context is MainActivity) {
            context.runOnUiThread {
                context.onJsTitleReceived(title)
            }
        }
    }

    // 【新增】JS通知：视频已开始播放，请求关闭开屏幕布
    @JavascriptInterface
    fun dismissSplash() {
        Log.d("LiteWebTV_Splash", "收到 JS 信号：视频已开始播放")
        if (context is MainActivity) {
            context.runOnUiThread {
                context.onVideoPlaySuccess()
            }
        }
    }
}
