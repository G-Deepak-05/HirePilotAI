'use client';

import { useState, useRef } from 'react';
import Link from 'next/link';
import { api, Resume } from '@/lib/api';
import { Upload, User, IndianRupee, CheckCircle, Loader2, Plus, X } from 'lucide-react';
import { toast } from 'sonner';

export default function OnboardingPage() {
  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [userId, setUserId] = useState('');

  // Step 1 — Profile
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [roles, setRoles] = useState<string[]>(['Software Engineer']);
  const [roleInput, setRoleInput] = useState('');
  const [locations, setLocations] = useState<string[]>(['Remote']);
  const [locInput, setLocInput] = useState('');
  const [salary, setSalary] = useState('');

  // Step 2 — Resume upload
  const [file, setFile] = useState<File | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [uploadResult, setUploadResult] = useState<Resume | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  // ─── Step 1: Create user ───────────────────────────────────────────────────
  async function createProfile() {
    if (!name.trim() || !email.trim()) { toast.warning('Name and email are required'); return; }
    setLoading(true);
    try {
      const user = await api.users.create({
        name: name.trim(),
        email: email.trim(),
        targetRoles: roles,
        targetLocations: locations,
        minimumSalary: salary ? parseInt(salary) : undefined,
      });
      setUserId(user.id);
      if (typeof window !== 'undefined') localStorage.setItem('hirepilot_user_id', user.id);
      toast.success('Profile created!');
      setStep(2);
    } catch (e: unknown) {
      toast.error(e instanceof Error ? e.message : 'Error creating profile');
    } finally {
      setLoading(false);
    }
  }

  // ─── Step 2: Upload resume ─────────────────────────────────────────────────
  async function uploadResume() {
    if (!file) { toast.warning('Select a PDF or DOCX file'); return; }
    setLoading(true);
    try {
      const resume = await api.resumes.upload(file, userId);
      setUploadResult(resume);
      toast.success('Resume parsed successfully by Qwen3!');
      setStep(3);
    } catch (e: unknown) {
      toast.error('Upload failed: ' + (e instanceof Error ? e.message : 'Unknown error'));
    } finally {
      setLoading(false);
    }
  }

  function addTag(list: string[], setList: (v: string[]) => void, val: string, setVal: (v: string) => void) {
    const trimmed = val.trim();
    if (trimmed && !list.includes(trimmed)) {
      setList([...list, trimmed]);
      setVal('');
    }
  }

  function removeTag(list: string[], setList: (v: string[]) => void, tag: string) {
    setList(list.filter(t => t !== tag));
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-8">
      <div className="w-full max-w-xl">
        {/* Progress */}
        <div className="flex items-center gap-2 mb-8">
          {[1, 2, 3].map(s => (
            <div key={s} className="flex items-center gap-2 flex-1">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0 transition-all duration-300
                ${s < step ? 'bg-indigo-600 text-white' : s === step ? 'btn-glow text-white' : 'bg-[#1c1c28] text-slate-600'}`}>
                {s < step ? <CheckCircle className="w-4 h-4" /> : s}
              </div>
              {s < 3 && <div className={`h-px flex-1 transition-all duration-300 ${s < step ? 'bg-indigo-600' : 'bg-[#1c1c28]'}`} />}
            </div>
          ))}
        </div>

        {/* ─── Step 1: Profile ─────────────────────────────────────────────── */}
        {step === 1 && (
          <div className="animate-fade-in-up">
            <h1 className="text-2xl font-bold text-white mb-1">Set up your <span className="gradient-text">profile</span></h1>
            <p className="text-sm text-slate-500 mb-8">Tell the agent what you're looking for</p>

            <div className="space-y-5">
              <Field icon={<User className="w-4 h-4 text-slate-500" />} label="Full Name">
                <input type="text" value={name} onChange={e => setName(e.target.value)}
                  placeholder="Jane Smith"
                  className="w-full bg-transparent text-sm text-slate-300 placeholder-slate-600 outline-none" />
              </Field>

              <Field icon={<User className="w-4 h-4 text-slate-500" />} label="Email">
                <input type="email" value={email} onChange={e => setEmail(e.target.value)}
                  placeholder="jane@example.com"
                  className="w-full bg-transparent text-sm text-slate-300 placeholder-slate-600 outline-none" />
              </Field>

              <div>
                <label className="text-xs text-slate-500 mb-2 block">Target Roles</label>
                <div className="bg-[#16161f] border border-white/10 rounded-xl p-3">
                  <div className="flex flex-wrap gap-2 mb-2">
                    {roles.map(r => (
                      <Tag key={r} label={r} onRemove={() => removeTag(roles, setRoles, r)} />
                    ))}
                  </div>
                  <div className="flex gap-2">
                    <input value={roleInput} onChange={e => setRoleInput(e.target.value)}
                      onKeyDown={e => e.key === 'Enter' && addTag(roles, setRoles, roleInput, setRoleInput)}
                      placeholder="Add role, press Enter..."
                      className="flex-1 bg-transparent text-xs text-slate-300 placeholder-slate-600 outline-none" />
                    <button onClick={() => addTag(roles, setRoles, roleInput, setRoleInput)}
                      className="text-indigo-400 hover:text-indigo-300">
                      <Plus className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>

              <div>
                <label className="text-xs text-slate-500 mb-2 block">Target Locations</label>
                <div className="bg-[#16161f] border border-white/10 rounded-xl p-3">
                  <div className="flex flex-wrap gap-2 mb-2">
                    {locations.map(l => (
                      <Tag key={l} label={l} onRemove={() => removeTag(locations, setLocations, l)} />
                    ))}
                  </div>
                  <div className="flex gap-2">
                    <input value={locInput} onChange={e => setLocInput(e.target.value)}
                      onKeyDown={e => e.key === 'Enter' && addTag(locations, setLocations, locInput, setLocInput)}
                      placeholder="Add location, press Enter..."
                      className="flex-1 bg-transparent text-xs text-slate-300 placeholder-slate-600 outline-none" />
                    <button onClick={() => addTag(locations, setLocations, locInput, setLocInput)}
                      className="text-indigo-400 hover:text-indigo-300">
                      <Plus className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>

              <Field icon={<IndianRupee className="w-4 h-4 text-slate-500" />} label="Minimum Salary (₹ per annum)">
                <input type="number" value={salary} onChange={e => setSalary(e.target.value)}
                  placeholder="1500000  (e.g. ₹15L p.a.)"
                  className="w-full bg-transparent text-sm text-slate-300 placeholder-slate-600 outline-none" />
              </Field>

              <button onClick={createProfile} disabled={loading}
                className="w-full btn-glow py-3 rounded-xl text-white font-semibold flex items-center justify-center gap-2">
                {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                Continue to Resume Upload →
              </button>
            </div>
          </div>
        )}

        {/* ─── Step 2: Resume Upload ────────────────────────────────────────── */}
        {step === 2 && (
          <div className="animate-fade-in-up">
            <h1 className="text-2xl font-bold text-white mb-1">Upload your <span className="gradient-text">resume</span></h1>
            <p className="text-sm text-slate-500 mb-8">Qwen3 will parse it into a structured profile</p>

            <div
              className={`border-2 border-dashed rounded-2xl p-12 text-center transition-all duration-200 cursor-pointer
                ${dragOver ? 'border-indigo-500 bg-indigo-500/5' : 'border-white/10 hover:border-indigo-500/40'}`}
              onDragOver={e => { e.preventDefault(); setDragOver(true); }}
              onDragLeave={() => setDragOver(false)}
              onDrop={e => { e.preventDefault(); setDragOver(false); if (e.dataTransfer.files[0]) setFile(e.dataTransfer.files[0]); }}
              onClick={() => fileRef.current?.click()}
            >
              <input ref={fileRef} type="file" accept=".pdf,.docx,.doc" className="hidden"
                onChange={e => e.target.files?.[0] && setFile(e.target.files[0])} />

              <Upload className={`w-10 h-10 mx-auto mb-4 ${file ? 'text-indigo-400' : 'text-slate-600'}`} />
              {file ? (
                <div>
                  <p className="text-sm font-medium text-indigo-300">{file.name}</p>
                  <p className="text-xs text-slate-500 mt-1">{(file.size / 1024).toFixed(0)} KB</p>
                </div>
              ) : (
                <div>
                  <p className="text-sm text-slate-400">Drop your PDF or DOCX here</p>
                  <p className="text-xs text-slate-600 mt-1">or click to browse</p>
                </div>
              )}
            </div>

            <button onClick={uploadResume} disabled={loading || !file}
              className="w-full btn-glow py-3 rounded-xl text-white font-semibold flex items-center justify-center gap-2 mt-6 disabled:opacity-50">
              {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
              {loading ? 'Parsing with Qwen3...' : 'Parse Resume →'}
            </button>
          </div>
        )}

        {/* ─── Step 3: Complete ─────────────────────────────────────────────── */}
        {step === 3 && (
          <div className="animate-fade-in-up text-center">
            <div className="w-20 h-20 rounded-full btn-glow flex items-center justify-center mx-auto mb-6 animate-pulse-glow">
              <CheckCircle className="w-10 h-10 text-white" />
            </div>
            <h1 className="text-2xl font-bold text-white mb-2">You're <span className="gradient-text">live!</span></h1>
            <p className="text-sm text-slate-400 mb-2">HirePilot is ready to hunt on your behalf!</p>

            {uploadResult && (
              <div className="glass-card p-4 text-left mb-6 mt-4">
                <p className="text-xs text-slate-500 mb-2">Parsed skills ({uploadResult.skills?.length ?? 0})</p>
                <div className="flex flex-wrap gap-1.5">
                  {(uploadResult.skills ?? []).slice(0, 12).map((s: string) => (
                    <span key={s} className="text-[10px] px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/15">{s}</span>
                  ))}
                </div>
              </div>
            )}

            <Link href="/" className="inline-block btn-glow px-8 py-3 rounded-xl text-white font-semibold">
              Open Dashboard &rarr;
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}

function Field({ icon, label, children }: { icon: React.ReactNode; label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-xs text-slate-500 mb-2 block">{label}</label>
      <div className="flex items-center gap-2 bg-[#16161f] border border-white/10 rounded-xl px-3 py-3 focus-within:border-indigo-500/50 transition-colors">
        {icon}
        {children}
      </div>
    </div>
  );
}

function Tag({ label, onRemove }: { label: string; onRemove: () => void }) {
  return (
    <span className="flex items-center gap-1 text-xs px-2.5 py-1 rounded-full bg-indigo-500/15 text-indigo-300 border border-indigo-500/20">
      {label}
      <button onClick={onRemove} className="ml-0.5 hover:text-red-400 transition-colors">
        <X className="w-3 h-3" />
      </button>
    </span>
  );
}
