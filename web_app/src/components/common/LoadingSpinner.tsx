import React from 'react';
import { Loader2 } from 'lucide-react';

interface LoadingSpinnerProps {
  size?: number;
  text?: string;
  fullScreen?: boolean;
}

export const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({ size = 32, text, fullScreen = false }) => {
  const content = (
    <div className="loading-content" role="status" aria-live="polite">
      <Loader2 size={size} color="var(--accent)" className="loading-icon" aria-hidden="true" />
      {text && <span className="loading-text">{text}</span>}
    </div>
  );

  if (fullScreen) {
    return <div className="loading-screen">{content}</div>;
  }

  return <div className="loading-wrap">{content}</div>;
};
