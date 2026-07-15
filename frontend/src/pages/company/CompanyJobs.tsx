import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Briefcase, Plus, Search, Users, MoreVertical } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/PageHeader';
import { Spinner, EmptyState, Pagination, StatusPill } from '../../components/ui';
import { JOB_STATUS_STYLES, formatDate, humanize } from '../../lib/format';
import type { Job, JobStatus, Page } from '../../types';

const STATUS_TABS: (JobStatus | 'ALL')[] = ['ALL', 'PUBLISHED', 'DRAFT', 'CLOSED', 'ARCHIVED'];

export default function CompanyJobs() {
  const toast = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<JobStatus | 'ALL'>('ALL');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [menuId, setMenuId] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['jobs', status, search, page],
    queryFn: async () =>
      (await api.get<Page<Job>>('/jobs', {
        params: { status: status === 'ALL' ? undefined : status, search, page, size: 10 },
      })).data,
  });

  const action = useMutation({
    mutationFn: async ({ id, verb }: { id: string; verb: string }) => {
      if (verb === 'delete') return api.delete(`/jobs/${id}`);
      return api.post(`/jobs/${id}/${verb}`);
    },
    onSuccess: () => {
      toast('Job updated', 'success');
      setMenuId(null);
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

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
                </div>
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
                      {(job.status === 'DRAFT' || job.status === 'CLOSED') && (
                        <button
                          onClick={() => action.mutate({ id: job.id, verb: 'publish' })}
                          className="block w-full px-4 py-2 text-left text-sm text-green-600 hover:bg-green-50"
                        >
                          Publish
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
    </div>
  );
}
