'use client';

import { useEffect, useState } from 'react';
import { api, KanbanBoard, Application } from '@/lib/api';
import ApplicationCard from '@/components/ApplicationCard';
import {
  TrendingUp, Briefcase, CheckCircle, Calendar,
  RefreshCw, Zap, ArrowUpRight
} from 'lucide-react';
import { toast } from 'sonner';

// userId is loaded from localStorage after mount to avoid SSR/client hydration mismatch

const KANBAN_COLUMNS = [
  { key: 'PENDING_CONFIRMATION', label: '⏳ Awaiting Confirmation', accent: '#fbbf24' },
  { key: 'APPLIED',              label: '📤 Applied',               accent: '#818cf8' },
  { key: 'ASSESSMENT',           label: '📝 Assessment',            accent: '#c084fc' },
  { key: 'INTERVIEW',            label: '🎤 Interview',             accent: '#4ade80' },
  { key: 'OFFER',                label: '🎉 Offer',                 accent: '#34d399' },
  { key: 'REJECTED',             label: '❌ Rejected',              accent: '#f87171' },
] as const;

export default function DashboardPage() {
  const [kanban, setKanban] = useState<KanbanBoard | null>(null);
  const [loading, setLoading] = useState(true);
  const [userId, setUserId] = useState(''); // empty on SSR, populated after mount

  // Compute interview/offer count safely — INTERVIEW is not a real status key
  const interviewKeys = ['ROUND_1','ROUND_1_CLEARED','ROUND_2','ROUND_2_CLEARED',
                         'ROUND_3','ROUND_3_CLEARED','HR_ROUND','WAITING_FOR_HR'] as const;
  const totalApps = kanban ? Object.values(kanban).flat().length : 0;
  const interviews = kanban
    ? interviewKeys.reduce((sum, k) => sum + (kanban[k as keyof KanbanBoard]?.length ?? 0), 0)
      + (kanban.OFFER?.length ?? 0) + (kanban.OFFER_ACCEPTED?.length ?? 0)
    : 0;
  const pending = kanban?.PENDING_CONFIRMATION?.length ?? 0;
  const applied = kanban?.APPLIED?.length ?? 0;

  // Load userId from localStorage only after mount (avoids SSR hydration mismatch)
  useEffect(() => {
    const id = localStorage.getItem('hirepilot_user_id') ?? '';
    setUserId(id);
  }, []);

  useEffect(() => {
    if (!userId) return;
    loadKanban();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);

  async function loadKanban() {
    setLoading(true);
    try {
      const data = await api.applications.kanban(userId);
      setKanban(data);
    } catch (e: unknown) {
      toast.error('Backend not reachable — ' + (e instanceof Error ? e.message : 'start the Spring Boot server'));
    } finally {
      setLoading(false);
    }
  }

  async function handleConfirm(app: Application) {
    if (!app.confirmationToken) return;
    try {
      toast.info('Launching Playwright automation...');
      await api.applications.confirm(app.id, app.confirmationToken);
      toast.success('Application submitted via Playwright!');
      loadKanban();
    } catch (e: unknown) {
      toast.error('Failed to confirm: ' + (e instanceof Error ? e.message : 'unknown error'));
    }
  }

  return (
    <div className="p-8 min-h-screen">
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-white mb-1">
            Mission Control <span className="gradient-text">Dashboard</span>
          </h1>
          <p className="text-sm text-slate-500">Your autonomous job search at a glance</p>
        </div>
        <div className="flex gap-3">
          <button
            onClick={() => api.jobs.scrapeYC().then(() => toast.success('YC Jobs scrape started!'))}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-orange-500/10 text-orange-400 border border-orange-500/20 text-sm font-medium hover:bg-orange-500/20 transition-colors"
          >
            <Zap className="w-4 h-4" /> Scrape YC Jobs
          </button>
          <button
            onClick={loadKanban}
            disabled={loading}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 text-slate-300 border border-white/10 text-sm font-medium hover:bg-white/10 transition-colors"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} /> Refresh
          </button>
        </div>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-4 gap-4 mb-8">
        {[
          { label: 'Total Applications', value: totalApps, icon: Briefcase, color: 'from-indigo-600/20 to-purple-600/10', accent: '#818cf8' },
          { label: 'Pending Review', value: pending, icon: Calendar, color: 'from-yellow-600/20 to-orange-600/10', accent: '#fbbf24' },
          { label: 'Applied', value: applied, icon: ArrowUpRight, color: 'from-blue-600/20 to-indigo-600/10', accent: '#60a5fa' },
          { label: 'Interviews / Offers', value: interviews, icon: TrendingUp, color: 'from-green-600/20 to-emerald-600/10', accent: '#4ade80' },
        ].map(({ label, value, icon: Icon, color, accent }) => (
          <div key={label} className={`glass-card p-5 bg-gradient-to-br ${color}`}>
            <div className="flex items-center justify-between mb-3">
              <p className="text-xs text-slate-400 font-medium">{label}</p>
              <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ background: `${accent}15` }}>
                <Icon className="w-4 h-4" style={{ color: accent }} />
              </div>
            </div>
            <p className="text-3xl font-bold text-white">{loading ? '—' : value}</p>
          </div>
        ))}
      </div>

      {/* Kanban board */}
      {!userId ? (
        <div className="glass-card p-10 text-center">
          <p className="text-slate-400 mb-4">No user profile found.</p>
          <a href="/onboarding" className="btn-glow px-6 py-2.5 rounded-xl text-white text-sm font-medium inline-block">
            Complete Onboarding →
          </a>
        </div>
      ) : (
        <div className="overflow-x-auto pb-4">
          <div className="flex gap-4" style={{ minWidth: `${KANBAN_COLUMNS.length * 280}px` }}>
            {KANBAN_COLUMNS.map(({ key, label, accent }) => {
              const cards = kanban ? kanban[key as keyof KanbanBoard] ?? [] : [];
              return (
                <div key={key} className="kanban-col flex-shrink-0 w-68 p-4" style={{ width: 268 }}>
                  {/* Column header */}
                  <div className="flex items-center justify-between mb-4">
                    <h3 className="text-xs font-semibold text-slate-300">{label}</h3>
                    <span className="text-[11px] px-2 py-0.5 rounded-full font-medium"
                      style={{ background: `${accent}15`, color: accent }}>
                      {loading ? '…' : cards.length}
                    </span>
                  </div>

                  {/* Loading skeleton */}
                  {loading && (
                    <div className="space-y-3">
                      {[1,2].map(i => (
                        <div key={i} className="shimmer rounded-xl h-32" />
                      ))}
                    </div>
                  )}

                  {/* Cards */}
                  {!loading && (
                    <div className="space-y-3">
                      {cards.length === 0 ? (
                        <p className="text-[11px] text-slate-700 text-center py-6">Empty</p>
                      ) : (
                        cards.map((app) => (
                          <ApplicationCard
                            key={app.id}
                            application={app}
                            onConfirm={handleConfirm}
                            compact
                          />
                        ))
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Conversion rate */}
      {kanban && totalApps > 0 && (
        <div className="mt-6 glass-card p-4 flex items-center gap-4">
          <CheckCircle className="w-5 h-5 text-green-400 flex-shrink-0" />
          <div className="flex-1">
            <p className="text-xs text-slate-400 mb-1">Interview Conversion Rate</p>
            <div className="w-full bg-[#1c1c28] rounded-full h-1.5">
              <div
                className="h-1.5 rounded-full bg-gradient-to-r from-indigo-500 to-green-400 transition-all duration-700"
                style={{ width: `${totalApps > 0 ? (interviews / totalApps) * 100 : 0}%` }}
              />
            </div>
          </div>
          <p className="text-lg font-bold text-white">
            {totalApps > 0 ? ((interviews / totalApps) * 100).toFixed(1) : 0}%
          </p>
        </div>
      )}
    </div>
  );
}
