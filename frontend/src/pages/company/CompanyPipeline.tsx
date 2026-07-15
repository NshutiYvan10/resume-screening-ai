import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Users, Download, ArrowUpDown, AlertTriangle } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/PageHeader';
import { Spinner, EmptyState, Pagination, StatusPill } from '../../components/ui';
import { ScoreRing, ScreeningStatusBadge } from '../../components/ScoreBadge';
import { APPLICATION_STATUS_STYLES, humanize, timeAgo } from '../../lib/format';
import type { Application, ApplicationStatus, Page } from '../../types';

const STATUS_FILTER: (ApplicationStatus | 'ALL')[] = [
  'ALL', 'SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED', 'REJECTED',
];

export default function CompanyPipeline() {
  const toast = useToast();
  const [status, setStatus] = useState<ApplicationStatus | 'ALL'>('ALL');
  const [sortBy, setSortBy] = useState<'score' | 'appliedAt'>('score');
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['company-pipeline', status, sortBy, page],
    queryFn: async () =>
      (await api.get<Page<Application>>('/applications/company', {
        params: { status: status === 'ALL' ? undefined : status, sortBy, page, size: 20 },
      })).data,
  });

  const exportCsv = async () => {
    setExporting(true);
    try {
      const res = await api.get('/applications/company/export', { responseType: 'blob' });
      const url = URL.createObjectURL(res.data as Blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'company-pipeline.csv';
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    } finally {
      setExporting(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="Candidates"
        description="Every applicant across all your company's jobs, ranked by AI match score"
        action={
          <button onClick={exportCsv} className="btn-secondary" disabled={exporting}>
            {exporting ? <Spinner className="h-4 w-4" /> : <Download className="h-4 w-4" />}
            Export CSV
          </button>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <div className="flex flex-wrap gap-1 rounded-lg bg-slate-100 p-1">
          {STATUS_FILTER.map((s) => (
            <button
              key={s}
              onClick={() => { setStatus(s); setPage(0); }}
              className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                status === s ? 'bg-white text-slate-800 shadow-sm' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              {s === 'ALL' ? 'All' : humanize(s)}
            </button>
          ))}
        </div>
        <button
          onClick={() => setSortBy((v) => (v === 'score' ? 'appliedAt' : 'score'))}
          className="btn-secondary py-1.5"
        >
          <ArrowUpDown className="h-4 w-4" /> {sortBy === 'score' ? 'AI score' : 'Most recent'}
        </button>
      </div>

      {isLoading ? (
        <div className="py-12 text-center"><Spinner className="mx-auto h-6 w-6 text-brand-600" /></div>
      ) : !data?.content.length ? (
        <EmptyState icon={<Users className="h-12 w-12" />} title="No candidates yet"
          description="Applications across all your company's jobs will appear here." />
      ) : (
        <div className="space-y-3">
          {data.content.map((app) => (
            <Link
              key={app.id}
              to={`/company/applications/${app.id}`}
              className="card flex items-center gap-4 p-4 transition-colors hover:border-brand-300"
            >
              <ScoreRing score={app.screening?.matchScore} />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="truncate font-semibold text-slate-800">{app.candidateName}</span>
                  {app.screening?.biasFlag && (
                    <span title="Advisory bias flag"><AlertTriangle className="h-4 w-4 text-amber-500" /></span>
                  )}
                </div>
                <div className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-400">
                  <span className="font-medium text-slate-500">{app.jobTitle}</span>
                  <span>Applied {timeAgo(app.appliedAt)}</span>
                  <ScreeningStatusBadge status={app.screening?.status} />
                </div>
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
