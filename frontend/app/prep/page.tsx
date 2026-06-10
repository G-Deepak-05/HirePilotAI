'use client';

import { useEffect, useState } from 'react';
import { api, PrepSheet } from '@/lib/api';
import { Brain, Loader2, RefreshCw, ChevronDown, Code, Server, Users } from 'lucide-react';
import { toast } from 'sonner';

const USER_ID = typeof window !== 'undefined'
  ? localStorage.getItem('hirepilot_user_id') ?? ''
  : '';

interface AppSummary { id: string; jobTitle: string; company: string; status: string; }

export default function PrepPage() {
  const [apps, setApps] = useState<AppSummary[]>([]);
  const [selectedApp, setSelectedApp] = useState<AppSummary | null>(null);
  const [prepSheet, setPrepSheet] = useState<PrepSheet | null>(null);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [activeSection, setActiveSection] = useState<'technical' | 'system' | 'behavioral'>('technical');

  useEffect(() => {
    if (USER_ID) {
      api.applications.listByUser(USER_ID)
        .then(list => setApps(list.filter(a => ['APPLIED','ASSESSMENT','INTERVIEW'].includes(a.status))))
        .catch(() => {});
    }
  }, []);

  async function loadPrepSheet(app: AppSummary) {
    setSelectedApp(app);
    setPrepSheet(null);
    setLoading(true);
    try {
      const sheet = await api.prep.get(app.id);
      setPrepSheet(sheet);
    } catch {
      setPrepSheet(null);
    } finally {
      setLoading(false);
    }
  }

  async function generatePrepSheet() {
    if (!selectedApp) return;
    setGenerating(true);
    try {
      toast.info('DeepSeek-R1 is generating your prep sheet...');
      const sheet = await api.prep.generate(selectedApp.id);
      setPrepSheet(sheet);
      toast.success('Prep sheet ready!');
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : 'Generation failed');
    } finally {
      setGenerating(false);
    }
  }

  const SECTIONS = [
    { key: 'technical' as const, label: 'Technical', icon: Code, questions: prepSheet?.technicalQuestions ?? [] },
    { key: 'system' as const, label: 'System Design', icon: Server, questions: prepSheet?.systemDesignScenarios ?? [] },
    { key: 'behavioral' as const, label: 'Behavioral', icon: Users, questions: prepSheet?.behavioralQuestions ?? [] },
  ];

  return (
    <div className="p-8 min-h-screen">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-white mb-1">
          Interview <span className="gradient-text">Prep Sheets</span>
        </h1>
        <p className="text-sm text-slate-500">AI-generated questions tailored to each JD by DeepSeek-R1</p>
      </div>

      <div className="grid grid-cols-12 gap-6">
        {/* Application list */}
        <div className="col-span-4">
          <p className="text-xs text-slate-500 mb-3 font-medium">SELECT APPLICATION</p>
          {apps.length === 0 ? (
            <div className="glass-card p-6 text-center">
              <Brain className="w-8 h-8 text-slate-600 mx-auto mb-2" />
              <p className="text-xs text-slate-500">No applied applications yet</p>
            </div>
          ) : (
            <div className="space-y-2">
              {apps.map(app => (
                <button
                  key={app.id}
                  onClick={() => loadPrepSheet(app)}
                  className={`w-full text-left glass-card p-4 transition-all duration-200 hover:border-indigo-500/30
                    ${selectedApp?.id === app.id ? 'border-indigo-500/40 bg-indigo-500/5' : ''}`}
                >
                  <p className="text-sm font-medium text-white truncate">{app.jobTitle}</p>
                  <p className="text-xs text-slate-500">{app.company}</p>
                  <span className={`text-[10px] mt-2 inline-block px-2 py-0.5 rounded-full border status-${app.status}`}>
                    {app.status}
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Prep sheet */}
        <div className="col-span-8">
          {!selectedApp ? (
            <div className="glass-card p-12 text-center h-full flex flex-col items-center justify-center">
              <Brain className="w-12 h-12 text-slate-700 mb-4" />
              <p className="text-slate-500">Select an application to view its prep sheet</p>
            </div>
          ) : loading ? (
            <div className="glass-card p-12 flex items-center justify-center">
              <Loader2 className="w-8 h-8 text-indigo-400 animate-spin" />
            </div>
          ) : !prepSheet || prepSheet.generationStatus === 'PENDING' ? (
            <div className="glass-card p-10 text-center">
              <Brain className="w-12 h-12 text-indigo-400/40 mx-auto mb-4" />
              <p className="text-white font-semibold mb-2">No prep sheet yet</p>
              <p className="text-sm text-slate-500 mb-6">Generate one using DeepSeek-R1 based on the JD</p>
              <button onClick={generatePrepSheet} disabled={generating}
                className="btn-glow px-6 py-2.5 rounded-xl text-white font-medium flex items-center gap-2 mx-auto">
                {generating ? <Loader2 className="w-4 h-4 animate-spin" /> : <Brain className="w-4 h-4" />}
                {generating ? 'Generating...' : 'Generate Prep Sheet'}
              </button>
            </div>
          ) : (
            <div className="glass-card p-6 animate-fade-in-up">
              {/* Header */}
              <div className="flex items-center justify-between mb-5">
                <div>
                  <h2 className="text-lg font-bold text-white">{selectedApp.jobTitle}</h2>
                  <p className="text-sm text-slate-500">{selectedApp.company}</p>
                </div>
                <button onClick={generatePrepSheet} disabled={generating}
                  className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-xl bg-white/5 text-slate-400 hover:bg-white/10 transition-colors">
                  <RefreshCw className={`w-3 h-3 ${generating ? 'animate-spin' : ''}`} /> Regenerate
                </button>
              </div>

              {/* Section tabs */}
              <div className="flex gap-2 mb-5">
                {SECTIONS.map(({ key, label, icon: Icon, questions }) => (
                  <button key={key}
                    onClick={() => setActiveSection(key)}
                    className={`flex items-center gap-1.5 text-xs px-3 py-2 rounded-xl font-medium transition-all
                      ${activeSection === key
                        ? 'bg-indigo-600/20 text-indigo-300 border border-indigo-500/25'
                        : 'text-slate-500 hover:text-slate-300 hover:bg-white/5'}`}>
                    <Icon className="w-3.5 h-3.5" />
                    {label}
                    <span className="ml-1 text-[10px] opacity-60">{questions.length}</span>
                  </button>
                ))}
              </div>

              {/* Questions */}
              <div className="space-y-3">
                {SECTIONS.find(s => s.key === activeSection)?.questions.map((q, i) => (
                  <Accordion key={i} number={i + 1} question={q} />
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function Accordion({ number, question }: { number: number; question: string }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="bg-[#16161f] border border-white/5 rounded-xl overflow-hidden">
      <button onClick={() => setOpen(!open)}
        className="w-full flex items-center gap-3 px-4 py-3.5 text-left hover:bg-white/3 transition-colors">
        <span className="w-6 h-6 rounded-lg bg-indigo-600/20 text-indigo-400 text-xs font-bold flex items-center justify-center flex-shrink-0">
          {number}
        </span>
        <p className="text-sm text-slate-300 flex-1">{question}</p>
        <ChevronDown className={`w-4 h-4 text-slate-600 flex-shrink-0 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="px-4 pb-4 pl-13 border-t border-white/5">
          <div className="mt-3 pl-9">
            <p className="text-xs text-slate-500 italic">
              💡 Think through this using the STAR method: Situation, Task, Action, Result.
            </p>
            <div className="mt-3 space-y-1.5">
              {['Situation', 'Task', 'Action', 'Result'].map(step => (
                <div key={step} className="flex items-center gap-2 text-xs text-slate-600">
                  <span className="w-2 h-2 rounded-full bg-indigo-600/40 flex-shrink-0" />
                  <span className="font-medium text-slate-500">{step}:</span>
                  <span className="italic">Your response here...</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
