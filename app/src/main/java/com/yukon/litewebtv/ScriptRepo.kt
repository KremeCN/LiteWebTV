package com.yukon.litewebtv

object ScriptRepo {

    /**
     * 核心自动化脚本合集
     * 架构：任务注册式 MutationObserver + requestAnimationFrame 节流
     *
     * 性能对比（vs 旧版6个 setInterval）：
     * - 旧版：6个独立计时器 ≈ 15次回调/秒，持续运行
     * - 新版：1个 MutationObserver，DOM 变化时才触发，rAF 合并为每帧最多1次
     *         任务完成即移除，全部完成后 Observer 自动断开
     *         视频监测改用原生事件，零持续 CPU 开销
     */
    val AUTOMATION_SCRIPT = """
        (function() {
            'use strict';

            // 【防御模块】强制禁用页面上所有输入框
            function disableAllInputs() {
                const inputs = document.querySelectorAll('input, textarea, [contenteditable="true"]');
                inputs.forEach(el => {
                    el.setAttribute('disabled', 'true');
                    el.setAttribute('readonly', 'true');
                    el.blur();
                });
            }

            // 定义向 Android 发送数据的函数
            function sendDataToAndroid() {
                disableAllInputs();

                // --- A. 提取频道列表 (严厉过滤版) ---
                const channelItems = document.querySelectorAll('.tv-main-con-r-list-left .oveerflow-1');
                if (channelItems.length > 0) {
                    let channelList = [];
                    channelItems.forEach((item, index) => {
                        const span = item.querySelector('span');
                        let isRestricted = false;

                        if (span) {
                            const tag = span.querySelector('.tv-main-con-r-list-left-tag');

                            // 【核心修复】同时检测 VIP 和 限免
                            if (tag) {
                                const tagText = tag.textContent;
                                if (tagText.includes('VIP') || tagText.includes('限免')) {
                                    isRestricted = true;
                                }
                            }

                            // 只有完全无限制的频道才加入列表
                            if (!isRestricted) {
                                let fullText = span.textContent;
                                if (tag) fullText = fullText.replace(tag.textContent, '');
                                const name = fullText.trim();

                                channelList.push({
                                    index: index,
                                    name: name,
                                    isActive: item.classList.contains('tvSelect')
                                });
                            }
                        }
                    });
                    if (window.Android && window.Android.receiveChannelList) {
                        window.Android.receiveChannelList(JSON.stringify(channelList));
                    }
                }

                // --- B. 提取节目单列表 ---
                const progItems = document.querySelectorAll('.tv-zhan-list-b-r .tv-zhan-list-b-r-item');
                if (progItems.length > 0) {
                    let progList = [];
                    progItems.forEach(item => {
                        if (item.children.length >= 2) {
                            const time = item.children[0].textContent.trim();
                            const name = item.children[1].textContent.trim();
                            progList.push({
                                time: time,
                                title: name,
                                isCurrent: item.classList.contains('now')
                            });
                        }
                    });
                    if (window.Android && window.Android.receiveProgramList) {
                        window.Android.receiveProgramList(JSON.stringify(progList));
                    }
                }

                // --- C. 提取当前标题 ---
                const titleEl = document.querySelector('.tv-zhan-title');
                if (titleEl) {
                    const titleText = titleEl.textContent.trim();
                    if (titleText.length > 0 && window.Android && window.Android.receiveTitle) {
                        window.Android.receiveTitle(titleText);
                    }
                }
            }

            // =========================================================
            // 数据变更监听器（永久运行）
            // 当网站更新节目单/频道列表/标题时自动重推数据到 Android
            // 作用域限定在3个容器节点上，不监听全局 DOM
            // =========================================================
            let _dataDebounce = null;
            function watchForDataUpdates() {
                const targets = [
                    document.querySelector('.tv-zhan-list-b-r'),
                    document.querySelector('.tv-zhan-title'),
                    document.querySelector('.tv-main-con-r-list-left')
                ].filter(Boolean);

                if (targets.length === 0) return false;

                const dataObserver = new MutationObserver(() => {
                    if (_dataDebounce) clearTimeout(_dataDebounce);
                    _dataDebounce = setTimeout(() => {
                        sendDataToAndroid();
                    }, 300);
                });

                targets.forEach(el => {
                    dataObserver.observe(el, {
                        childList: true,
                        subtree: true,
                        characterData: true
                    });
                });

                return true;
            }

            // 辅助：模拟鼠标移动唤出播放器控制条
            function revealControls() {
                const c = document.querySelector('.container');
                if (c) c.dispatchEvent(new MouseEvent('mousemove', {bubbles: true}));
            }

            // =========================================================
            // 核心引擎: 任务注册式 MutationObserver
            // - 每个任务是一个函数，返回 true 表示完成并永久移除
            // - 所有任务完成后 Observer 自动 disconnect，零开销
            // - requestAnimationFrame 合并同一帧内的多次 DOM 变更
            // =========================================================
            const _tasks = new Map();
            let _observer = null;
            let _rafId = null;

            function addTask(id, fn) {
                _tasks.set(id, fn);
                if (!_observer) _startObserver();
                _scheduleRun();
            }

            function _scheduleRun() {
                if (_rafId !== null) return;
                _rafId = requestAnimationFrame(() => {
                    _rafId = null;
                    for (const [id, fn] of _tasks) {
                        try { if (fn()) _tasks.delete(id); } catch(e) {}
                    }
                    if (_tasks.size === 0 && _observer) {
                        _observer.disconnect();
                        _observer = null;
                    }
                });
            }

            function _startObserver() {
                _observer = new MutationObserver(_scheduleRun);
                _observer.observe(document.documentElement, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['style', 'class']
                });
            }

            // ==========================================
            // 1. 网页加载监测器
            // ==========================================
            addTask('pageLoad', () => {
                disableAllInputs();
                revealControls();

                const hasPlayer = !!(document.querySelector('#vodbox2024078201') || document.querySelector('.c-container') || document.querySelector('.video-con'));
                const hasChannelList = document.querySelectorAll('.tv-main-con-r-list-left .oveerflow-1').length > 0;
                const hasProgramList = document.querySelectorAll('.tv-zhan-list-b-r .tv-zhan-list-b-r-item').length > 0;
                const titleEl = document.querySelector('.tv-zhan-title');
                const hasTitle = titleEl && titleEl.textContent.trim().length > 0;

                if (hasPlayer && hasChannelList && hasProgramList && hasTitle) {
                    sendDataToAndroid();
                    return true;
                }
                return false;
            });

            // ==========================================
            // 2. 画质 (自动选 1080P)
            // ==========================================
            addTask('quality', () => {
                revealControls();
                const qualityItems = document.querySelectorAll('.bei-list-inner .item');
                if (qualityItems.length === 0) return false;
                for (const item of qualityItems) {
                    if (item.textContent.trim().includes('1080P')) {
                        if (!item.classList.contains('active')) item.click();
                        return true;
                    }
                }
                return false;
            });

            // ==========================================
            // 3. 声音 (自动取消静音)
            // ==========================================
            addTask('unmute', () => {
                revealControls();
                const muteBtn = document.querySelector('.voice.off');
                if (!muteBtn) return false;
                if (window.getComputedStyle(muteBtn).display !== 'none') muteBtn.click();
                return true;
            });

            // ==========================================
            // 4. 全屏 (强制样式覆盖)
            // ==========================================
            addTask('fullscreen', () => {
                const pc = document.querySelector('#vodbox2024078201') || document.querySelector('.c-container') || document.querySelector('.video-con');
                if (!pc) return false;
                if (pc.style.position === 'fixed') {
                    if (pc.style.zIndex !== '99999') pc.style.setProperty('z-index', '99999', 'important');
                    return true;
                }
                pc.style.cssText = 'position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 99999 !important; background-color: black !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important;';
                const videoTag = pc.querySelector('video');
                if (videoTag) videoTag.style.cssText = 'width: 100% !important; height: 100% !important; object-fit: contain !important;';
                const sideBar = document.querySelector('.tv-main-con-r');
                if (sideBar) sideBar.style.display = 'none';
                return true;
            });

            // ==========================================
            // 5. 播放 (自动点击播放按钮)
            // ==========================================
            addTask('autoPlay', () => {
                revealControls();
                const startBtn = document.querySelector('.y-full-control-btnl .play.play1');
                const playingBtn = document.querySelector('.y-full-control-btnl .play.play2');
                if (startBtn && window.getComputedStyle(startBtn).display !== 'none') {
                    startBtn.click();
                    return true;
                }
                if (playingBtn && window.getComputedStyle(playingBtn).display !== 'none') {
                    return true;
                }
                return false;
            });

            // ==========================================
            // 6. 视频播放监测 → 智能幕布触发
            //    纯事件驱动，替代原 500ms 持续轮询
            // ==========================================
            addTask('videoMonitor', () => {
                const video = document.querySelector('video');
                if (!video) return false;

                let dismissed = false;
                let tuListening = false;

                function tryDismiss() {
                    if (dismissed) return;
                    if (!video.paused && video.readyState >= 3 && video.currentTime > 0.1) {
                        if (window.Android && window.Android.dismissSplash) {
                            window.Android.dismissSplash();
                            dismissed = true;
                            stopTimeUpdate();
                        }
                    }
                }

                function onTimeUpdate() {
                    tryDismiss();
                }

                function startTimeUpdate() {
                    if (!tuListening) {
                        video.addEventListener('timeupdate', onTimeUpdate);
                        tuListening = true;
                    }
                }

                function stopTimeUpdate() {
                    if (tuListening) {
                        video.removeEventListener('timeupdate', onTimeUpdate);
                        tuListening = false;
                    }
                }

                // playing 事件：视频开始播放时触发
                video.addEventListener('playing', () => {
                    tryDismiss();
                    if (!dismissed) startTimeUpdate();
                });

                // loadstart 事件：换台加载新源时重置状态
                video.addEventListener('loadstart', () => {
                    dismissed = false;
                    startTimeUpdate();
                });

                // 立即检查当前状态
                tryDismiss();
                if (!dismissed) startTimeUpdate();

                return true; // 事件监听器已挂载，任务完成
            });

            // ==========================================
            // 7. 节目单时区修正 (强制对齐北京时间)
            // ==========================================
            let _lastTzFixTs = 0;
            addTask('timezoneFix', () => {
                const dayTabs = document.querySelectorAll('.tv-zhan-list-week-item');
                if (dayTabs.length === 0) return false;

                const curTab = Array.from(dayTabs).find(tb => tb.classList.contains('cur'));

                const now = new Date();
                const bjTime = new Date(now.getTime() + (now.getTimezoneOffset() - (-480)) * 60000);
                const m = (bjTime.getMonth() + 1).toString().padStart(2, '0');
                const d = bjTime.getDate().toString().padStart(2, '0');
                const bjDateStr = `${'$'}{m}/${'$'}{d}`;

                const targetTab = Array.from(dayTabs).find(tb => tb.textContent.includes(bjDateStr));
                if (!targetTab) return false;

                if (curTab && curTab.textContent.includes(bjDateStr)) {
                    return false;
                }

                const nowTs = Date.now();
                if (nowTs - _lastTzFixTs < 1000) {
                    return false;
                }

                targetTab.click();
                _lastTzFixTs = nowTs;

                return false;
            });

            // ==========================================
            // 8. 数据变更自动同步（永久监听）
            //    换台后网站更新节目单/标题时自动重推
            // ==========================================
            addTask('dataWatcher', () => {
                return watchForDataUpdates();
            });

            window.extractData = sendDataToAndroid;

        })();
    """.trimIndent()
}

