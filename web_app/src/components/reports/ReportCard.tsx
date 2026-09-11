import React from 'react';
import { Calendar, Eye, MapPin, User } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useLanguage } from '../../context/LanguageContext';
import { APP_CONSTANTS } from '../../config/constants';
import { MissingPerson } from '../../types/report';
import { StatusBadge } from '../common/StatusBadge';

interface ReportCardProps {
  person: MissingPerson;
}

export const ReportCard: React.FC<ReportCardProps> = ({ person }) => {
  const navigate = useNavigate();
  const { t } = useLanguage();
  const photoUrl = person.primary_photo_url || person.photos?.[0]?.original_path;
  const fullPhotoSrc = photoUrl ? (photoUrl.startsWith('http') ? photoUrl : `${APP_CONSTANTS.API_BASE_URL}${photoUrl}`) : null;

  return (
    <article
      className="glass-panel glass-panel-hover report-card"
      onClick={() => navigate(`/reports/${person.id}`)}
      onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') navigate(`/reports/${person.id}`); }}
      role="link"
      tabIndex={0}
    >
      <div className="report-card__media">
        {fullPhotoSrc ? <img src={fullPhotoSrc} alt={person.full_name} loading="lazy" /> : <User size={42} aria-hidden="true" />}
        <span className="report-card__status"><StatusBadge status={person.status} /></span>
      </div>
      <div className="report-card__body">
        <h3 className="report-card__name">{person.full_name}</h3>
        <div className="report-card__age">
          {t.reports.ageGender.replace('{age}', person.age.toString()).replace('{gender}', person.gender)}
        </div>
        <div className="report-card__meta">
          <div><MapPin size={14} aria-hidden="true" /><span>{person.last_seen_location}</span></div>
          <div><Calendar size={14} aria-hidden="true" /><span>{new Date(person.last_seen_time).toLocaleDateString()}</span></div>
        </div>
        <div className="report-card__footer">
          <span>{t.reports.sightingsCount}</span>
          <span><Eye size={13} aria-hidden="true" /> {person.photos?.length || 0} Photos Linked</span>
        </div>
      </div>
    </article>
  );
};
