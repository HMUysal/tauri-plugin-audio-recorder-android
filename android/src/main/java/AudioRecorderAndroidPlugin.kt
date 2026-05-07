package com.plugin.audio_recorder_android

import android.app.Activity
import android.media.MediaRecorder
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import app.tauri.plugin.Invoke

@InvokeArg
class RecordArgs {
    var fileName: String? = null
    var format: Int? = null
    var encoder: Int? = null
}

@TauriPlugin
class AudioRecorderAndroidPlugin(private val activity: Activity): Plugin(activity) {
    private val implementation = AudioRecorderAndroid()

    @Command
    fun checkPermission(invoke: Invoke) {
        val result = implementation.checkPermission(activity)
        val ret = JSObject()
        result.forEach { (key, value) ->
            when(value) {
                is Boolean -> ret.put(key, value)
                is Int -> ret.put(key, value)
                else -> ret.put(key, value.toString())
            }
        }
        invoke.resolve(ret)
    }

    @Command
    fun requestPermission(invoke: Invoke) {
        val result = implementation.requestPermission(activity)
        val ret = JSObject()
        result.forEach { (key, value) ->
            when(value) {
                is Boolean -> ret.put(key, value)
                is Int -> ret.put(key, value)
                else -> ret.put(key, value.toString())
            }
        }
        invoke.resolve(ret)
    }

    @Command
    fun record(invoke: Invoke) {
        try {
            val args = invoke.parseArgs(RecordArgs::class.java)
            val fileName = args.fileName ?: "tmp-record"
            val format = args.format ?: MediaRecorder.OutputFormat.MPEG_4
            val encoder = args.encoder ?: MediaRecorder.AudioEncoder.AAC

            val result = implementation.record(activity, fileName, format, encoder)
            val ret = JSObject()
            result.forEach { (key, value) ->
                when(value) {
                    is Boolean -> ret.put(key, value)
                    is Int -> ret.put(key, value)
                    else -> ret.put(key, value.toString())
                }
            }
            invoke.resolve(ret)
        } catch (e: Exception) {
            invoke.reject("Args parse error: ${e.message}")
        }
    }

    @Command
    fun stop(invoke: Invoke) {
        val result = implementation.stop(activity)
        val ret = JSObject()
        result.forEach { (key, value) ->
            when(value) {
                is Boolean -> ret.put(key, value)
                is Int -> ret.put(key, value)
                else -> ret.put(key, value.toString())
            }
        }
        invoke.resolve(ret)
    }

    @Command
    fun pause(invoke: Invoke) {
        val result = implementation.pause(activity)
        val ret = JSObject()
        result.forEach { (key, value) ->
            when(value) {
                is Boolean -> ret.put(key, value)
                is Int -> ret.put(key, value)
                else -> ret.put(key, value.toString())
            }
        }
        invoke.resolve(ret)
    }

    @Command
    fun resume(invoke: Invoke) {
        val result = implementation.resume(activity)
        val ret = JSObject()
        result.forEach { (key, value) ->
            when(value) {
                is Boolean -> ret.put(key, value)
                is Int -> ret.put(key, value)
                else -> ret.put(key, value.toString())
            }
        }
        invoke.resolve(ret)
    }

    @Command
    fun getStatus(invoke: Invoke) {
        val result = implementation.getStatus()
        val ret = JSObject()
        result.forEach { (key, value) ->
            when(value) {
                is Boolean -> ret.put(key, value)
                is Int -> ret.put(key, value)
                else -> ret.put(key, value.toString())
            }
        }
        invoke.resolve(ret)
    }
}
