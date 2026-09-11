import React from 'react';
import { BrowserRouter as Router, Navigate, Route, Routes, Outlet } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { LanguageProvider } from './context/LanguageContext';
import { ToastProvider } from './context/ToastContext';

import { AuthenticatedLayout } from './components/layout/AuthenticatedLayout';
import { Footer } from './components/layout/Footer';
import { Navbar } from './components/layout/Navbar';
import { ProtectedRoute, PublicOnlyRoute } from './components/layout/ProtectedRoute';
import { ToastContainer } from './components/common/ToastContainer';

import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { DashboardPage } from './pages/DashboardPage';
import { MyReportsPage } from './pages/MyReportsPage';
import { NewReportPage } from './pages/NewReportPage';
import { ReportDetailPage } from './pages/ReportDetailPage';
import { SightingDetailPage } from './pages/SightingDetailPage';
import { TimelinePage } from './pages/TimelinePage';
import { NotificationsPage } from './pages/NotificationsPage';
import { ProfilePage } from './pages/ProfilePage';

const PublicLayout: React.FC = () => (
  <div className="public-shell">
    <Navbar />
    <main className="public-main">
      <Outlet />
    </main>
    <Footer />
  </div>
);

export const App: React.FC = () => {
  return (
    <LanguageProvider>
      <AuthProvider>
        <ToastProvider>
          <Router>
            <Routes>
              <Route element={<PublicOnlyRoute />}>
                <Route element={<PublicLayout />}>
                  <Route path="/login" element={<LoginPage />} />
                  <Route path="/register" element={<RegisterPage />} />
                  <Route path="/forgot-password" element={<ForgotPasswordPage />} />
                </Route>
              </Route>

              <Route element={<ProtectedRoute />}>
                <Route element={<AuthenticatedLayout />}>
                  <Route path="/" element={<Navigate to="/dashboard" replace />} />
                  <Route path="/dashboard" element={<DashboardPage />} />
                  <Route path="/reports" element={<MyReportsPage />} />
                  <Route path="/reports/new" element={<NewReportPage />} />
                  <Route path="/reports/:id" element={<ReportDetailPage />} />
                  <Route path="/reports/:id/timeline" element={<TimelinePage />} />
                  <Route path="/sightings/:id" element={<SightingDetailPage />} />
                  <Route path="/notifications" element={<NotificationsPage />} />
                  <Route path="/profile" element={<ProfilePage />} />
                </Route>
              </Route>

              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
            <ToastContainer />
          </Router>
        </ToastProvider>
      </AuthProvider>
    </LanguageProvider>
  );
};

export default App;
