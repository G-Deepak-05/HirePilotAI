'use client';

import { useState, useEffect } from 'react';
import { api, Job } from '@/lib/api';
import JobCard from '@/components/JobCard';
import { Search, RefreshCw, Zap, Building2, ChevronLeft, ChevronRight } from 'lucide-react';
import { toast } from 'sonner';

export default function JobsPage() {
  const [jobs, setJobs] = useState<Job[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [search, setSearch] = useState('');
  const [ghSlug, setGhSlug] = useState('');
  const [userId, setUserId] = useState('');

  useEffect(() => {
    const id = localStorage.getItem('hirepilot_user_id') ?? '';
    setUserId(id);
  }, []);

  useEffect(() => {
    loadJobs();
  }, [page, userId]);

  async function loadJobs() {
    setLoading(true);
    try {
      const res = await api.jobs.list(page, 20, userId || undefined);
      setJobs(res.content);
      setTotalPages(res.totalPages);
    } catch {
      toast.error('Could not load jobs — is the backend running?');
    } finally {
      setLoading(false);
    }
  }

  async function scrapeYC() {
    try {
      await api.jobs.scrapeYC();
      toast.success('YC Jobs scrape started! Jobs will appear shortly.');
      setTimeout(loadJobs, 3000);
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : 'YC scrape failed');
    }
  }

  async function scrapeAll() {
    try {
      await fetch(`${process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'}/api/jobs/scrape/all`, { method: 'POST' });
      toast.success('All 5 sources triggered! Jobs will appear in ~30s.');
      setTimeout(loadJobs, 5000);
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : 'Scrape all failed');
    }
  }

  async function scrapeSource(source: 'remotive' | 'remoteok' | 'jobicy') {
    try {
      await fetch(`${process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'}/api/jobs/scrape/${source}`, { method: 'POST' });
      toast.success(`${source} scrape started!`);
      setTimeout(loadJobs, 4000);
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : 'Scrape failed');
    }
  }

  async function scrapeGreenhouse() {
    if (!ghSlug.trim()) { toast.warning('Enter a company slug first (e.g. "stripe")'); return; }
    try {
      await api.jobs.scrapeGreenhouse(ghSlug.trim());
      toast.success(`Greenhouse scrape started for ${ghSlug}`);
      setTimeout(loadJobs, 3000);
    } catch (e: any) {
      toast.error(e.message);
    }
  }

  async function handleQueueApply(jobId: string) {
    if (!userId) { toast.warning('Complete onboarding first'); return; }
    try {
      await api.applications.queue(userId, jobId);
      toast.success('🚀 Job added to apply queue! Optimization started.');
      loadJobs();
    } catch (e: any) {
      toast.error('Failed to queue: ' + e.message);
    }
  }

  async function handleClearAll() {
    if (!confirm('Are you sure you want to clear all jobs, applications, and prep sheets? This cannot be undone.')) return;
    try {
      await api.jobs.clearAll();
      toast.success('Job queue and all applications cleared successfully.');
      loadJobs();
    } catch (e: any) {
      toast.error('Failed to clear queue: ' + e.message);
    }
  }

  const filtered = jobs.filter(j =>
    !search || j.title.toLowerCase().includes(search.toLowerCase()) ||
    j.company.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="p-8 min-h-screen">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white mb-1">
            Job <span className="gradient-text">Discovery Queue</span>
          </h1>
          <p className="text-sm text-slate-500">{jobs.length} active postings from multiple sources</p>
        </div>
        <button onClick={loadJobs} className="flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 text-slate-300 border border-white/10 text-sm hover:bg-white/10 transition-colors">
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} /> Refresh
        </button>
      </div>

      {/* Scrape controls */}
      <div className="glass-card p-4 mb-6 space-y-3">
        <p className="text-[11px] text-slate-500 font-medium uppercase tracking-wide">Job Sources — No API key required</p>
        <div className="flex flex-wrap gap-2">
          <button onClick={scrapeYC}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-orange-500/10 text-orange-400 border border-orange-500/20 text-xs font-medium hover:bg-orange-500/20 transition-colors">
            <Zap className="w-3.5 h-3.5" /> YC Jobs
          </button>
          <button onClick={() => scrapeSource('remotive')}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 text-xs font-medium hover:bg-indigo-500/20 transition-colors">
            <Zap className="w-3.5 h-3.5" /> Remotive
          </button>
          <button onClick={() => scrapeSource('remoteok')}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20 text-xs font-medium hover:bg-purple-500/20 transition-colors">
            <Zap className="w-3.5 h-3.5" /> RemoteOK
          </button>
          <button onClick={() => scrapeSource('jobicy')}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-blue-500/10 text-blue-400 border border-blue-500/20 text-xs font-medium hover:bg-blue-500/20 transition-colors">
            <Zap className="w-3.5 h-3.5" /> Jobicy
          </button>
          <div className="h-4 w-px bg-white/10 self-center" />
          <button onClick={scrapeAll}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl btn-glow text-white text-xs font-semibold">
            <Zap className="w-3.5 h-3.5" /> Scrape All
          </button>
          <button onClick={handleClearAll}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-red-500/10 text-red-400 border border-red-500/20 text-xs font-semibold hover:bg-red-500/20 transition-colors whitespace-nowrap ml-auto">
            Clear Queue
          </button>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex-1 flex items-center gap-2 bg-[#1c1c28] border border-white/10 rounded-xl px-3 py-2">
            <Building2 className="w-4 h-4 text-slate-500" />
            <input
              type="text"
              placeholder="Greenhouse company slug (e.g. stripe, airbnb, figma, datadog)..."
              value={ghSlug}
              onChange={e => setGhSlug(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && scrapeGreenhouse()}
              className="bg-transparent text-sm text-slate-300 placeholder-slate-600 outline-none flex-1"
            />
          </div>
          <button onClick={scrapeGreenhouse}
            className="px-4 py-2 rounded-xl bg-green-500/10 text-green-400 border border-green-500/20 text-sm font-medium hover:bg-green-500/20 transition-colors whitespace-nowrap">
            Greenhouse Board
          </button>
        </div>
      </div>

      {/* Search */}
      <div className="relative mb-6">
        <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
        <input
          type="text"
          placeholder="Filter jobs by title or company..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="w-full bg-[#16161f] border border-white/10 rounded-xl pl-10 pr-4 py-3 text-sm text-slate-300 placeholder-slate-600 outline-none focus:border-indigo-500/50 transition-colors"
        />
      </div>

      {/* Job grid */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array(9).fill(0).map((_, i) => (
            <div key={i} className="shimmer rounded-2xl h-52" />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-20">
          <p className="text-slate-500 text-sm mb-4">No jobs found. Try scraping a source above.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map(job => (
            <JobCard key={job.id} job={job} onQueueApply={userId ? handleQueueApply : undefined} />
          ))}
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 mt-8">
          <button
            onClick={() => setPage(p => Math.max(0, p - 1))}
            disabled={page === 0}
            className="flex items-center gap-1 px-4 py-2 rounded-xl bg-white/5 text-slate-400 text-sm disabled:opacity-30 hover:bg-white/10 transition-colors"
          >
            <ChevronLeft className="w-4 h-4" /> Prev
          </button>
          <span className="text-sm text-slate-500">Page {page + 1} of {totalPages}</span>
          <button
            onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1}
            className="flex items-center gap-1 px-4 py-2 rounded-xl bg-white/5 text-slate-400 text-sm disabled:opacity-30 hover:bg-white/10 transition-colors"
          >
            Next <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      )}
    </div>
  );
}
