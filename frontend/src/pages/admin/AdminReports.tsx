import { useQuery } from '@tanstack/react-query';
import { Building2, Users, UserCheck, Briefcase, FileText, Activity, AlertTriangle } from 'lucide-react';
import { api } from '../../lib/api';
import StatCard from '../../components/StatCard';
import { PageLoader, EmptyState } from '../../components/ui';
import { humanize } from '../../lib/format';
import {
  ReportHeader, ReportSection, BreakdownDonut, PipelineFunnel, TrendChart,
  PrintButton, ExportCsvButton, downloadCsv,
} from '../../components/reports/ReportKit';

interface PlatformAnalytics {
  totalCompanies: number;
  activeCompanies: number;
  totalUsers: number;
  totalCandidates: number;
  totalRecruiters: number;
  totalJobs: number;
  publishedJobs: number;
  totalApplications: number;
  usersByRole: Record<string, number>;
  jobsByStatus: Record<string, number>;
  applicationsByStatus: Record<string, number>;
  screeningHealth: Record<string, number>;
  applicationsOverTime: { month: string; count: number }[];
  usersOverTime: { month: string; count: number }[];
}

const APP_ORDER = ['SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED', 'REJECTED', 'WITHDRAWN'];

export default function AdminReports() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['analytics', 'platform'],
    queryFn: async () => (await api.get<PlatformAnalytics>('/analytics/platform')).data,
  });

  if (isLoading) return <PageLoader />;
  if (isError || !data) {
    return (
      <EmptyState
        icon={<AlertTriangle className="h-12 w-12" />}
        title="Couldn’t load analytics"
        description="Something went wrong fetching platform analytics. Please try again."
        action={<button className="btn-secondary" onClick={() => refetch()}>Retry</button>}
      />
    );
  }

  const exportSummary = () =>
    downloadCsv('platform-summary.csv', ['Metric', 'Value'], [
      ['Companies', data.totalCompanies],
      ['Active companies (with published jobs)', data.activeCompanies],
      ['Total users', data.totalUsers],
      ['Candidates', data.totalCandidates],
      ['Recruiters & admins', data.totalRecruiters],
      ['Total jobs', data.totalJobs],
      ['Published jobs', data.publishedJobs],
      ['Total applications', data.totalApplications],
      ...Object.entries(data.jobsByStatus).map(([k, v]) => [`Jobs · ${humanize(k)}`, v] as [string, number]),
      ...Object.entries(data.applicationsByStatus).map(([k, v]) => [`Applications · ${humanize(k)}`, v] as [string, number]),
    ]);

  return (
    <div>
      <ReportHeader
        title="Platform analytics"
        description="Usage, growth and screening health across every company on ResumeAI"
        action={<><ExportCsvButton onClick={exportSummary} /><PrintButton /></>}
      />

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
        <StatCard label="Companies" value={data.totalCompanies} icon={<Building2 className="h-5 w-5" />} />
        <StatCard label="Active" value={data.activeCompanies} icon={<Activity className="h-5 w-5" />}
          accent="text-green-600 bg-green-50" />
        <StatCard label="Users" value={data.totalUsers} icon={<Users className="h-5 w-5" />}
          accent="text-teal-600 bg-teal-50" />
        <StatCard label="Candidates" value={data.totalCandidates} icon={<UserCheck className="h-5 w-5" />}
          accent="text-violet-600 bg-violet-50" />
        <StatCard label="Jobs" value={data.totalJobs} icon={<Briefcase className="h-5 w-5" />} />
        <StatCard label="Applications" value={data.totalApplications} icon={<FileText className="h-5 w-5" />}
          accent="text-blue-600 bg-blue-50" />
      </div>

      <div className="mt-6 grid gap-5 lg:grid-cols-2">
        <ReportSection title="Applications over time" subtitle="Monthly application volume platform-wide">
          <TrendChart data={data.applicationsOverTime} color="#2563eb" label="Applications" />
        </ReportSection>
        <ReportSection title="User growth" subtitle="New signups per month">
          <TrendChart data={data.usersOverTime} color="#7c3aed" label="New users" />
        </ReportSection>
      </div>

      <div className="mt-5 grid gap-5 lg:grid-cols-3">
        <ReportSection title="Users by role">
          <BreakdownDonut data={data.usersByRole} />
        </ReportSection>
        <ReportSection title="Jobs by status">
          <BreakdownDonut data={data.jobsByStatus} />
        </ReportSection>
        <ReportSection title="Screening health" subtitle="AI screening outcomes">
          <BreakdownDonut data={data.screeningHealth} />
        </ReportSection>
      </div>

      <div className="mt-5">
        <ReportSection title="Applications by stage" subtitle="Where applications sit across the platform">
          <PipelineFunnel data={data.applicationsByStatus} order={APP_ORDER} />
        </ReportSection>
      </div>
    </div>
  );
}
