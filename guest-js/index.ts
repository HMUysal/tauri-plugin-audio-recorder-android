import { invoke } from "@tauri-apps/api/core";

export enum AudioQuality {
  LOW = 0,
  STANDARD = 1,
  HIGH = 2,
}

/**
 * Android MediaRecorder OutputFormat constants.
 * These match the integer values defined in android.media.MediaRecorder.OutputFormat.
 */
export enum OutputFormat {
  DEFAULT = 0,
  THREE_GPP = 1,
  MPEG_4 = 2,
  AMR_NB = 3,
  AMR_WB = 4,
  AAC_ADTS = 6,
  MPEG_2_TS = 8,
  WEBM = 9,
  OGG = 11,
}

/**
 * Android MediaRecorder AudioEncoder constants.
 * These match the integer values defined in android.media.MediaRecorder.AudioEncoder.
 */
export enum AudioEncoder {
  DEFAULT = 0,
  AMR_NB = 1,
  AMR_WB = 2,
  AAC = 3,
  HE_AAC = 4,
  AAC_ELD = 5,
  VORBIS = 6,
  OPUS = 7,
}

/**
 * Arguments for the recording process.
 */
export interface RecordRequest {
  /** The full path or name where the audio file will be saved. */
  fileName: string;
  /** The output format (Use OutputFormat enum). Defaults to MPEG_4. */
  format?: OutputFormat;
  /** The audio encoder (Use AudioEncoder enum). Defaults to AAC. */
  encoder?: AudioEncoder;
  bitRate?: number;
  sampleRate?: number;
  channels?: number;
}

export interface PermissionResponse {
  recordPermissionState: "granted" | "prompt" | "denied";
  notificationPermissionState: "granted" | "prompt" | "denied";
  batteryOptimizationPermissionState: "granted" | "prompt" | "denied";
}

/**
 * Standard response structure for permissions and simple operations.
 */
export interface GenericResponse {
  /** Indicates if the operation was successful. */
  success: boolean;
  /** Optional message detailing the result or error. */
  message?: string;
  /** Whether the requested permission has been granted. */
  isGranted?: boolean;
}

/**
 * Detailed status of the recorder, including real-time amplitude data.
 */
export interface RecorderStatusResponse {
  /** Indicates if the status check was successful. */
  success: boolean;
  /** True if the recording service is currently active. */
  isRecording: boolean;
  /** The file path of the current or last recording. */
  currentPath?: string;
  /** The maximum absolute amplitude measured since the last call. Useful for visualizers. */
  maxAmplitude?: number;
  /** Optional message detailing the state. */
  message?: string;
}

// --- API Functions ---

/**
 * Checks if the RECORD_AUDIO permission is granted on the Android device.
 * @returns A promise resolving to the permission status.
 */
export async function checkPermissions(): Promise<PermissionResponse> {
  return await invoke<PermissionResponse>(
    "plugin:audio-recorder-android|check_permissions",
  );
}

/**
 * Requests the RECORD_AUDIO permission from the user.
 * @returns A promise resolving to the result of the permission request.
 */
export async function requestPermissions(): Promise<PermissionResponse> {
  return await invoke<PermissionResponse>(
    "plugin:audio-recorder-android|request_permissions",
  );
}
/**
 * Starts the foreground recording service.
 * @param payload - Configuration including fileName, format, and encoder.
 * @returns A promise resolving to the operation result.
 */
export async function record(payload: RecordRequest): Promise<GenericResponse> {
  return await invoke<GenericResponse>("plugin:audio-recorder-android|record", {
    payload,
  });
}

/**
 * Stops the recording service and saves the file.
 * @returns A promise resolving to the final recording status and file path.
 */
export async function stop(): Promise<RecorderStatusResponse> {
  return await invoke<RecorderStatusResponse>(
    "plugin:audio-recorder-android|stop",
  );
}

/**
 * Pauses the current recording session.
 * @returns A promise resolving to the operation result.
 */
export async function pause(): Promise<GenericResponse> {
  return await invoke<GenericResponse>("plugin:audio-recorder-android|pause");
}

/**
 * Resumes a paused recording session.
 * @returns A promise resolving to the operation result.
 */
export async function resume(): Promise<GenericResponse> {
  return await invoke<GenericResponse>("plugin:audio-recorder-android|resume");
}

/**
 * Retrieves the current state of the recorder, including peak amplitude.
 * @returns A promise resolving to the current recorder status.
 */
export async function getStatus(): Promise<RecorderStatusResponse> {
  return await invoke<RecorderStatusResponse>(
    "plugin:audio-recorder-android|get_status",
  );
}
