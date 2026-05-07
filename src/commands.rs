use tauri::{AppHandle, command, Runtime};

use crate::models::*;
use crate::Result;
use crate::AudioRecorderAndroidExt;

#[command]
pub(crate) async fn check_permission<R: Runtime>(
    app: AppHandle<R>,
) -> Result<GenericResponse> {
    app.audio_recorder_android().check_permission()
}

#[command]
pub(crate) async fn request_permission<R: Runtime>(
    app: AppHandle<R>,
) -> Result<GenericResponse> {
    app.audio_recorder_android().request_permission()
}

#[command]
pub(crate) async fn record<R: Runtime>(
    app: AppHandle<R>,
    payload: RecordRequest,
) -> Result<GenericResponse> {
    app.audio_recorder_android().record(payload)
}

#[command]
pub(crate) async fn stop<R: Runtime>(
    app: AppHandle<R>,
) -> Result<RecorderStatusResponse> {
    app.audio_recorder_android().stop()
}

#[command]
pub(crate) async fn pause<R: Runtime>(
    app: AppHandle<R>,
) -> Result<GenericResponse> {
    app.audio_recorder_android().pause()
}

#[command]
pub(crate) async fn resume<R: Runtime>(
    app: AppHandle<R>,
) -> Result<GenericResponse> {
    app.audio_recorder_android().resume()
}

#[command]
pub(crate) async fn get_status<R: Runtime>(
    app: AppHandle<R>,
) -> Result<RecorderStatusResponse> {
    app.audio_recorder_android().get_status()
}