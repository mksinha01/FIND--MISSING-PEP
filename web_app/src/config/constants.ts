export const APP_CONSTANTS = {
  APP_NAME: import.meta.env.VITE_APP_NAME || 'FIND-MISSING-PEP Portal',
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL || '',
  DEFAULT_PAGE_SIZE: 20,
  MAX_PHOTOS: 5,
  MAX_PHOTO_DIMENSION: 1280,
  COMPRESSION_QUALITY: 0.85,
  ENABLE_MOCK_AUTH: import.meta.env.VITE_ENABLE_MOCK_AUTH === 'true',
  DEFAULT_MOCK_TOKEN: import.meta.env.VITE_DEFAULT_MOCK_TOKEN || 'mock-token-admin',
  OSM_TILE_URL: import.meta.env.VITE_OSM_TILE_URL || 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
  OSM_ATTRIBUTION: import.meta.env.VITE_OSM_ATTRIBUTION || '&copy; OpenStreetMap contributors',
};

export const ROUTES = {
  LOGIN: '/login',
  REGISTER: '/register',
  FORGOT_PASSWORD: '/forgot-password',
  DASHBOARD: '/dashboard',
  MY_REPORTS: '/reports',
  NEW_REPORT: '/reports/new',
  REPORT_DETAIL: (id: string) => `/reports/${id}`,
  TIMELINE: (id: string) => `/reports/${id}/timeline`,
  SIGHTING_DETAIL: (id: string) => `/sightings/${id}`,
  NOTIFICATIONS: '/notifications',
  PROFILE: '/profile',
};
