import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Shield, Mail, Lock, AlertCircle, ArrowRight, Sparkles } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';
import { useToast } from '../context/ToastContext';

export const LoginPage: React.FC = () => {
  const { loginWithEmail, loginWithGoogle, loginWithMock } = useAuth();
  const { t } = useLanguage();
  const { addToast } = useToast();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setError('Please fill in both email and password.');
      return;
    }

    setError(null);
    setIsLoading(true);

    try {
      await loginWithEmail(email, password);
      addToast('Welcome back! Successfully signed in.', 'success');
      navigate('/dashboard');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Authentication failed';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  const [showGoogleModal, setShowGoogleModal] = useState(false);
  const [newGoogleEmail, setNewGoogleEmail] = useState('');
  const [newGoogleName, setNewGoogleName] = useState('');

  const handleGoogleSignIn = async (customEmail?: string, customName?: string) => {
    setError(null);
    setIsLoading(true);
    try {
      await loginWithGoogle(customEmail, customName);
      addToast(customEmail ? `Signed in as ${customEmail}!` : 'Signed in with Google!', 'success');
      setShowGoogleModal(false);
      navigate('/dashboard');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Google sign-in failed';
      setError(msg);
      // If popup failed due to domain / network, open account picker modal
      setShowGoogleModal(true);
    } finally {
      setIsLoading(false);
    }
  };

  const handleDevMockLogin = async (role = "admin") => {
    setError(null);
    setIsLoading(true);
    try {
      await loginWithMock(role);
      addToast(`Logged in with mock developer session (${role})!`, 'info');
      navigate('/dashboard');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Mock login failed';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: 'calc(100vh - var(--header-height) - 100px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '2rem 1rem',
    }}>
      <div className="glass-panel" style={{
        maxWidth: 460,
        width: '100%',
        padding: '2.5rem',
        boxShadow: 'var(--shadow-lg)',
      }}>
        
        {/* Brand Icon & Heading */}
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <div style={{
            width: 56, height: 56, borderRadius: 'var(--radius-lg)',
            background: 'var(--primary-gradient)',
            margin: '0 auto 1rem',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: '0 0 24px rgba(56, 189, 248, 0.4)',
          }}>
            <Shield size={32} color="#fff" />
          </div>
          <h2 style={{ fontSize: '1.6rem', fontWeight: 800 }}>{t.auth.welcomeBack}</h2>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.86rem', marginTop: '0.35rem' }}>
            {t.auth.signInPrompt}
          </p>
        </div>

        {/* Error Alert */}
        {error && (
          <div style={{
            background: 'rgba(239, 68, 68, 0.12)',
            border: '1px solid rgba(239, 68, 68, 0.3)',
            borderRadius: 'var(--radius-md)',
            padding: '0.75rem 1rem',
            display: 'flex',
            alignItems: 'center',
            gap: '0.6rem',
            color: 'var(--error)',
            fontSize: '0.85rem',
            marginBottom: '1.25rem',
          }}>
            <AlertCircle size={18} style={{ flexShrink: 0 }} />
            <span>{error}</span>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label" htmlFor="login-email">{t.auth.emailLabel}</label>
            <div style={{ position: 'relative' }}>
              <Mail size={16} color="var(--text-muted)" style={{ position: 'absolute', left: 14, top: 14 }} />
              <input
                id="login-email"
                type="email"
                required
                className="form-control"
                placeholder="name@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                style={{ paddingLeft: '2.5rem' }}
              />
            </div>
          </div>

          <div className="form-group" style={{ marginBottom: '0.5rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <label className="form-label" htmlFor="login-password">{t.auth.passwordLabel}</label>
              <Link to="/forgot-password" style={{ fontSize: '0.78rem', color: 'var(--primary)' }}>
                {t.auth.forgotPasswordLink}
              </Link>
            </div>
            <div style={{ position: 'relative' }}>
              <Lock size={16} color="var(--text-muted)" style={{ position: 'absolute', left: 14, top: 14 }} />
              <input
                id="login-password"
                type="password"
                required
                className="form-control"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                style={{ paddingLeft: '2.5rem' }}
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="btn btn-primary"
            style={{ width: '100%', marginTop: '1.25rem', padding: '0.8rem' }}
          >
            {isLoading ? 'Signing In...' : t.auth.signInTab}
            <ArrowRight size={16} />
          </button>
        </form>

        {/* Divider */}
        <div style={{
          display: 'flex', alignItems: 'center', gap: '1rem',
          margin: '1.5rem 0', color: 'var(--text-muted)', fontSize: '0.78rem'
        }}>
          <div style={{ flex: 1, height: 1, background: 'var(--border-color)' }} />
          <span>{t.auth.orDivider}</span>
          <div style={{ flex: 1, height: 1, background: 'var(--border-color)' }} />
        </div>

        {/* Google Sign In */}
        <button
          type="button"
          onClick={() => setShowGoogleModal(true)}
          disabled={isLoading}
          className="btn btn-secondary"
          style={{ width: '100%', marginBottom: '0.4rem', padding: '0.75rem' }}
        >
          <svg width="18" height="18" viewBox="0 0 24 24">
            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
          </svg>
          <span>{t.auth.googleBtn}</span>
        </button>

        {/* Switch / New Google Account Option */}
        <div style={{ textAlign: 'center', marginBottom: '0.85rem' }}>
          <button
            type="button"
            onClick={() => setShowGoogleModal(true)}
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--primary)',
              fontSize: '0.78rem',
              cursor: 'pointer',
              textDecoration: 'underline',
              padding: '2px 6px',
            }}
          >
            Switch or Sign in with any Google Account
          </button>
        </div>

        {/* 1-Click Dev Mock Login */}
        <button
          type="button"
          onClick={() => handleDevMockLogin("admin")}
          disabled={isLoading}
          className="btn btn-secondary"
          style={{
            width: '100%',
            borderColor: 'rgba(56, 189, 248, 0.3)',
            backgroundColor: 'rgba(56, 189, 248, 0.06)',
            color: 'var(--primary)',
            padding: '0.7rem',
          }}
        >
          <Sparkles size={16} />
          <span>{t.auth.devMockBtn} (Admin)</span>
        </button>

        {/* Register Link */}
        <div style={{ textAlign: 'center', marginTop: '1.75rem', fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
          {t.auth.noAccount}{' '}
          <Link to="/register" style={{ fontWeight: 600, color: 'var(--primary)' }}>
            {t.auth.registerTab}
          </Link>
        </div>

      </div>

      {/* ── Google Account Selector Modal ── */}
      {showGoogleModal && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0, 0, 0, 0.75)',
          backdropFilter: 'blur(8px)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          padding: '1rem',
        }}>
          <div className="glass-panel" style={{
            maxWidth: 440,
            width: '100%',
            padding: '2rem',
            borderRadius: 'var(--radius-lg)',
            boxShadow: '0 20px 40px rgba(0, 0, 0, 0.5)',
            border: '1px solid rgba(56, 189, 248, 0.3)',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                <svg width="20" height="20" viewBox="0 0 24 24">
                  <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                  <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                  <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
                  <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
                </svg>
                <h3 style={{ fontSize: '1.2rem', fontWeight: 700, margin: 0 }}>Choose Google Account</h3>
              </div>
              <button
                type="button"
                onClick={() => setShowGoogleModal(false)}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--text-muted)',
                  fontSize: '1.4rem',
                  cursor: 'pointer',
                  lineHeight: 1,
                }}
              >
                &times;
              </button>
            </div>

            <p style={{ color: 'var(--text-secondary)', fontSize: '0.84rem', marginBottom: '1.25rem' }}>
              Select an account or enter a new Google account to sign in and sync with the database:
            </p>

            {/* Official Google OAuth Browser Popup */}
            <button
              type="button"
              disabled={isLoading}
              onClick={() => handleGoogleSignIn()}
              className="btn btn-primary"
              style={{
                width: '100%',
                marginBottom: '1rem',
                padding: '0.7rem 1rem',
                fontSize: '0.88rem',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '0.6rem',
              }}
            >
              <svg width="18" height="18" viewBox="0 0 24 24">
                <path fill="#ffffff" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                <path fill="#ffffff" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                <path fill="#ffffff" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"/>
                <path fill="#ffffff" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"/>
              </svg>
              <span>Google Account Picker (Browser Popup)</span>
            </button>

            <div style={{
              display: 'flex', alignItems: 'center', gap: '0.75rem',
              marginBottom: '1rem', color: 'var(--text-muted)', fontSize: '0.72rem', letterSpacing: '0.05em'
            }}>
              <div style={{ flex: 1, height: 1, background: 'var(--border-color)' }} />
              <span>OR INSTANT SYNC AS</span>
              <div style={{ flex: 1, height: 1, background: 'var(--border-color)' }} />
            </div>

            {/* Quick account buttons */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem', marginBottom: '1.5rem' }}>
              <button
                type="button"
                disabled={isLoading}
                onClick={() => handleGoogleSignIn("mksinha77756@gmail.com", "Mk Sinha")}
                className="btn btn-secondary"
                style={{
                  justifyContent: 'flex-start',
                  padding: '0.65rem 1rem',
                  fontSize: '0.85rem',
                  borderColor: 'rgba(56, 189, 248, 0.4)',
                }}
              >
                <div style={{ textAlign: 'left' }}>
                  <div style={{ fontWeight: 600, color: 'var(--primary)' }}>Mk Sinha</div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>mksinha77756@gmail.com</div>
                </div>
              </button>

              <button
                type="button"
                disabled={isLoading}
                onClick={() => handleGoogleSignIn("officer.admin@police.gov.in", "Inspector Sharma")}
                className="btn btn-secondary"
                style={{ justifyContent: 'flex-start', padding: '0.65rem 1rem', fontSize: '0.85rem' }}
              >
                <div style={{ textAlign: 'left' }}>
                  <div style={{ fontWeight: 600 }}>Inspector Sharma (Control Room)</div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>officer.admin@police.gov.in</div>
                </div>
              </button>

              <button
                type="button"
                disabled={isLoading}
                onClick={() => handleGoogleSignIn("citizen.user@example.com", "Aarav Patel")}
                className="btn btn-secondary"
                style={{ justifyContent: 'flex-start', padding: '0.65rem 1rem', fontSize: '0.85rem' }}
              >
                <div style={{ textAlign: 'left' }}>
                  <div style={{ fontWeight: 600 }}>Aarav Patel (Citizen Reporter)</div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>citizen.user@example.com</div>
                </div>
              </button>
            </div>

            {/* Or enter new account */}
            <div style={{
              borderTop: '1px solid var(--border-color)',
              paddingTop: '1.25rem',
            }}>
              <h4 style={{ fontSize: '0.9rem', fontWeight: 600, marginBottom: '0.75rem' }}>
                Use a New Google Account:
              </h4>

              <div className="form-group" style={{ marginBottom: '0.6rem' }}>
                <input
                  type="email"
                  className="form-control"
                  placeholder="Enter new Google email (e.g. name@gmail.com)"
                  value={newGoogleEmail}
                  onChange={(e) => setNewGoogleEmail(e.target.value)}
                  style={{ fontSize: '0.85rem', padding: '0.6rem 0.8rem' }}
                />
              </div>

              <div className="form-group" style={{ marginBottom: '0.85rem' }}>
                <input
                  type="text"
                  className="form-control"
                  placeholder="Display Name (e.g. Alex Sharma)"
                  value={newGoogleName}
                  onChange={(e) => setNewGoogleName(e.target.value)}
                  style={{ fontSize: '0.85rem', padding: '0.6rem 0.8rem' }}
                />
              </div>

              <div style={{ display: 'flex', gap: '0.6rem' }}>
                <button
                  type="button"
                  disabled={isLoading || !newGoogleEmail.trim()}
                  onClick={() => handleGoogleSignIn(newGoogleEmail.trim(), newGoogleName.trim() || undefined)}
                  className="btn btn-primary"
                  style={{ flex: 1, padding: '0.65rem', fontSize: '0.85rem' }}
                >
                  {isLoading ? 'Signing in...' : 'Sign In as New User'}
                </button>
                <button
                  type="button"
                  onClick={() => setShowGoogleModal(false)}
                  className="btn btn-secondary"
                  style={{ padding: '0.65rem 1rem', fontSize: '0.85rem' }}
                >
                  Cancel
                </button>
              </div>
            </div>

          </div>
        </div>
      )}

    </div>
  );
};
