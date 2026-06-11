'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  LayoutDashboard, Briefcase, FileText, Brain, Settings, Zap,
  ChevronRight, Activity, BarChart3
} from 'lucide-react';

const navItems = [
  { href: '/',              label: 'Dashboard',    icon: LayoutDashboard },
  { href: '/jobs',          label: 'Job Queue',    icon: Briefcase },
  { href: '/applications',  label: 'Applications', icon: FileText },
  { href: '/tracker',       label: 'Tracker',      icon: BarChart3 },
  { href: '/prep',          label: 'Prep Sheets',  icon: Brain },
  { href: '/onboarding',   label: 'Profile',       icon: Settings },
];

export default function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="w-64 flex-shrink-0 flex flex-col border-r border-white/5 bg-[#111118]">
      {/* Logo */}
      <div className="px-6 pt-6 pb-4">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl btn-glow flex items-center justify-center">
            <Zap className="w-5 h-5 text-white" />
          </div>
          <div>
            <p className="text-sm font-bold gradient-text">HirePilot</p>
            <p className="text-[10px] text-slate-500 leading-tight">Autonomous AI Agent</p>
          </div>
        </div>
      </div>

      {/* Agent status pill */}
      <div className="mx-4 mb-4 px-3 py-2 rounded-xl bg-[#1c1c28] border border-white/5 flex items-center gap-2">
        <span className="relative flex h-2 w-2">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75" />
          <span className="relative inline-flex rounded-full h-2 w-2 bg-green-400" />
        </span>
        <span className="text-xs text-slate-400">Agents Running</span>
        <Activity className="w-3 h-3 text-slate-500 ml-auto" />
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-3 space-y-1">
        {navItems.map(({ href, label, icon: Icon }) => {
          const active = pathname === href || (href !== '/' && pathname.startsWith(href));
          return (
            <Link
              key={href}
              href={href}
              className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-150 group
                ${active
                  ? 'bg-gradient-to-r from-indigo-600/20 to-purple-600/10 text-indigo-300 border border-indigo-500/20'
                  : 'text-slate-500 hover:text-slate-200 hover:bg-white/5'
                }`}
            >
              <Icon className={`w-4 h-4 flex-shrink-0 transition-colors ${active ? 'text-indigo-400' : 'text-slate-600 group-hover:text-slate-400'}`} />
              {label}
              {active && <ChevronRight className="w-3 h-3 ml-auto text-indigo-400" />}
            </Link>
          );
        })}
      </nav>

      {/* Footer */}
      <div className="p-4 mt-auto">
        <div className="glass-card p-3">
          <p className="text-[11px] text-slate-500 leading-relaxed">
            Running locally on <span className="text-indigo-400">Ollama</span> with Qwen3 · Llama 3.1 · DeepSeek-R1
          </p>
        </div>
      </div>
    </aside>
  );
}
