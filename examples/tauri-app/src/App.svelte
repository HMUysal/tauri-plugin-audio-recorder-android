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
  let amplitude = $state(0);
  let intervalId = $state<any>(null);
  let lastRecordedPath = $state<string | null>(null);
  let bitRate = $state(128000);
  let sampleRate = $state(44100);
  let channels = $state(1);
  
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
      // Hata 4 almamak için mutlaka .mp4 ekliyoruz
      const fileName = `rec_${Date.now()}.mp4`;
      const cacheDir = await path.downloadDir();
      const fullPath = await join(cacheDir, fileName);
      
      const res = await record({
        fileName: fullPath,
        format: OutputFormat.MPEG_4,
        encoder: AudioEncoder.AAC,
        bitRate,
        sampleRate,
        channels
      });
      
      addLog("Start Record", res);
      
      if (res.success) {
        isRecording = true;
        lastRecordedPath = fullPath;
        
        intervalId = setInterval(async () => {
          const s = await getStatus();
          amplitude = s.maxAmplitude || 0;
          isRecording = s.isRecording;
          if(!s.isRecording) handleStop();
        }, 200);
      }
    } catch (err: any) {
      addLog("Record Error", err.message);
    }
  }

  async function handleStop() {
    const res = await stop();
    addLog("Stop Record", res);
    
    // Kayıt bitince son yolu güncelle
    if (res.currentPath) lastRecordedPath = res.currentPath;
    
    isRecording = false;
    if (intervalId) clearInterval(intervalId);
    intervalId = null;
    amplitude = 0;
  }

 async function handlePlay() {
    if (!lastRecordedPath) return;

    try {
      // 1. Asset URL'i al
      let assetUrl = convertFileSrc(lastRecordedPath);
      
      // 2. Decode et (yüzde işaretlerini temizle)
      assetUrl = decodeURIComponent(assetUrl);

      // 3. KRİTİK: Çift slash hatasını temizle
      // http://asset.localhost//storage -> http://asset.localhost/storage
      if (assetUrl.includes("localhost//")) {
        assetUrl = assetUrl.replace("localhost//", "localhost/");
      }

      addLog("Final Clean URL", assetUrl);

      // 4. Eski player varsa temizle (Android'de çakışma yapmasın)
      if (audioPlayer) {
        audioPlayer.pause();
        audioPlayer.src = "";
        audioPlayer.load();
      }

      const audio = new Audio(assetUrl);
      audioPlayer = audio;
      
      // Android WebView için bu flag'ler hayat kurtarır
      audio.preload = "auto";
      
      audio.onplay = () => isPlaying = true;
      audio.onpause = () => isPlaying = false;
      audio.onended = () => isPlaying = false;
      
      audio.onerror = () => {
        const err = audio.error;
        addLog("Media Error Detail", `Kod: ${err?.code} - ${err?.message}`);
        isPlaying = false;
      };

      await audio.play();
    } catch (err: any) {
      addLog("Playback Exception", err.message);
    }
  }

  async function handleCheck() { addLog("Check", await checkPermissions()); }
  async function handleRequest() { addLog("Request", await requestPermissions()); }

  async function handlePause() { addLog("Pause", await pause()); }
  async function handleResume() { addLog("Resume", await resume()); }

  async function handleManualStatus() {
    try {
      const s = await getStatus();
      // Loga detaylı basıyoruz ki isRecording: true mu false mu kesin görelim
      addLog("Manual Status Check", {
        recording: s.isRecording,
        amplitude: s.maxAmplitude,
        path: s.currentPath
      });
      
      // UI'daki durumu da senkronize edelim
      amplitude = s.maxAmplitude || 0;
      if (!s.isRecording && isRecording) {
        isRecording = false;
        if (intervalId) clearInterval(intervalId);
      }
    } catch (err: any) {
      addLog("Status Check Error", err.message);
    }
  }
async function handleClearLogs() {
    logs = []
  }
  onDestroy(() => {
    if (intervalId) clearInterval(intervalId);
    if (audioPlayer) audioPlayer.pause();
  });
</script>

<main class="container">
  <div class="visualizer">
    <div class="bar" style="height: {visualizerHeight}%"></div>
    <p class={isRecording ? 'recording' : ''}>
      {isRecording ? '● RECORDING' : '○ STANDBY'}
    </p>
  </div>

  <div class="controls">
    <div class="group">
    <label for="">
      <span>Bitrate</span>
      <input type="number" value={bitRate}>
    </label>
    <label for="">
      <span>SampleRate</span>
      <input type="number" value={sampleRate}>
    </label>
    <label for="">
      <span>Channels</span>
      <input type="number" value={channels}>
    </label>
    </div>
    <div class="group">
      <button onclick={handleCheck}>Check</button>
      <button onclick={handleRequest}>Request</button>
    </div>

    <div class="group">
      <button onclick={handleRecord} disabled={isRecording} class="start">Start</button>
      <button onclick={handleStop} disabled={!isRecording} class="stop">Stop</button>
    </div>

    <!-- Oynatma Butonu -->
    <div class="group">
      <button 
        onclick={handlePlay} 
        disabled={isRecording || !lastRecordedPath} 
        class="play-btn"
      >
        {isPlaying ? '⏸ Pause' : '▶ Play Last Recording'}
      </button>
    </div>
    
    <div class="group">
      <button onclick={handlePause} disabled={!isRecording}>Pause</button>
      <button onclick={handleResume} disabled={!isRecording}>Resume</button>
    </div>
    <div class="group">
      <button 
        onclick={handleManualStatus} 
        style="background: #455a64; border-color: #546e7a;"
      >
        🔍 Get Status
      </button>
      <button 
        onclick={handleClearLogs} 
        style="background: #455a64; border-color: #546e7a;"
      >
        Clear Logs
      </button>
    </div>
  </div>

  <div class="log-view">
    {#each logs as log}
      <div class="log-item">
        <small>{log.time}</small> <strong>{log.action}:</strong> <code>{log.data}</code>
      </div>
    {/each}
  </div>
</main>

<style>
  :global(body) { background: #121212; color: white; font-family: 'Segoe UI', sans-serif; margin: 0; }
  .container { max-width: 480px; margin: 0 auto; padding: 20px; text-align: center; }
  
  .visualizer { 
    height: 120px; 
    background: #000; 
    margin-bottom: 20px; 
    display: flex; 
    flex-direction: column; 
    align-items: center; 
    justify-content: flex-end; 
    border-radius: 12px; 
    overflow: hidden; 
    position: relative; 
    border: 1px solid #333;
  }
  
  .bar { width: 100%; background: linear-gradient(to top, #00ff88, #60efff); transition: height 0.1s ease; }
  .visualizer p { position: absolute; top: 10px; margin: 0; font-size: 11px; font-weight: bold; color: #666; }
  .visualizer p.recording { color: #ff4444; animation: blink 1s infinite; }
  
  .controls { display: flex; flex-direction: column; gap: 8px; }
  .group { display: flex; gap: 8px; }
  
  button { 
    flex: 1; 
    padding: 14px; 
    cursor: pointer; 
    background: #252525; 
    color: white; 
    border: 1px solid #333; 
    border-radius: 8px; 
    font-weight: bold; 
    transition: all 0.2s;
  }
  
  button:active { transform: scale(0.98); background: #333; }
  button:disabled { opacity: 0.2; cursor: not-allowed; }
  
  .start { background: #1b5e20; border-color: #2e7d32; }
  .stop { background: #b71c1c; border-color: #c62828; }
  .play-btn { background: #311b92; border-color: #4527a0; }

  .log-view { 
    margin-top: 20px; 
    height: 400px; 
    overflow-y: auto; 
    background: #080808; 
    padding: 12px; 
    font-size: 11px; 
    border-radius: 8px; 
    text-align: left; 
    border: 1px solid #222; 
    font-family: monospace;
  }
  
  .log-item { border-bottom: 1px solid #1a1a1a; padding: 6px 0; color: #aaa; }
  code { color: #00ff88; word-break: break-all; }

  @keyframes blink { 0% { opacity: 1; } 50% { opacity: 0.5; } 100% { opacity: 1; } }

  .group {
  display: flex;
  gap: 12px; /* Elemanlar arası boşluğu biraz açtık */
  margin-bottom: 15px;
}

.group label {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
  font-size: 12px;
  color: #888; /* Yazı rengini biraz soft yaptık */
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.group input {
  width: 100%;
  padding: 10px 12px;
  background: #1a1a1a; /* Arka plandan biraz daha açık bir siyah */
  color: #00ff88; /* Değerler ana aksan renginde */
  border: 1px solid #333;
  border-radius: 6px;
  font-family: 'Segoe UI', sans-serif;
  font-size: 14px;
  outline: none;
  transition: all 0.2s ease;
  box-sizing: border-box; /* Padding'in genişliği bozmasını engeller */
}

.group input:focus {
  border-color: #00ff88;
  background: #222;
  box-shadow: 0 0 8px rgba(0, 255, 136, 0.2);
}

/* Number input yanındaki okları (spinners) temizlemek istersen (isteğe bağlı) */
.group input::-webkit-inner-spin-button,
.group input::-webkit-outer-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
</style>