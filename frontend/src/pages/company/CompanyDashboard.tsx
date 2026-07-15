import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell,
} from 'recharts';
import { Briefcase, FileText, Users, Gauge, Plus } from 'lucide-react';
import { api } from '../../lib/api';
import { useAuth } from '../../context/AuthContext';
import PageHeader from '../../components/PageHeader';
import StatCard from '../../components/StatCard';
import { PageLoader } from '../../components/ui';
import { humanize } from '../../lib/format';

interface CompanyAnalytics {
  totalJobs: number;
  publishedJobs: number;
  draftJobs: number;
  closedJobs: number;
  totalApplications: number;
  teamMembers: number;
  averageMatchScore: number | null;
  pipeline: Record<string, number>;
  scoreDistribution: Record<string, number>;
  topSkills: { skill: string; count: number }[];
}

const PIPELINE_ORDER = ['SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED', 'REJECTED'];

export default function CompanyDashboard() {
  const { user } = useAuth();
  const { data, isLoading } = useQuery({
    queryKey: ['analytics', 'company'],
    queryFn: async () => (await api.get<CompanyAnalytics>('/analytics/company')).data,
  });

  if (isLoading) return <PageLoader />;

  const dist = Object.entries(data?.scoreDistribution || {}).map(([range, count]) => ({ range, count }));
  const pipeline = PIPELINE_ORDER.filter((s) => (data?.pipeline?.[s] ?? 0) >= 0).map((s) => ({
    stage: humanize(s),
    count: data?.pipeline?.[s] ?? 0,
  }));

  return (
    <div>
      <PageHeader
        title={`Welcome, ${user?.fullName.split(' ')[0]}`}
        description={user?.companyName}
        action={
          <Link to="/company/jobs/new" className="btn-primary">
            <Plus className="h-4 w-4" /> Post a job
          </Link>
        }
      />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Published jobs" value={data?.publishedJobs ?? 0} icon={<Briefcase className="h-5 w-5" />}
          accent="text-green-600 bg-green-50" />
        <StatCard label="Total applications" value={data?.totalApplications ?? 0}
          icon={<FileText className="h-5 w-5" />} accent="text-blue-600 bg-blue-50" />
        <StatCard label="Avg. match score"
          value={data?.averageMatchScore != null ? `${data.averageMatchScore}` : '—'}
          icon={<Gauge className="h-5 w-5" />} accent="text-violet-600 bg-violet-50" />
        <StatCard label="Team members" value={data?.teamMembers ?? 0} icon={<Users className="h-5 w-5" />}
          accent="text-amber-600 bg-amber-50" />
      </div>

      <div className="mt-6 grid gap-5 lg:grid-cols-2">
        <div className="card p-6">
          <h3 className="mb-4 font-semibold text-slate-800">Candidate pipeline</h3>
          {data?.totalApplications ? (
            <div className="space-y-3">
              {pipeline.map((p) => {
                const max = Math.max(...pipeline.map((x) => x.count), 1);
                return (
                  <div key={p.stage}>
                    <div className="mb-1 flex justify-between text-sm">
                      <span className="text-slate-600">{p.stage}</span>
                      <span className="font-medium text-slate-800">{p.count}</span>
                    </div>
                    <div className="h-2 rounded-full bg-slate-100">
                      <div
                        className="h-2 rounded-full bg-brand-500"
                        style={{ width: `${(p.count / max) * 100}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            <p className="py-10 text-center text-sm text-slate-400">No applications yet</p>
          )}
        </div>

        <div className="card p-6">
          <h3 className="mb-4 font-semibold text-slate-800">AI match score distribution</h3>
          {data?.totalApplications ? (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={dist}>
                <XAxis dataKey="range" tick={{ fontSize: 10 }} interval={0} angle={-30} textAnchor="end" height={50} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                <Tooltip />
                <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                  {dist.map((d, i) => {
                    const low = parseInt(d.range.split('-')[0], 10);
                    const color = low >= 70 ? '#16a34a' : low >= 45 ? '#f59e0b' : '#ef4444';
                    return <Cell key={i} fill={color} />;
                  })}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <p className="py-10 text-center text-sm text-slate-400">No screening data yet</p>
          )}
        </div>
      </div>

      {!!data?.topSkills?.length && (
        <div className="mt-5 card p-6">
          <h3 className="mb-4 font-semibold text-slate-800">Most common candidate skills</h3>
          <div className="flex flex-wrap gap-2">
            {data.topSkills.map((s) => (
              <span
                key={s.skill}
                className="badge bg-brand-50 text-brand-700"
              >
                {s.skill} <span className="text-brand-400">· {s.count}</span>
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
