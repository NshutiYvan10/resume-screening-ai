import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  CalendarPlus, Video, Phone, Building, Code, CheckCircle2, Clock, XCircle, Lock, Star,
} from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import { Field, Modal, Spinner, StatusPill } from '../ui';
import { formatDateTime, humanize } from '../../lib/format';
import type {
  FeedbackRecommendation, Interview, InterviewType, User,
} from '../../types';

const TYPE_ICON: Record<InterviewType, React.ReactNode> = {
  PHONE: <Phone className="h-4 w-4" />,
  VIDEO: <Video className="h-4 w-4" />,
  ONSITE: <Building className="h-4 w-4" />,
  TECHNICAL: <Code className="h-4 w-4" />,
};

const REC_STYLE: Record<FeedbackRecommendation, string> = {
  STRONG_YES: 'bg-green-100 text-green-700',
  YES: 'bg-teal-100 text-teal-700',
  NO: 'bg-amber-100 text-amber-700',
  STRONG_NO: 'bg-red-100 text-red-600',
};

export default function InterviewsCard({
  applicationId,
  interviews,
  canSchedule,
  onChanged,
}: {
  applicationId: string;
  interviews: Interview[];
  canSchedule: boolean;
  onChanged: () => void;
}) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [feedbackFor, setFeedbackFor] = useState<Interview | null>(null);

  // schedule form state
  const [form, setForm] = useState({
    date: '', time: '', durationMinutes: 60, type: 'VIDEO' as InterviewType,
    location: '', notes: '', panelUserIds: [] as string[],
  });
  const [fb, setFb] = useState({
    rating: 3, recommendation: 'YES' as FeedbackRecommendation, strengths: '', concerns: '',
  });

  const { data: team } = useQuery({
    queryKey: ['team-members'],
    queryFn: async () => (await api.get<User[]>('/users/team')).data,
    enabled: scheduleOpen,
  });

  const schedule = useMutation({
    mutationFn: async () =>
      api.post(`/applications/${applicationId}/interviews`, {
        scheduledAt: new Date(`${form.date}T${form.time}`).toISOString(),
        durationMinutes: form.durationMinutes,
        type: form.type,
        location: form.location.trim() || undefined,
        notes: form.notes.trim() || undefined,
        panelUserIds: form.panelUserIds,
      }),
    onSuccess: () => {
      toast('Interview scheduled — panel and candidate notified', 'success');
      setScheduleOpen(false);
      setForm({ date: '', time: '', durationMinutes: 60, type: 'VIDEO', location: '', notes: '', panelUserIds: [] });
      onChanged();
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const act = useMutation({
    mutationFn: async ({ id, verb }: { id: string; verb: 'complete' | 'cancel' }) =>
      api.post(`/applications/interviews/${id}/${verb}`),
    onSuccess: () => {
      toast('Interview updated', 'success');
      onChanged();
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const submitFeedback = useMutation({
    mutationFn: async () =>
      api.post(`/applications/interviews/${feedbackFor!.id}/feedback`, {
        rating: fb.rating,
        recommendation: fb.recommendation,
        strengths: fb.strengths.trim() || undefined,
        concerns: fb.concerns.trim() || undefined,
      }),
    onSuccess: () => {
      toast('Scorecard submitted', 'success');
      setFeedbackFor(null);
      setFb({ rating: 3, recommendation: 'YES', strengths: '', concerns: '' });
      onChanged();
      queryClient.invalidateQueries({ queryKey: ['application', applicationId] });
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const togglePanelist = (id: string) =>
    setForm((f) => ({
      ...f,
      panelUserIds: f.panelUserIds.includes(id)
        ? f.panelUserIds.filter((x) => x !== id)
        : [...f.panelUserIds, id],
    }));

  return (
    <div className="card p-6">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="font-semibold text-slate-800">Interviews</h2>
        {canSchedule && (
          <button className="btn-secondary py-1.5" onClick={() => setScheduleOpen(true)}>
            <CalendarPlus className="h-4 w-4" /> Schedule
          </button>
        )}
      </div>

      {!interviews.length ? (
        <p className="py-6 text-center text-sm text-slate-400">
          No interviews yet{canSchedule ? ' — schedule one to evaluate this candidate' : ''}
        </p>
      ) : (
        <div className="space-y-4">
          {interviews.map((iv) => (
            <div key={iv.id} className="rounded-lg border border-slate-200 p-4">
              <div className="flex flex-wrap items-center gap-2">
                <span className="flex items-center gap-1.5 text-sm font-medium text-slate-700">
                  {TYPE_ICON[iv.type]} {humanize(iv.type)} interview
                </span>
                <StatusPill
                  label={humanize(iv.status)}
                  className={
                    iv.status === 'COMPLETED' ? 'bg-green-100 text-green-700'
                      : iv.status === 'CANCELLED' ? 'bg-slate-100 text-slate-500'
                      : 'bg-blue-100 text-blue-700'
                  }
                />
                <span className="ml-auto flex items-center gap-1 text-xs text-slate-400">
                  <Clock className="h-3.5 w-3.5" />
                  {formatDateTime(iv.scheduledAt)} · {iv.durationMinutes}min
                </span>
              </div>
              {iv.location && <p className="mt-1 text-xs text-slate-500">Location/link: {iv.location}</p>}
              {iv.notes && <p className="mt-1 text-xs text-slate-500">Panel notes: {iv.notes}</p>}

              {/* panel + feedback status */}
              <div className="mt-3 space-y-2">
                {iv.panel.map((p) => {
                  const feedback = iv.feedback.find((f) => f.interviewerId === p.userId);
                  return (
                    <div key={p.userId} className="rounded-md bg-slate-50 px-3 py-2">
                      <div className="flex items-center gap-2 text-sm">
                        <span className="font-medium text-slate-700">{p.name}</span>
                        {feedback ? (
                          feedback.hidden ? (
                            <span className="flex items-center gap-1 text-xs text-slate-400">
                              <Lock className="h-3 w-3" /> Submitted — visible after you submit yours
                            </span>
                          ) : (
                            <>
                              <StatusPill
                                label={humanize(feedback.recommendation || '')}
                                className={REC_STYLE[feedback.recommendation!]}
                              />
                              <span className="flex items-center gap-0.5 text-xs text-amber-500">
                                {Array.from({ length: feedback.rating || 0 }).map((_, i) => (
                                  <Star key={i} className="h-3 w-3 fill-current" />
                                ))}
                              </span>
                            </>
                          )
                        ) : (
                          <span className="text-xs text-slate-400">Scorecard pending</span>
                        )}
                      </div>
                      {feedback && !feedback.hidden && (feedback.strengths || feedback.concerns) && (
                        <div className="mt-1.5 space-y-0.5 text-xs text-slate-600">
                          {feedback.strengths && <p><strong className="text-green-700">Strengths:</strong> {feedback.strengths}</p>}
                          {feedback.concerns && <p><strong className="text-amber-700">Concerns:</strong> {feedback.concerns}</p>}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>

              {/* actions */}
              <div className="mt-3 flex flex-wrap gap-2">
                {iv.status === 'SCHEDULED' && (
                  <>
                    <button
                      onClick={() => act.mutate({ id: iv.id, verb: 'complete' })}
                      className="inline-flex items-center gap-1 text-xs font-medium text-green-600 hover:text-green-700"
                    >
                      <CheckCircle2 className="h-3.5 w-3.5" /> Mark completed
                    </button>
                    <button
                      onClick={() => act.mutate({ id: iv.id, verb: 'cancel' })}
                      className="inline-flex items-center gap-1 text-xs font-medium text-red-500 hover:text-red-600"
                    >
                      <XCircle className="h-3.5 w-3.5" /> Cancel
                    </button>
                  </>
                )}
                {iv.viewerOnPanel && !iv.viewerFeedbackSubmitted && iv.status !== 'CANCELLED' && (
                  <button
                    onClick={() => setFeedbackFor(iv)}
                    className="btn-primary py-1 px-3 text-xs"
                  >
                    Submit your scorecard
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* schedule modal */}
      <Modal open={scheduleOpen} onClose={() => setScheduleOpen(false)} title="Schedule interview">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (!form.panelUserIds.length) {
              toast('Select at least one interviewer', 'error');
              return;
            }
            schedule.mutate();
          }}
          className="space-y-4"
        >
          <div className="grid grid-cols-2 gap-3">
            <Field label="Date" required>
              <input type="date" className="input" value={form.date}
                onChange={(e) => setForm({ ...form, date: e.target.value })} required />
            </Field>
            <Field label="Time" required>
              <input type="time" className="input" value={form.time}
                onChange={(e) => setForm({ ...form, time: e.target.value })} required />
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Type" required>
              <select className="input" value={form.type}
                onChange={(e) => setForm({ ...form, type: e.target.value as InterviewType })}>
                <option value="VIDEO">Video</option>
                <option value="PHONE">Phone</option>
                <option value="ONSITE">On-site</option>
                <option value="TECHNICAL">Technical</option>
              </select>
            </Field>
            <Field label="Duration (minutes)" required>
              <input type="number" min={15} max={480} step={15} className="input"
                value={form.durationMinutes}
                onChange={(e) => setForm({ ...form, durationMinutes: Number(e.target.value) })} />
            </Field>
          </div>
          <Field label="Location or meeting link">
            <input className="input" value={form.location}
              onChange={(e) => setForm({ ...form, location: e.target.value })}
              placeholder="https://meet… or Room 4B" />
          </Field>
          <Field label="Notes for the panel" hint="Focus areas, questions to cover">
            <textarea className="input min-h-16" value={form.notes}
              onChange={(e) => setForm({ ...form, notes: e.target.value })} />
          </Field>
          <Field label="Interview panel" required hint="Panelists are notified and must each submit a scorecard">
            <div className="max-h-40 space-y-1 overflow-y-auto rounded-lg border border-slate-200 p-2">
              {team?.map((u) => (
                <label key={u.id} className="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-sm hover:bg-slate-50">
                  <input
                    type="checkbox"
                    className="h-4 w-4 rounded border-slate-300 text-brand-600"
                    checked={form.panelUserIds.includes(u.id)}
                    onChange={() => togglePanelist(u.id)}
                  />
                  <span className="text-slate-700">{u.fullName}</span>
                  <span className="text-xs text-slate-400">{humanize(u.role)}</span>
                </label>
              ))}
              {!team?.length && <p className="px-2 py-1 text-xs text-slate-400">Loading team…</p>}
            </div>
          </Field>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setScheduleOpen(false)}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={schedule.isPending}>
              {schedule.isPending && <Spinner className="h-4 w-4" />}
              Schedule & notify
            </button>
          </div>
        </form>
      </Modal>

      {/* scorecard modal */}
      <Modal open={!!feedbackFor} onClose={() => setFeedbackFor(null)} title="Your interview scorecard">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            submitFeedback.mutate();
          }}
          className="space-y-4"
        >
          <p className="text-sm text-slate-500">
            Your feedback is independent — other panelists' scorecards unlock only after you submit yours.
            Scorecards cannot be edited after submission.
          </p>
          <Field label="Overall rating" required>
            <div className="flex gap-1">
              {[1, 2, 3, 4].map((n) => (
                <button
                  key={n}
                  type="button"
                  onClick={() => setFb({ ...fb, rating: n })}
                  className={`rounded-lg p-2 ${fb.rating >= n ? 'text-amber-500' : 'text-slate-300'}`}
                >
                  <Star className="h-6 w-6 fill-current" />
                </button>
              ))}
            </div>
          </Field>
          <Field label="Recommendation" required>
            <div className="grid grid-cols-4 gap-2">
              {(['STRONG_NO', 'NO', 'YES', 'STRONG_YES'] as FeedbackRecommendation[]).map((r) => (
                <button
                  key={r}
                  type="button"
                  onClick={() => setFb({ ...fb, recommendation: r })}
                  className={`rounded-lg border px-2 py-2 text-xs font-medium transition-colors ${
                    fb.recommendation === r
                      ? 'border-brand-600 bg-brand-50 text-brand-700'
                      : 'border-slate-200 text-slate-500 hover:border-slate-300'
                  }`}
                >
                  {humanize(r)}
                </button>
              ))}
            </div>
          </Field>
          <Field label="Strengths">
            <textarea className="input min-h-16" value={fb.strengths}
              onChange={(e) => setFb({ ...fb, strengths: e.target.value })} />
          </Field>
          <Field label="Concerns">
            <textarea className="input min-h-16" value={fb.concerns}
              onChange={(e) => setFb({ ...fb, concerns: e.target.value })} />
          </Field>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setFeedbackFor(null)}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={submitFeedback.isPending}>
              {submitFeedback.isPending && <Spinner className="h-4 w-4" />}
              Submit scorecard
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
