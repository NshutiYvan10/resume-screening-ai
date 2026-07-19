import { useQuery } from '@tanstack/react-query';
import {
  Briefcase, FileText, Gauge, Users, Trophy, Clock, HandCoins, ShieldAlert, AlertTriangle,
} from 'lucide-react';
import { api } from '../../lib/api';
import { useAuth } from '../../context/AuthContext';
import StatCard from '../../components/StatCard';
import { PageLoader, EmptyState } from '../../components/ui';
import { humanize } from '../../lib/format';
import type { Company } from '../../types';
import {
  ReportHeader, ReportSection, PipelineFunnel, TrendChart, ScoreDistributionChart,
  PrintButton, ExportCsvButton, downloadCsv,
} from '../../components/reports/ReportKit';

interface JobPerf {
  title: string;
  status: string;
  applications: number;
  avgScore: number | null;
}
interface CompanyAnalytics {
  totalJobs: number;
  publishedJobs: number;
  draftJobs: number;
  pendingApprovalJobs: number;
  totalApplications: number;
  teamMembers?: number;
  averageMatchScore: number | null;
  pipeline: Record<string, number>;
  hires?: number;
  scoreDistribution: Record<string, number>;
  topSkills?: { skill: string; count: number }[];
  avgTimeToHireDays?: number | null;
  offers?: { accepted: number; declined: number; acceptanceRate: number | null };
  biasFlaggedCount?: number;
  applicationsOverTime?: { month: string; count: number }[];
  jobPerformance?: JobPerf[];
}

const PIPELINE = ['SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED', 'REJECTED'];

export default function CompanyReports() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'COMPANY_ADMIN';

  const { data: company } = useQuery({
    queryKey: ['company', 'my'],
    queryFn: async () => (await api.get<Company>('/companies/my')).data,
  });

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['analytics', isAdmin ? 'company' : 'recruiter'],
    queryFn: async () =>
      (await api.get<CompanyAnalytics>(isAdmin ? '/analytics/company' : '/analytics/recruiter')).data,
  });

  if (isLoading) return <PageLoader />;
  if (isError || !data) {
    return (
      <EmptyState
        icon={<AlertTriangle className="h-12 w-12" />}
        title="Couldn’t load your report"
        description="Something went wrong fetching analytics. Please try again."
        action={<button className="btn-secondary" onClick={() => refetch()}>Retry</button>}
      />
    );
  }

  const jobPerf = data?.jobPerformance || [];
  const exportJobs = () =>
    downloadCsv(
      'job-performance.csv',
      ['Job', 'Status', 'Applications', 'Avg. match score'],
      jobPerf.map((j) => [j.title, humanize(j.status), j.applications, j.avgScore ?? '']),
    );

  return (
    <div>
      <ReportHeader
        title={isAdmin ? 'Hiring performance report' : 'My recruiting report'}
        description={isAdmin
          ? 'Pipeline health, screening quality and hiring velocity across your organization'
          : 'Applications and screening quality across the jobs you manage'}
        brandName={company?.name}
        brandLogoUrl={company?.logoUrl}
        action={<><ExportCsvButton onClick={exportJobs} /><PrintButton /></>}
      />

      {/* KPI row */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Published jobs" value={data?.publishedJobs ?? 0} icon={<Briefcase className="h-5 w-5" />}
          accent="text-green-600 bg-green-50" />
        <StatCard label="Total applications" value={data?.totalApplications ?? 0} icon={<FileText className="h-5 w-5" />}
          accent="text-blue-600 bg-blue-50" />
        <StatCard label="Avg. match score" value={data?.averageMatchScore != null ? data.averageMatchScore : '—'}
          icon={<Gauge className="h-5 w-5" />} accent="text-violet-600 bg-violet-50" />
        {isAdmin ? (
          <StatCard label="Hires" value={data?.hires ?? 0} icon={<Trophy className="h-5 w-5" />}
            accent="text-teal-600 bg-teal-50" />
        ) : (
          <StatCard label="Total jobs" value={data?.totalJobs ?? 0} icon={<Briefcase className="h-5 w-5" />} />
        )}
      </div>

      {/* admin-only velocity KPIs */}
      {isAdmin && (
        <div className="mt-4 grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard label="Avg. time to hire"
            value={data?.avgTimeToHireDays != null ? `${data.avgTimeToHireDays} days` : '—'}
            icon={<Clock className="h-5 w-5" />} accent="text-amber-600 bg-amber-50" />
          <StatCard label="Offer acceptance"
            value={data?.offers?.acceptanceRate != null ? `${data.offers.acceptanceRate}%` : '—'}
            icon={<HandCoins className="h-5 w-5" />} accent="text-teal-600 bg-teal-50" />
          <StatCard label="Team members" value={data?.teamMembers ?? 0} icon={<Users className="h-5 w-5" />} />
          <StatCard label="Bias flags raised" value={data?.biasFlaggedCount ?? 0}
            icon={<ShieldAlert className="h-5 w-5" />} accent="text-red-600 bg-red-50" />
        </div>
      )}

      <div className="mt-6 grid gap-5 lg:grid-cols-2">
        <ReportSection title="Candidate pipeline" subtitle="Applications by hiring stage">
          <PipelineFunnel data={data?.pipeline} order={PIPELINE} />
        </ReportSection>
        <ReportSection title="AI match score distribution" subtitle="Screened applications by score band">
          <ScoreDistributionChart data={data?.scoreDistribution} />
        </ReportSection>
      </div>

      {isAdmin && (
        <div className="mt-5">
          <ReportSection title="Applications over time" subtitle="Monthly application volume">
            <TrendChart data={data?.applicationsOverTime} color="#2563eb" label="Applications" />
          </ReportSection>
        </div>
      )}

      <div className="mt-5">
        <ReportSection
          title="Job performance"
          subtitle="Applications and average AI match score per posting"
          action={jobPerf.length ? <ExportCsvButton onClick={exportJobs} /> : undefined}
        >
          {!jobPerf.length ? (
            <p className="py-8 text-center text-sm text-slate-400">No jobs to report on yet</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-400">
                    <th className="pb-2 pr-4 font-medium">Job</th>
                    <th className="pb-2 pr-4 font-medium">Status</th>
                    <th className="pb-2 pr-4 font-medium text-right">Applications</th>
                    <th className="pb-2 font-medium text-right">Avg. score</th>
                  </tr>
                </thead>
                <tbody>
                  {jobPerf.map((j, i) => (
                    <tr key={i} className="border-b border-slate-50 last:border-0">
                      <td className="py-2 pr-4 font-medium text-slate-700">{j.title}</td>
                      <td className="py-2 pr-4 text-slate-500">{humanize(j.status)}</td>
                      <td className="py-2 pr-4 text-right text-slate-700">{j.applications}</td>
                      <td className="py-2 text-right font-semibold text-slate-800">
                        {j.avgScore != null ? j.avgScore : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </ReportSection>
      </div>

      {isAdmin && !!data?.topSkills?.length && (
        <div className="mt-5">
          <ReportSection title="Most common candidate skills" subtitle="Across all screened applicants">
            <div className="flex flex-wrap gap-2">
              {data.topSkills.map((s) => (
                <span key={s.skill} className="badge bg-brand-50 text-brand-700">
                  {s.skill} <span className="text-brand-400">· {s.count}</span>
                </span>
              ))}
            </div>
          </ReportSection>
        </div>
      )}
    </div>
  );
}
