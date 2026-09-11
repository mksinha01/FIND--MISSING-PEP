import React from 'react';
import { useLanguage } from '../../context/LanguageContext';
import { CaseStatus } from '../../types/report';
import { SightingStatus } from '../../types/sighting';

interface StatusBadgeProps {
  status: CaseStatus | SightingStatus | string;
  className?: string;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, className = '' }) => {
  const { t } = useLanguage();

  let badgeClass = 'badge-active';
  let label = status;

  switch (status) {
    case 'ACTIVE':
      badgeClass = 'badge-active';
      label = t.status.ACTIVE;
      break;
    case 'FOUND':
      badgeClass = 'badge-found';
      label = t.status.FOUND;
      break;
    case 'PROCESSING':
      badgeClass = 'badge-processing';
      label = t.status.PROCESSING;
      break;
    case 'CLOSED':
      badgeClass = 'badge-closed';
      label = t.status.CLOSED;
      break;
    case 'REJECTED_NO_FACE':
      badgeClass = 'badge-rejected';
      label = t.status.REJECTED_NO_FACE;
      break;
    case 'CONFIRMED':
      badgeClass = 'badge-found';
      label = t.status.CONFIRMED;
      break;
    case 'PENDING':
      badgeClass = 'badge-processing';
      label = t.status.PENDING;
      break;
    case 'REJECTED':
      badgeClass = 'badge-rejected';
      label = t.status.REJECTED;
      break;
  }

  return (
    <span className={`badge ${badgeClass} ${className}`}>
      {label}
    </span>
  );
};
