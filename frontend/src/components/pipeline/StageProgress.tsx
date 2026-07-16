import { Check, X } from 'lucide-react';
import clsx from 'clsx';
import type { ApplicationStatus } from '../../types';

const STAGES: { key: ApplicationStatus; label: string }[] = [
  { key: 'SUBMITTED', label: 'Submitted' },
  { key: 'UNDER_REVIEW', label: 'Under Review' },
  { key: 'SHORTLISTED', label: 'Shortlisted' },
  { key: 'INTERVIEW', label: 'Interview' },
  { key: 'OFFERED', label: 'Offered' },
  { key: 'HIRED', label: 'Hired' },
];

export default function StageProgress({ status }: { status: ApplicationStatus }) {
  const terminal = status === 'REJECTED' || status === 'WITHDRAWN';
  const currentIdx = STAGES.findIndex((s) => s.key === status);

  return (
    <div className="card p-5">
      <div className="flex items-center">
        {STAGES.map((stage, idx) => {
          const done = currentIdx > idx || status === 'HIRED';
          const current = currentIdx === idx && !terminal;
          return (
            <div key={stage.key} className="flex flex-1 items-center last:flex-none">
              <div className="flex flex-col items-center">
                <div
                  className={clsx(
                    'flex h-8 w-8 items-center justify-center rounded-full border-2 text-xs font-bold',
                    done && 'border-green-500 bg-green-500 text-white',
                    current && 'border-brand-600 bg-brand-600 text-white',
                    !done && !current && 'border-slate-300 bg-white text-slate-400'
                  )}
                >
                  {done ? <Check className="h-4 w-4" /> : idx + 1}
                </div>
                <span
                  className={clsx(
                    'mt-1.5 whitespace-nowrap text-[11px] font-medium',
                    current ? 'text-brand-700' : done ? 'text-green-700' : 'text-slate-400'
                  )}
                >
                  {stage.label}
                </span>
              </div>
              {idx < STAGES.length - 1 && (
                <div
                  className={clsx(
                    'mx-2 mb-5 h-0.5 flex-1',
                    currentIdx > idx || status === 'HIRED' ? 'bg-green-500' : 'bg-slate-200'
                  )}
                />
              )}
            </div>
          );
        })}
      </div>
      {terminal && (
        <div
          className={clsx(
            'mt-4 flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium',
            status === 'REJECTED' ? 'bg-red-50 text-red-700' : 'bg-slate-100 text-slate-600'
          )}
        >
          <X className="h-4 w-4" />
          {status === 'REJECTED' ? 'This application was rejected' : 'The candidate withdrew this application'}
        </div>
      )}
    </div>
  );
}
