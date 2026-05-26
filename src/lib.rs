use tauri::{
    plugin::{Builder, TauriPlugin},
    Manager, Runtime,
};

pub use models::*;

#[cfg(desktop)]
mod desktop;
#[cfg(mobile)]
mod mobile;

mod commands;
mod error;
mod models;

pub use error::{Error, Result};

#[cfg(desktop)]
use desktop::AudioRecorderAndroid;
#[cfg(mobile)]
use mobile::AudioRecorderAndroid;

/// Extensions to [`tauri::App`], [`tauri::AppHandle`] and [`tauri::Window`] to access the audio-recorder-android APIs.
pub trait AudioRecorderAndroidExt<R: Runtime> {
    fn audio_recorder_android(&self) -> &AudioRecorderAndroid<R>;
}

impl<R: Runtime, T: Manager<R>> crate::AudioRecorderAndroidExt<R> for T {
    fn audio_recorder_android(&self) -> &AudioRecorderAndroid<R> {
        self.state::<AudioRecorderAndroid<R>>().inner()
    }
}

/// Initializes the plugin.
pub fn init<R: Runtime>() -> TauriPlugin<R> {
    Builder::new("audio-recorder-android")
        .invoke_handler(tauri::generate_handler![
            commands::check_permission,
            commands::request_permission,
            commands::check_notification_permission,
            commands::request_notification_permission,
            commands::record,
            commands::stop,
            commands::pause,
            commands::resume,
            commands::get_status
        ])
        .setup(|app, api| {
            #[cfg(mobile)]
            let audio_recorder_android = mobile::init(app, api)?;
            #[cfg(desktop)]
            let audio_recorder_android = desktop::init(app, api)?;
            app.manage(audio_recorder_android);
            Ok(())
        })
        .build()
}
