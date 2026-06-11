'use client';

import { useState, useEffect, useCallback } from 'react';
import { api, Application, ApplicationStatus } from '@/lib/api';
import {
  ExternalLink, RefreshCw, Search, TrendingUp,
  CheckCircle2, XCircle, Clock, Zap, StickyNote, ChevronDown
} from 'lucide-react';
import { toast } from 'sonner';

const USER_ID = typeof window !== 'undefined'
  ? localStorage.getItem('hirepilot_user_id') ?? ''
  : '';

// ─── Status metadata ────────────────────────────────────────────────────────

type StatusGroup = 'system' | 'active' | 'interviewing' | 'final' | 'closed';

interface StatusMeta {
  label: string;
  color: string;        // Tailwind text color
  bg: string;           // Tailwind bg/border
  dot: string;          // dot color
  group: StatusGroup;
}

const STATUS_META: Record<ApplicationStatus, StatusMeta> = {
  // System
  QUEUED:               { label: 'Queued',            color: 'text-slate-400',   bg: 'bg-slate-500/10 border-slate-500/20',   dot: 'bg-slate-400',   group: 'system' },
  TAILORING:            { label: 'Tailoring',         color: 'text-blue-400',    bg: 'bg-blue-500/10 border-blue-500/20',     dot: 'bg-blue-400',    group: 'system' },
  PENDING_CONFIRMATION: { label: 'Confirm Apply',     color: 'text-amber-400',   bg: 'bg-amber-500/10 border-amber-500/20',   dot: 'bg-amber-400',   group: 'system' },
  APPLYING:             { label: 'Applying…',         color: 'text-indigo-400',  bg: 'bg-indigo-500/10 border-indigo-500/20', dot: 'bg-indigo-400',  group: 'system' },
  FAILED:               { label: 'Failed',            color: 'text-red-500',     bg: 'bg-red-500/10 border-red-500/20',       dot: 'bg-red-500',     group: 'system' },
  // Active
  APPLIED:              { label: 'Applied',           color: 'text-cyan-400',    bg: 'bg-cyan-500/10 border-cyan-500/20',     dot: 'bg-cyan-400',    group: 'active' },
  SHORTLISTED:          { label: 'Shortlisted',       color: 'text-teal-400',    bg: 'bg-teal-500/10 border-teal-500/20',     dot: 'bg-teal-400',    group: 'active' },
  ASSESSMENT:           { label: 'Assessment',        color: 'text-violet-400',  bg: 'bg-violet-500/10 border-violet-500/20', dot: 'bg-violet-400',  group: 'active' },
  // Interview rounds
  ROUND_1:              { label: '1st Round',         color: 'text-purple-400',  bg: 'bg-purple-500/10 border-purple-500/20', dot: 'bg-purple-400',  group: 'interviewing' },
  ROUND_1_CLEARED:      { label: '1st Round ✓',       color: 'text-purple-300',  bg: 'bg-purple-500/15 border-purple-400/30', dot: 'bg-purple-300',  group: 'interviewing' },
  ROUND_2:              { label: '2nd Round',         color: 'text-fuchsia-400', bg: 'bg-fuchsia-500/10 border-fuchsia-500/20',dot:'bg-fuchsia-400', group: 'interviewing' },
  ROUND_2_CLEARED:      { label: '2nd Round ✓',       color: 'text-fuchsia-300', bg: 'bg-fuchsia-500/15 border-fuchsia-400/30',dot:'bg-fuchsia-300', group: 'interviewing' },
  ROUND_3:              { label: '3rd Round',         color: 'text-pink-400',    bg: 'bg-pink-500/10 border-pink-500/20',     dot: 'bg-pink-400',    group: 'interviewing' },
  ROUND_3_CLEARED:      { label: '3rd Round ✓',       color: 'text-pink-300',    bg: 'bg-pink-500/15 border-pink-400/30',     dot: 'bg-pink-300',    group: 'interviewing' },
  HR_ROUND:             { label: 'HR Round',          color: 'text-rose-400',    bg: 'bg-rose-500/10 border-rose-500/20',     dot: 'bg-rose-400',    group: 'interviewing' },
  WAITING_FOR_HR:       { label: 'Waiting for HR',    color: 'text-orange-400',  bg: 'bg-orange-500/10 border-orange-500/20', dot: 'bg-orange-400',  group: 'interviewing' },
  // Final
  OFFER:                { label: 'Offer Received 🎉', color: 'text-emerald-400', bg: 'bg-emerald-500/10 border-emerald-500/20',dot:'bg-emerald-400', group: 'final' },
  OFFER_ACCEPTED:       { label: 'Accepted 🎊',       color: 'text-green-400',   bg: 'bg-green-500/15 border-green-400/40',   dot: 'bg-green-400',   group: 'final' },
  OFFER_DECLINED:       { label: 'Declined',          color: 'text-slate-400',   bg: 'bg-slate-500/10 border-slate-500/20',   dot: 'bg-slate-400',   group: 'final' },
  // Closed
  REJECTED:             { label: 'Rejected',          color: 'text-red-400',     bg: 'bg-red-500/10 border-red-500/20',       dot: 'bg-red-400',     group: 'closed' },
  GHOSTED:              { label: 'Ghosted 👻',         color: 'text-slate-500',   bg: 'bg-slate-500/8 border-slate-500/15',    dot: 'bg-slate-500',   group: 'closed' },
  WITHDRAWN:            { label: 'Withdrawn',         color: 'text-slate-400',   bg: 'bg-slate-500/10 border-slate-500/20',   dot: 'bg-slate-400',   group: 'closed' },
};

// Statuses available in the human dropdown (exclude pure system statuses)
const HUMAN_STATUSES: ApplicationStatus[] = [
  'APPLIED', 'SHORTLISTED', 'ASSESSMENT',
  'ROUND_1', 'ROUND_1_CLEARED',
  'ROUND_2', 'ROUND_2_CLEARED',
  'ROUND_3', 'ROUND_3_CLEARED',
  'HR_ROUND', 'WAITING_FOR_HR',
  'OFFER', 'OFFER_ACCEPTED', 'OFFER_DECLINED',
  'REJECTED', 'GHOSTED', 'WITHDRAWN',
];

const GROUP_LABELS: Record<StatusGroup, string> = {
  system:       '⚙️ System',
  active:       '📬 Active',
  interviewing: '🎯 Interviewing',
  final:        '🏆 Final',
  closed:       '🔒 Closed',
};

// ─── Status badge ────────────────────────────────────────────────────────────

function StatusBadge({ status }: { status: ApplicationStatus }) {
  const meta = STATUS_META[status] ?? STATUS_META.APPLIED;
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-[11px] font-semibold whitespace-nowrap ${meta.color} ${meta.bg}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${meta.dot}`} />
      {meta.label}
    </span>
  );
}

// ─── Inline status dropdown ───────────────────────────────────────────────────

function StatusDropdown({
  current,
  onChange,
}: {
  current: ApplicationStatus;
  onChange: (s: ApplicationStatus) => void;
}) {
  const [open, setOpen] = useState(false);
  const meta = STATUS_META[current] ?? STATUS_META.APPLIED;

  // Group statuses for the dropdown
  const grouped = HUMAN_STATUSES.reduce<Partial<Record<StatusGroup, ApplicationStatus[]>>>((acc, s) => {
    const g = STATUS_META[s].group;
    if (!acc[g]) acc[g] = [];
    acc[g]!.push(s);
    return acc;
  }, {});

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(o => !o)}
        className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-[11px] font-semibold whitespace-nowrap transition-opacity hover:opacity-80 ${meta.color} ${meta.bg}`}
      >
        <span className={`w-1.5 h-1.5 rounded-full ${meta.dot}`} />
        {meta.label}
        <ChevronDown className="w-3 h-3 opacity-60" />
      </button>

      {open && (
        <>
          {/* Backdrop */}
          <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />
          <div className="absolute left-0 top-full mt-1 z-50 w-52 bg-[#1a1a26] border border-white/10 rounded-xl shadow-2xl shadow-black/60 overflow-hidden">
            {(Object.entries(grouped) as [StatusGroup, ApplicationStatus[]][]).map(([group, statuses]) => (
              <div key={group}>
                <div className="px-3 py-1.5 text-[10px] font-semibold text-slate-600 uppercase tracking-wider border-b border-white/5">
                  {GROUP_LABELS[group]}
                </div>
                {statuses.map(s => {
                  const m = STATUS_META[s];
                  return (
                    <button
                      key={s}
                      onClick={() => { onChange(s); setOpen(false); }}
                      className={`w-full flex items-center gap-2 px-3 py-2 text-[12px] text-left transition-colors hover:bg-white/5
                        ${s === current ? 'bg-white/5' : ''} ${m.color}`}
                    >
                      <span className={`w-2 h-2 rounded-full flex-shrink-0 ${m.dot}`} />
                      {m.label}
                      {s === current && <CheckCircle2 className="w-3 h-3 ml-auto opacity-60" />}
                    </button>
                  );
                })}
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

// ─── Notes cell ──────────────────────────────────────────────────────────────

function NotesCell({ appId, initial, onSave }: { appId: string; initial?: string; onSave: (n: string) => void }) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(initial ?? '');

  async function save() {
    setEditing(false);
    try {
      await api.applications.updateNotes(appId, value);
      onSave(value);
    } catch {
      toast.error('Failed to save notes');
    }
  }

  if (editing) {
    return (
      <textarea
        autoFocus
        value={value}
        onChange={e => setValue(e.target.value)}
        onBlur={save}
        onKeyDown={e => { if (e.key === 'Escape') { setValue(initial ?? ''); setEditing(false); } }}
        rows={2}
        className="w-full bg-[#1c1c28] border border-indigo-500/40 rounded-lg px-2 py-1 text-xs text-slate-300 outline-none resize-none min-w-[160px]"
      />
    );
  }

  return (
    <button
      onClick={() => setEditing(true)}
      title="Click to add notes"
      className="flex items-center gap-1.5 text-left text-xs text-slate-500 hover:text-slate-300 transition-colors max-w-[200px] truncate"
    >
      <StickyNote className="w-3 h-3 flex-shrink-0" />
      <span className="truncate">{value || 'Add note…'}</span>
    </button>
  );
}

// ─── Stats bar ───────────────────────────────────────────────────────────────

function StatsBar({ apps }: { apps: Application[] }) {
  const total = apps.length;
  const active = apps.filter(a => ['APPLIED', 'SHORTLISTED', 'ASSESSMENT', 'ROUND_1', 'ROUND_1_CLEARED', 'ROUND_2', 'ROUND_2_CLEARED', 'ROUND_3', 'ROUND_3_CLEARED', 'HR_ROUND', 'WAITING_FOR_HR'].includes(a.status)).length;
  const offers = apps.filter(a => ['OFFER', 'OFFER_ACCEPTED'].includes(a.status)).length;
  const rejected = apps.filter(a => ['REJECTED', 'GHOSTED'].includes(a.status)).length;
  const convRate = total > 0 ? Math.round((offers / total) * 100) : 0;

  const stat = (icon: React.ReactNode, label: string, value: number | string, color: string) => (
    <div className="glass-card p-4 flex items-center gap-4 flex-1 min-w-[140px]">
      <div className={`w-9 h-9 rounded-xl flex items-center justify-center ${color}`}>{icon}</div>
      <div>
        <p className="text-xl font-bold text-white">{value}</p>
        <p className="text-[11px] text-slate-500">{label}</p>
      </div>
    </div>
  );

  return (
    <div className="flex flex-wrap gap-3 mb-6">
      {stat(<Zap className="w-4 h-4 text-indigo-400" />, 'Total Applied', total, 'bg-indigo-500/10')}
      {stat(<Clock className="w-4 h-4 text-cyan-400" />, 'Active / In Progress', active, 'bg-cyan-500/10')}
      {stat(<CheckCircle2 className="w-4 h-4 text-emerald-400" />, 'Offers Received', offers, 'bg-emerald-500/10')}
      {stat(<XCircle className="w-4 h-4 text-red-400" />, 'Rejected / Ghosted', rejected, 'bg-red-500/10')}
      {stat(<TrendingUp className="w-4 h-4 text-purple-400" />, 'Offer Rate', `${convRate}%`, 'bg-purple-500/10')}
    </div>
  );
}

// ─── Main page ───────────────────────────────────────────────────────────────

type Filter = 'all' | StatusGroup;

export default function TrackerPage() {
  const [apps, setApps] = useState<Application[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<Filter>('all');

  const load = useCallback(async () => {
    if (!USER_ID) { setLoading(false); return; }
    setLoading(true);
    try {
      const list = await api.applications.listByUser(USER_ID);
      setApps(list);
    } catch {
      toast.error('Could not load applications — is the backend running?');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  async function handleStatusChange(id: string, status: ApplicationStatus) {
    try {
      const updated = await api.applications.updateStatus(id, status);
      setApps(prev => prev.map(a => a.id === id ? { ...a, status: updated.status } : a));
      toast.success(`Status updated → ${STATUS_META[status]?.label ?? status}`);
    } catch {
      toast.error('Status update failed');
    }
  }

  function handleNoteSave(id: string, notes: string) {
    setApps(prev => prev.map(a => a.id === id ? { ...a, notes } : a));
  }

  const filterGroups: { label: string; value: Filter }[] = [
    { label: 'All', value: 'all' },
    { label: '📬 Active', value: 'active' },
    { label: '🎯 Interviewing', value: 'interviewing' },
    { label: '🏆 Final', value: 'final' },
    { label: '🔒 Closed', value: 'closed' },
  ];

  const displayed = apps.filter(a => {
    const q = search.toLowerCase();
    const matchesSearch = !q || a.jobTitle.toLowerCase().includes(q) || a.company.toLowerCase().includes(q);
    const matchesFilter = filter === 'all' || STATUS_META[a.status]?.group === filter;
    return matchesSearch && matchesFilter;
  });

  return (
    <div className="p-8 min-h-screen">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white mb-1">
            Application <span className="gradient-text">Tracker</span>
          </h1>
          <p className="text-sm text-slate-500">{apps.length} applications tracked · update status as you progress</p>
        </div>
        <button
          onClick={load}
          className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 text-slate-300 border border-white/10 text-sm hover:bg-white/10 transition-colors"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} /> Refresh
        </button>
      </div>

      {/* Stats */}
      {!loading && <StatsBar apps={apps} />}

      {/* Filter + Search row */}
      <div className="flex flex-wrap items-center gap-3 mb-5">
        <div className="flex gap-1 bg-[#16161f] border border-white/8 rounded-xl p-1">
          {filterGroups.map(f => (
            <button
              key={f.value}
              onClick={() => setFilter(f.value)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all duration-150
                ${filter === f.value
                  ? 'bg-gradient-to-r from-indigo-600/30 to-purple-600/20 text-indigo-300 border border-indigo-500/20'
                  : 'text-slate-500 hover:text-slate-300'}`}
            >
              {f.label}
            </button>
          ))}
        </div>

        <div className="flex-1 flex items-center gap-2 bg-[#16161f] border border-white/10 rounded-xl px-3 py-2 min-w-[220px]">
          <Search className="w-4 h-4 text-slate-500 flex-shrink-0" />
          <input
            type="text"
            placeholder="Search by job title or company…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="bg-transparent text-sm text-slate-300 placeholder-slate-600 outline-none flex-1"
          />
        </div>
      </div>

      {/* Table */}
      {loading ? (
        <div className="space-y-2">
          {Array(8).fill(0).map((_, i) => (
            <div key={i} className="shimmer rounded-xl h-14" />
          ))}
        </div>
      ) : displayed.length === 0 ? (
        <div className="text-center py-24 glass-card">
          <p className="text-4xl mb-4">📭</p>
          <p className="text-slate-400 font-medium mb-1">No applications yet</p>
          <p className="text-slate-600 text-sm">Apply to jobs from the Job Queue and they'll appear here.</p>
        </div>
      ) : (
        <div className="glass-card overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-white/5">
                <th className="text-left px-4 py-3 text-[11px] font-semibold text-slate-500 uppercase tracking-wider w-8">#</th>
                <th className="text-left px-4 py-3 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Role &amp; Company</th>
                <th className="text-left px-4 py-3 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Status</th>
                <th className="text-left px-4 py-3 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Match</th>
                <th className="text-left px-4 py-3 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Notes</th>
                <th className="text-left px-4 py-3 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">Applied</th>
                <th className="text-left px-4 py-3 text-[11px] font-semibold text-slate-500 uppercase tracking-wider w-10" />
              </tr>
            </thead>
            <tbody>
              {displayed.map((app, idx) => {
                const appliedDate = app.appliedAt
                  ? new Date(app.appliedAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: '2-digit' })
                  : new Date(app.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: '2-digit' });
                const score = app.matchScore ? Math.round(Number(app.matchScore)) : null;

                return (
                  <tr
                    key={app.id}
                    className="border-b border-white/4 hover:bg-white/3 transition-colors group"
                  >
                    {/* # */}
                    <td className="px-4 py-3 text-slate-600 text-xs">{idx + 1}</td>

                    {/* Role & Company */}
                    <td className="px-4 py-3">
                      <p className="font-semibold text-white text-sm leading-tight">{app.jobTitle}</p>
                      <p className="text-slate-500 text-xs mt-0.5">{app.company}</p>
                    </td>

                    {/* Status dropdown */}
                    <td className="px-4 py-3">
                      <StatusDropdown
                        current={app.status}
                        onChange={s => handleStatusChange(app.id, s)}
                      />
                    </td>

                    {/* Match score */}
                    <td className="px-4 py-3">
                      {score !== null ? (
                        <div className="flex items-center gap-2">
                          <div className="w-16 h-1.5 rounded-full bg-white/10 overflow-hidden">
                            <div
                              className="h-full rounded-full"
                              style={{
                                width: `${score}%`,
                                background: score >= 80 ? '#10b981' : score >= 60 ? '#f59e0b' : '#ef4444',
                              }}
                            />
                          </div>
                          <span className={`text-xs font-semibold ${score >= 80 ? 'text-emerald-400' : score >= 60 ? 'text-amber-400' : 'text-red-400'}`}>
                            {score}%
                          </span>
                        </div>
                      ) : (
                        <span className="text-slate-600 text-xs">—</span>
                      )}
                    </td>

                    {/* Notes */}
                    <td className="px-4 py-3">
                      <NotesCell
                        appId={app.id}
                        initial={app.notes}
                        onSave={n => handleNoteSave(app.id, n)}
                      />
                    </td>

                    {/* Applied date */}
                    <td className="px-4 py-3 text-slate-500 text-xs whitespace-nowrap">{appliedDate}</td>

                    {/* External link */}
                    <td className="px-4 py-3">
                      {app.jobUrl && (
                        <a
                          href={app.jobUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="opacity-0 group-hover:opacity-100 transition-opacity text-slate-500 hover:text-indigo-400"
                          title="Open job posting"
                        >
                          <ExternalLink className="w-3.5 h-3.5" />
                        </a>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
