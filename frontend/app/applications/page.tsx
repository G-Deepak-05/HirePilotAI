'use client';

import { useEffect, useState } from 'react';
import { api, Application, KanbanBoard } from '@/lib/api';
import ApplicationCard from '@/components/ApplicationCard';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription
} from '@/components/ui/dialog';
import { Loader2, RefreshCw, Eye, FileText, Mail } from 'lucide-react';
import { toast } from 'sonner';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

// userId loaded after mount to avoid SSR/client hydration mismatch

const KANBAN_COLUMNS = [
  { key: 'PENDING_CONFIRMATION', label: '⏳ Confirm',   accent: '#fbbf24' },
  { key: 'APPLIED',              label: '📤 Applied',   accent: '#818cf8' },
  { key: 'ASSESSMENT',           label: '📝 Assessment',accent: '#c084fc' },
  { key: 'INTERVIEW',            label: '🎤 Interview', accent: '#4ade80' },
  { key: 'OFFER',                label: '🎉 Offer',     accent: '#34d399' },
  { key: 'REJECTED',             label: '❌ Rejected',  accent: '#f87171' },
] as const;

export default function ApplicationsPage() {
  const [kanban, setKanban] = useState<KanbanBoard | null>(null);
  const [loading, setLoading] = useState(true);
  const [userId, setUserId] = useState('');
  const [confirmApp, setConfirmApp] = useState<Application | null>(null);
  const [confirming, setConfirming] = useState(false);

  // Populate userId after mount only (avoids SSR mismatch)
  useEffect(() => {
    const id = localStorage.getItem('hirepilot_user_id') ?? '';
    setUserId(id);
  }, []);

  useEffect(() => { if (userId) loadKanban(); }, [userId]);

  async function loadKanban() {
    const id = userId || localStorage.getItem('hirepilot_user_id') || '';
    if (!id) return;
    setLoading(true);
    try {
      const data = await api.applications.kanban(id);
      setKanban(data);
    } catch {
      toast.error('Could not load applications');
    } finally {
      setLoading(false);
    }
  }

  async function handleConfirm(app: Application) {
    setConfirmApp(app);
  }

  async function executeConfirm() {
    if (!confirmApp?.confirmationToken) return;
    setConfirming(true);
    try {
      await api.applications.confirm(confirmApp.id, confirmApp.confirmationToken);
      toast.success('🚀 Playwright automation launched! Check your browser window.');
      setConfirmApp(null);
      loadKanban();
    } catch (e: any) {
      toast.error('Failed: ' + e.message);
    } finally {
      setConfirming(false);
    }
  }

  const allApps = kanban ? Object.values(kanban).flat() : [];

  return (
    <div className="p-8 min-h-screen">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white mb-1">
            Applications <span className="gradient-text">Kanban</span>
          </h1>
          <p className="text-sm text-slate-500">
            {allApps.length} total
            · {['ROUND_1','ROUND_1_CLEARED','ROUND_2','ROUND_2_CLEARED','ROUND_3',
                'HR_ROUND','WAITING_FOR_HR'].reduce((s,k) => s + (kanban?.[k as keyof KanbanBoard]?.length ?? 0), 0)} interviews
            · {kanban?.OFFER?.length ?? 0} offers
          </p>
        </div>
        <button onClick={loadKanban}
          className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 text-slate-300 border border-white/10 text-sm hover:bg-white/10 transition-colors">
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} /> Refresh
        </button>
      </div>

      {!userId ? (
        <div className="glass-card p-10 text-center">
          <p className="text-slate-400 mb-4">Complete onboarding to see your applications.</p>
          <a href="/onboarding" className="btn-glow px-6 py-2.5 rounded-xl text-white text-sm font-medium inline-block">
            Go to Onboarding →
          </a>
        </div>
      ) : (
        <div className="overflow-x-auto pb-6">
          <div className="flex gap-4" style={{ minWidth: `${KANBAN_COLUMNS.length * 280}px` }}>
            {KANBAN_COLUMNS.map(({ key, label, accent }) => {
              const cards = kanban ? kanban[key as keyof KanbanBoard] ?? [] : [];
              return (
                <div key={key} className="kanban-col flex-shrink-0 p-4" style={{ width: 272 }}>
                  {/* Column header */}
                  <div className="flex items-center justify-between mb-4">
                    <h3 className="text-xs font-semibold text-slate-300">{label}</h3>
                    <span className="text-[11px] px-2 py-0.5 rounded-full font-medium"
                      style={{ background: `${accent}15`, color: accent }}>
                      {loading ? '…' : cards.length}
                    </span>
                  </div>

                  {loading ? (
                    <div className="space-y-3">
                      {[1, 2].map(i => <div key={i} className="shimmer rounded-xl h-28" />)}
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {cards.length === 0 ? (
                        <p className="text-[11px] text-slate-700 text-center py-8">Empty</p>
                      ) : (
                        cards.map(app => (
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

      {/* Human confirmation dialog */}
      <Dialog open={!!confirmApp} onOpenChange={() => setConfirmApp(null)}>
        <DialogContent className="bg-[#16161f] border border-indigo-500/20 text-white max-w-2xl">
          <DialogHeader>
            <DialogTitle className="text-lg font-bold">
              Review & Confirm Application
            </DialogTitle>
            <DialogDescription className="text-slate-400">
              {confirmApp?.jobTitle} at {confirmApp?.company}
            </DialogDescription>
          </DialogHeader>

          {confirmApp && (
            <Tabs defaultValue="resume" className="mt-2">
              <TabsList className="bg-[#1c1c28] border border-white/5">
                <TabsTrigger value="resume" className="data-[state=active]:bg-indigo-600/20 data-[state=active]:text-indigo-300 text-slate-500 text-xs">
                  <FileText className="w-3.5 h-3.5 mr-1.5" /> Tailored Resume
                </TabsTrigger>
                <TabsTrigger value="cover" className="data-[state=active]:bg-indigo-600/20 data-[state=active]:text-indigo-300 text-slate-500 text-xs">
                  <Mail className="w-3.5 h-3.5 mr-1.5" /> Cover Letter
                </TabsTrigger>
              </TabsList>

              <TabsContent value="resume">
                <div className="bg-[#111118] rounded-xl p-4 mt-3 max-h-64 overflow-y-auto">
                  <pre className="text-xs text-slate-300 whitespace-pre-wrap font-sans leading-relaxed">
                    {confirmApp.tailoredResumeText
                      ? JSON.stringify(JSON.parse(confirmApp.tailoredResumeText || '{}'), null, 2)
                      : 'Tailored resume content will appear here after optimization...'}
                  </pre>
                </div>
              </TabsContent>

              <TabsContent value="cover">
                <div className="bg-[#111118] rounded-xl p-4 mt-3 max-h-64 overflow-y-auto">
                  <p className="text-sm text-slate-300 leading-relaxed whitespace-pre-wrap">
                    {confirmApp.coverLetterText ?? 'Cover letter will appear here after optimization...'}
                  </p>
                </div>
              </TabsContent>
            </Tabs>
          )}

          <div className="flex items-center gap-3 mt-4 pt-4 border-t border-white/5">
            <button onClick={() => setConfirmApp(null)}
              className="flex-1 py-2.5 rounded-xl border border-white/10 text-slate-400 text-sm hover:bg-white/5 transition-colors">
              Cancel
            </button>
            <button onClick={executeConfirm} disabled={confirming}
              className="flex-1 btn-glow py-2.5 rounded-xl text-white text-sm font-semibold flex items-center justify-center gap-2">
              {confirming ? <Loader2 className="w-4 h-4 animate-spin" /> : <Eye className="w-4 h-4" />}
              {confirming ? 'Launching Playwright...' : 'Confirm & Apply'}
            </button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
