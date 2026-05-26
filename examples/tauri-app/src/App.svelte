<script lang="ts">
  import { path } from '@tauri-apps/api';
  import { join } from '@tauri-apps/api/path';
  import { convertFileSrc } from '@tauri-apps/api/core';
  import { onDestroy } from 'svelte';
  import { 
    checkPermissions,
    requestPermissions,
    record,
    stop,
    pause,
    resume,
    getStatus,
    OutputFormat,
    AudioEncoder,
  } from 'tauri-plugin-audio-recorder-android-api';

  // --- States (Svelte 5 Runes) ---
  let logs = $state<Array<{time: string, action: string, data: string}>>([]);
  let isRecording = $state(false);
  let isPausedState = $state(false);
  let amplitude = $state(0);
  let maxObservedAmplitude = $state(0); // Test boyunca ulaşılan en yüksek ses seviyesi
  let intervalId = $state<any>(null);
  let lastRecordedPath = $state<string | null>(null);
  
  // Parametreler (bind:value ile çift yönlü bağlandı)
  let bitRate = $state(128000);
  let sampleRate = $state(16000); // Chromium soket hatası için varsayılanı 16000 yaptım, istersen 44100 dene
  let channels = $state(1);
  
  // Sayaç Durumu
  let recordDuration = $state(0);
  
  // Playback States
  let isPlaying = $state(false);
  let audioPlayer = $state<HTMLAudioElement | null>(null);

  // Visualizer yüksekliği (Svelte 5 Derived)
  let visualizerHeight = $derived(Math.min(100, (amplitude / 32767) * 100));

  function addLog(action: string, result: any) {
    logs = [{
      time: new Date().toLocaleTimeString(),
      action,
      data: typeof result === 'string' ? result : JSON.stringify(result)
    }, ...logs];
  }

  async function handleRecord() {
    try {
      maxObservedAmplitude = 0;
      recordDuration = 0;
      
      // Android çökmesini engellemek için wav veya mp4 formatına uygun isimlendirme
      const fileName = `rec_${Date.now()}.wav`;
      const cacheDir = await path.downloadDir();
      const fullPath = await join(cacheDir, fileName);
      
      addLog("Config sending...", { bitRate, sampleRate, channels, fullPath });

      const res = await record({
        fileName: fullPath,
        format: OutputFormat.MPEG_4,
        encoder: AudioEncoder.AAC,
        bitRate: Number(bitRate),
        sampleRate: Number(sampleRate),
        channels: Number(channels)
      });
      
      addLog("Start Record Response", res);
      
      console.log(res)
      if (res.success) {
        isRecording = true;
        isPausedState = false;
        lastRecordedPath = fullPath;
        
        // CANLI TAKİP ENGINE (200ms'de bir durumu sorgular)
        intervalId = setInterval(async () => {
          try {
            const s = await getStatus();
            amplitude = s.maxAmplitude || 0;
            if (amplitude > maxObservedAmplitude) maxObservedAmplitude = amplitude;
            
            isRecording = s.isRecording;
            
            if (isRecording && !isPausedState) {
              recordDuration += 0.2;
            }
            
            // Eğer servis arka planda bizden habersiz durduysa (soket patlaması vs.) UI'ı temizle
            if (!s.isRecording) {
              addLog("System Info", "Kayıt servis tarafından otomatik durduruldu (Soket/Timeout).");
              cleanInterval();
            }
          } catch (e: any) {
            addLog("Interval Status Error", e.message);
          }
        }, 200);
      }
    } catch (err: any) {
      addLog("Record Exception", err.message);
    }
  }

  function cleanInterval() {
    isRecording = false;
    if (intervalId) clearInterval(intervalId);
    intervalId = null;
    amplitude = 0;
  }

  async function handleStop() {
    try {
      const res = await stop();
      addLog("Stop Record Response", res);
      if (res.currentPath) lastRecordedPath = res.currentPath;
    } catch (err: any) {
      addLog("Stop Exception", err.message);
    } finally {
      cleanInterval();
    }
  }

  async function handlePlay() {
    if (!lastRecordedPath) return;

    try {
      let assetUrl = convertFileSrc(lastRecordedPath);
      assetUrl = decodeURIComponent(assetUrl);

      if (assetUrl.includes("localhost//")) {
        assetUrl = assetUrl.replace("localhost//", "localhost/");
      }

      addLog("Playing Asset URL", assetUrl);

      if (audioPlayer) {
        audioPlayer.pause();
        audioPlayer.src = "";
        audioPlayer.load();
      }

      const audio = new Audio(assetUrl);
      audioPlayer = audio;
      audio.preload = "auto";
      
      audio.onplay = () => isPlaying = true;
      audio.onpause = () => isPlaying = false;
      audio.onended = () => isPlaying = false;
      
      audio.onerror = () => {
        const err = audio.error;
        addLog("Media Playback Error", `Kod: ${err?.code} - ${err?.message}`);
        isPlaying = false;
      };

      await audio.play();
    } catch (err: any) {
      addLog("Playback Exception", err.message);
    }
  }

  async function handlePause() { 
    const res = await pause(); 
    addLog("Pause Action", res);
    if(res.success) isPausedState = true;
  }
  
  async function handleResume() { 
    const res = await resume(); 
    addLog("Resume Action", res);
    if(res.success) isPausedState = false;
  }

  async function handleCheck() { addLog("Permission Check", await checkPermissions()); }
  async function handleRequest() { addLog("Permission Request", await requestPermissions()); }

  async function handleManualStatus() {
    try {
      const s = await getStatus();
      addLog("Manual Status Result", {
        isRecording: s.isRecording,
        maxAmplitude: s.maxAmplitude,
        currentPath: s.currentPath
      });
      amplitude = s.maxAmplitude || 0;
      isRecording = s.isRecording;
    } catch (err: any) {
      addLog("Manual Status Error", err.message);
    }
  }

  function handleClearLogs() { logs = []; }

  onDestroy(() => {
    if (intervalId) clearInterval(intervalId);
    if (audioPlayer) audioPlayer.pause();
  });
</script>

<main class="container">
  <div class="visualizer">
    <div class="bar" style="height: {visualizerHeight}%"></div>
    <div class="overlay-info">
      <p class={isRecording ? (isPausedState ? 'paused' : 'recording') : ''}>
        {isRecording ? (isPausedState ? '⏸ PAUSED' : '● RECORDING') : '○ STANDBY'}
      </p>
      <div class="timer">{recordDuration.toFixed(1)}s</div>
    </div>
    <div class="amp-badge">Amp: {amplitude} / Max: {maxObservedAmplitude}</div>
  </div>

  <div class="controls">
    <div class="group inputs-panel">
      <label>
        <span>Bitrate (bps)</span>
        <input type="number" bind:value={bitRate} step="32000">
      </label>
      <label>
        <span>Sample Rate (Hz)</span>
        <input type="number" bind:value={sampleRate} step="4000">
      </label>
      <label>
        <span>Channels</span>
        <input type="number" bind:value={channels} min="1" max="2">
      </label>
    </div>

    <div class="group">
      <button onclick={handleCheck} class="secondary">Check Perms</button>
      <button onclick={handleRequest} class="secondary">Request Perms</button>
    </div>

    <div class="group main-actions">
      <button onclick={handleRecord} disabled={isRecording} class="start">▶ START RECORD</button>
      <button onclick={handleStop} disabled={!isRecording} class="stop">■ STOP</button>
    </div>
    
    <div class="group">
      <button onclick={handlePause} disabled={!isRecording || isPausedState} class="pause-btn">Pause</button>
      <button onclick={handleResume} disabled={!isRecording || !isPausedState} class="resume-btn">Resume</button>
    </div>

    <div class="group">
      <button 
        onclick={handlePlay} 
        disabled={isRecording || !lastRecordedPath} 
        class="play-btn"
      >
        {isPlaying ? '🔊 Playing...' : '🎵 Play Last Recording'}
      </button>
    </div>
    
    <div class="group utility-actions">
      <button onclick={handleManualStatus}>🔍 Get Status</button>
      <button onclick={handleClearLogs} class="clear-btn">🧹 Clear Logs</button>
    </div>
  </div>

  {#if lastRecordedPath}
    <div class="path-badge">
      <strong>Last Path:</strong> <span>{lastRecordedPath}</span>
    </div>
  {/if}

  <div class="log-view">
    {#each logs as log}
      <div class="log-item">
        <small>{log.time}</small> <strong class="action-tag">{log.action}:</strong> <code>{log.data}</code>
      </div>
    {/each}
  </div>
</main>

<style>
  :global(body) { background: #0f111a; color: #e2e8f0; font-family: 'Segoe UI', system-ui, sans-serif; margin: 0; padding: 10px; }
  .container { max-width: 480px; margin: 0 auto; padding: 15px; text-align: center; background: #1a1d29; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
  
  .visualizer { 
    height: 140px; 
    background: #090b11; 
    margin-bottom: 16px; 
    display: flex; 
    flex-direction: column; 
    align-items: center; 
    justify-content: flex-end; 
    border-radius: 12px; 
    overflow: hidden; 
    position: relative; 
    border: 1px solid #2d3142;
  }
  
  .bar { width: 100%; background: linear-gradient(to top, #00ff88, #00bfff); transition: height 0.08s ease; opacity: 0.8; }
  
  .overlay-info { position: absolute; top: 12px; left: 12px; right: 12px; display: flex; justify-content: space-between; align-items: center; }
  .visualizer p { margin: 0; font-size: 12px; font-weight: bold; color: #565f89; background: rgba(0,0,0,0.4); padding: 4px 8px; border-radius: 6px; }
  .visualizer p.recording { color: #ff4757; animation: blink 1s infinite; background: rgba(255, 71, 87, 0.15); }
  .visualizer p.paused { color: #ffa502; background: rgba(255, 165, 2, 0.15); }
  
  .timer { font-size: 18px; font-weight: 700; color: #00ff88; font-family: monospace; }
  .amp-badge { position: absolute; bottom: 8px; right: 12px; font-size: 11px; color: #717c9b; font-family: monospace; background: rgba(0,0,0,0.5); padding: 2px 6px; border-radius: 4px; }
  
  .controls { display: flex; flex-direction: column; gap: 10px; }
  .group { display: flex; gap: 10px; }
  
  button { 
    flex: 1; padding: 12px; cursor: pointer; background: #242b3d; color: #fff; border: 1px solid #3b4261; border-radius: 8px; font-weight: 600; font-size: 13px; transition: all 0.15s ease;
  }
  button:active { transform: scale(0.97); }
  button:disabled { opacity: 0.15; cursor: not-allowed; transform: none; }
  
  .main-actions button { padding: 16px; font-size: 14px; }
  .start { background: #10b981; border-color: #059669; text-shadow: 0 1px 2px rgba(0,0,0,0.3); }
  .stop { background: #ef4444; border-color: #dc2626; }
  .play-btn { background: #6366f1; border-color: #4f46e5; padding: 14px; font-size: 14px; }
  .secondary { background: #1e2235; color: #a0aec0; }
  .clear-btn { color: #ff79c6; border-color: rgba(255,121,198,0.3); }
  
  .pause-btn { background: #b45309; border-color: #92400e; }
  .resume-btn { background: #0369a1; border-color: #075985; }

  .inputs-panel { background: #131622; padding: 10px; border-radius: 10px; border: 1px solid #23283b; }
  .inputs-panel label { flex: 1; display: flex; flex-direction: column; align-items: flex-start; gap: 4px; font-size: 11px; color: #7982a9; font-weight: bold; }
  .inputs-panel input { width: 100%; padding: 8px; background: #1e2235; color: #00ff88; border: 1px solid #2d3142; border-radius: 6px; font-size: 13px; font-weight: 600; outline: none; box-sizing: border-box; }
  .inputs-panel input:focus { border-color: #00ff88; }

  .path-badge { margin-top: 10px; background: #131622; border: 1px solid #23283b; padding: 8px; border-radius: 6px; font-size: 11px; text-align: left; display: flex; flex-direction: column; gap: 2px; }
  .path-badge span { color: #38bdf8; word-break: break-all; font-family: monospace; }

  .log-view { margin-top: 12px; height: 260px; overflow-y: auto; background: #090b11; padding: 10px; font-size: 11px; border-radius: 8px; text-align: left; border: 1px solid #23283b; font-family: 'Courier New', monospace; }
  .log-item { border-bottom: 1px solid #161925; padding: 5px 0; display: flex; flex-direction: column; gap: 2px; }
  .log-item small { color: #6272a4; }
  .action-tag { color: #ffb86c; }
  code { color: #50fa7b; word-break: break-all; background: rgba(80,250,123,0.05); padding: 2px; border-radius: 3px; }

  @keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
</style>