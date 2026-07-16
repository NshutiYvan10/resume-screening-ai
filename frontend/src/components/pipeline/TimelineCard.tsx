import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import {
  Activity, MessageSquarePlus, ArrowRight, FileText, CalendarPlus, CalendarCheck, CalendarX,
  ClipboardCheck, BadgeDollarSign, CheckCircle2, XCircle, RotateCcw, PartyPopper, Send, Sparkles,
} from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import { Spinner } from '../ui';
import { formatDateTime, humanize } from '../../lib/format';
import type { PipelineEvent } from '../../types';

const EVENT_META: Record<string, { icon: React.ReactNode; color: string }> = {
  APPLIED: { icon: <FileText className="h-3.5 w-3.5" />, color: 'bg-slate-100 text-slate-600' },
  STAGE_CHANGED: { icon: <ArrowRight className="h-3.5 w-3.5" />, color: 'bg-brand-100 text-brand-700' },
  NOTE_ADDED: { icon: <MessageSquarePlus className="h-3.5 w-3.5" />, color: 'bg-slate-100 text-slate-600' },
  SCREENING_COMPLETED: { icon: <Sparkles className="h-3.5 w-3.5" />, color: 'bg-violet-100 text-violet-700' },
  INTERVIEW_SCHEDULED: { icon: <CalendarPlus className="h-3.5 w-3.5" />, color: 'bg-blue-100 text-blue-700' },
  INTERVIEW_COMPLETED: { icon: <CalendarCheck className="h-3.5 w-3.5" />, color: 'bg-green-100 text-green-700' },
  INTERVIEW_CANCELLED: { icon: <CalendarX className="h-3.5 w-3.5" />, color: 'bg-red-100 text-red-600' },
  FEEDBACK_SUBMITTED: { icon: <ClipboardCheck className="h-3.5 w-3.5" />, color: 'bg-teal-100 text-teal-700' },
  OFFER_CREATED: { icon: <BadgeDollarSign className="h-3.5 w-3.5" />, color: 'bg-amber-100 text-amber-700' },
  OFFER_APPROVED: { icon: <CheckCircle2 className="h-3.5 w-3.5" />, color: 'bg-green-100 text-green-700' },
  OFFER_REVISED: { icon: <BadgeDollarSign className="h-3.5 w-3.5" />, color: 'bg-amber-100 text-amber-700' },
  OFFER_EXTENDED: { icon: <Send className="h-3.5 w-3.5" />, color: 'bg-violet-100 text-violet-700' },
  OFFER_ACCEPTED: { icon: <CheckCircle2 className="h-3.5 w-3.5" />, color: 'bg-green-100 text-green-700' },
  OFFER_DECLINED: { icon: <XCircle className="h-3.5 w-3.5" />, color: 'bg-red-100 text-red-600' },
  REJECTED: { icon: <XCircle className="h-3.5 w-3.5" />, color: 'bg-red-100 text-red-600' },
  REOPENED: { icon: <RotateCcw className="h-3.5 w-3.5" />, color: 'bg-blue-100 text-blue-700' },
  HIRED: { icon: <PartyPopper className="h-3.5 w-3.5" />, color: 'bg-green-100 text-green-700' },
  WITHDRAWN: { icon: <XCircle className="h-3.5 w-3.5" />, color: 'bg-slate-100 text-slate-500' },
};

function describe(e: PipelineEvent): string {
  const d = e.details || {};
  switch (e.type) {
    case 'APPLIED': return d.resubmitted ? 'Re-applied after withdrawing' : 'Applied for the position';
    case 'STAGE_CHANGED': return `Moved from ${humanize(String(d.from))} to ${humanize(String(d.to))}`;
    case 'NOTE_ADDED': return String(d.text || '');
    case 'INTERVIEW_SCHEDULED': return `Scheduled a ${humanize(String(d.type))} interview for ${d.when}`;
    case 'INTERVIEW_COMPLETED': return `Marked the interview of ${d.when} completed`;
    case 'INTERVIEW_CANCELLED': return `Cancelled the interview of ${d.when}`;
    case 'FEEDBACK_SUBMITTED': return `Submitted a scorecard (${humanize(String(d.recommendation))}, ${d.rating}/4)`;
    case 'OFFER_CREATED': return `Created an offer at ${d.salary} (${humanize(String(d.status))})`;
    case 'OFFER_APPROVED': return `Approved the offer (${d.salary})`;
    case 'OFFER_REVISED': return `Revised the offer: ${d.from} → ${d.to}`;
    case 'OFFER_EXTENDED': return `Extended the offer to the candidate (${d.salary})`;
    case 'OFFER_ACCEPTED': return 'Candidate accepted the offer';
    case 'OFFER_DECLINED': return 'Candidate declined the offer';
    case 'REJECTED': return `Rejected (${humanize(String(d.reason))})${d.internalNote ? ` — ${d.internalNote}` : ''}`;
    case 'REOPENED': return `Reopened the application to ${humanize(String(d.to))}`;
    case 'HIRED': return `Marked hired${d.startDate ? ` — start date ${d.startDate}` : ''}`;
    case 'WITHDRAWN': return 'Candidate withdrew the application';
    default: return humanize(e.type);
  }
}

export default function TimelineCard({
  applicationId,
  events,
  canAddNote,
  onChanged,
}: {
  applicationId: string;
  events: PipelineEvent[];
  canAddNote: boolean;
  onChanged: () => void;
}) {
  const toast = useToast();
  const [note, setNote] = useState('');

  const addNote = useMutation({
    mutationFn: async () => api.post(`/applications/${applicationId}/notes`, { text: note.trim() }),
    onSuccess: () => {
      setNote('');
      onChanged();
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  return (
    <div className="card p-6">
      <h2 className="mb-4 flex items-center gap-2 font-semibold text-slate-800">
        <Activity className="h-4 w-4 text-brand-600" /> Activity timeline
      </h2>

      {canAddNote && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (note.trim()) addNote.mutate();
          }}
          className="mb-4 flex gap-2"
        >
          <input
            className="input flex-1"
            placeholder="Add a note for the hiring team…"
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
          <button className="btn-secondary shrink-0" disabled={addNote.isPending || !note.trim()}>
            {addNote.isPending ? <Spinner className="h-4 w-4" /> : <MessageSquarePlus className="h-4 w-4" />}
            Note
          </button>
        </form>
      )}

      {!events.length ? (
        <p className="py-4 text-center text-sm text-slate-400">No activity yet</p>
      ) : (
        <ol className="relative space-y-4 border-l border-slate-200 pl-5">
          {events.map((e) => {
            const meta = EVENT_META[e.type] || EVENT_META.STAGE_CHANGED;
            return (
              <li key={e.id} className="relative">
                <span className={`absolute -left-[27px] flex h-5 w-5 items-center justify-center rounded-full ${meta.color}`}>
                  {meta.icon}
                </span>
                <p className="text-sm text-slate-700">{describe(e)}</p>
                <p className="mt-0.5 text-xs text-slate-400">
                  {e.actorName || 'System'} · {formatDateTime(e.createdAt)}
                </p>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}
