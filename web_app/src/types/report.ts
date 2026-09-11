export type CaseStatus = 
  | 'ACTIVE'
  | 'FOUND'
  | 'PROCESSING'
  | 'CLOSED'
  | 'REJECTED_NO_FACE';

export type Gender = 'Male' | 'Female' | 'Other';

export interface PhotoItem {
  id: string;
  person_id: string;
  original_path: string;
  face_crop_path?: string | null;
  is_primary: boolean;
  processing_status: 'PENDING' | 'SUCCESS' | 'NO_FACE' | 'FAILED';
  created_at: string;
}

export interface MissingPerson {
  id: string;
  user_id: string;
  full_name: string;
  age: number;
  gender: Gender | string;
  height_cm?: number | null;
  description?: string | null;
  last_seen_location: string;
  last_seen_time: string;
  contact_info: string;
  status: CaseStatus;
  primary_photo_url?: string | null;
  photos: PhotoItem[];
  created_at: string;
  updated_at: string;
}

export interface MissingPersonCreatePayload {
  full_name: string;
  age: number;
  gender: string;
  height_cm?: number;
  description?: string;
  last_seen_location: string;
  last_seen_time: string;
  contact_info: string;
}
