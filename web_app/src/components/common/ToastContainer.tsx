import React from 'react';
import { AlertTriangle, CheckCircle2, Info, X, XCircle } from 'lucide-react';
import { useToast } from '../../context/ToastContext';

export const ToastContainer: React.FC = () => {
  const { toasts, removeToast } = useToast();

  if (toasts.length === 0) return null;

  return (
    <div className="toast-container" aria-live="polite" aria-atomic="true">
      {toasts.map((toast) => {
        let Icon = CheckCircle2;
        let iconColor = 'var(--success)';

        if (toast.type === 'warning') {
          Icon = AlertTriangle;
          iconColor = 'var(--warning)';
        } else if (toast.type === 'error') {
          Icon = XCircle;
          iconColor = 'var(--error)';
        } else if (toast.type === 'info') {
          Icon = Info;
          iconColor = 'var(--accent)';
        }

        return (
          <div key={toast.id} className={`toast ${toast.type}`}>
            <Icon size={19} color={iconColor} className="toast__icon" aria-hidden="true" />
            <div className="toast__content">
              {toast.title && <div className="toast__title">{toast.title}</div>}
              <div className="toast__message">{toast.message}</div>
            </div>
            <button type="button" className="toast__close" onClick={() => removeToast(toast.id)} aria-label="Dismiss notification">
              <X size={16} aria-hidden="true" />
            </button>
          </div>
        );
      })}
    </div>
  );
};
