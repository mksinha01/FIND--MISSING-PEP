import React from 'react';

interface SimilarityGaugeProps {
  score: number;
  size?: number;
  strokeWidth?: number;
}

export const SimilarityGauge: React.FC<SimilarityGaugeProps> = ({ score, size = 110, strokeWidth = 10 }) => {
  const percentage = Math.min(100, Math.max(0, Math.round(score * 100)));
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (percentage / 100) * circumference;
  const tone = percentage >= 75 ? 'success' : percentage < 60 ? 'danger' : 'warning';
  const strokeColor = `var(--${tone})`;

  return (
    <div className={`similarity-gauge similarity-gauge--${tone}`} style={{ width: size, height: size }} aria-label={`${percentage}% match`}>
      <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }} aria-hidden="true">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          stroke="var(--border-soft)"
          strokeWidth={strokeWidth}
          fill="transparent"
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          stroke={strokeColor}
          strokeWidth={strokeWidth}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
          fill="transparent"
          style={{ transition: 'stroke-dashoffset 800ms var(--ease)' }}
        />
      </svg>
      <div className="similarity-gauge__copy">
        <div className="similarity-gauge__value">{percentage}%</div>
        <div className="similarity-gauge__label">Match</div>
      </div>
    </div>
  );
};
