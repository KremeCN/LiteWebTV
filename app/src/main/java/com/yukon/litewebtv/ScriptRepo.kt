package com.yukon.litewebtv

object ScriptRepo {

    /**
     * 核心自动化脚本合集
     * 更新日志：
     * 1. [修复] 频道过滤逻辑：同时剔除 "VIP" 和 "限免" 频道
     * 2. [新增] 视频播放状态监测 -> 触发智能幕布
     */
    val AUTOMATION_SCRIPT = """
        (function() {
            
            // 【防御模块】强制禁用页面上所有输入框
            function disableAllInputs() {
                const inputs = document.querySelectorAll('input, textarea, [contenteditable="true"]');
                inputs.forEach(el => {
                    el.setAttribute('disabled', 'true');
                    el.setAttribute('readonly', 'true');
                    el.blur(); 
                });
            }

            // 【智能幕布触发源】持续监测视频是否真的开始播放了
            setInterval(() => {
                const video = document.querySelector('video');
                if (video) {
                    // readyState: 4=HAVE_ENOUGH_DATA
                    // currentTime > 0.1 确保视频已经走了一点点
                    if (!video.paused && video.readyState >= 3 && video.currentTime > 0.1) {
                        if (window.Android && window.Android.dismissSplash) {
                            window.Android.dismissSplash();
                        }
                    }
                }
            }, 500);

            // 定义向 Android 发送数据的函数
            function sendDataToAndroid() {
                disableAllInputs();

                // --- A. 提取频道列表 (严厉过滤版) ---
                const channelItems = document.querySelectorAll('.tv-main-con-r-list-left .oveerflow-1');
                if (channelItems.length > 0) {
                    let channelList = [];
                    channelItems.forEach((item, index) => {
                        const span = item.querySelector('span');
                        let name = "未知频道";
                        let isRestricted = false; // 是否受限（VIP或限免）
                        
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
                                name = fullText.trim();
                                
                                channelList.push({
                                    index: index, // 这里的 index 是网页 DOM 的原始索引，点击必须用这个
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

            // ==========================================
            // 1. 网页加载监测器
            // ==========================================
            const checkTimer = setInterval(() => {
                const playerContainer = document.querySelector('.container');
                if (playerContainer) {
                    playerContainer.dispatchEvent(new MouseEvent('mousemove', {bubbles: true}));
                }
                disableAllInputs();

                const hasPlayer = document.querySelector('#vodbox2024078201') || document.querySelector('.c-container') || document.querySelector('.video-con');
                const hasChannelList = document.querySelectorAll('.tv-main-con-r-list-left .oveerflow-1').length > 0;
                const hasProgramList = document.querySelectorAll('.tv-zhan-list-b-r .tv-zhan-list-b-r-item').length > 0;
                const titleEl = document.querySelector('.tv-zhan-title');
                const hasTitle = titleEl && titleEl.textContent.trim().length > 0;

                if (hasPlayer && hasChannelList && hasProgramList && hasTitle) {
                    clearInterval(checkTimer);
                    sendDataToAndroid();
                }
            }, 200);

            // ==========================================
            // 2. 画质 (自动选 1080P)
            // ==========================================
            const qualityIntervalId = setInterval(() => {
                document.querySelector('.container')?.dispatchEvent(new MouseEvent('mousemove', {bubbles: true}));
                const targetQuality = "1080P";
                const qualityItems = document.querySelectorAll('.bei-list-inner .item');
                if (qualityItems.length > 0) {
                    for (const item of qualityItems) {
                        const text = item.textContent.trim();
                        if (text.includes(targetQuality)) {
                            if (!item.classList.contains('active')) {
                                item.click();
                            }
                            clearInterval(qualityIntervalId);
                            break;
                        }
                    }
                }
            }, 500);

            // ==========================================
            // 3. 声音 (自动取消静音)
            // ==========================================
            const soundIntervalId = setInterval(() => {
                document.querySelector('.container')?.dispatchEvent(new MouseEvent('mousemove', {bubbles: true}));
                const muteBtn = document.querySelector('.voice.off');
                if (muteBtn) {
                    if (window.getComputedStyle(muteBtn).display !== 'none') {
                        muteBtn.click();
                        clearInterval(soundIntervalId);
                    } else {
                        clearInterval(soundIntervalId);
                    }
                }
            }, 500);

            // ==========================================
            // 4. 全屏 (强制样式覆盖)
            // ==========================================
            const fsIntervalId = setInterval(() => {
                const playerContainer = document.querySelector('#vodbox2024078201') || document.querySelector('.c-container') || document.querySelector('.video-con');
                if (playerContainer) {
                    if (playerContainer.style.position === 'fixed') {
                         if(playerContainer.style.zIndex !== '99999') playerContainer.style.setProperty('z-index', '99999', 'important');
                         clearInterval(fsIntervalId);
                         return;
                    }
                    playerContainer.style.cssText = 'position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 99999 !important; background-color: black !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important;';
                    const videoTag = playerContainer.querySelector('video');
                    if (videoTag) videoTag.style.cssText = 'width: 100% !important; height: 100% !important; object-fit: contain !important;';
                    const sideBar = document.querySelector('.tv-main-con-r');
                    if (sideBar) sideBar.style.display = 'none';
                }
            }, 500);

            // ==========================================
            // 5. 播放 (自动点击播放按钮)
            // ==========================================
            const playIntervalId = setInterval(() => {
                document.querySelector('.container')?.dispatchEvent(new MouseEvent('mousemove', {bubbles: true}));
                const startPlayBtn = document.querySelector('.y-full-control-btnl .play.play1');
                const playingBtn = document.querySelector('.y-full-control-btnl .play.play2');
                if (startPlayBtn && window.getComputedStyle(startPlayBtn).display !== 'none') {
                        startPlayBtn.click();
                        clearInterval(playIntervalId);
                } else if (playingBtn && window.getComputedStyle(playingBtn).display !== 'none') {
                        clearInterval(playIntervalId);
                }
            }, 500);
            
            window.extractData = sendDataToAndroid;

        })();
    """.trimIndent()
}