package com.plugin.audio_recorder_android

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.Permission
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import app.tauri.plugin.Invoke

@InvokeArg
class RecordArgs {
    var fileName: String? = null
    var format: Int? = null
    var encoder: Int? = null
    var bitRate: Int? = null
    var sampleRate: Int? = null
    var channels: Int? = null
}

const val RECORD_AUDIO = "recordPermissionState"
const val POST_NOTIFICATIONS = "notificationPermissionState"
const val REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = "batteryOptimizationPermissionState"

@TauriPlugin(
    permissions = [
        Permission(strings = [Manifest.permission.RECORD_AUDIO], alias = RECORD_AUDIO),
        Permission(strings = [Manifest.permission.POST_NOTIFICATIONS], alias = POST_NOTIFICATIONS),
        Permission(strings = [Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS], alias = REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
    ]
)
class AudioRecorderAndroidPlugin(private val activity: Activity): Plugin(activity) {
    private val implementation = AudioRecorderAndroid()

    private fun mapToJSObject(map: Map<String, Any?>): JSObject {
        val ret = JSObject()
        map.forEach { (key, value) ->
            if (value == null) {
                ret.put(key, null)
            } else {
                when(value) {
                    is Boolean -> ret.put(key, value)
                    is Int -> ret.put(key, value)
                    is Long -> ret.put(key, value)
                    is Float -> ret.put(key, value.toDouble())
                    is Double -> ret.put(key, value)
                    else -> ret.put(key, value.toString())
                }
            }
        }
        return ret
    }

    @Command
    override fun checkPermissions(invoke: Invoke) {
        val permissionsResultJSON = JSObject()

        val recordAudioGranted = ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        permissionsResultJSON.put(RECORD_AUDIO, if (recordAudioGranted) "granted" else "prompt")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationGranted = ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            permissionsResultJSON.put(POST_NOTIFICATIONS, if (notificationGranted) "granted" else "prompt")
        } else {
            permissionsResultJSON.put(POST_NOTIFICATIONS, "granted")
        }
        permissionsResultJSON.put(REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "granted")
        invoke.resolve(permissionsResultJSON)
    }

    @Command
    override fun requestPermissions(invoke: Invoke) {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, permissionsToRequest.toTypedArray(), 200)
            val res = JSObject()
            res.put("message", "Permissions requested natively")
            invoke.resolve(res)
        } else {
            val permissionsResultJSON = JSObject()
            permissionsResultJSON.put(RECORD_AUDIO, "granted")
            permissionsResultJSON.put(POST_NOTIFICATIONS, "granted")
            permissionsResultJSON.put(REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "granted")
            invoke.resolve(permissionsResultJSON)
        }
    }

    @Command
    fun record(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(RecordArgs::class.java)
            val fileName = args.fileName ?: "tmp-record"
            val format = args.format ?: 2
            val encoder = args.encoder ?: 3
            val bitRate = args.bitRate ?: 128000
            val sampleRate = args.sampleRate ?: 16000
            val channels = args.channels ?: 1

            val result = implementation.record(activity, fileName, format, encoder, bitRate, sampleRate, channels)
            invoke.resolve(mapToJSObject(result))
        } catch (e: Exception) {
            invoke.reject("Args parse error: ${e.message}")
        }
    }

    @Command
    fun stop(invoke: Invoke) {
        val result = implementation.stop(activity)
        val modifiedResult = result.toMutableMap()
        modifiedResult["isRecording"] = false
        invoke.resolve(mapToJSObject(modifiedResult))
    }

    @Command
    fun pause(invoke: Invoke) {
        val result = implementation.pause(activity)
        invoke.resolve(mapToJSObject(result))
    }

    @Command
    fun resume(invoke: Invoke) {
        val result = implementation.resume(activity)
        invoke.resolve(mapToJSObject(result))
    }

    @Command
    fun getStatus(invoke: Invoke) {
        val result = implementation.getStatus()
        invoke.resolve(mapToJSObject(result))
    }
}