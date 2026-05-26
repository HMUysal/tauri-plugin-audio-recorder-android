use serde::de::DeserializeOwned;
use tauri::{plugin::PluginApi, AppHandle, Runtime};

use crate::models::*;

pub fn init<R: Runtime, C: DeserializeOwned>(
    app: &AppHandle<R>,
    _api: PluginApi<R, C>,
) -> crate::Result<AudioRecorderAndroid<R>> {
    Ok(AudioRecorderAndroid(app.clone()))
}

/// Access to the audio-recorder-android APIs (Desktop Mock).
pub struct AudioRecorderAndroid<R: Runtime>(AppHandle<R>);

impl<R: Runtime> AudioRecorderAndroid<R> {
    pub fn check_permissions(&self) -> crate::Result<PermissionResponse> {
        Ok(PermissionResponse {
            record_permission_state: "Not Available".to_string(),
            notification_permission_state: "Not Available".to_string(),
        })
    }

    pub fn request_permissions(&self) -> crate::Result<PermissionResponse> {
        Ok(PermissionResponse {
            record_permission_state: "Not Available".to_string(),
            notification_permission_state: "Not Available".to_string(),
        })
    }

    pub fn record(&self, _payload: RecordRequest) -> crate::Result<GenericResponse> {
        Ok(GenericResponse {
            success: false,
            message: Some("Recording feature is not implemented for desktop".to_string()),
        })
    }

    pub fn stop(&self) -> crate::Result<RecorderStatusResponse> {
        Ok(RecorderStatusResponse {
            success: false,
            is_recording: false,
            current_path: None,
            max_amplitude: Some(0),
            message: Some("Operation not supported on desktop".to_string()),
        })
    }

    pub fn pause(&self) -> crate::Result<GenericResponse> {
        Ok(GenericResponse {
            success: false,
            message: Some("Pause not supported on desktop".to_string()),
        })
    }

    pub fn resume(&self) -> crate::Result<GenericResponse> {
        Ok(GenericResponse {
            success: false,
            message: Some("Resume not supported on desktop".to_string()),
        })
    }

    pub fn get_status(&self) -> crate::Result<RecorderStatusResponse> {
        Ok(RecorderStatusResponse {
            success: true,
            is_recording: false,
            current_path: None,
            max_amplitude: Some(0),
            message: Some("Running on desktop mock".to_string()),
        })
    }
}
