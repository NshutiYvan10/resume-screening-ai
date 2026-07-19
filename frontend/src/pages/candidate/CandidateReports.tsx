import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { FileText, Gauge, CalendarCheck, Trophy, Send, Search, AlertTriangle } from 'lucide-react';
import { api } from '../../lib/api';
import StatCard from '../../components/StatCard';
import { PageLoader, EmptyState } from '../../components/ui';
import {
  ReportHeader, ReportSection, PipelineFunnel, TrendChart, ScoreDistributionChart, PrintButton,
} from '../../components/reports/ReportKit';

interface CandidateAnalytics {
  totalApplications: number;
  statusBreakdown: Record<string, number>;
  interviews: number;
  offers: number;
  hired: number;
  rejected: number;
  averageMatchScore: number | null;
  scoreDistribution: Record<string, number>;
  applicationsOverTime: { month: string; count: number }[];
}

const FUNNEL = ['SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED'];

export default function CandidateReports() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['analytics', 'candidate'],
    queryFn: async () => (await api.get<CandidateAnalytics>('/analytics/candidate')).data,
  });

  if (isLoading) return <PageLoader />;
  if (isError || !data) {
    return (
      <EmptyState
        icon={<AlertTriangle className="h-12 w-12" />}
        title="Couldn’t load your insights"
        description="Something went wrong. Please try again."
        action={<button className="btn-secondary" onClick={() => refetch()}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <ReportHeader
        title="My application insights"
        description="How your applications are progressing and how you score against roles"
        action={<PrintButton />}
      />

      {!data?.totalApplications ? (
        <EmptyState
          icon={<FileText className="h-12 w-12" />}
          title="No applications yet"
          description="Once you apply to jobs, your personalized insights will appear here."
          action={<Link to="/candidate" className="btn-primary"><Search className="h-4 w-4" /> Browse jobs</Link>}
        />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
            <StatCard label="Applications" value={data.totalApplications} icon={<FileText className="h-5 w-5" />} />
            <StatCard label="Avg. match score" value={data.averageMatchScore != null ? data.averageMatchScore : '—'}
              icon={<Gauge className="h-5 w-5" />} accent="text-violet-600 bg-violet-50" />
            <StatCard label="Interviews" value={data.interviews} icon={<CalendarCheck className="h-5 w-5" />}
              accent="text-amber-600 bg-amber-50" />
            <StatCard label="Offers" value={data.offers} icon={<Send className="h-5 w-5" />}
              accent="text-teal-600 bg-teal-50" />
            <StatCard label="Hired" value={data.hired} icon={<Trophy className="h-5 w-5" />}
              accent="text-green-600 bg-green-50" />
          </div>

          <div className="mt-6 grid gap-5 lg:grid-cols-2">
            <ReportSection title="Your application funnel" subtitle="Where your applications currently stand">
              <PipelineFunnel data={data.statusBreakdown} order={FUNNEL} />
            </ReportSection>
            <ReportSection title="Your match scores" subtitle="AI match score across the roles you applied to">
              <ScoreDistributionChart data={data.scoreDistribution} />
            </ReportSection>
          </div>

          <div className="mt-5">
            <ReportSection title="Applications over time" subtitle="Your monthly application activity">
              <TrendChart data={data.applicationsOverTime} color="#2563eb" label="Applications" />
            </ReportSection>
          </div>
        </>
      )}
    </div>
  );
}
