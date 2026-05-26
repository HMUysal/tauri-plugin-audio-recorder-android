package com.plugin.audio_recorder_android

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.abs

private const val TAG = "AudioRecordService"
private const val CHANNEL_ID = "audio_record_service_channel"
private const val NOTIFICATION_ID = 101

class AudioRecordService : Service() {
    private var startTime: Long = 0
    private var pausedTimeOffset: Long = 0
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isPaused = false
    private var wakeLock: PowerManager.WakeLock? = null

    private var mediaSession: MediaSession? = null

    companion object {
        var isRecording = false
            private set
        var currentFilePath: String? = null
            private set
        var lastAmplitude: Int = 0
            private set

        fun getStatus(): Map<String, Any?> {
            return mapOf(
                "success" to true,
                "isRecording" to isRecording,
                "currentPath" to currentFilePath,
                "maxAmplitude" to lastAmplitude
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service onCreate called")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.v(TAG, "onStartCommand received action: $action")

        when (action) {
            "START" -> {
                val fileName = intent.getStringExtra("fileName")
                val sampleRate = intent.getIntExtra("sampleRate", 16000)
                val channels = intent.getIntExtra("channels", 1)
                Log.d(TAG, "Processing START request - File: $fileName, SampleRate: $sampleRate")
                startRecording(fileName, sampleRate, channels)
            }
            "STOP" -> {
                Log.d(TAG, "Processing STOP request")
                stopRecording()
            }
            "PAUSE" -> {
                Log.d(TAG, "Processing PAUSE request")
                pauseRecording()
            }
            "RESUME" -> {
                Log.d(TAG, "Processing RESUME request")
                resumeRecording()
            }
            else -> Log.w(TAG, "Unknown action received: $action")
        }
        return START_STICKY
    }

    private fun startRecording(fileName: String?, sampleRate: Int, channels: Int) {
        if (isRecording || fileName == null) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "FATAL: RECORD_AUDIO permission not granted!")
            stopSelf()
            return
        }

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AudioRecorder::RecordingWakeLock").apply {
            acquire(30 * 60 * 1000L)
        }

        currentFilePath = fileName
        isPaused = false
        startTime = System.currentTimeMillis()
        pausedTimeOffset = 0

        val notification = buildNotification("Recording...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val channelConfig = if (channels >= 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "FATAL: Invalid AudioRecord parameters!")
            releaseWakeLock()
            stopSelf()
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "FATAL: AudioRecord initialization failed!")
            audioRecord?.release()
            audioRecord = null
            releaseWakeLock()
            stopSelf()
            return
        }

        isRecording = true
        mediaSession = MediaSession(this, "AudioRecordService")
        mediaSession?.isActive = true

        recordingThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            writeAudioDataToFile(fileName, bufferSize, sampleRate, if (channels >= 2) 2 else 1)
        }.apply { start() }

        Log.i(TAG, "Successfully started recording: $fileName")
    }

    private fun writeAudioDataToFile(fileName: String, bufferSize: Int, sampleRate: Int, channels: Int) {
        val audioData = ShortArray(bufferSize)
        var fileOutputStream: FileOutputStream? = null
        var hasErrorOccurred = false
        try {
            audioRecord?.startRecording()
            fileOutputStream = FileOutputStream(fileName)

            val headerPlaceholder = ByteArray(44)
            fileOutputStream.write(headerPlaceholder)

            while (isRecording) {
                if (isPaused) {
                    Thread.sleep(100)
                    continue
                }

                val readResult = audioRecord?.read(audioData, 0, bufferSize) ?: 0
                if (readResult > 0) {
                    var maxAmp = 0
                    val byteBuffer = ByteArray(readResult * 2)
                    for (i in 0 until readResult) {
                        maxAmp = maxOf(maxAmp, abs(audioData[i].toInt()))
                        byteBuffer[i * 2] = (audioData[i].toInt() and 0x00FF).toByte()
                        byteBuffer[i * 2 + 1] = (audioData[i].toInt() shr 8).toByte()
                    }
                    lastAmplitude = maxAmp
                    fileOutputStream.write(byteBuffer, 0, readResult * 2)
                } else if (readResult < 0) {
                    Log.e(TAG, "AudioRecord Read Error! Kod: $readResult.")
                    hasErrorOccurred = true
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audio data: ${e.message}")
            hasErrorOccurred = true
        } finally {
            try {
                fileOutputStream?.close()
                updateWavHeader(File(fileName), sampleRate, channels)
                Log.d(TAG, "WAV header safely updated in finally block.")
            } catch (e: IOException) {
                Log.e(TAG, "Error closing file: ${e.message}")
            }

            if (hasErrorOccurred) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Log.w(TAG, "Automatically stop due to loop error")
                    stopRecording()
                }
            }
        }
    }

    private fun stopRecording() {
        Log.d(TAG, "stopRecording called")
        isRecording = false
        isPaused = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error during AudioRecord stop: ${e.message}")
        } finally {
            audioRecord = null
            recordingThread = null
            lastAmplitude = 0

            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null

            releaseWakeLock()

            Log.v(TAG, "Removing foreground notification")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
            Log.i(TAG, "Recording stopped and resources released.")
        }
    }

    private fun pauseRecording() {
        if (isRecording && !isPaused) {
            isPaused = true
            pausedTimeOffset += System.currentTimeMillis() - startTime
            audioRecord?.stop()
            updateNotification("Recording Paused", showChronometer = false)
            Log.i(TAG, "Recording paused.")
        }
    }

    private fun resumeRecording() {
        if (isRecording && isPaused) {
            isPaused = false
            startTime = System.currentTimeMillis()
            audioRecord?.startRecording()
            updateNotification("Recording...", showChronometer = true)
            Log.i(TAG, "Recording resumed.")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun updateWavHeader(file: File, sampleRate: Int, channels: Int) {
        if (!file.exists()) return

        val byteRate = sampleRate * channels * 2
        val totalAudioLen = file.length() - 44
        val totalDataLen = totalAudioLen + 36

        try {
            val randomAccessFile = RandomAccessFile(file, "rw")
            randomAccessFile.seek(0)

            val header = ByteArray(44)
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte(); header[5] = ((totalDataLen shr 8) and 0xff).toByte(); header[6] = ((totalDataLen shr 16) and 0xff).toByte(); header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
            header[20] = 1; header[21] = 0
            header[22] = channels.toByte(); header[23] = 0
            header[24] = (sampleRate and 0xff).toByte(); header[25] = ((sampleRate shr 8) and 0xff).toByte(); header[26] = ((sampleRate shr 16) and 0xff).toByte(); header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte(); header[29] = ((byteRate shr 8) and 0xff).toByte(); header[30] = ((byteRate shr 16) and 0xff).toByte(); header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = (channels * 2).toByte(); header[33] = 0
            header[34] = 16; header[35] = 0
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte(); header[41] = ((totalAudioLen shr 8) and 0xff).toByte(); header[42] = ((totalAudioLen shr 16) and 0xff).toByte(); header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

            randomAccessFile.write(header, 0, 44)
            randomAccessFile.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating WAV header: ${e.message}")
        }
    }

    private fun buildNotification(contentText: String, showChronometer: Boolean = true): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Recorder")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)

        if (showChronometer) {
            builder.setUsesChronometer(true)
            builder.setWhen(startTime - pausedTimeOffset)
        } else {
            builder.setUsesChronometer(false)
        }

        return builder.build()
    }

    private fun updateNotification(contentText: String, showChronometer: Boolean = true) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText, showChronometer))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Audio Recording Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy called")
        if (isRecording) {
            stopRecording()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}