package com.plugin.audio_recorder_android

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val PERMISSION_REQUEST_CODE = 1001
const val NOTIFICATION_PERMISSION_CODE = 101
class AudioRecorderAndroid {

    fun checkPermission(context: Context): Map<String, Any?> {
        val permission = Manifest.permission.RECORD_AUDIO
        val isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return mapOf("success" to true, "isGranted" to isGranted)
    }

    fun requestPermission(activity: Activity): Map<String, Any?> {
        val permission = Manifest.permission.RECORD_AUDIO
        return if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(permission), PERMISSION_REQUEST_CODE)
            mapOf("success" to true, "message" to "Permission dialog opened", "isGranted" to false)
        } else {
            mapOf("success" to true, "message" to "Already granted", "isGranted" to true)
        }
    }
    fun checkNotificationPermission(context: Context): Map<String, Any?> {
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return mapOf("success" to true, "isGranted" to isGranted)
    }

    fun requestNotificationPermission(activity: Activity): Map<String, Any?> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(permission), 101)
                mapOf("success" to true, "message" to "Permission dialog opened", "isGranted" to false)
            }else {
                mapOf("success" to true, "message" to "Already granted", "isGranted" to true)
            }
        } else {
            mapOf("success" to true, "message" to "No need for Permission", "isGranted" to true)
        }
    }
    fun record(context: Context, fileName: String, format: Int, encoder: Int, bitRate:Int, sampleRate:Int, channels:Int): Map<String, Any?> {
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
