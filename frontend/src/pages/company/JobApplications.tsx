import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Users, AlertTriangle, ArrowUpDown } from 'lucide-react';
import { api } from '../../lib/api';
import PageHeader from '../../components/PageHeader';
import { Spinner, EmptyState, Pagination, StatusPill } from '../../components/ui';
import { ScoreRing, ScreeningStatusBadge } from '../../components/ScoreBadge';
import { APPLICATION_STATUS_STYLES, humanize, timeAgo } from '../../lib/format';
import type { Application, ApplicationStatus, Job, Page } from '../../types';

const STATUS_FILTER: (ApplicationStatus | 'ALL')[] = [
  'ALL', 'SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED', 'REJECTED',
];

export default function JobApplications() {
  const { jobId } = useParams();
  const [status, setStatus] = useState<ApplicationStatus | 'ALL'>('ALL');
  const [sortBy, setSortBy] = useState<'score' | 'appliedAt'>('score');
  const [page, setPage] = useState(0);

  const { data: job } = useQuery({
    queryKey: ['job', jobId],
    queryFn: async () => (await api.get<Job>(`/jobs/${jobId}`)).data,
  });

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['applications', jobId, status, sortBy, page],
    queryFn: async () =>
      (await api.get<Page<Application>>(`/applications/jobs/${jobId}`, {
        params: { status: status === 'ALL' ? undefined : status, sortBy, page, size: 20 },
      })).data,
    refetchInterval: 15000,
  });

  return (
    <div>
      <Link
        to="/company/jobs"
        className="mb-4 flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="h-4 w-4" /> Back to jobs
      </Link>

      <PageHeader
        title={job?.title || 'Applications'}
        description={
          job
            ? `${humanize(job.employmentType)} · ${humanize(job.workMode)}${job.location ? ` · ${job.location}` : ''}`
            : undefined
        }
        action={
          <button
            onClick={() => setSortBy((s) => (s === 'score' ? 'appliedAt' : 'score'))}
            className="btn-secondary"
          >
            <ArrowUpDown className="h-4 w-4" />
            Sort: {sortBy === 'score' ? 'AI score' : 'Most recent'}
          </button>
        }
      />

      <div className="mb-4 flex flex-wrap gap-1 rounded-lg bg-slate-100 p-1">
        {STATUS_FILTER.map((s) => (
          <button
            key={s}
            onClick={() => {
              setStatus(s);
              setPage(0);
            }}
            className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
              status === s ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            {s === 'ALL' ? 'All' : humanize(s)}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="py-12 text-center"><Spinner className="mx-auto h-6 w-6 text-brand-600" /></div>
      ) : !data?.content.length ? (
        <EmptyState
          icon={<Users className="h-12 w-12" />}
          title="No applications yet"
          description={
            job?.status === 'PUBLISHED'
              ? 'Applications will appear here as candidates apply, ranked by AI match score.'
              : 'Publish this job to start receiving applications.'
          }
        />
      ) : (
        <div className="space-y-3">
          {sortBy === 'score' && isFetching && (
            <p className="text-xs text-slate-400">Refreshing rankings…</p>
          )}
          {data.content.map((app, idx) => (
            <Link
              key={app.id}
              to={`/company/applications/${app.id}`}
              className="card flex items-center gap-4 p-4 transition-colors hover:border-brand-300"
            >
              {sortBy === 'score' && (
                <div className="w-6 text-center text-sm font-bold text-slate-300">
                  {page * 20 + idx + 1}
                </div>
              )}
              <ScoreRing score={app.screening?.matchScore} />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="truncate font-semibold text-slate-800">{app.candidateName}</span>
                  {app.screening?.biasFlag && (
                    <span title="Advisory bias flag">
                      <AlertTriangle className="h-4 w-4 text-amber-500" />
                    </span>
                  )}
                </div>
                <div className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-400">
                  <span>{app.candidateEmail}</span>
                  <span>Applied {timeAgo(app.appliedAt)}</span>
                  <ScreeningStatusBadge status={app.screening?.status} />
                </div>
                {!!app.screening?.extractedSkills?.length && (
                  <div className="mt-2 flex flex-wrap gap-1">
                    {app.screening.extractedSkills.slice(0, 6).map((s) => (
                      <span key={s} className="badge bg-slate-100 text-slate-600">{s}</span>
                    ))}
                    {app.screening.extractedSkills.length > 6 && (
                      <span className="badge bg-slate-100 text-slate-400">
                        +{app.screening.extractedSkills.length - 6}
                      </span>
                    )}
                  </div>
                )}
              </div>
              <StatusPill label={humanize(app.status)} className={APPLICATION_STATUS_STYLES[app.status]} />
            </Link>
          ))}
        </div>
      )}
      {data && <Pagination page={page} totalPages={data.totalPages} onChange={setPage} />}
    </div>
  );
}
