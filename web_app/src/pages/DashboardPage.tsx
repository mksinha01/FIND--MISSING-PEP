import React, { useEffect, useState } from 'react';
import { CheckCircle2, FileText, Lightbulb, PlusCircle, RefreshCw, Search, Users, Zap } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useLanguage } from '../context/LanguageContext';
import { reportsApi, sightingsApi } from '../services/api';
import { Sighting } from '../types/sighting';
import { SightingCard } from '../components/reports/SightingCard';
import { LoadingSpinner } from '../components/common/LoadingSpinner';

export const DashboardPage: React.FC = () => {
  const { t } = useLanguage();
  const navigate = useNavigate();
  const [stats, setStats] = useState({ active: 0, matches: 0, total: 0, found: 0 });
  const [sightings, setSightings] = useState<Sighting[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);

  const loadDashboardData = async (silent = false) => {
    if (!silent) setIsLoading(true);
    else setIsRefreshing(true);

    try {
      const [allCasesRes, sightingsRes] = await Promise.all([
        reportsApi.getAllReports(undefined, 1, 100).catch(() => ({ items: [], total: 0, page: 1, per_page: 100 })),
        sightingsApi.listRecent(20).catch(() => []),
      ]);
      const cases = allCasesRes.items || [];
      setStats({
        active: cases.filter((item) => item.status === 'ACTIVE').length,
        matches: sightingsRes.length,
        total: allCasesRes.total || cases.length,
        found: cases.filter((item) => item.status === 'FOUND').length,
      });
      setSightings(sightingsRes);
    } catch (error) {
      console.error('Failed to load dashboard metrics', error);
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    void loadDashboardData();
    const interval = setInterval(() => void loadDashboardData(true), 10000);
    return () => clearInterval(interval);
  }, []);

  const metrics = [
    { label: t.stats.activeSearches, value: stats.active, hint: t.stats.monitoredFeeds, icon: Search, tone: 'accent' },
    { label: t.stats.cctvMatches, value: stats.matches, hint: t.stats.biometricLogged, icon: Zap, tone: 'success' },
    { label: t.stats.totalCases, value: stats.total, hint: t.stats.registeredCases, icon: Users, tone: 'warning' },
    { label: t.stats.personsFound, value: stats.found, hint: t.stats.resolvedCases, icon: CheckCircle2, tone: 'purple' },
  ];

  return (
    <div className="main-content">
      <section className="stats-grid" aria-label="System metrics">
        {metrics.map(({ label, value, hint, icon: Icon, tone }) => (
          <div className={`glass-panel dashboard-metric dashboard-metric--${tone}`} key={label}>
            <span className="dashboard-metric__icon"><Icon size={23} aria-hidden="true" /></span>
            <span className="dashboard-metric__copy">
              <span className="dashboard-metric__label">{label}</span>
              <strong className="dashboard-metric__value">{value}</strong>
              <span className="dashboard-metric__hint">{hint}</span>
            </span>
          </div>
        ))}
      </section>

      <div className="dashboard-grid">
        <section>
          <div className="section-header">
            <div>
              <h2 className="section-title"><span aria-hidden="true">📡</span> {t.dashboard.liveSightingsTitle}</h2>
              <p className="section-subtitle">{t.dashboard.liveSightingsSub}</p>
            </div>
            <button type="button" onClick={() => void loadDashboardData(true)} className="btn btn-secondary btn-sm" disabled={isRefreshing}>
              <RefreshCw size={14} className={isRefreshing ? 'spin' : ''} aria-hidden="true" />
              <span>{t.dashboard.refreshFeed}</span>
            </button>
          </div>

          {isLoading ? (
            <LoadingSpinner text="Scanning CCTV Sighting Streams..." />
          ) : sightings.length === 0 ? (
            <div className="glass-panel empty-state">
              <div className="empty-state__icon" aria-hidden="true">🛡️</div>
              <h3>No Active Sighting Alerts</h3>
              <p>{t.dashboard.noSightings}</p>
            </div>
          ) : (
            <div className="stack-list">
              {sightings.map((sighting) => <SightingCard key={sighting.id} sighting={sighting} />)}
            </div>
          )}
        </section>

        <aside className="dashboard-aside">
          <div className="glass-panel quick-actions">
            <h3><span aria-hidden="true">⚡</span> {t.dashboard.quickActions}</h3>
            <div className="quick-actions__list">
              <button type="button" onClick={() => navigate('/reports/new')} className="btn btn-primary quick-actions__button">
                <PlusCircle size={18} aria-hidden="true" /> <span>{t.dashboard.reportPersonBtn}</span>
              </button>
              <button type="button" onClick={() => navigate('/reports')} className="btn btn-secondary quick-actions__button">
                <FileText size={18} aria-hidden="true" /> <span>{t.dashboard.viewMyReportsBtn}</span>
              </button>
            </div>
          </div>

          <div className="glass-panel dashboard-tip">
            <div className="dashboard-tip__title"><Lightbulb size={18} aria-hidden="true" /> <h4>{t.dashboard.proTipTitle}</h4></div>
            <p>{t.dashboard.proTipBody}</p>
          </div>
        </aside>
      </div>
    </div>
  );
};
