import React from 'react';
import { ArrowRight, Camera, Clock, MapPin } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useLanguage } from '../../context/LanguageContext';
import { APP_CONSTANTS } from '../../config/constants';
import { Sighting } from '../../types/sighting';
import { StatusBadge } from '../common/StatusBadge';

interface SightingCardProps {
  sighting: Sighting;
  personName?: string;
}

export const SightingCard: React.FC<SightingCardProps> = ({ sighting, personName }) => {
  const navigate = useNavigate();
  const { t } = useLanguage();
  const faceUrl = sighting.face_crop_path
    ? (sighting.face_crop_path.startsWith('http') ? sighting.face_crop_path : `${APP_CONSTANTS.API_BASE_URL}${sighting.face_crop_path}`)
    : '/favicon.svg';
  const percentage = Math.round(sighting.similarity_score * 100);
  const isHighConfidence = percentage >= 75;

  return (
    <article className="glass-panel glass-panel-hover sighting-card">
      <div className={`sighting-card__thumb${isHighConfidence ? ' is-high-confidence' : ''}`}>
        <img src={faceUrl} alt="CCTV face crop" />
      </div>
      <div className="sighting-card__body">
        <div className="sighting-card__title">
          <span>{personName || 'Missing Individual'}</span>
          <StatusBadge status={sighting.status} />
        </div>
        <div className="sighting-card__meta">
          <span><MapPin size={13} aria-hidden="true" /> {sighting.camera_location || 'CCTV Surveillance Camera'}</span>
          <span><Clock size={13} aria-hidden="true" /> {new Date(sighting.detected_at).toLocaleString()}</span>
          {sighting.num_frames_matched && <span><Camera size={13} aria-hidden="true" /> {sighting.num_frames_matched} frames matched</span>}
        </div>
      </div>
      <div className={`sighting-card__score${isHighConfidence ? ' is-high-confidence' : ''}`}>
        <strong>{percentage}%</strong>
        <span>{t.sighting.similarityScore}</span>
      </div>
      <button type="button" onClick={() => navigate(`/sightings/${sighting.id}`)} className="btn btn-secondary btn-sm sighting-card__action">
        <span>{t.dashboard.reviewMatch}</span>
        <ArrowRight size={14} aria-hidden="true" />
      </button>
    </article>
  );
};
