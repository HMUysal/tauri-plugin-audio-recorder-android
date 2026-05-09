use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RecordRequest {
    pub file_name: String,
    pub format: Option<i32>,
    pub encoder: Option<i32>,
    pub bit_rate: Option<i32>,
    pub sample_rate: Option<i32>,
    pub channels: Option<i32>,
}

#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct EmptyRequest {}


#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RecorderStatusResponse {
    pub success: bool,
    pub is_recording: bool,
    pub current_path: Option<String>,
    pub max_amplitude: Option<i32>,
    pub message: Option<String>,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GenericResponse {
    pub success: bool,
    pub message: Option<String>,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PermissionResponse {
    pub success: bool,
    pub message: Option<String>,
    pub is_granted: Option<bool>,
}