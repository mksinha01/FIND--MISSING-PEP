import { 
  signInWithEmailAndPassword, 
  createUserWithEmailAndPassword, 
  signInWithPopup, 
  GoogleAuthProvider, 
  signOut as fbSignOut, 
  sendPasswordResetEmail,
  onAuthStateChanged,
  User as FirebaseUser
} from 'firebase/auth';
import { auth } from '../config/firebase';
import { usersApi } from './api';
import { UserProfile } from '../types/auth';

type AuthListener = (user: UserProfile | null) => void;

export class AuthService {
  private currentUser: FirebaseUser | null = null;
  private listeners: AuthListener[] = [];
  private currentProfile: UserProfile | null = null;

  constructor() {
    // Restore cached profile immediately if available
    const cachedProfile = localStorage.getItem('fmp_user_profile');
    if (cachedProfile) {
      try {
        this.currentProfile = JSON.parse(cachedProfile);
      } catch (e) {
        // ignore malformed cache
      }
    }

    if (auth) {
      onAuthStateChanged(auth, async (user) => {
        this.currentUser = user;
        if (user) {
          try {
            const token = await user.getIdToken();
            localStorage.setItem('fmp_auth_token', token);
            // Fetch or sync user profile
            const profile = await usersApi.getProfile();
            this.currentProfile = profile;
            localStorage.setItem('fmp_user_profile', JSON.stringify(profile));
            this.notifyListeners(profile);
          } catch (err) {
            console.error('Failed to sync authenticated user profile', err);
            this.notifyListeners(this.currentProfile);
          }
        } else {
          // If no Firebase user, check if we have a mock token
          const mockToken = localStorage.getItem('fmp_auth_token');
          if (mockToken && mockToken.startsWith('mock-token')) {
            this.notifyListeners(this.currentProfile);
          } else {
            this.currentProfile = null;
            localStorage.removeItem('fmp_auth_token');
            localStorage.removeItem('fmp_user_profile');
            this.notifyListeners(null);
          }
        }
      });
    }
  }

  subscribe(listener: AuthListener): () => void {
    this.listeners.push(listener);
    listener(this.currentProfile);
    return () => {
      this.listeners = this.listeners.filter(l => l !== listener);
    };
  }

  private notifyListeners(user: UserProfile | null) {
    this.listeners.forEach(l => l(user));
  }

  async getIdToken(forceRefresh = false): Promise<string | null> {
    if (this.currentUser) {
      return await this.currentUser.getIdToken(forceRefresh);
    }
    return localStorage.getItem('fmp_auth_token');
  }

  getCurrentProfile(): UserProfile | null {
    return this.currentProfile;
  }

  async loginWithEmail(email: string, pass: string): Promise<UserProfile> {
    if (!auth) {
      throw new Error("Firebase Authentication is not configured. Please use 1-Click Dev Mock Login.");
    }
    const cred = await signInWithEmailAndPassword(auth, email.trim(), pass);
    this.currentUser = cred.user;
    const token = await cred.user.getIdToken();
    localStorage.setItem('fmp_auth_token', token);
    
    // Sync with backend database
    const profile = await usersApi.sync(cred.user.uid, cred.user.displayName || email.split('@')[0], email);
    this.currentProfile = profile;
    localStorage.setItem('fmp_user_profile', JSON.stringify(profile));
    this.notifyListeners(profile);
    return profile;
  }

  async registerWithEmail(email: string, pass: string, name: string): Promise<UserProfile> {
    if (!auth) {
      throw new Error("Firebase Authentication is not configured. Please use 1-Click Dev Mock Login.");
    }
    const cred = await createUserWithEmailAndPassword(auth, email.trim(), pass);
    this.currentUser = cred.user;
    const token = await cred.user.getIdToken();
    localStorage.setItem('fmp_auth_token', token);

    const profile = await usersApi.sync(cred.user.uid, name.trim(), email);
    this.currentProfile = profile;
    localStorage.setItem('fmp_user_profile', JSON.stringify(profile));
    this.notifyListeners(profile);
    return profile;
  }

  async loginWithGoogle(): Promise<UserProfile> {
    if (!auth) {
      throw new Error("Firebase Authentication is not configured. Please use 1-Click Dev Mock Login.");
    }
    const provider = new GoogleAuthProvider();
    const cred = await signInWithPopup(auth, provider);
    this.currentUser = cred.user;
    const token = await cred.user.getIdToken();
    localStorage.setItem('fmp_auth_token', token);

    const profile = await usersApi.sync(
      cred.user.uid, 
      cred.user.displayName || "Google User", 
      cred.user.email || undefined
    );
    this.currentProfile = profile;
    localStorage.setItem('fmp_user_profile', JSON.stringify(profile));
    this.notifyListeners(profile);
    return profile;
  }

  async loginWithMock(role = "admin"): Promise<UserProfile> {
    const token = `mock-token-${role}`;
    localStorage.setItem('fmp_auth_token', token);
    
    // Call /auth/verify or /users/me which supports mock token auto-provisioning
    try {
      const profile = await usersApi.getProfile();
      this.currentProfile = profile;
      localStorage.setItem('fmp_user_profile', JSON.stringify(profile));
      this.notifyListeners(profile);
      return profile;
    } catch (e) {
      const fallback: UserProfile = {
        id: "00000000-0000-0000-0000-000000000001",
        firebase_uid: `uid_${token}`,
        name: `Dev ${role.charAt(0).toUpperCase() + role.slice(1)}`,
        email: `${role}@dev.local`,
        language: "en",
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      };
      this.currentProfile = fallback;
      localStorage.setItem('fmp_user_profile', JSON.stringify(fallback));
      this.notifyListeners(fallback);
      return fallback;
    }
  }

  async signOut(): Promise<void> {
    if (auth) {
      await fbSignOut(auth).catch(() => {});
    }
    this.currentUser = null;
    this.currentProfile = null;
    localStorage.removeItem('fmp_auth_token');
    localStorage.removeItem('fmp_user_profile');
    this.notifyListeners(null);
  }

  async resetPassword(email: string): Promise<void> {
    if (!auth) {
      throw new Error("Firebase Authentication is not configured.");
    }
    await sendPasswordResetEmail(auth, email.trim());
  }
}

export const authService = new AuthService();
