use serde::de::DeserializeOwned;
use tauri::{
    plugin::{PluginApi, PluginHandle},
    AppHandle, Runtime,
};

use crate::models::*;

#[cfg(target_os = "ios")]
tauri::ios_plugin_binding!(init_plugin_audio_recorder_android);

// initializes the Kotlin or Swift plugin classes
pub fn init<R: Runtime, C: DeserializeOwned>(
    _app: &AppHandle<R>,
    api: PluginApi<R, C>,
) -> crate::Result<AudioRecorderAndroid<R>> {
    #[cfg(target_os = "android")]
    let handle = api.register_android_plugin(
        "com.plugin.audio_recorder_android",
        "AudioRecorderAndroidPlugin",
    )?;
    #[cfg(target_os = "ios")]
    let handle = api.register_ios_plugin(init_plugin_audio_recorder_android)?;
    Ok(AudioRecorderAndroid(handle))
}

/// Access to the audio-recorder-android APIs.
pub struct AudioRecorderAndroid<R: Runtime>(PluginHandle<R>);

impl<R: Runtime> AudioRecorderAndroid<R> {
    pub fn check_permission(&self) -> crate::Result<PermissionResponse> {
        self.0
            .run_mobile_plugin("checkPermission", EmptyRequest {})
            .map_err(Into::into)
    }

    pub fn request_permission(&self) -> crate::Result<PermissionResponse> {
        self.0
            .run_mobile_plugin("requestPermission", EmptyRequest {})
            .map_err(Into::into)
    }

    pub fn check_notification_permission(&self) -> crate::Result<PermissionResponse> {
        self.0
            .run_mobile_plugin("checkNotificationPermission", EmptyRequest {})
            .map_err(Into::into)
    }

    pub fn request_notification_permission(&self) -> crate::Result<PermissionResponse> {
        self.0
            .run_mobile_plugin("requestNotificationPermission", EmptyRequest {})
            .map_err(Into::into)
    }
    pub fn record(&self, payload: RecordRequest) -> crate::Result<GenericResponse> {
        self.0
            .run_mobile_plugin("record", payload)
            .map_err(Into::into)
    }

    pub fn stop(&self) -> crate::Result<RecorderStatusResponse> {
        self.0
            .run_mobile_plugin("stop", EmptyRequest {})
            .map_err(Into::into)
    }

    pub fn pause(&self) -> crate::Result<GenericResponse> {
        self.0
            .run_mobile_plugin("pause", EmptyRequest {})
            .map_err(Into::into)
    }

    pub fn resume(&self) -> crate::Result<GenericResponse> {
        self.0
            .run_mobile_plugin("resume", EmptyRequest {})
            .map_err(Into::into)
    }

    pub fn get_status(&self) -> crate::Result<RecorderStatusResponse> {
        self.0
            .run_mobile_plugin("getStatus", EmptyRequest {})
            .map_err(Into::into)
    }
}
