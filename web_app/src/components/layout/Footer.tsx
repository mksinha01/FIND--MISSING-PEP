import React from 'react';
import { Shield } from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';

export const Footer: React.FC = () => {
  const { t } = useLanguage();

  return (
    <footer className="app-footer">
      <div className="app-footer__brand">
        <Shield size={17} aria-hidden="true" />
        <strong>FIND-MISSING-PEP</strong>
        <span>— {t.appSubtitle}</span>
      </div>
      <div className="app-footer__meta">
        <a href="/docs" target="_blank" rel="noopener noreferrer">FastAPI Docs</a>
        <span aria-hidden="true">•</span>
        <span>Edge Biometric CCTV Network</span>
        <span aria-hidden="true">•</span>
        <span>© 2026 FIND-MISSING-PEP</span>
      </div>
    </footer>
  );
};
