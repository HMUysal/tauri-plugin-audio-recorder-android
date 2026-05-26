package com.plugin.audio_recorder_android

import android.content.Context
import android.content.Intent
import android.os.Build

class AudioRecorderAndroid {
    fun record(context: Context, fileName: String, format: Int, encoder: Int, bitRate: Int, sampleRate: Int, channels: Int): Map<String, Any?> {
        val intent = Intent(context, AudioRecordService::class.java).apply {
            action = "START"
            putExtra("fileName", fileName)
            putExtra("format", format)
            putExtra("encoder", encoder)
            putExtra("bitRate", bitRate)
            putExtra("sampleRate", sampleRate)
            putExtra("channels", channels)
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            mapOf("success" to true, "message" to "Recording service started")
        } catch (e: Exception) {
            mapOf("success" to false, "message" to e.message)
        }
    }

    fun stop(context: Context): Map<String, Any?> {
        val intent = Intent(context, AudioRecordService::class.java).apply {
            action = "STOP"
        }
        context.startService(intent)
        return AudioRecordService.getStatus()
    }

    fun pause(context: Context): Map<String, Any?> {
        val intent = Intent(context, AudioRecordService::class.java).apply {
            action = "PAUSE"
        }
        context.startService(intent)
        return mapOf("success" to true)
    }

    fun resume(context: Context): Map<String, Any?> {
        val intent = Intent(context, AudioRecordService::class.java).apply {
            action = "RESUME"
        }
        context.startService(intent)
        return mapOf("success" to true)
    }

    fun getStatus(): Map<String, Any?> {
        return AudioRecordService.getStatus()
    }
}