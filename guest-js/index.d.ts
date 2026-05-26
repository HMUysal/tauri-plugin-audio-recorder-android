export type AudioLowQuality = {
  bitRate: 32000;
  samplingRate: 22050;
  channels: 1;
};
export type AudioStandardQuality = {
  bitRate: 128000;
  samplingRate: 44100;
  channels: 1;
};
export type AudioHighQuality = {
  bitRate: 320000;
  samplingRate: 48000;
  channels: 2;
};

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

export interface RecordRequest {
  fileName: string;
  format?: OutputFormat;
  encoder?: AudioEncoder;
  bitRate?: number;
  sampleRate?: number;
  channels?: number;
}

export interface GenericResponse {
  success: boolean;
  message?: string;
  isGranted?: boolean;
}

export interface RecorderStatusResponse {
  success: boolean;
  isRecording: boolean;
  currentPath?: string;
  maxAmplitude?: number;
  message?: string;
}

/**
 * Checks if the RECORD_AUDIO permission is granted.
 */
export function checkPermission(): Promise<GenericResponse>;

/**
 * Requests the RECORD_AUDIO permission.
 */
export function requestPermission(): Promise<GenericResponse>;
/**
 * Checks if the RECORD_AUDIO permission is granted.
 */
export function checkNotificationPermission(): Promise<GenericResponse>;

/**
 * Requests the RECORD_AUDIO permission.
 */
export function requestNotificationPermission(): Promise<GenericResponse>;
/**
 * Starts the recording service.
 */
export function record(payload: RecordRequest): Promise<GenericResponse>;

/**
 * Stops the recording service.
 */
export function stop(): Promise<RecorderStatusResponse>;

/**
 * Pauses the recording.
 */
export function pause(): Promise<GenericResponse>;

/**
 * Resumes the recording.
 */
export function resume(): Promise<GenericResponse>;

/**
 * Gets the current recorder status.
 */
export function getStatus(): Promise<RecorderStatusResponse>;
