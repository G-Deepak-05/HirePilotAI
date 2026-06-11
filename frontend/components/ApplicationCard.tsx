'use client';

import { Application } from '@/lib/api';
import { CheckCircle, Clock, XCircle, Award, AlertCircle, Loader2 } from 'lucide-react';

const STATUS_CONFIG: Record<string, { label: string; icon: React.ElementType; color: string }> = {
  QUEUED:               { label: 'Queued',        icon: Clock,        color: 'status-QUEUED' },
  TAILORING:            { label: 'Tailoring...',  icon: Loader2,      color: 'status-TAILORING' },
  PENDING_CONFIRMATION: { label: 'Confirm',       icon: AlertCircle,  color: 'status-PENDING_CONFIRMATION' },
  APPLYING:             { label: 'Applying...',   icon: Loader2,      color: 'status-APPLYING' },
  APPLIED:              { label: 'Applied',       icon: CheckCircle,  color: 'status-APPLIED' },
  ASSESSMENT:           { label: 'Assessment',    icon: Clock,        color: 'status-ASSESSMENT' },
  INTERVIEW:            { label: 'Interview!',    icon: Award,        color: 'status-INTERVIEW' },
  OFFER:                { label: 'Offer! 🎉',     icon: Award,        color: 'status-OFFER' },
  REJECTED:             { label: 'Rejected',      icon: XCircle,      color: 'status-REJECTED' },
  FAILED:               { label: 'Failed',        icon: XCircle,      color: 'status-FAILED' },
};

interface ApplicationCardProps {
  application: Application;
  onConfirm?: (app: Application) => void;
  onStatusChange?: (app: Application, status: string) => void;
  compact?: boolean;
}

export default function ApplicationCard({
  application,
  onConfirm,
  compact = false,
}: ApplicationCardProps) {
  const cfg = STATUS_CONFIG[application.status] ?? STATUS_CONFIG.QUEUED;
  const Icon = cfg.icon;
  const score = application.matchScore ?? 0;
  const scoreColor = score >= 90 ? '#4ade80' : score >= 80 ? '#818cf8' : '#fbbf24';

  return (
    <div className="glass-card p-4 hover:border-indigo-500/25 transition-all duration-200 cursor-default animate-fade-in-up">
      {/* Header */}
      <div className="flex items-start justify-between gap-2 mb-3">
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-white truncate">{application.jobTitle}</p>
          <p className="text-xs text-slate-500 truncate">{application.company}</p>
        </div>

        {/* Match score */}
        <div className="flex-shrink-0 text-center">
          <svg width="44" height="44" viewBox="0 0 44 44">
            <circle cx="22" cy="22" r="18" fill="none" stroke="#1c1c28" strokeWidth="4"/>
            <circle
              cx="22" cy="22" r="18"
              fill="none"
              stroke={scoreColor}
              strokeWidth="4"
              strokeDasharray={`${(score / 100) * 113} 113`}
              strokeLinecap="round"
              transform="rotate(-90 22 22)"
              style={{ transition: 'stroke-dasharray 0.8s ease' }}
            />
            <text x="22" y="26" textAnchor="middle" fontSize="10" fill={scoreColor} fontWeight="700">
              {Math.round(score)}%
            </text>
          </svg>
        </div>
      </div>

      {/* Status badge */}
      <div className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-medium border ${cfg.color} mb-3`}>
        <Icon className={`w-3 h-3 ${application.status === 'TAILORING' || application.status === 'APPLYING' ? 'animate-spin' : ''}`} />
        {cfg.label}
      </div>

      {/* Confirm button */}
      {application.status === 'PENDING_CONFIRMATION' && onConfirm && (
        <button
          onClick={() => onConfirm(application)}
          className="w-full mt-1 px-3 py-2 text-xs font-semibold btn-glow rounded-xl text-white"
        >
          ✅ Review & Apply
        </button>
      )}

      {/* Applied date */}
      {application.appliedAt && !compact && (
        <p className="text-[10px] text-slate-600 mt-2">
          Applied {new Date(application.appliedAt).toLocaleDateString()}
        </p>
      )}
    </div>
  );
}
