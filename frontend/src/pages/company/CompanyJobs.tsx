import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Briefcase, Plus, Search, Users, MoreVertical, AlertTriangle } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import { useAuth } from '../../context/AuthContext';
import PageHeader from '../../components/PageHeader';
import { Spinner, EmptyState, Pagination, StatusPill, Modal, Field } from '../../components/ui';
import { JOB_STATUS_STYLES, formatDate, humanize } from '../../lib/format';
import type { Job, JobStatus, Page } from '../../types';

const STATUS_TABS: (JobStatus | 'ALL')[] = ['ALL', 'PUBLISHED', 'PENDING_APPROVAL', 'DRAFT', 'CLOSED', 'ARCHIVED'];

export default function CompanyJobs() {
  const toast = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const isAdmin = user?.role === 'COMPANY_ADMIN';
  const [status, setStatus] = useState<JobStatus | 'ALL'>('ALL');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [menuId, setMenuId] = useState<string | null>(null);
  const [rejectId, setRejectId] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['jobs', status, search, page],
    queryFn: async () =>
      (await api.get<Page<Job>>('/jobs', {
        params: { status: status === 'ALL' ? undefined : status, search, page, size: 10 },
      })).data,
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['jobs'] });
    queryClient.invalidateQueries({ queryKey: ['analytics', 'company'] });
  };

  const action = useMutation({
    mutationFn: async ({ id, verb }: { id: string; verb: string }) => {
      if (verb === 'delete') return api.delete(`/jobs/${id}`);
      return api.post(`/jobs/${id}/${verb}`);
    },
    onSuccess: (_d, v) => {
      toast(actionToast(v.verb), 'success');
      setMenuId(null);
      invalidate();
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const reject = useMutation({
    mutationFn: async () => api.post(`/jobs/${rejectId}/reject`, { reason: rejectReason.trim() }),
    onSuccess: () => {
      toast('Job returned to the recruiter', 'success');
      setRejectId(null);
      setRejectReason('');
      invalidate();
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const actionToast = (verb: string) =>
    verb === 'submit' ? 'Submitted for approval'
      : verb === 'approve' ? 'Approved & published'
      : verb === 'publish' ? 'Published'
      : verb === 'close' ? 'Job closed'
      : verb === 'archive' ? 'Job archived'
      : verb === 'delete' ? 'Job deleted'
      : 'Job updated';

  return (
    <div>
      <PageHeader
        title="Jobs"
        description="Create and manage your open positions"
        action={
          <Link to="/company/jobs/new" className="btn-primary">
            <Plus className="h-4 w-4" /> Post a job
          </Link>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="flex gap-1 rounded-lg bg-slate-100 p-1">
          {STATUS_TABS.map((t) => (
            <button
              key={t}
              onClick={() => {
                setStatus(t);
                setPage(0);
              }}
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                status === t ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              {t === 'ALL' ? 'All' : humanize(t)}
            </button>
          ))}
        </div>
        <div className="relative flex-1 min-w-48">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
          <input
            className="input pl-9"
            placeholder="Search jobs…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </div>
      </div>

      {isLoading ? (
        <div className="py-12 text-center"><Spinner className="mx-auto h-6 w-6 text-brand-600" /></div>
      ) : !data?.content.length ? (
        <EmptyState
          icon={<Briefcase className="h-12 w-12" />}
          title="No jobs here"
          description="Post your first job to start receiving AI-screened applications."
          action={
            <Link to="/company/jobs/new" className="btn-primary">
              <Plus className="h-4 w-4" /> Post a job
            </Link>
          }
        />
      ) : (
        <div className="space-y-3">
          {data.content.map((job) => (
            <div key={job.id} className="card flex items-center gap-4 p-5 hover:border-brand-200">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <Link
                    to={`/company/jobs/${job.id}/applications`}
                    className="font-semibold text-slate-800 hover:text-brand-600 truncate"
                  >
                    {job.title}
                  </Link>
                  <StatusPill label={humanize(job.status)} className={JOB_STATUS_STYLES[job.status]} />
                </div>
                <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-400">
                  {job.department && <span>{job.department}</span>}
                  <span>{humanize(job.employmentType)}</span>
                  <span>{humanize(job.workMode)}</span>
                  {job.location && <span>{job.location}</span>}
                  <span>Created {formatDate(job.createdAt)}</span>
                  {job.createdByName && <span>by {job.createdByName}</span>}
                </div>
                {job.status === 'DRAFT' && job.rejectionReason && (
                  <div className="mt-2 flex items-start gap-1.5 rounded-md bg-red-50 px-2.5 py-1.5 text-xs text-red-700">
                    <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                    <span><strong>Returned by admin:</strong> {job.rejectionReason}</span>
                  </div>
                )}
              </div>
              <Link
                to={`/company/jobs/${job.id}/applications`}
                className="flex items-center gap-1.5 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600 hover:bg-slate-100"
              >
                <Users className="h-4 w-4" />
                <span className="font-semibold">{job.applicationCount ?? 0}</span>
              </Link>
              <div className="relative">
                <button
                  onClick={() => setMenuId(menuId === job.id ? null : job.id)}
                  className="rounded p-1.5 text-slate-400 hover:bg-slate-100"
                >
                  <MoreVertical className="h-4 w-4" />
                </button>
                {menuId === job.id && (
                  <>
                    <div className="fixed inset-0 z-20" onClick={() => setMenuId(null)} />
                    <div className="absolute right-0 z-30 mt-1 w-44 card py-1 text-left">
                      <Link
                        to={`/company/jobs/${job.id}/applications`}
                        className="block px-4 py-2 text-sm text-slate-600 hover:bg-slate-50"
                      >
                        View applications
                      </Link>
                      {job.status !== 'ARCHIVED' && (
                        <button
                          onClick={() => navigate(`/company/jobs/${job.id}/edit`)}
                          className="block w-full px-4 py-2 text-left text-sm text-slate-600 hover:bg-slate-50"
                        >
                          Edit
                        </button>
                      )}
                      {/* Recruiter (or admin) submits a draft/closed job for approval */}
                      {(job.status === 'DRAFT' || job.status === 'CLOSED') && (
                        <button
                          onClick={() => action.mutate({ id: job.id, verb: 'submit' })}
                          className="block w-full px-4 py-2 text-left text-sm text-brand-600 hover:bg-brand-50"
                        >
                          Submit for approval
                        </button>
                      )}
                      {/* Admin-only: approve or reject a pending job */}
                      {isAdmin && job.status === 'PENDING_APPROVAL' && (
                        <>
                          <button
                            onClick={() => action.mutate({ id: job.id, verb: 'approve' })}
                            className="block w-full px-4 py-2 text-left text-sm text-green-600 hover:bg-green-50"
                          >
                            Approve &amp; publish
                          </button>
                          <button
                            onClick={() => { setRejectId(job.id); setRejectReason(''); setMenuId(null); }}
                            className="block w-full px-4 py-2 text-left text-sm text-red-600 hover:bg-red-50"
                          >
                            Reject…
                          </button>
                        </>
                      )}
                      {/* Admin-only: publish directly, bypassing the queue */}
                      {isAdmin && (job.status === 'DRAFT' || job.status === 'CLOSED') && (
                        <button
                          onClick={() => action.mutate({ id: job.id, verb: 'publish' })}
                          className="block w-full px-4 py-2 text-left text-sm text-green-600 hover:bg-green-50"
                        >
                          Publish directly
                        </button>
                      )}
                      {job.status === 'PUBLISHED' && (
                        <button
                          onClick={() => action.mutate({ id: job.id, verb: 'close' })}
                          className="block w-full px-4 py-2 text-left text-sm text-amber-600 hover:bg-amber-50"
                        >
                          Close
                        </button>
                      )}
                      {(job.status === 'DRAFT' || job.status === 'CLOSED') && (
                        <button
                          onClick={() => action.mutate({ id: job.id, verb: 'archive' })}
                          className="block w-full px-4 py-2 text-left text-sm text-slate-600 hover:bg-slate-50"
                        >
                          Archive
                        </button>
                      )}
                      {job.status === 'DRAFT' && (
                        <button
                          onClick={() => {
                            if (confirm('Delete this draft job permanently?'))
                              action.mutate({ id: job.id, verb: 'delete' });
                          }}
                          className="block w-full px-4 py-2 text-left text-sm text-red-600 hover:bg-red-50"
                        >
                          Delete
                        </button>
                      )}
                    </div>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
      {data && <Pagination page={page} totalPages={data.totalPages} onChange={setPage} />}

      <Modal open={!!rejectId} onClose={() => setRejectId(null)} title="Return job to recruiter">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (!rejectReason.trim()) {
              toast('Please give a reason', 'error');
              return;
            }
            reject.mutate();
          }}
          className="space-y-4"
        >
          <p className="text-sm text-slate-500">
            The job goes back to draft and the recruiter is notified with your feedback.
          </p>
          <Field label="Reason" required>
            <textarea
              className="input min-h-24"
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="e.g. Salary range is missing; please add it before resubmitting."
            />
          </Field>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setRejectId(null)}>
              Cancel
            </button>
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
