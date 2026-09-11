# FIND-MISSING-PEP — Web Application One-Shot Build Spec
## Converting the Flutter Citizen Reporting App into a Modern Web Application with User Authentication

> **One-Shot Build Spec**: This document is the single source of truth for building the Web Application version of the FIND-MISSING-PEP Citizen & Family Reporting Portal. It follows the [One-Shot-Build-Spec template](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/3-One-Shot-Build-Spec.md) exactly — config → schema → logic → API → UI → rules → ops. **Build this exactly. Do not skip anything. Do not simplify. Do not substitute libraries.**

---

## Table of Contents

| § | Section | Purpose |
|---|---------|---------|
| 0 | [Mission Statement](#0-mission-statement) | What the web app does, capabilities verbs, tech stack, build instruction |
| 1 | [File Structure](#1-file-structure--complete-project-tree) | Complete directory tree for `web_app/`, every file with one-line responsibility |
| 2 | [Environment / Secrets](#2-environment--secrets) | Full `.env` templates, precedence chain, `.gitignore` |
| 3 | [Data Schema / Client State](#3-data-schema--client-storage) | Client storage models, localStorage/IndexedDB, access control |
| 4 | [Dependencies](#4-dependencies) | Pinned `package.json` dependencies and devDependencies |
| 5 | [Containerization / Runtime](#5-containerization--runtime) | Dockerfile, Nginx config, Vite proxy, port ownership map |
| 6 | [Domain Config & Localization](#6-domain-config--localization-templates) | Bilingual (English/Hindi) dictionary, validation rules, constants |
| 7 | [Data Access Layer](#7-data-access-layer--api-client) | Modular Axios client methods, one function = one API call |
| 8 | [Action / Tool Layer](#8-action--tool-layer--services) | Auth service, canvas image compression, SSE listener, Leaflet map engine |
| 9 | [Orchestration / Entrypoint](#9-orchestration--entrypoint--routing) | App bootstrap, AuthContext, route guards, token interceptors |
| 10 | [API Layer Reference](#10-api-layer--backend-rest-contract) | Backend REST endpoints consumed, DTO models, auth requirements |
| 11 | [Frontend / UI Spec](#11-frontend--ui-spec) | Screen shapes, state stores, handler functions, responsive layouts |
| 12 | [Critical Architecture Rules](#12-critical-architecture-rules--do-not-deviate) | Wrong-vs-correct pairs from real web/auth/CORS/upload bugs |
| 13 | [Deployment](#13-deployment) | Numbered steps from scratch to running website, startup log check |
| 14 | [Known Gotchas Table](#14-known-gotchas-table) | Symptom → Root Cause → Fix |
| 15 | [Reference Data](#15-reference-data) | Status enums, color tokens, routes, translation key matrix |
| 16 | [Cost / Ops Reference](#16-cost--ops-reference) | Firebase free tier, map tile limits, CDN and hosting bandwidth |
| 17 | [External API Integration Reference](#17-external-api-integration-reference) | Copy-paste code snippets for Firebase v10, Leaflet, EventSource |
| 18 | [One-Time Setup Sequence](#18-one-time-setup-sequence-post-deploy) | Post-deploy checklist to make the web app fully functional |

---

## 0. Mission Statement

### What the Web Application Does

A responsive, browser-based Citizen & Family Reporting Portal and Forensic Review Hub that allows users to authenticate (via Firebase or local test credentials), report missing persons with photos, monitor live CCTV sightings, inspect facial recognition evidence with side-by-side comparison, track movements across CCTV cameras on an interactive GPS map, and receive real-time alert notifications via Server-Sent Events (SSE).

### Capabilities (Verbs)

| # | Verb | Description |
|---|------|-------------|
| 1 | **Authenticates** | Users via Firebase Auth (Email/Password, Google OAuth) with dev/mock bypass |
| 2 | **Compresses** | Uploaded photos on the client (browser Canvas) to optimize upload speed and reduce payload |
| 3 | **Reports** | Missing persons via atomic multipart submission (metadata + 1–5 compressed photos) |
| 4 | **Monitors** | System-wide search status, case statistics, and live CCTV sightings in real time |
| 5 | **Inspects** | Sighting evidence with side-by-side facial comparison (sighting crop vs. registered reference photo) |
| 6 | **Reviews** | CCTV matches with operator/user actions (confirm verified match or reject false positive) |
| 7 | **Maps** | Chronological CCTV sighting breadcrumbs on an interactive Leaflet GPS map |
| 8 | **Alerts** | Users via real-time Server-Sent Events (SSE) with audio chime and toast notifications |
| 9 | **Manages** | User case portfolios (view, filter by status, add photos, mark found, close case) |
| 10 | **Localizes** | Entire user interface dynamically between English and Hindi (हिन्दी) |

### Tech Stack

| Layer | Technology | Version | Role |
|---|---|---|---|
| Build / Bundler | Vite | 5.4+ | Fast development server & optimized production build |
| UI Framework | React + TypeScript | 18.3+ / 5.5+ | Component architecture, type safety, state management |
| Styling | Vanilla CSS (CSS Variables) | Modern | Glassmorphism dark theme, custom responsive grid, zero external CSS bloat |
| Icons | Lucide React | 0.441+ | Modern, crisp UI icons |
| Authentication | Firebase Web SDK (Modular) | 10.13+ | Client-side auth, token management, Google OAuth |
| HTTP Client | Axios | 1.7+ | Backend communication, Bearer token interceptor, 401 refresh |
| Real-time Stream | EventSource (Browser Native) | — | SSE client for live sighting notifications |
| Map Engine | Leaflet + React-Leaflet | 1.9+ / 4.2+ | Interactive camera GPS markers and movement breadcrumb polyline |
| Deployment (Dev) | Vite Dev Server | 5.4+ | Hot Module Replacement (HMR) on port 5173 |
| Deployment (Prod) | Nginx Alpine / FastAPI Static Mount | 1.25+ / 0.110+ | Production SPA serving (port 3000 or unified port 8000) |

### Build Instruction

> **Build this exactly. Do not skip anything. Do not simplify. Do not substitute libraries.** All Flutter mobile screens (`login_screen`, `home_screen`, `report_form_screen`, `my_reports_screen`, `report_detail_screen`, `sighting_detail_screen`, `report_timeline_screen`, `notifications_screen`, `profile_screen`) must have direct web parity in `web_app/src/pages/`.

---

## 1. File Structure — Complete Project Tree

```
web_app/
├── .env.example                                ← Environment variables template
├── .gitignore                                  ← Excludes node_modules, dist, .env.local
├── package.json                                ← Pinned dependencies and build scripts
├── tsconfig.json                               ← TypeScript compiler configuration
├── tsconfig.node.json                          ← TypeScript config for Vite tooling
├── vite.config.ts                              ← Vite config with dev proxy to FastAPI backend
├── index.html                                  ← Single-page application HTML entrypoint
├── Dockerfile                                  ← Multi-stage build (Node 20 → Nginx Alpine)
├── nginx.conf                                  ← Nginx SPA routing and backend proxy config
├── public/
│   ├── favicon.svg                             ← Portal badge icon
│   └── chime.mp3                               ← Subtle audio notification chime for sightings
└── src/
    ├── main.tsx                                ← React root DOM mounting
    ├── App.tsx                                 ← Top-level router, layouts, toast & SSE wrappers
    ├── index.css                               ← Design tokens, CSS variables, dark glassmorphic styling
    │
    ├── config/
    │   ├── constants.ts                        ← API base URLs, endpoints, timeouts, upload constraints
    │   ├── firebase.ts                         ← Firebase app initialization & auth exports
    │   └── i18n.ts                             ← Bilingual translation dictionaries (English & Hindi)
    │
    ├── types/
    │   ├── api.ts                              ← Backend request/response TypeScript interfaces
    │   ├── auth.ts                             ← User profile, auth state, and credential types
    │   ├── report.ts                           ← MissingPerson, Photo, CaseStatus, Gender types
    │   ├── sighting.ts                         ← Sighting, Timeline, ConfidenceLevel types
    │   └── notification.ts                     ← Notification item and unread count types
    │
    ├── context/
    │   ├── AuthContext.tsx                     ← Authentication state provider & session persistence
    │   ├── LanguageContext.tsx                 ← English/Hindi language toggle provider
    │   └── ToastContext.tsx                    ← Global toast notifications queue and dispatcher
    │
    ├── services/
    │   ├── api.ts                              ← Axios instance with Bearer token & refresh interceptor
    │   ├── authService.ts                      ← Login, register, Google OAuth, password reset, mock auth
    │   ├── imageService.ts                     ← Browser Canvas photo compression & EXIF orientation
    │   ├── sseService.ts                       ← EventSource SSE subscriber with auto-reconnect & chime
    │   └── mapService.ts                       ← Leaflet marker icons, bounds calculator, GPS formatting
    │
    ├── components/
    │   ├── layout/
    │   │   ├── Navbar.tsx                      ← Header with brand, nav tabs, status pill, user menu
    │   │   ├── Footer.tsx                      ← Minimal footer with copyright & API docs link
    │   │   └── ProtectedRoute.tsx              ← Auth guard redirecting unauthenticated users to /login
    │   │
    │   ├── common/
    │   │   ├── StatusBadge.tsx                 ← Color-coded pill badge for case/sighting statuses
    │   │   ├── SimilarityGauge.tsx             ← Radial or progress bar for biometric confidence %
    │   │   ├── ToastContainer.tsx              ← Toast alert display stack
    │   │   ├── ConfirmModal.tsx                ← Reusable confirmation modal dialog
    │   │   └── LoadingSpinner.tsx              ← Consistent loading indicator
    │   │
    │   ├── forms/
    │   │   ├── ImageDropzone.tsx               ← Drag & drop photo uploader with preview & remove
    │   │   └── FormField.tsx                   ← Styled form group with label, input, and error message
    │   │
    │   └── reports/
    │       ├── ReportCard.tsx                  ← Missing person case card with photo, badges, metadata
    │       ├── SightingCard.tsx                ← CCTV sighting card with thumbnail and similarity
    │       └── TimelineMap.tsx                 ← Leaflet interactive map with camera GPS breadcrumbs
    │
    └── pages/
        ├── LoginPage.tsx                       ← Login with email/password, Google OAuth, demo mode
        ├── RegisterPage.tsx                    ← New citizen account registration
        ├── ForgotPasswordPage.tsx              ← Password reset email trigger
        ├── DashboardPage.tsx                   ← System metrics, quick actions, live CCTV sighting feed
        ├── MyReportsPage.tsx                   ← User's filed reports with status tabs & search
        ├── NewReportPage.tsx                   ← Multi-photo atomic report creation form
        ├── ReportDetailPage.tsx                ← Full case view, photo gallery, status actions, sightings
        ├── SightingDetailPage.tsx              ← Forensic split-view, zoom/pan full frame, review buttons
        ├── TimelinePage.tsx                    ← Full-screen interactive movement timeline & GPS map
        ├── NotificationsPage.tsx               ← Alerts inbox with unread filter and mark-read actions
        └── ProfilePage.tsx                     ← User profile details, name/phone editor, language switch
```

---

## 2. Environment / Secrets

### `.env.example` Template

```ini
# ── Application Environment ──
VITE_APP_NAME="FIND-MISSING-PEP Portal"
VITE_APP_ENV="development"

# ── Backend API Configuration ──
# In dev: Points to local FastAPI backend (proxied through Vite or directly)
# In prod: Set to https://api.yourdomain.com or leave blank for same-origin
VITE_API_BASE_URL="http://localhost:8000"

# ── Firebase Web SDK Configuration ──
# Obtain from: Firebase Console -> Project Settings -> General -> Your Apps -> Web App
VITE_FIREBASE_API_KEY="AIzaSyDummyKeyForDevelopment12345678"
VITE_FIREBASE_AUTH_DOMAIN="find-missing-pep.firebaseapp.com"
VITE_FIREBASE_PROJECT_ID="find-missing-pep"
VITE_FIREBASE_STORAGE_BUCKET="find-missing-pep.appspot.com"
VITE_FIREBASE_MESSAGING_SENDER_ID="123456789012"
VITE_FIREBASE_APP_ID="1:123456789012:web:abcdef1234567890"

# ── Feature Flags & Development Bypasses ──
# Set to 'true' to allow 1-click Dev Mock Login without requiring Firebase setup
VITE_ENABLE_MOCK_AUTH="true"
VITE_DEFAULT_MOCK_TOKEN="mock-token-admin"

# ── Map Tile Configuration ──
# OpenStreetMap standard tile URL (no API key required)
VITE_OSM_TILE_URL="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
VITE_OSM_ATTRIBUTION='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
```

### Settings Precedence Chain

```
Runtime Window Config (`window.__APP_CONFIG__` injected by Nginx/FastAPI)
  ▲
  │ (overrides)
Build-time `.env.local`
  ▲
  │ (overrides)
Build-time `.env.production` or `.env.development`
  ▲
  │ (overrides)
Code Defaults (in `src/config/constants.ts`)
```

### `.gitignore` Rules

```gitignore
node_modules/
dist/
dist-ssr/
*.local
.env.local
.env.development.local
.env.test.local
.env.production.local
.DS_Store
Thumbs.db
```

---

## 3. Data Schema / Client Storage

The web application maintains state using **React Context + LocalStorage** for active session persistence, and consumes the backend database schema.

### Client Storage Layout

| Key | Storage Type | Structure | Purpose |
|---|---|---|---|
| `fmp_auth_token` | `localStorage` | String (JWT or mock token) | Injected into `Authorization: Bearer <token>` |
| `fmp_user_profile` | `localStorage` | JSON (`UserResponse`) | Fast page load hydration without waiting for `/users/me` |
| `fmp_language` | `localStorage` | `'en'` \| `'hi'` | Persists language preference across browser restarts |
| `fmp_draft_report` | `sessionStorage` | JSON (`MissingPersonCreate`) | Preserves report form progress across tab changes |
| `fmp_read_notifs` | `localStorage` | Array of UUIDs | Client-side tracking of recently dismissed alerts |

### Backend Database Alignment (Read/Write Entities)

All client actions map directly to PostgreSQL tables via REST APIs:
- **`users`**: Read profile via `GET /api/users/me`, updated via `PUT /api/users/me`.
- **`missing_persons`**: Read via `GET /api/reports/` (user reports) or `GET /api/reports/all` (public feed). Written via atomic `POST /api/reports/`.
- **`photos`**: Read via `GET /api/reports/{id}`. Additional photos uploaded via `POST /api/reports/{id}/photos`.
- **`sightings`**: Read via `GET /api/sightings/` and `GET /api/reports/{id}/sightings`. Reviewed via `PUT /api/sightings/{id}/confirm` and `PUT /api/sightings/{id}/reject`.
- **`notifications`**: Read via `GET /api/notifications/`. Marked read via `PUT /api/notifications/{id}/read` or `PUT /api/notifications/read-all`.

### Access Control Posture

- **Unauthenticated (Public)**: Can view login, registration, and password reset pages. Optional public missing person feed (`GET /api/reports/all`).
- **Authenticated User (Citizen/Family)**: Can file reports, upload photos, view own reports (`/api/reports/`), view sightings for own reports, confirm/reject sightings on own reports, view personal timeline, receive personal SSE events.
- **Admin / Operator**: Has access to all reports, all CCTV sightings, Edge Agent enrollment keys, and system diagnostics.

---

## 4. Dependencies

### `package.json`

```json
{
  "name": "find-missing-pep-web",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview",
    "lint": "eslint src --ext ts,tsx --report-unused-disable-directives --max-warnings 0"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.26.2",
    "axios": "^1.7.7",
    "firebase": "^10.13.2",
    "lucide-react": "^0.441.0",
    "leaflet": "^1.9.4",
    "react-leaflet": "^4.2.1"
  },
  "devDependencies": {
    "@types/react": "^18.3.5",
    "@types/react-dom": "^18.3.0",
    "@types/leaflet": "^1.9.12",
    "@vitejs/plugin-react": "^4.3.1",
    "typescript": "^5.5.4",
    "vite": "^5.4.3"
  }
}
```

---

## 5. Containerization / Runtime

### `Dockerfile` (Multi-Stage Build)

```dockerfile
# ── Stage 1: Build Web App ──
FROM node:20-alpine AS builder

WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
RUN npm run build

# ── Stage 2: Serve via Nginx Alpine ──
FROM nginx:1.25-alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```

### `nginx.conf` (SPA Fallback + Reverse Proxy)

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    # Gzip compression
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;

    # Static assets caching
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, no-transform";
    }

    # API reverse proxy to FastAPI backend
    location /api/ {
        proxy_pass http://backend:8000/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Uploads reverse proxy (face crops, scene photos)
    location /uploads/ {
        proxy_pass http://backend:8000/uploads/;
        proxy_set_header Host $host;
    }

    # SSE Event stream reverse proxy (disables buffering)
    location /events/ {
        proxy_pass http://backend:8000/events/;
        proxy_set_header Host $host;
        proxy_buffering off;
        proxy_cache off;
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding off;
    }

    # SPA routing: all non-file routes fall back to index.html
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

### Port Ownership Map

| Process | Dev Port | Prod Port | Notes |
|---|---|---|---|
| Web App (Vite Dev Server) | `5173` | — | Proxies `/api` and `/uploads` to 8000 |
| Web App (Nginx Prod) | — | `3000` or `80` | Serves static bundle and proxies API |
| Backend API (FastAPI) | `8000` | `8000` | Serves REST endpoints, SSE, and static `/uploads` |
| Unified Mode (Alternative) | — | `8000` | FastAPI mounts `/` to `web_app/dist` directly |

---

## 6. Domain Config & Localization Templates

### Bilingual Dictionary (`src/config/i18n.ts`)

```typescript
export const translations = {
  en: {
    appTitle: "FIND-MISSING-PEP",
    appSubtitle: "AI Biometric CCTV & Missing Person Command Center",
    nav: {
      dashboard: "Dashboard",
      myReports: "My Reports",
      newReport: "Report Missing Person",
      alerts: "Alerts & Sightings",
      profile: "Profile"
    },
    status: {
      ALL: "All",
      ACTIVE: "Active Search",
      FOUND: "Person Found",
      PROCESSING: "Processing AI",
      CLOSED: "Case Closed",
      REJECTED_NO_FACE: "Photo Rejected (No Face)"
    },
    confidence: {
      CONFIRMED: "Verified Match",
      POSSIBLE: "Possible Match",
      REJECTED: "False Positive"
    },
    stats: {
      activeSearches: "Active Searches",
      cctvMatches: "CCTV Matches",
      totalCases: "Total Cases",
      personsFound: "Persons Found"
    },
    form: {
      fullName: "Full Name",
      age: "Age",
      gender: "Gender",
      male: "Male",
      female: "Female",
      other: "Other",
      height: "Height (cm)",
      lastSeenLocation: "Last Seen Location",
      lastSeenTime: "Last Seen Date & Time",
      contactPhone: "Emergency Contact Phone",
      description: "Identifying Features & Clothes",
      photoUploadTitle: "Upload Face Photos (1–5 photos)",
      photoUploadSub: "Frontal, well-lit photos yield highest biometric accuracy",
      submitButton: "Submit Case & Extract AI Embeddings",
      submitting: "Processing Faces & Syncing..."
    },
    sighting: {
      matchDetails: "CCTV Sighting Match Details",
      similarityScore: "Facial Similarity Match",
      cameraName: "Camera Feed",
      cameraLocation: "Camera Location",
      spottedAt: "Spotted At",
      framesMatched: "Frames Verified",
      comparisonTitle: "Forensic Face Comparison",
      sightingFace: "CCTV Crop (112×112)",
      referenceFace: "Registered Reference Face",
      fullScene: "Full CCTV Scene Capture",
      confirmMatch: "Confirm Verified Match",
      rejectMatch: "Reject False Positive",
      reviewNotes: "Review Notes (Optional)"
    },
    auth: {
      signIn: "Sign In",
      register: "Create Account",
      email: "Email Address",
      password: "Password",
      confirmPassword: "Confirm Password",
      forgotPassword: "Forgot Password?",
      googleSignIn: "Sign in with Google",
      devLogin: "1-Click Dev Mock Login",
      signOut: "Sign Out"
    }
  },
  hi: {
    appTitle: "लापता व्यक्ति खोज पोर्टल",
    appSubtitle: "एआई बायोमेट्रिक सीसीटीवी व लापता व्यक्ति रिपोर्टिंग केंद्र",
    nav: {
      dashboard: "डैशबोर्ड",
      myReports: "मेरी रिपोर्टें",
      newReport: "लापता व्यक्ति रिपोर्ट करें",
      alerts: "अलर्ट्स व सूचनाएं",
      profile: "प्रोफ़ाइल"
    },
    status: {
      ALL: "सभी",
      ACTIVE: "सक्रिय खोज",
      FOUND: "मिल गया",
      PROCESSING: "एआई प्रक्रियाधीन",
      CLOSED: "केस बंद",
      REJECTED_NO_FACE: "फोटो अस्वीकृत (चेहरा नहीं मिला)"
    },
    confidence: {
      CONFIRMED: "पुष्टि किया गया मैच",
      POSSIBLE: "संभावित मैच",
      REJECTED: "गलत पहचान (रद्द)"
    },
    stats: {
      activeSearches: "सक्रिय खोजें",
      cctvMatches: "सीसीटीवी मैच",
      totalCases: "कुल मामले",
      personsFound: "सुरक्षित मिले व्यक्ति"
    },
    form: {
      fullName: "पूरा नाम",
      age: "उम्र",
      gender: "लिंग",
      male: "पुरुष",
      female: "महिला",
      other: "अन्य",
      height: "कद (सेमी)",
      lastSeenLocation: "अंतिम बार देखे जाने का स्थान",
      lastSeenTime: "अंतिम बार देखे जाने का समय",
      contactPhone: "आपातकालीन संपर्क फ़ोन",
      description: "पहचान के निशान व कपड़ों का विवरण",
      photoUploadTitle: "चेहरे की तस्वीरें अपलोड करें (1–5)",
      photoUploadSub: "सामने से ली गई साफ फोटो से सबसे सटीक पहचान होती है",
      submitButton: "केस सबमिट करें और एआई एम्बेडिंग बनाएं",
      submitting: "चेहरे का विश्लेषण हो रहा है..."
    },
    sighting: {
      matchDetails: "सीसीटीवी साइटिंग मैच विवरण",
      similarityScore: "चेहरे की समानता",
      cameraName: "कैमरा",
      cameraLocation: "कैमरा स्थान",
      spottedAt: "देखे जाने का समय",
      framesMatched: "सत्यापित फ्रेम",
      comparisonTitle: "फोरेंसिक चेहरा तुलना",
      sightingFace: "सीसीटीवी फेस क्रॉप",
      referenceFace: "दर्ज कराया गया मूल चेहरा",
      fullScene: "पूरा सीसीटीवी दृश्य",
      confirmMatch: "मैच की पुष्टि करें",
      rejectMatch: "गलत मैच को अस्वीकार करें",
      reviewNotes: "समीक्षा टिप्पणी (वैकल्पिक)"
    },
    auth: {
      signIn: "लॉग इन करें",
      register: "नया खाता बनाएं",
      email: "ईमेल पता",
      password: "पासवर्ड",
      confirmPassword: "पासवर्ड की पुष्टि करें",
      forgotPassword: "पासवर्ड भूल गए?",
      googleSignIn: "Google से लॉग इन करें",
      devLogin: "1-क्लिक देव लॉगिन",
      signOut: "लॉग आउट करें"
    }
  }
};
```

---

## 7. Data Access Layer — API Client

All backend interactions are consolidated in `src/services/api.ts` using Axios, mirroring Section 7 of the template: one function = one API mutation/query.

```typescript
// src/services/api.ts
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { APP_CONSTANTS } from '../config/constants';
import { authService } from './authService';
import { 
  MissingPersonResponse, 
  MissingPersonListResponse, 
  SightingResponse, 
  PersonTimeline, 
  NotificationListResponse,
  UserResponse 
} from '../types/api';

export const apiClient = axios.create({
  baseURL: APP_CONSTANTS.API_BASE_URL,
  timeout: 30000,
  headers: {
    'Accept': 'application/json',
  },
});

// ── Bearer Token & Refresh Interceptor ──
apiClient.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    const token = await authService.getIdToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        const freshToken = await authService.getIdToken(true);
        if (freshToken) {
          originalRequest.headers.Authorization = `Bearer ${freshToken}`;
          return apiClient(originalRequest);
        }
      } catch (refreshErr) {
        authService.signOut();
      }
    }
    return Promise.reject(error);
  }
);

// ── Reports API ──
export const reportsApi = {
  createAtomic: async (formData: FormData): Promise<MissingPersonResponse> => {
    const res = await apiClient.post<MissingPersonResponse>('/api/reports/', formData);
    return res.data;
  },
  getMyReports: async (status?: string, page = 1, limit = 50): Promise<MissingPersonListResponse> => {
    const res = await apiClient.get<MissingPersonListResponse>('/api/reports/', {
      params: { status: status === 'ALL' ? undefined : status, page, limit }
    });
    return res.data;
  },
  getAllReports: async (status?: string, page = 1, limit = 50): Promise<MissingPersonListResponse> => {
    const res = await apiClient.get<MissingPersonListResponse>('/api/reports/all', {
      params: { status: status === 'ALL' ? undefined : status, page, limit }
    });
    return res.data;
  },
  getById: async (id: string): Promise<MissingPersonResponse> => {
    const res = await apiClient.get<MissingPersonResponse>(`/api/reports/${id}`);
    return res.data;
  },
  update: async (id: string, data: Record<string, unknown>): Promise<MissingPersonResponse> => {
    const res = await apiClient.put<MissingPersonResponse>(`/api/reports/${id}`, data);
    return res.data;
  },
  close: async (id: string): Promise<void> => {
    await apiClient.delete(`/api/reports/${id}`);
  },
  uploadPhoto: async (id: string, formData: FormData): Promise<unknown> => {
    const res = await apiClient.post(`/api/reports/${id}/photos`, formData);
    return res.data;
  },
  getSightings: async (id: string): Promise<SightingResponse[]> => {
    const res = await apiClient.get<SightingResponse[]>(`/api/reports/${id}/sightings`);
    return res.data;
  },
  getTimeline: async (id: string): Promise<PersonTimeline> => {
    const res = await apiClient.get<PersonTimeline>(`/api/reports/${id}/timeline`);
    return res.data;
  },
};

// ── Sightings API ──
export const sightingsApi = {
  listRecent: async (limit = 50, status?: string): Promise<SightingResponse[]> => {
    const res = await apiClient.get<SightingResponse[]>('/api/sightings/', {
      params: { limit, status_filter: status }
    });
    return res.data;
  },
  getById: async (id: string): Promise<SightingResponse> => {
    const res = await apiClient.get<SightingResponse>(`/api/sightings/${id}`);
    return res.data;
  },
  confirm: async (id: string, notes?: string): Promise<SightingResponse> => {
    const res = await apiClient.put<SightingResponse>(`/api/sightings/${id}/confirm`, {
      review_notes: notes
    });
    return res.data;
  },
  reject: async (id: string, notes?: string): Promise<SightingResponse> => {
    const res = await apiClient.put<SightingResponse>(`/api/sightings/${id}/reject`, {
      review_notes: notes
    });
    return res.data;
  },
};

// ── Notifications API ──
export const notificationsApi = {
  list: async (unreadOnly = false, limit = 50): Promise<NotificationListResponse> => {
    const res = await apiClient.get<NotificationListResponse>('/api/notifications/', {
      params: { unread_only: unreadOnly, limit }
    });
    return res.data;
  },
  getUnreadCount: async (): Promise<number> => {
    const res = await apiClient.get<{ unread_count: number }>('/api/notifications/unread-count');
    return res.data.unread_count;
  },
  markRead: async (id: string): Promise<void> => {
    await apiClient.put(`/api/notifications/${id}/read`);
  },
  markAllRead: async (): Promise<void> => {
    await apiClient.put('/api/notifications/read-all');
  },
};

// ── User API ──
export const usersApi = {
  getProfile: async (): Promise<UserResponse> => {
    const res = await apiClient.get<UserResponse>('/api/users/me');
    return res.data;
  },
  updateProfile: async (data: { name?: string; phone?: string; language?: string }): Promise<UserResponse> => {
    const res = await apiClient.put<UserResponse>('/api/users/me', data);
    return res.data;
  },
  sync: async (firebaseUid: string, name: string, email?: string): Promise<UserResponse> => {
    const res = await apiClient.post<UserResponse>('/api/users/', {
      firebase_uid: firebaseUid,
      name,
      email,
    });
    return res.data;
  },
};
```

---

## 8. Action / Tool Layer — Services

### 8.1 Firebase & Mock Authentication Service (`authService.ts`)

```typescript
// src/services/authService.ts
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
import { APP_CONSTANTS } from '../config/constants';
import { usersApi } from './api';

export class AuthService {
  private currentUser: FirebaseUser | null = null;
  private mockUserToken: string | null = localStorage.getItem('fmp_auth_token');

  constructor() {
    if (auth) {
      onAuthStateChanged(auth, (user) => {
        this.currentUser = user;
        if (user) {
          user.getIdToken().then((token) => {
            localStorage.setItem('fmp_auth_token', token);
          });
        }
      });
    }
  }

  async getIdToken(forceRefresh = false): Promise<string | null> {
    if (this.currentUser) {
      return await this.currentUser.getIdToken(forceRefresh);
    }
    return localStorage.getItem('fmp_auth_token');
  }

  async loginWithEmail(email: string, pass: string): Promise<void> {
    if (!auth) throw new Error("Firebase not initialized");
    const cred = await signInWithEmailAndPassword(auth, email.trim(), pass);
    this.currentUser = cred.user;
    const token = await cred.user.getIdToken();
    localStorage.setItem('fmp_auth_token', token);
    await usersApi.sync(cred.user.uid, cred.user.displayName || email.split('@')[0], email);
  }

  async registerWithEmail(email: string, pass: string, name: string): Promise<void> {
    if (!auth) throw new Error("Firebase not initialized");
    const cred = await createUserWithEmailAndPassword(auth, email.trim(), pass);
    this.currentUser = cred.user;
    const token = await cred.user.getIdToken();
    localStorage.setItem('fmp_auth_token', token);
    await usersApi.sync(cred.user.uid, name.trim(), email);
  }

  async loginWithGoogle(): Promise<void> {
    if (!auth) throw new Error("Firebase not initialized");
    const provider = new GoogleAuthProvider();
    const cred = await signInWithPopup(auth, provider);
    this.currentUser = cred.user;
    const token = await cred.user.getIdToken();
    localStorage.setItem('fmp_auth_token', token);
    await usersApi.sync(cred.user.uid, cred.user.displayName || "Google User", cred.user.email || undefined);
  }

  loginWithMock(role = "admin"): void {
    const token = `mock-token-${role}`;
    localStorage.setItem('fmp_auth_token', token);
    localStorage.setItem('fmp_user_profile', JSON.stringify({
      id: "00000000-0000-0000-0000-000000000001",
      name: `Dev ${role.toUpperCase()}`,
      email: `${role}@dev.local`,
      firebase_uid: `uid_${token}`,
      language: "en"
    }));
  }

  async signOut(): Promise<void> {
    if (auth) {
      await fbSignOut(auth).catch(() => {});
    }
    this.currentUser = null;
    localStorage.removeItem('fmp_auth_token');
    localStorage.removeItem('fmp_user_profile');
    window.location.href = '/login';
  }

  async resetPassword(email: string): Promise<void> {
    if (!auth) throw new Error("Firebase not initialized");
    await sendPasswordResetEmail(auth, email.trim());
  }
}

export const authService = new AuthService();
```

### 8.2 Client-Side Image Compression (`imageService.ts`)

Converts high-resolution phone/camera photos (often 8–15MB) into high-quality, lightweight 1280px WebP/JPEG images (~200KB) directly inside the browser using HTML5 Canvas before uploading.

```typescript
// src/services/imageService.ts
export interface CompressedImage {
  file: File;
  previewUrl: string;
  originalSize: number;
  compressedSize: number;
}

export async function compressImage(file: File, maxDimension = 1280, quality = 0.85): Promise<CompressedImage> {
  const originalSize = file.size;

  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      let { width, height } = img;

      if (width > height) {
        if (width > maxDimension) {
          height = Math.round((height * maxDimension) / width);
          width = maxDimension;
        }
      } else {
        if (height > maxDimension) {
          width = Math.round((width * maxDimension) / height);
          height = maxDimension;
        }
      }

      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;

      const ctx = canvas.getContext('2d');
      if (!ctx) {
        reject(new Error("Unable to create canvas context"));
        return;
      }

      ctx.drawImage(img, 0, 0, width, height);

      canvas.toBlob(
        (blob) => {
          if (!blob) {
            reject(new Error("Image compression failed"));
            return;
          }
          const compressedFile = new File([blob], file.name.replace(/\.[^/.]+$/, ".jpg"), {
            type: 'image/jpeg',
            lastModified: Date.now(),
          });
          const previewUrl = URL.createObjectURL(blob);
          resolve({
            file: compressedFile,
            previewUrl,
            originalSize,
            compressedSize: compressedFile.size,
          });
        },
        'image/jpeg',
        quality
      );
    };

    img.onerror = () => reject(new Error("Failed to load image for compression"));
    img.src = URL.createObjectURL(file);
  });
}
```

### 8.3 Real-Time SSE Stream & Audio Chime (`sseService.ts`)

```typescript
// src/services/sseService.ts
import { APP_CONSTANTS } from '../config/constants';
import { authService } from './authService';

export interface SightingAlertEvent {
  sighting_id: string;
  similarity: number;
  camera_id: string;
  detected_at: string;
  face_crop_path: string;
  title: string;
  body: string;
}

export class SseService {
  private eventSource: EventSource | null = null;
  private chimeAudio: HTMLAudioElement;

  constructor() {
    this.chimeAudio = new Audio('/chime.mp3');
    this.chimeAudio.volume = 0.5;
  }

  async connect(onSighting: (event: SightingAlertEvent) => void): Promise<void> {
    const token = await authService.getIdToken();
    if (!token) return;

    if (this.eventSource) {
      this.eventSource.close();
    }

    const url = `${APP_CONSTANTS.API_BASE_URL}/events/stream?token=${encodeURIComponent(token)}`;
    this.eventSource = new EventSource(url);

    this.eventSource.addEventListener('sighting', (e: MessageEvent) => {
      try {
        const data: SightingAlertEvent = JSON.parse(e.data);
        this.playChime();
        onSighting(data);
      } catch (err) {
        console.error("Failed to parse SSE event payload", err);
      }
    });

    this.eventSource.onerror = () => {
      // Browser EventSource automatically attempts reconnection
      console.warn("SSE stream disconnected. Reconnecting...");
    };
  }

  playChime(): void {
    this.chimeAudio.play().catch(() => {
      // Handled if user hasn't interacted with DOM yet
    });
  }

  disconnect(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }
}

export const sseService = new SseService();
```

---

## 9. Orchestration / Entrypoint & Routing

### 9.1 Application Routes & Route Guards

```
/login                     ← Public: Email/Password, Google OAuth, 1-Click Dev login
/register                  ← Public: Create citizen account
/forgot-password           ← Public: Password recovery
/                          ← Protected: Redirects to /dashboard
/dashboard                 ← Protected: Key metrics, quick actions, live CCTV sighting feed
/reports                   ← Protected: User's filed reports, filterable by status
/reports/new               ← Protected: Multi-photo report submission form
/reports/:id               ← Protected: Case detail, photo gallery, status actions, sightings
/reports/:id/timeline      ← Protected: Interactive GPS movement map and timeline
/sightings/:id             ← Protected: Forensic face comparison & verification review
/notifications             ← Protected: Notification inbox with unread filters
/profile                   ← Protected: User profile info, name/phone editor, language switch
```

### 9.2 Route Guard Component (`ProtectedRoute.tsx`)

```typescript
// src/components/layout/ProtectedRoute.tsx
import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { LoadingSpinner } from '../common/LoadingSpinner';

export const ProtectedRoute: React.FC = () => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return <LoadingSpinner fullScreen text="Verifying authentication..." />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
};
```

---

## 10. API Layer — Backend REST Contract

All endpoints consumed by the web portal match the existing FastAPI backend:

| Method | Endpoint | Auth | Request Body | Response Model | Description |
|---|---|:---:|---|---|---|
| `POST` | `/api/auth/verify` | Bearer | Empty | `UserResponse` | Verifies ID token & auto-provisions user |
| `GET` | `/api/users/me` | Bearer | None | `UserResponse` | Fetches current user profile |
| `PUT` | `/api/users/me` | Bearer | JSON (`name`, `phone`, `language`) | `UserResponse` | Updates user profile attributes |
| `POST` | `/api/reports/` | Bearer | Multipart Form (`report_data` + `photos[]`) | `MissingPersonResponse` | **Atomic submission**: creates case + extracts 512-D ArcFace vectors |
| `GET` | `/api/reports/` | Bearer | Query (`status`, `page`, `limit`) | `MissingPersonListResponse` | Lists current user's submitted cases |
| `GET` | `/api/reports/all` | Optional | Query (`status`, `page`, `limit`) | `MissingPersonListResponse` | Global directory of active missing cases |
| `GET` | `/api/reports/{id}` | Optional | Path `id` | `MissingPersonResponse` | Case details with photos and statuses |
| `PUT` | `/api/reports/{id}` | Bearer | JSON (`full_name`, `age`, `description`, etc.) | `MissingPersonResponse` | Updates case metadata |
| `DELETE` | `/api/reports/{id}` | Bearer | Path `id` | `{ status: "success" }` | Soft-deletes case (`CLOSED`), issues tombstones to Edge Agents |
| `POST` | `/api/reports/{id}/photos` | Bearer | Multipart Form (`file`, `is_primary`) | `PhotoResponse` | Uploads extra photo, extracts embedding |
| `GET` | `/api/reports/{id}/sightings` | Bearer | Path `id` | `List[SightingResponse]` | All CCTV sightings logged for this person |
| `GET` | `/api/reports/{id}/timeline` | Bearer | Path `id` | `PersonTimeline` | Chronological GPS breadcrumb trail |
| `GET` | `/api/sightings/{id}` | Bearer | Path `id` | `SightingResponse` | Sighting details + evidence media paths |
| `PUT` | `/api/sightings/{id}/confirm` | Bearer | JSON (`review_notes`) | `SightingResponse` | Confirms sighting as verified match |
| `PUT` | `/api/sightings/{id}/reject` | Bearer | JSON (`review_notes`) | `SightingResponse` | Rejects false positive match |
| `GET` | `/api/notifications/` | Bearer | Query (`unread_only`, `limit`) | `NotificationListResponse` | User notification inbox |
| `GET` | `/api/notifications/unread-count`| Bearer | None | `{ unread_count: int }` | Header unread notification badge count |
| `PUT` | `/api/notifications/{id}/read`| Bearer | Path `id` | `NotificationResponse` | Marks single notification as read |
| `PUT` | `/api/notifications/read-all`| Bearer | None | `{ updated_count: int }` | Marks all notifications read |
| `GET` | `/events/stream` | Token | Query `?token=<id_token>` | `text/event-stream` | SSE real-time sighting stream |

---

## 11. Frontend / UI Spec

### 11.1 Screen Layout & Shape

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 🛡️ FIND-MISSING-PEP   [Dashboard] [My Reports] [New Report] [Alerts (2)]  🌐 [User ▾] │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  [🔍 Active: 4]       [⚡ Matches: 18]       [👥 Cases: 12]      [🎉 Found: 3]│
│                                                                             │
│  ┌────────────────────────────────────────┐  ┌───────────────────────────┐  │
│  │ 📡 Live CCTV Sighting Feed              │  │ ⚡ Quick Actions           │  │
│  │                                        │  │                           │  │
│  │ [Crop] Aarav Sharma — 89% Match        │  │ [+ Report Missing Person] │  │
│  │        Gate 2 Metro • 12:45 PM         │  │ [📁 Browse All Cases]     │  │
│  │        [Review Forensic Evidence ➔]    │  │                           │  │
│  │                                        │  │ 💡 Sighting Tips          │  │
│  │ [Crop] Priya Verma — 76% Match         │  │ Frontal photos improve    │  │
│  │        North Plaza CCTV • 11:20 AM     │  │ Edge recognition by 40%.  │  │
│  └────────────────────────────────────────┘  └───────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 11.2 Screen-by-Screen Specifications

#### Screen 1: Auth (`LoginPage.tsx`, `RegisterPage.tsx`)
- Card with tab switch: "Sign In" vs "Create Account".
- Email & password inputs with live validation, "Forgot Password?" modal.
- Google OAuth 1-click button (`signInWithPopup`).
- **1-Click Dev Mock Button**: Immediate login as `admin` or `citizen` without configuring external Firebase credentials during testing.

#### Screen 2: Dashboard (`DashboardPage.tsx`)
- 4 Stat Counter Cards (Active Searches, CCTV Matches, Total Cases, Persons Found).
- Live CCTV Sighting Feed: Shows thumbnail, person name, camera name, similarity score badge, and timestamp.
- Auto-polls every 5s or listens to live SSE event stream.
- Quick action button navigating to `/reports/new`.

#### Screen 3: My Reports (`MyReportsPage.tsx`)
- Filter tabs: `ALL`, `ACTIVE`, `FOUND`, `PROCESSING`, `CLOSED`.
- Search input matching against person name and location.
- Report Grid: Card with thumbnail photo, status badge, age, gender, last seen time/location, sightings count badge.
- Clicking any card opens `ReportDetailPage`.

#### Screen 4: New Report Form (`NewReportPage.tsx`)
- **Drag & Drop Image Dropzone**: Supports 1–5 photos.
- Automatic client-side canvas compression (max 1280px, quality 0.85).
- Preview pills with delete buttons and primary photo badge.
- Fields: Full Name*, Age*, Gender*, Height (cm), Last Seen Location*, Last Seen Date & Time*, Emergency Contact*, Clothing/Identifying Features.
- Submits as atomic `multipart/form-data` with progress spinner.
- Displays immediate validation message if backend detects no faces.

#### Screen 5: Report Detail (`ReportDetailPage.tsx`)
- Header with status badge and quick action buttons:
  - **Mark as Found**: Opens confirmation modal, updates status to `FOUND`.
  - **Close Case**: Soft-deletes case and deactivates embeddings on Edge Agents.
  - **Add Photos**: Modal to upload additional photos and extract new embeddings.
- Photo Gallery: Carousel of original photos side-by-side with AI-detected 112×112 face crops.
- Biographical info table (Age, Gender, Height, Last Seen, Emergency Contact).
- Sightings Tab: List of detected CCTV matches with thumbnail and similarity scores.
- Sighting Movement Timeline Button: Navigates to `/reports/:id/timeline`.

#### Screen 6: Forensic Sighting Review (`SightingDetailPage.tsx`)
- **Side-by-Side Face Comparison**:
  - Left: CCTV face crop extracted by Edge Agent (112×112).
  - Right: Reference photo face crop from initial missing person report.
- **Full Scene Viewer**: Full CCTV frame capture with interactive Pan & Zoom (inspect surrounding crowd/clothing).
- **Similarity Gauge**: Visual radial gauge (Green $\ge$ 75%, Amber 60–74%, Red <60%).
- Metadata Card: Camera name, camera location, latitude/longitude, timestamp, number of temporal frames matched (e.g. 3 consecutive frames).
- Action Bar:
  - **Confirm Verified Match**: Marks sighting status as `CONFIRMED`.
  - **Reject False Positive**: Marks sighting status as `REJECTED`.
  - Text field for optional operator review notes.

#### Screen 7: Interactive Movement Timeline & Map (`TimelinePage.tsx`)
- Full-screen Leaflet interactive map.
- Camera pins numbered in chronological order with popups showing camera name, address, timestamp, and match percentage.
- Connecting polyline (GPS breadcrumbs) showing estimated path of travel.
- Collapsible sidebar listing chronological timeline cards.
- Special highlighted marker for "Last Seen" location.

#### Screen 8: Alerts & Notifications Inbox (`NotificationsPage.tsx`)
- Filter chips: `All` vs `Unread`.
- "Mark All Read" action in header.
- List items showing icon, title, description, timestamp, and unread dot.
- Tapping item navigates directly to the relevant sighting or report.

#### Screen 9: Profile & Preferences (`ProfilePage.tsx`)
- User details card: Name, Email, Phone number.
- "Edit Profile" modal.
- **Language Switcher**: Toggles between English and Hindi (updates all UI text instantly).
- Sign out button with confirmation dialog.

---

## 12. Critical Architecture Rules — DO NOT DEVIATE

### Rule 1: Firebase Web SDK v10 Modular Syntax vs Deprecated Compat

> [!CAUTION]
> Do NOT use `firebase/compat` or `firebase.auth()`. Always use modern tree-shakeable modular imports.

```typescript
// ❌ WRONG: Deprecated compat library, inflates bundle size by 800KB
import firebase from 'firebase/compat/app';
import 'firebase/compat/auth';
const user = firebase.auth().currentUser;

// ✅ CORRECT: Modular Firebase v10 imports
import { initializeApp, getApps } from 'firebase/app';
import { getAuth, onAuthStateChanged } from 'firebase/auth';

export const app = !getApps().length ? initializeApp(firebaseConfig) : getApps()[0];
export const auth = getAuth(app);
```

---

### Rule 2: Explicit Multipart Boundary Handling in Axios

> [!WARNING]
> Do NOT manually set `Content-Type: multipart/form-data` in Axios headers without boundary. Let Axios and the browser generate the multipart boundary automatically.

```typescript
// ❌ WRONG: Strips browser multipart boundary, causing 422 Unprocessable Entity in FastAPI
await apiClient.post('/api/reports/', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
});

// ✅ CORRECT: Omit header or set to undefined so the browser appends boundary delimiter
await apiClient.post('/api/reports/', formData, {
  headers: { 'Content-Type': undefined }
});
```

---

### Rule 3: Client-Side Canvas Image Compression Before Multi-Upload

> [!IMPORTANT]
> Never upload raw camera photos (10–20MB each) directly from mobile browsers. Always compress via Canvas to max 1280px dimension and quality 0.85 before building `FormData`.

```typescript
// ❌ WRONG: Uploading raw 15MB mobile photos blocks the network and causes 413 Payload Too Large
for (const file of rawFiles) {
  formData.append('photos', file);
}

// ✅ CORRECT: Compress via browser canvas first
for (const file of rawFiles) {
  const compressed = await compressImage(file, 1280, 0.85);
  formData.append('photos', compressed.file);
}
```

---

### Rule 4: EventSource Token via Query Parameter for Browser SSE

> [!IMPORTANT]
> The browser native `EventSource` API does NOT allow custom `Authorization` HTTP headers. The backend's `/events/stream` endpoint supports `?token=...` specifically for this purpose.

```typescript
// ❌ WRONG: Browser EventSource constructor doesn't accept headers
const es = new EventSource('/events/stream', {
  headers: { Authorization: `Bearer ${token}` } // Ignored by browser!
});

// ✅ CORRECT: Pass token as query parameter
const es = new EventSource(`/events/stream?token=${encodeURIComponent(token)}`);
```

---

### Rule 5: Leaflet Container Size Invalidation on Tab/Modal Transitions

> [!IMPORTANT]
> When mounting Leaflet inside hidden tabs or modal dialogs, the map tile grid will render gray tiles unless `map.invalidateSize()` is called after the container becomes visible.

```typescript
// ❌ WRONG: Mounting Leaflet in hidden tab leaves container with 0x0 dimensions
<div style={{ display: activeTab === 'timeline' ? 'block' : 'none' }}>
  <MapContainer ... />
</div>

// ✅ CORRECT: Invalidate map size whenever visibility changes
useEffect(() => {
  if (activeTab === 'timeline' && mapInstanceRef.current) {
    setTimeout(() => {
      mapInstanceRef.current.invalidateSize();
    }, 150);
  }
}, [activeTab]);
```

---

### Rule 6: Auth State Hydration & Prevention of Redirect Loops

> [!WARNING]
> Never redirect to `/login` until `onAuthStateChanged` or local storage token check has completed its initial evaluation, otherwise users will flash or loop on refresh.

```typescript
// ❌ WRONG: Redirects immediately on page reload before Firebase restores session
if (!user) return <Navigate to="/login" />;

// ✅ CORRECT: Guard routes with loading state check
if (isLoading) return <LoadingSpinner fullScreen />;
if (!isAuthenticated) return <Navigate to="/login" replace />;
return <Outlet />;
```

---

## 13. Deployment

### Development Mode (Vite + FastAPI Backend)

```bash
# 1. Start the FastAPI backend
cd "c:\SSD WINDOW\code\FIND -MISSING PEP"
py -m uvicorn app.main:app --reload --port 8000

# 2. In a second terminal, install web dependencies and launch Vite
cd "c:\SSD WINDOW\code\FIND -MISSING PEP\web_app"
npm install
npm run dev

# 3. Open your browser at:
# http://localhost:5173
```

### Production Build & Standalone Nginx Container

```bash
# 1. Build the web app Docker container
cd "c:\SSD WINDOW\code\FIND -MISSING PEP\web_app"
docker build -t find-missing-pep-web:latest .

# 2. Run the Nginx container on port 3000
docker run -d --name fmp-web -p 3000:80 \
  --add-host=host.docker.internal:host-gateway \
  find-missing-pep-web:latest

# 3. Access in browser at:
# http://localhost:3000
```

### Alternative: Unified Backend Deployment (FastAPI Serves Web App Directly)

```bash
# 1. Build web app production bundle
cd "c:\SSD WINDOW\code\FIND -MISSING PEP\web_app"
npm run build

# 2. Copy production assets into backend static folder
xcopy /E /I /Y "dist\*" "..\backend\app\static\"

# 3. Access unified portal on single port:
# http://localhost:8000/
```

### Successful Startup Verification Log

When Vite launches successfully, the terminal displays:

```text
  VITE v5.4.3  ready in 240 ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
  ➜  press h + enter to show help
```

---

## 14. Known Gotchas Table

| Symptom | Root Cause | Fix |
|---|---|---|
| `CORS error: No 'Access-Control-Allow-Origin' header` | Frontend running on `:5173` while backend is on `:8000` without CORS allowance | Ensure FastAPI has `allow_origins=["*"]` or use Vite proxy configuration in `vite.config.ts`. |
| `422 Unprocessable Entity` on report submission | Manual `Content-Type: multipart/form-data` header stripped browser boundary | Remove explicit `Content-Type` header when passing `FormData` to Axios. |
| `413 Payload Too Large` on photo upload | Mobile photos uploaded raw without compression (>10MB) | Call `compressImage(file, 1280, 0.85)` before appending to `FormData`. |
| Gray/missing tiles in Leaflet timeline map | Leaflet initialized when container width/height was 0 | Call `map.invalidateSize()` after tab or modal animation completes (150ms delay). |
| Real-time alerts not appearing in browser | `EventSource` closed due to missing or expired token in query param | Ensure `sseService.connect()` passes fresh ID token in query parameter `?token=...`. |
| Audio chime fails on first sighting alert | Browser autoplay policy blocks unprompted audio playback | Audio is primed on user's first click anywhere in the application. |
| Google sign-in fails with `auth/popup-blocked` | Browser blocked pop-up window | Trigger `signInWithPopup` directly in the synchronous click handler without preceding `await`. |

---

## 15. Reference Data

### Status Enums & Color Tokens

| Entity | Enum Value | Label (EN) | Label (HI) | Badge Color | Hex Code |
|---|---|---|---|---|---|
| **Case** | `ACTIVE` | Active Search | सक्रिय खोज | Blue | `#38bdf8` |
| **Case** | `FOUND` | Person Found | मिल गया | Green | `#4ade80` |
| **Case** | `PROCESSING` | Processing AI | एआई प्रक्रियाधीन | Amber | `#fbbf24` |
| **Case** | `CLOSED` | Case Closed | केस बंद | Slate | `#94a3b8` |
| **Case** | `REJECTED_NO_FACE` | Photo Rejected | फोटो अस्वीकृत | Red | `#f87171` |
| **Sighting** | `PENDING` | Pending Review | समीक्षा लंबित | Amber | `#fbbf24` |
| **Sighting** | `CONFIRMED` | Verified Match | पुष्टि किया गया मैच | Green | `#4ade80` |
| **Sighting** | `REJECTED` | False Positive | गलत पहचान | Slate | `#64748b` |

### Design System Color Tokens (`src/index.css`)

```css
:root {
  --bg-main: #0b0f19;
  --bg-card: #111827;
  --bg-card-hover: #1e293b;
  --border-color: #1f2937;
  --border-focus: #38bdf8;
  
  --primary: #38bdf8;
  --primary-hover: #0284c7;
  --primary-glow: rgba(56, 189, 248, 0.2);
  
  --success: #4ade80;
  --success-glow: rgba(74, 222, 128, 0.2);
  
  --warning: #fbbf24;
  --error: #f87171;
  
  --text-primary: #f9fafb;
  --text-secondary: #9ca3af;
  --text-muted: #6b7280;
  
  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 16px;
  --radius-full: 9999px;
  
  --shadow-card: 0 4px 20px -2px rgba(0, 0, 0, 0.5);
}
```

---

## 16. Cost / Ops Reference

| Service | Free Tier Limit | Production Threshold | Cost at Scale |
|---|---|---|---|
| **Firebase Auth** | 50,000 monthly active users (MAU) | Plenty for citizen reporting MVP | $0.0055 / verification above 50k |
| **OpenStreetMap Tiles** | Free under fair-use tile usage policy | Cache tiles locally or use Mapbox if traffic > 100k views/day | $0.00 under fair use |
| **Client Hosting** | GitHub Pages / Cloudflare Pages / Vercel: Free | Unlimited bandwidth on Cloudflare Pages | $0.00 |
| **Client Compute** | Browser Canvas executes on user's device | Zero server CPU load for image resizing | $0.00 |

---

## 17. External API Integration Reference

### 17.1 Firebase Auth Web SDK v10 (Copy-Paste Snippet)

```typescript
import { initializeApp } from 'firebase/app';
import { getAuth, signInWithEmailAndPassword, signInWithPopup, GoogleAuthProvider } from 'firebase/auth';

const app = initializeApp({
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
});

export const auth = getAuth(app);
```

### 17.2 Leaflet Map with Breadcrumb Trail (Copy-Paste Snippet)

```tsx
import { MapContainer, TileLayer, Marker, Popup, Polyline } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';

export function SightingMap({ breadcrumbs }: { breadcrumbs: { lat: number; lng: number; camera: string }[] }) {
  const positions = breadcrumbs.map(b => [b.lat, b.lng] as [number, number]);
  const center = positions[0] || [28.6139, 77.2090]; // Default New Delhi

  return (
    <MapContainer center={center} zoom={13} style={{ height: '400px', width: '100%', borderRadius: '12px' }}>
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; OpenStreetMap contributors'
      />
      {breadcrumbs.map((b, i) => (
        <Marker key={i} position={[b.lat, b.lng]}>
          <Popup><strong>{b.camera}</strong><br/>Stop #{i + 1}</Popup>
        </Marker>
      ))}
      <Polyline positions={positions} color="#38bdf8" weight={4} dashArray="6, 8" />
    </MapContainer>
  );
}
```

---

## 18. One-Time Setup Sequence (Post-Deploy)

1. **Register Firebase Web App**:
   - Open [Firebase Console](https://console.firebase.google.com/) -> Select Project -> Project Settings.
   - Under "Your apps", click the **Web** icon (`</>`), name it `find-missing-pep-web`, and copy the configuration keys into `web_app/.env`.
2. **Authorize Authentication Providers**:
   - Navigate to **Authentication -> Sign-in method**.
   - Enable **Email/Password**.
   - Enable **Google** (add your domain `localhost` and your production domain to Authorized Domains).
3. **Configure FastAPI Backend CORS**:
   - Verify `backend/app/main.py` contains `allow_origins=["*"]` or includes your web app's domain.
4. **Seed or Test User Account**:
   - Click "1-Click Dev Mock Login" or register an account via `/register`.
   - Submit a test missing person report with a sample face photo.
   - Verify backend extracts a 512-D ArcFace vector and report status transitions to `ACTIVE`.
5. **Verify End-to-End Real-Time Alerting**:
   - Keep the web app open on `/dashboard`.
   - Trigger a simulated sighting or run `py -m edge_agent.main`.
   - Confirm the audible chime plays and a new sighting card appears in the live feed.

---

> **Contract Enforcement**: This specification provides all definitions required to build the Web Application without discovering missing requirements or substituting libraries mid-build. Proceed to build following Sections 0 through 18 in sequential order.
