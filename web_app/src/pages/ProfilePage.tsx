import React, { useState } from 'react';
import { Edit3, Globe, LogOut, Mail, Phone, Shield, User } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useLanguage } from '../context/LanguageContext';
import { useToast } from '../context/ToastContext';
import { usersApi } from '../services/api';
import { ConfirmModal } from '../components/common/ConfirmModal';

export const ProfilePage: React.FC = () => {
  const { user, signOut } = useAuth();
  const { language, setLanguage, t } = useLanguage();
  const { addToast } = useToast();
  const navigate = useNavigate();
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isSignOutOpen, setIsSignOutOpen] = useState(false);
  const [name, setName] = useState(user?.name || '');
  const [phone, setPhone] = useState(user?.phone || '');
  const [isSaving, setIsSaving] = useState(false);

  const handleSaveProfile = async () => {
    if (!name.trim()) return;
    setIsSaving(true);
    try {
      await usersApi.updateProfile({ name: name.trim(), phone: phone.trim() || undefined, language });
      addToast('Profile updated successfully!', 'success');
      setIsEditOpen(false);
    } catch {
      addToast('Failed to update profile', 'error');
    } finally {
      setIsSaving(false);
    }
  };

  const handleConfirmSignOut = async () => {
    await signOut();
    addToast('Signed out of FIND-MISSING-PEP.', 'info');
    navigate('/login');
  };

  return (
    <div className="main-content">
      <div className="section-header"><div><h2 className="section-title"><User size={20} aria-hidden="true" /> {t.profile.title}</h2><p className="section-subtitle">{t.profile.subtitle}</p></div></div>
      <div className="profile-stack">
        <div className="glass-panel profile-card">
          <div className="profile-card__header">
            <div className="profile-avatar">{user?.name?.charAt(0).toUpperCase() || 'U'}</div>
            <div className="profile-card__identity"><h3>{user?.name || 'Authorized User'}</h3><p>{user?.email || 'Logged in via Device Session'}</p><span><Shield size={12} aria-hidden="true" /> Verified Reporter</span></div>
            <button type="button" onClick={() => { setName(user?.name || ''); setPhone(user?.phone || ''); setIsEditOpen(true); }} className="btn btn-secondary btn-sm"><Edit3 size={15} aria-hidden="true" /> {t.profile.editProfile}</button>
          </div>
          <div className="profile-details">
            <div><span><Mail size={16} aria-hidden="true" /> {t.profile.email}</span><strong>{user?.email || 'Not provided'}</strong></div>
            <div><span><Phone size={16} aria-hidden="true" /> {t.profile.phone}</span><strong>{user?.phone || 'Not registered'}</strong></div>
            <div><span><Globe size={16} aria-hidden="true" /> {t.profile.language}</span><span className="language-choice"><button type="button" onClick={() => setLanguage('en')} className={`btn btn-sm ${language === 'en' ? 'btn-primary' : 'btn-secondary'}`}>English</button><button type="button" onClick={() => setLanguage('hi')} className={`btn btn-sm ${language === 'hi' ? 'btn-primary' : 'btn-secondary'}`}>हिन्दी</button></span></div>
          </div>
        </div>
        <div className="glass-panel session-card"><div><strong>Session Management</strong><p>Log out from this computer to end your session.</p></div><button type="button" onClick={() => setIsSignOutOpen(true)} className="btn btn-danger btn-sm"><LogOut size={16} aria-hidden="true" /> {t.nav.signOut}</button></div>
      </div>

      <ConfirmModal isOpen={isEditOpen} title={t.profile.editProfile} message="Update your account details." confirmText={t.profile.saveChanges} cancelText={t.profile.cancel} isLoading={isSaving} onConfirm={() => void handleSaveProfile()} onCancel={() => setIsEditOpen(false)}>
        <div className="modal-form"><div className="form-group"><label className="form-label" htmlFor="profile-name">{t.profile.fullName}</label><input id="profile-name" type="text" required className="form-control" value={name} onChange={(event) => setName(event.target.value)} /></div><div className="form-group"><label className="form-label" htmlFor="profile-phone">{t.profile.phone}</label><input id="profile-phone" type="text" className="form-control" placeholder="+91 9876543210" value={phone} onChange={(event) => setPhone(event.target.value)} /></div></div>
      </ConfirmModal>
      <ConfirmModal isOpen={isSignOutOpen} title={t.nav.signOut} message={t.profile.signOutPrompt} confirmText={t.nav.signOut} cancelText={t.profile.cancel} isDanger onConfirm={() => void handleConfirmSignOut()} onCancel={() => setIsSignOutOpen(false)} />
    </div>
  );
};
