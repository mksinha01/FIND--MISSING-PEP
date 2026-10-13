import { initializeApp, getApps, FirebaseApp } from 'firebase/app';
import { getAuth, Auth } from 'firebase/auth';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || 'AIzaSyAYnAkbJNRpgAqWvKjmh482QHpW_bhfmOY',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || 'find-missing-pep.firebaseapp.com',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || 'find-missing-pep',
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || 'find-missing-pep.firebasestorage.app',
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || '293939241446',
  appId: import.meta.env.VITE_FIREBASE_APP_ID || '1:293939241446:web:abc1a37cc6ab88e28c3627',
  measurementId: import.meta.env.VITE_FIREBASE_MEASUREMENT_ID || 'G-H309RWCPC9',
};

let app: FirebaseApp | null = null;
let auth: Auth | null = null;

try {
  if (firebaseConfig.apiKey && !firebaseConfig.apiKey.includes('Dummy')) {
    app = !getApps().length ? initializeApp(firebaseConfig) : getApps()[0];
    auth = getAuth(app);
  } else {
    // If running in dev mode without real Firebase credentials
    console.info('Firebase running in mock/offline mode (dummy keys detected).');
  }
} catch (e) {
  console.warn('Firebase initialization error, running in offline/mock mode:', e);
}

export { app, auth };
