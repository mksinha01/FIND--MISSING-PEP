export interface UserProfile {
  id: string;
  firebase_uid: string;
  name: string;
  email?: string | null;
  phone?: string | null;
  avatar_url?: string | null;
  language: string;
  fcm_token?: string | null;
  created_at: string;
  updated_at: string;
}

export interface AuthState {
  user: UserProfile | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
}
