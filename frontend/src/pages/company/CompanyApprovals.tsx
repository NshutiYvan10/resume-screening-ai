import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ClipboardCheck, Check, X, Eye } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/PageHeader';
import { Spinner, EmptyState, Modal, Field, StatusPill } from '../../components/ui';
import { formatSalary, humanize, timeAgo } from '../../lib/format';
import type { Job, Page } from '../../types';

export default function CompanyApprovals() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [rejectId, setRejectId] = useState<string | null>(null);
  const [reason, setReason] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['jobs', 'pending-approval'],
    queryFn: async () =>
      (await api.get<Page<Job>>('/jobs', { params: { status: 'PENDING_APPROVAL', size: 50 } })).data,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['jobs'] });
    queryClient.invalidateQueries({ queryKey: ['analytics', 'company'] });
  };

  const approve = useMutation({
    mutationFn: async (id: string) => api.post(`/jobs/${id}/approve`),
    onSuccess: () => { toast('Approved & published', 'success'); invalidate(); },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const reject = useMutation({
    mutationFn: async () => api.post(`/jobs/${rejectId}/reject`, { reason: reason.trim() }),
    onSuccess: () => {
      toast('Returned to recruiter', 'success');
      setRejectId(null);
      setReason('');
      invalidate();
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  return (
    <div>
      <PageHeader
        title="Approvals"
        description="Review jobs recruiters have submitted before they go live on the public board"
      />

      {isLoading ? (
        <div className="py-12 text-center"><Spinner className="mx-auto h-6 w-6 text-brand-600" /></div>
      ) : !data?.content.length ? (
        <EmptyState
          icon={<ClipboardCheck className="h-12 w-12" />}
          title="Nothing awaiting approval"
          description="When a recruiter submits a job for approval, it will appear here for you to review."
        />
      ) : (
        <div className="space-y-3">
          {data.content.map((job) => {
            const salary = formatSalary(job.salaryMin, job.salaryMax, job.salaryCurrency);
            return (
              <div key={job.id} className="card p-5">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <h3 className="font-semibold text-slate-800">{job.title}</h3>
                      <StatusPill label="Pending approval" className="bg-amber-100 text-amber-700" />
                    </div>
                    <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-400">
                      {job.department && <span>{job.department}</span>}
                      <span>{humanize(job.employmentType)}</span>
                      <span>{humanize(job.workMode)}</span>
                      {job.location && <span>{job.location}</span>}
                      {salary && <span>{salary}</span>}
                    </div>
                    <p className="mt-2 text-xs text-slate-500">
                      Submitted by <strong>{job.submittedByName || job.createdByName || 'a recruiter'}</strong> {timeAgo(job.submittedAt)}
                    </p>
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {job.qualifications.slice(0, 8).map((q) => (
                        <span key={q.skill} className="badge bg-slate-100 text-slate-600">
                          {q.skill}{q.required ? ' *' : ''}
                        </span>
                      ))}
                    </div>
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <Link to={`/company/jobs/${job.id}/edit`} className="btn-secondary py-2" title="Review details">
                      <Eye className="h-4 w-4" /> Review
                    </Link>
                    <button
                      onClick={() => { setRejectId(job.id); setReason(''); }}
                      className="btn-secondary py-2 text-red-600"
                    >
                      <X className="h-4 w-4" /> Reject
                    </button>
                    <button
                      onClick={() => approve.mutate(job.id)}
                      disabled={approve.isPending}
                      className="btn-primary py-2"
                    >
                      <Check className="h-4 w-4" /> Approve
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      <Modal open={!!rejectId} onClose={() => setRejectId(null)} title="Return job to recruiter">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (!reason.trim()) { toast('Please give a reason', 'error'); return; }
            reject.mutate();
          }}
          className="space-y-4"
        >
          <p className="text-sm text-slate-500">
            The job returns to draft and the recruiter is notified with your feedback.
          </p>
          <Field label="Reason" required>
            <textarea className="input min-h-24" value={reason} onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Add a salary range and tighten the required skills before resubmitting." />
          </Field>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setRejectId(null)}>Cancel</button>
            <button type="submit" className="btn-danger" disabled={reject.isPending}>
              {reject.isPending && <Spinner className="h-4 w-4" />}
              Return to recruiter
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
