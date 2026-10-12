/**
 * Geolocation Auto-Sender for Pipecat Mobile Bot
 * 
 * This script automatically requests the user's location and sends it to the backend.
 */

(function() {
    'use strict';
    
    console.log('🌍 Geolocation script loaded');
    
    // Generate or retrieve peer connection ID
    let pcId = sessionStorage.getItem('pipecat_pc_id');
    if (!pcId) {
        pcId = 'pc_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
        sessionStorage.setItem('pipecat_pc_id', pcId);
    }
    console.log('📍 PC ID:', pcId);
    
    /**
     * /api/offer 호출을 가로채서 요청에 pc_id를 추가하고 응답의 pc_id를 저장
     */
    const originalFetch = window.fetch;
    window.fetch = async function(...args) {
        const url = args[0];
        const options = args[1] || {};
        
        // /api/offer POST 요청인지 확인
        if (url && typeof url === 'string' && url.includes('/api/offer') && 
            (options.method === 'POST' || !options.method)) {
            // 요청 본문에 pc_id 추가
            try {
                if (options.body) {
                    let bodyData;
                    if (typeof options.body === 'string') {
                        try {
                            bodyData = JSON.parse(options.body);
                        } catch (e) {
                            // JSON이 아니면 그대로 사용
                            bodyData = options.body;
                        }
                    } else if (options.body instanceof FormData) {
                        // FormData는 처리하지 않음
                        bodyData = null;
                    } else {
                        bodyData = options.body;
                    }
                    
                    if (bodyData && typeof bodyData === 'object') {
                        // pc_id가 없으면 추가
                        if (!bodyData.pc_id && !bodyData.pcId && pcId) {
                            bodyData.pc_id = pcId;
                            options.body = JSON.stringify(bodyData);
                            // Content-Type 헤더 확인 및 설정
                            if (!options.headers) {
                                options.headers = {};
                            }
                            if (typeof options.headers === 'object' && !Array.isArray(options.headers)) {
                                options.headers['Content-Type'] = 'application/json';
                            }
                            console.log('✅ Added pc_id to /api/offer request:', pcId);
                        }
                    }
                } else if (pcId) {
                    // body가 없으면 새로 생성 (일반적으로는 발생하지 않음)
                    options.body = JSON.stringify({ pc_id: pcId });
                    // Content-Type 헤더 설정
                    if (!options.headers) {
                        options.headers = {};
                    }
                    if (typeof options.headers === 'object' && !Array.isArray(options.headers)) {
                        options.headers['Content-Type'] = 'application/json';
                    }
                    console.log('✅ Created /api/offer request body with pc_id:', pcId);
                }
            } catch (e) {
                console.warn('Could not modify /api/offer request body:', e);
            }
        }
        
        const response = await originalFetch.apply(this, args);
        
        // /api/offer POST 요청의 응답에서 pc_id 저장
        if (url && typeof url === 'string' && url.includes('/api/offer') && 
            (options.method === 'POST' || !options.method)) {
            // 응답을 복제하여 본문을 읽을 수 있도록 함
            const clonedResponse = response.clone();
            try {
                const data = await clonedResponse.json();
                if (data && data.pc_id) {
                    // 서버에서 반환한 pc_id 저장 (이것이 실제 사용되는 pc_id)
                    pcId = data.pc_id;
                    sessionStorage.setItem('pipecat_pc_id', pcId);
                    console.log('✅ Updated PC ID from /api/offer response:', pcId);
                }
            } catch (e) {
                // JSON 파싱 실패는 무시 (이미 사용된 응답일 수 있음)
                console.debug('Could not parse /api/offer response:', e);
            }
        }
        
        return response;
    };

    let currentSttEnabled = true;
    let currentTtsEnabled = true;
    
    /**
     * Send location to backend
     */
    function sendLocationToBackend(latitude, longitude) {
        const locationData = {
            pc_id: pcId,
            latitude: latitude,
            longitude: longitude,
            timestamp: new Date().toISOString()
        };
        
        console.log('📤 Sending location to backend:', locationData);
        
        fetch('/api/location', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(locationData)
        })
        .then(response => response.json())
        .then(data => {
            console.log('✅ Location sent successfully:', data);
            
            // Store location in sessionStorage for later use
            sessionStorage.setItem('user_latitude', latitude);
            sessionStorage.setItem('user_longitude', longitude);
            
            // Show success message (optional - can be commented out)
            showNotification('위치 정보가 전송되었습니다', 'success');
        })
        .catch(error => {
            console.error('❌ Failed to send location:', error);
            showNotification('위치 정보 전송 실패', 'error');
        });
    }
    
    /**
     * Show notification banner
     */
    function showNotification(message, type = 'info') {
        const notification = document.createElement('div');
        notification.textContent = message;
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 12px 20px;
            background: ${type === 'success' ? '#4CAF50' : type === 'error' ? '#f44336' : '#2196F3'};
            color: white;
            border-radius: 4px;
            box-shadow: 0 2px 5px rgba(0,0,0,0.2);
            z-index: 10000;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            font-size: 14px;
            animation: slideIn 0.3s ease-out;
        `;
        
        document.body.appendChild(notification);
        
        setTimeout(() => {
            notification.style.animation = 'slideOut 0.3s ease-in';
            setTimeout(() => notification.remove(), 300);
        }, 3000);
    }

    /**
     * Send free-form text to the backend for LLM processing.
     */
    async function sendTextToBackend(text, { audioResponse } = {}) {
        const shouldSpeak = audioResponse !== undefined ? audioResponse : currentTtsEnabled;
        const payload = {
            text,
            audio_response: shouldSpeak,
        };
        if (pcId) {
            payload.pc_id = pcId;
        }

        try {
            const response = await fetch('/api/text', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });

            if (!response.ok) {
                const error = await response.json().catch(() => ({}));
                throw new Error(error.detail || error.message || '텍스트 전송 실패');
            }

            const data = await response.json();
            console.log('✅ Text message queued:', data);
            showNotification('텍스트를 전송했습니다.', 'success');
        } catch (error) {
            console.error('❌ Failed to send text:', error);
            showNotification(error.message || '텍스트 전송에 실패했습니다.', 'error');
        }
    }

    /**
     * Toggle STT streaming on or off for the active session.
     */
    async function toggleStt(enabled, { silent = false } = {}) {
        const payload = {
            enabled,
        };
        if (pcId) {
            payload.pc_id = pcId;
        }

        try {
            const response = await fetch('/api/stt', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });

            if (!response.ok) {
                const error = await response.json().catch(() => ({}));
                throw new Error(error.detail || error.message || 'STT 토글 실패');
            }

            const data = await response.json();
            console.log('🎚️ STT toggle response:', data);
            currentSttEnabled = enabled;
            sessionStorage.setItem('pipecat_stt_enabled', String(enabled));
            if (!silent) {
                showNotification(enabled ? '음성 입력이 활성화되었습니다.' : '음성 입력이 비활성화되었습니다.', 'success');
            }
            return true;
        } catch (error) {
            console.error('❌ Failed to toggle STT:', error);
            if (!silent) {
                showNotification(error.message || '음성 입력 상태 변경에 실패했습니다.', 'error');
            }
            throw error;
        }
    }

    /**
     * Toggle TTS responses on or off for the active session.
     */
    async function toggleTts(enabled, { silent = false } = {}) {
        const payload = {
            enabled,
        };
        if (pcId) {
            payload.pc_id = pcId;
        }

        try {
            const response = await fetch('/api/tts', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });

            if (!response.ok) {
                const error = await response.json().catch(() => ({}));
                throw new Error(error.detail || error.message || 'TTS 토글 실패');
            }

            const data = await response.json();
            console.log('🔊 TTS toggle response:', data);
            currentTtsEnabled = enabled;
            sessionStorage.setItem('pipecat_tts_enabled', String(enabled));
            if (!silent) {
                showNotification(enabled ? '음성 출력이 활성화되었습니다.' : '음성 출력이 비활성화되었습니다.', 'success');
            }
            return true;
        } catch (error) {
            console.error('❌ Failed to toggle TTS:', error);
            if (!silent) {
                showNotification(error.message || '음성 출력 상태 변경에 실패했습니다.', 'error');
            }
            throw error;
        }
    }

    /**
     * Create UI controls for text input and STT/TTS toggling.
     * Renders as a fixed input bar above the bottom bar.
     */
    function createControlPanel() {
        if (document.getElementById('pipecat-control-panel')) {
            return;
        }

        const panel = document.createElement('div');
        panel.id = 'pipecat-control-panel';
        
        // Load saved position or use default
        const savedPosition = sessionStorage.getItem('pipecat_panel_position');
        let initialStyle = '';
        if (savedPosition) {
            try {
                const pos = JSON.parse(savedPosition);
                const savedWidth = pos.width || 'min(440px, 90vw)';
                initialStyle = `
                    left: ${pos.left}px;
                    top: ${pos.top}px;
                    right: auto;
                    bottom: auto;
                    width: ${savedWidth};
                `;
            } catch (e) {
                // Use default if parsing fails
            }
        }
        
        if (!initialStyle) {
            initialStyle = `
                left: 0;
                right: 0;
                bottom: calc(env(safe-area-inset-bottom) + 80px);
                width: 100%;
            `;
        }
        
        panel.style.cssText = `
            position: fixed;
            ${initialStyle}
            padding: 14px 16px;
            background: rgba(20, 24, 28, 0.95);
            border: 1px solid rgba(255,255,255,0.1);
            border-radius: 12px;
            color: #f5f5f5;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            z-index: 10002;
            backdrop-filter: blur(20px);
            box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.3);
            cursor: move;
            user-select: none;
        `;

        panel.innerHTML = `
            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; cursor: move;">
                <div style="display: flex; align-items: center; gap: 8px; flex: 1; cursor: move;">
                    <span style="color: #888; font-size: 12px;">⋮⋮</span>
                    <span style="color: #aaa; font-size: 13px;"></span>
                </div>
                <button
                    id="pipecat-control-close"
                    style="
                        background: none;
                        border: none;
                        color: #bbb;
                        font-size: 20px;
                        cursor: pointer;
                        padding: 4px 8px;
                        line-height: 1;
                        border-radius: 4px;
                    "
                    title="닫기"
                >×</button>
            </div>
            <div style="display: flex; flex-direction: column; gap: 10px;">
                <form id="pipecat-text-form" style="display: flex; gap: 8px; align-items: center;">
                    <input
                        id="pipecat-text-input"
                        type="text"
                        placeholder="텍스트로 질문해 보세요"
                        autocomplete="off"
                        style="
                            flex: 1;
                            padding: 10px 14px;
                            border-radius: 24px;
                            border: 1px solid rgba(255,255,255,0.15);
                            background: rgba(255,255,255,0.1);
                            color: #f5f5f5;
                            font-size: 15px;
                            outline: none;
                        "
                    />
                    <button
                        type="submit"
                        style="
                            padding: 10px 20px;
                            border: none;
                            border-radius: 24px;
                            background: #4F8CFF;
                            color: #fff;
                            font-weight: 600;
                            cursor: pointer;
                            white-space: nowrap;
                            font-size: 15px;
                        "
                    >전송</button>
                </form>
                <div style="display: flex; gap: 8px; align-items: center; justify-content: center;">
                    <button
                        id="pipecat-stt-toggle"
                        title="음성 입력"
                        style="
                            width: 40px;
                            height: 40px;
                            border-radius: 50%;
                            border: 1px solid rgba(255,255,255,0.2);
                            background: rgba(79,140,255,0.2);
                            color: #4F8CFF;
                            font-size: 18px;
                            cursor: pointer;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            flex-shrink: 0;
                        "
                    >🎤</button>
                    <button
                        id="pipecat-tts-toggle"
                        title="음성 출력"
                        style="
                            width: 40px;
                            height: 40px;
                            border-radius: 50%;
                            border: 1px solid rgba(255,255,255,0.2);
                            background: rgba(79,140,255,0.2);
                            color: #4F8CFF;
                            font-size: 18px;
                            cursor: pointer;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            flex-shrink: 0;
                        "
                    >🔊</button>
                </div>
            </div>
        `;

        document.body.appendChild(panel);

        const textForm = panel.querySelector('#pipecat-text-form');
        const textInput = panel.querySelector('#pipecat-text-input');
        const sttToggle = panel.querySelector('#pipecat-stt-toggle');
        const ttsToggle = panel.querySelector('#pipecat-tts-toggle');
        const closeButton = panel.querySelector('#pipecat-control-close');
        
        // Drag functionality
        let isDragging = false;
        let dragStartX = 0;
        let dragStartY = 0;
        let initialLeft = 0;
        let initialTop = 0;
        let initialWidth = '';
        
        function getPanelPosition() {
            const rect = panel.getBoundingClientRect();
            return {
                left: rect.left,
                top: rect.top,
                width: panel.style.width || 'min(440px, 90vw)'
            };
        }
        
        function savePanelPosition() {
            const pos = getPanelPosition();
            sessionStorage.setItem('pipecat_panel_position', JSON.stringify(pos));
        }
        
        function startDrag(e) {
            // Don't start drag if clicking on input, button, or close button
            if (e.target.tagName === 'INPUT' || 
                e.target.tagName === 'BUTTON' || 
                e.target.closest('form') ||
                e.target.id === 'pipecat-control-close') {
                return;
            }
            
            isDragging = true;
            const pos = getPanelPosition();
            initialLeft = pos.left;
            initialTop = pos.top;
            initialWidth = panel.style.width || 'min(440px, 90vw)';
            
            const clientX = e.touches ? e.touches[0].clientX : e.clientX;
            const clientY = e.touches ? e.touches[0].clientY : e.clientY;
            dragStartX = clientX - initialLeft;
            dragStartY = clientY - initialTop;
            
            panel.style.cursor = 'grabbing';
            panel.style.transition = 'none';
            e.preventDefault();
        }
        
        function onDrag(e) {
            if (!isDragging) return;
            
            const clientX = e.touches ? e.touches[0].clientX : e.clientX;
            const clientY = e.touches ? e.touches[0].clientY : e.clientY;
            
            let newLeft = clientX - dragStartX;
            let newTop = clientY - dragStartY;
            
            // Constrain to viewport
            const maxLeft = window.innerWidth - panel.offsetWidth;
            const maxTop = window.innerHeight - panel.offsetHeight;
            
            newLeft = Math.max(0, Math.min(newLeft, maxLeft));
            newTop = Math.max(0, Math.min(newTop, maxTop));
            
            panel.style.left = `${newLeft}px`;
            panel.style.top = `${newTop}px`;
            panel.style.right = 'auto';
            panel.style.bottom = 'auto';
            // 드래그 중에는 현재 width 유지
            if (initialWidth) {
                panel.style.width = initialWidth;
            }
            
            e.preventDefault();
        }
        
        function endDrag() {
            if (isDragging) {
                isDragging = false;
                panel.style.cursor = 'move';
                panel.style.transition = '';
                savePanelPosition();
            }
        }
        
        // Add drag event listeners
        panel.addEventListener('mousedown', startDrag);
        panel.addEventListener('touchstart', startDrag, { passive: false });
        
        document.addEventListener('mousemove', onDrag);
        document.addEventListener('touchmove', onDrag, { passive: false });
        
        document.addEventListener('mouseup', endDrag);
        document.addEventListener('touchend', endDrag);
        
        // Close button functionality
        closeButton.addEventListener('click', (e) => {
            e.stopPropagation();
            panel.style.display = 'none';
            sessionStorage.setItem('pipecat_panel_visible', 'false');
            
            // Create floating button to show panel again
            createShowPanelButton();
        });
        
        // Prevent drag when interacting with form elements
        textInput.addEventListener('mousedown', (e) => e.stopPropagation());
        textInput.addEventListener('touchstart', (e) => e.stopPropagation());
        textForm.querySelector('button[type="submit"]').addEventListener('mousedown', (e) => e.stopPropagation());
        sttToggle.addEventListener('mousedown', (e) => e.stopPropagation());
        ttsToggle.addEventListener('mousedown', (e) => e.stopPropagation());

        textForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const value = textInput.value.trim();
            if (!value) {
                showNotification('텍스트를 입력해주세요.', 'error');
                return;
            }
            await sendTextToBackend(value);
            textInput.value = '';
        });

        // Update button style based on enabled state
        function updateSttButtonStyle(enabled) {
            if (enabled) {
                sttToggle.style.background = 'rgba(79,140,255,0.3)';
                sttToggle.style.borderColor = '#4F8CFF';
                sttToggle.style.opacity = '1';
            } else {
                sttToggle.style.background = 'rgba(255,255,255,0.05)';
                sttToggle.style.borderColor = 'rgba(255,255,255,0.1)';
                sttToggle.style.opacity = '0.5';
            }
        }

        function updateTtsButtonStyle(enabled) {
            if (enabled) {
                ttsToggle.style.background = 'rgba(79,140,255,0.3)';
                ttsToggle.style.borderColor = '#4F8CFF';
                ttsToggle.style.opacity = '1';
            } else {
                ttsToggle.style.background = 'rgba(255,255,255,0.05)';
                ttsToggle.style.borderColor = 'rgba(255,255,255,0.1)';
                ttsToggle.style.opacity = '0.5';
            }
        }

        const savedState = sessionStorage.getItem('pipecat_stt_enabled');
        const initialSttEnabled = savedState === null ? true : savedState === 'true';
        currentSttEnabled = initialSttEnabled;
        updateSttButtonStyle(initialSttEnabled);

        sttToggle.addEventListener('click', async () => {
            const newEnabled = !currentSttEnabled;
            // Optimistically update UI
            updateSttButtonStyle(newEnabled);
            try {
                await toggleStt(newEnabled, { silent: true });
                // toggleStt already updates currentSttEnabled
            } catch (error) {
                // Revert UI state if the request fails
                updateSttButtonStyle(currentSttEnabled);
            }
        });

        const savedTts = sessionStorage.getItem('pipecat_tts_enabled');
        const initialTtsEnabled = savedTts === null ? true : savedTts === 'true';
        currentTtsEnabled = initialTtsEnabled;
        updateTtsButtonStyle(initialTtsEnabled);

        ttsToggle.addEventListener('click', async () => {
            const newEnabled = !currentTtsEnabled;
            // Optimistically update UI
            updateTtsButtonStyle(newEnabled);
            try {
                await toggleTts(newEnabled, { silent: true });
                // toggleTts already updates currentTtsEnabled
            } catch (error) {
                // Revert UI state if the request fails
                updateTtsButtonStyle(currentTtsEnabled);
            }
        });
    }
    
    /**
     * Create a floating button to show the panel when it's hidden
     */
    function createShowPanelButton() {
        // Remove existing button if any
        const existingButton = document.getElementById('pipecat-show-panel-button');
        if (existingButton) {
            return;
        }
        
        const button = document.createElement('button');
        button.id = 'pipecat-show-panel-button';
        button.textContent = '＋';
        button.title = '입력창 보이기';
        button.style.cssText = `
            position: fixed;
            bottom: calc(env(safe-area-inset-bottom) + 20px);
            right: 20px;
            width: 54px;
            height: 54px;
            border-radius: 50%;
            background: #4F8CFF;
            color: #fff;
            border: none;
            box-shadow: 0 12px 25px rgba(79,140,255,0.35);
            font-size: 32px;
            line-height: 1;
            cursor: pointer;
            z-index: 10003;
            display: flex;
            align-items: center;
            justify-content: center;
        `;
        
        button.addEventListener('click', () => {
            const panel = document.getElementById('pipecat-control-panel');
            if (panel) {
                panel.style.display = 'block';
                sessionStorage.setItem('pipecat_panel_visible', 'true');
                button.remove();
            }
        });
        
        document.body.appendChild(button);
    }
    
    /**
     * Request geolocation permission and get location
     */
    function requestLocation() {
        console.log('📍 Requesting geolocation...');
        
        if (!navigator.geolocation) {
            console.error('❌ Geolocation is not supported by this browser');
            showNotification('이 브라우저는 위치 정보를 지원하지 않습니다', 'error');
            return;
        }
        
        // Show loading state
        showNotification('위치 정보를 가져오는 중...', 'info');
        
        navigator.geolocation.getCurrentPosition(
            // Success callback
            (position) => {
                const latitude = position.coords.latitude;
                const longitude = position.coords.longitude;
                const accuracy = position.coords.accuracy;
                
                console.log('✅ Location obtained:', {
                    latitude,
                    longitude,
                    accuracy: `${accuracy.toFixed(0)}m`
                });
                
                // Send to backend
                sendLocationToBackend(latitude, longitude);
            },
            // Error callback
            (error) => {
                console.error('❌ Geolocation error:', error);
                
                let errorMessage = '';
                switch(error.code) {
                    case error.PERMISSION_DENIED:
                        errorMessage = '위치 정보 접근이 거부되었습니다';
                        console.error('User denied geolocation permission');
                        break;
                    case error.POSITION_UNAVAILABLE:
                        errorMessage = '위치 정보를 사용할 수 없습니다';
                        console.error('Location information unavailable');
                        break;
                    case error.TIMEOUT:
                        errorMessage = '위치 정보 요청 시간이 초과되었습니다';
                        console.error('Geolocation request timeout');
                        break;
                    default:
                        errorMessage = '위치 정보를 가져올 수 없습니다';
                        console.error('Unknown geolocation error');
                }
                
                showNotification(errorMessage, 'error');
            },
            // Options
            {
                enableHighAccuracy: true,  // Use GPS if available
                timeout: 10000,            // 10 second timeout
                maximumAge: 60000          // Accept 1 minute old cached position
            }
        );
    }
    
    /**
     * Add CSS animations
     */
    const style = document.createElement('style');
    style.textContent = `
        @keyframes slideIn {
            from {
                transform: translateX(400px);
                opacity: 0;
            }
            to {
                transform: translateX(0);
                opacity: 1;
            }
        }
        @keyframes slideOut {
            from {
                transform: translateX(0);
                opacity: 1;
            }
            to {
                transform: translateX(400px);
                opacity: 0;
            }
        }
    `;
    document.head.appendChild(style);
    
    /**
     * Initialize UI components and set up Connect button listener.
     */
    function initializeEnhancements() {
        console.log('📄 DOM ready, initializing enhancements...');
        // 텍스트 입력 패널(createControlPanel)과 우하단 플로팅 버튼
        // (createShowPanelButton)은 하단 AskBar로 대체돼 더 이상 생성하지 않는다.
        // 위치 전송(Connect 버튼 리스너)만 유지한다.
        setupConnectButtonListener();
    }
    
    /**
     * Connect 버튼 클릭 이벤트 리스너 설정
     */
    function setupConnectButtonListener() {
        // 여러 방법으로 Connect 버튼 찾기 시도
        function findAndAttachListener() {
            // 방법 1: 일반적인 Connect 버튼 선택자들
            const selectors = [
                'button:contains("Connect")',
                'button[aria-label*="onnect"]',
                'button[aria-label*="연결"]',
                '[data-testid*="connect"]',
                '[class*="connect"]',
                '[id*="connect"]',
            ];
            
            // 방법 2: 모든 버튼을 확인하여 텍스트로 찾기
            const allButtons = document.querySelectorAll('button');
            let connectButton = null;
            
            for (const button of allButtons) {
                const text = button.textContent?.toLowerCase() || '';
                const ariaLabel = button.getAttribute('aria-label')?.toLowerCase() || '';
                if (text.includes('connect') || text.includes('연결') || 
                    ariaLabel.includes('connect') || ariaLabel.includes('연결')) {
                    connectButton = button;
                    break;
                }
            }
            
            // 방법 3: rtvi-ai 라이브러리의 연결 이벤트 감지
            // rtvi-ai는 연결 시작 시 이벤트를 발생시킬 수 있음
            if (window.rtviClient || window.PipecatClient) {
                const client = window.rtviClient || window.PipecatClient;
                if (client && typeof client.on === 'function') {
                    client.on('connecting', () => {
                        console.log('🔗 Connection starting, requesting location and syncing STT/TTS...');
                        onConnectButtonClicked();
                    });
                }
            }
            
            // 방법 4: MutationObserver로 동적으로 추가되는 버튼 감지
            if (!connectButton) {
                const observer = new MutationObserver((mutations) => {
                    for (const button of document.querySelectorAll('button')) {
                        const text = button.textContent?.toLowerCase() || '';
                        const ariaLabel = button.getAttribute('aria-label')?.toLowerCase() || '';
                        if ((text.includes('connect') || text.includes('연결') || 
                             ariaLabel.includes('connect') || ariaLabel.includes('연결')) &&
                            !button.hasAttribute('data-location-listener-attached')) {
                            attachLocationRequest(button);
                        }
                    }
                });
                
                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });
                
                // 초기 검색도 수행
                for (const button of allButtons) {
                    const text = button.textContent?.toLowerCase() || '';
                    const ariaLabel = button.getAttribute('aria-label')?.toLowerCase() || '';
                    if (text.includes('connect') || text.includes('연결') || 
                        ariaLabel.includes('connect') || ariaLabel.includes('연결')) {
                        attachLocationRequest(button);
                        break;
                    }
                }
            } else {
                attachLocationRequest(connectButton);
            }
        }
        
        function attachLocationRequest(button) {
            if (button.hasAttribute('data-location-listener-attached')) {
                return; // 이미 리스너가 붙어있음
            }
            
            button.setAttribute('data-location-listener-attached', 'true');
            button.addEventListener('click', () => {
                console.log('🔗 Connect button clicked, requesting location and syncing STT/TTS...');
                onConnectButtonClicked();
            }, { once: false }); // 여러 번 클릭 가능하도록 once: false
        }
        
        /**
         * Connect 버튼 클릭 시 실행할 함수
         * 위치 정보 요청 및 STT/TTS 상태 동기화
         */
        function onConnectButtonClicked() {
            // 위치 정보 요청
            requestLocation();
            
            // STT/TTS 상태 동기화
            const savedStt = sessionStorage.getItem('pipecat_stt_enabled');
            if (savedStt !== null) {
                const enabled = savedStt === 'true';
                currentSttEnabled = enabled;
                toggleStt(enabled, { silent: true }).catch(() => {});
            }
            
            const savedTts = sessionStorage.getItem('pipecat_tts_enabled');
            if (savedTts !== null) {
                const enabled = savedTts === 'true';
                currentTtsEnabled = enabled;
                toggleTts(enabled, { silent: true }).catch(() => {});
            }
        }
        
        // DOM이 준비되면 즉시 시도
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', findAndAttachListener);
        } else {
            findAndAttachListener();
        }
        
        // 추가로 약간의 지연 후에도 시도 (동적으로 추가되는 버튼 대비)
        setTimeout(findAndAttachListener, 500);
        setTimeout(findAndAttachListener, 2000);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeEnhancements);
    } else {
        initializeEnhancements();
    }
    
    // Expose function to manually request location (useful for debugging)
    window.requestLocation = requestLocation;
    console.log('💡 Tip: You can manually request location by calling window.requestLocation()');
    
})();

