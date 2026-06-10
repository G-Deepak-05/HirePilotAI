'use client';

import { Job } from '@/lib/api';
import { ExternalLink, MapPin, IndianRupee, Wifi } from 'lucide-react';

interface JobCardProps {
  job: Job;
  matchScore?: number;
  onEvaluate?: (jobId: string) => void;
}

const SOURCE_COLORS: Record<string, string> = {
  YC_JOBS:    'text-orange-400 bg-orange-400/10',
  GREENHOUSE: 'text-green-400 bg-green-400/10',
  LINKEDIN:   'text-blue-400 bg-blue-400/10',
  WELLFOUND:  'text-purple-400 bg-purple-400/10',
  DEFAULT:    'text-slate-400 bg-slate-400/10',
};

export default function JobCard({ job, matchScore, onEvaluate }: JobCardProps) {
  const sourceColor = SOURCE_COLORS[job.source] ?? SOURCE_COLORS.DEFAULT;
  const scoreColor = matchScore != null
    ? matchScore >= 80 ? 'text-green-400' : matchScore >= 60 ? 'text-yellow-400' : 'text-red-400'
    : '';

  return (
    <div className="glass-card p-5 hover:border-indigo-500/30 hover:shadow-lg hover:shadow-indigo-500/5 transition-all duration-200 animate-fade-in-up group">
      {/* Top row */}
      <div className="flex items-start gap-3 mb-4">
        {/* Company initials avatar */}
        <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-600/20 to-purple-600/20 border border-indigo-500/20 flex items-center justify-center flex-shrink-0">
          <span className="text-sm font-bold text-indigo-300">
            {job.company.slice(0, 2).toUpperCase()}
          </span>
        </div>

        <div className="flex-1 min-w-0">
          <h3 className="text-sm font-semibold text-white truncate">{job.title}</h3>
          <p className="text-xs text-slate-400">{job.company}</p>
        </div>

        {/* Source badge */}
        <span className={`text-[10px] font-medium px-2 py-0.5 rounded-full ${sourceColor}`}>
          {job.source?.replace('_', ' ')}
        </span>
      </div>

      {/* Meta */}
      <div className="flex flex-wrap gap-3 mb-4 text-[11px] text-slate-500">
        {job.location && (
          <span className="flex items-center gap-1">
            <MapPin className="w-3 h-3" /> {job.location}
          </span>
        )}
        {job.isRemote && (
          <span className="flex items-center gap-1 text-indigo-400">
            <Wifi className="w-3 h-3" /> Remote
          </span>
        )}
        {(job.salaryMin || job.salaryMax) && (
          <span className="flex items-center gap-1">
            <IndianRupee className="w-3 h-3" />
            {job.salaryMin ? `${(job.salaryMin / 100000).toFixed(1)}L` : ''}
            {job.salaryMax ? ` – ₹${(job.salaryMax / 100000).toFixed(1)}L` : ''}
          </span>
        )}
      </div>

      {/* Skills */}
      {job.requiredSkills?.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mb-4">
          {job.requiredSkills.slice(0, 5).map((skill) => (
            <span key={skill} className="text-[10px] px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/15">
              {skill}
            </span>
          ))}
          {job.requiredSkills.length > 5 && (
            <span className="text-[10px] text-slate-600">+{job.requiredSkills.length - 5} more</span>
          )}
        </div>
      )}

      {/* Footer */}
      <div className="flex items-center justify-between pt-3 border-t border-white/5">
        {matchScore != null ? (
          <span className={`text-sm font-bold ${scoreColor}`}>{Math.round(matchScore)}% match</span>
        ) : (
          <span className="text-[11px] text-slate-600">
            {job.scrapedAt ? new Date(job.scrapedAt).toLocaleDateString() : ''}
          </span>
        )}

        <div className="flex gap-2">
          {onEvaluate && (
            <button
              onClick={() => onEvaluate(job.id)}
              className="text-[11px] px-3 py-1.5 rounded-lg bg-indigo-600/20 text-indigo-300 border border-indigo-500/20 hover:bg-indigo-600/30 transition-colors"
            >
              Match Me
            </button>
          )}
          <a
            href={job.url}
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-1 text-[11px] px-3 py-1.5 rounded-lg bg-white/5 text-slate-400 hover:bg-white/10 transition-colors"
          >
            View <ExternalLink className="w-3 h-3" />
          </a>
        </div>
      </div>
    </div>
  );
}
