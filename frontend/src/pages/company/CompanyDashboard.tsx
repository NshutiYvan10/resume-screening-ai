import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell,
} from 'recharts';
import { Briefcase, FileText, Users, Gauge, Plus, ClipboardCheck, Clock, ArrowRight } from 'lucide-react';
import { api } from '../../lib/api';
import { useAuth } from '../../context/AuthContext';
import PageHeader from '../../components/PageHeader';
import StatCard from '../../components/StatCard';
import { PageLoader } from '../../components/ui';
import { humanize } from '../../lib/format';
import type { Job, Page } from '../../types';

interface CompanyAnalytics {
  totalJobs: number;
  publishedJobs: number;
  draftJobs: number;
  closedJobs: number;
  pendingApprovalJobs: number;
  totalApplications: number;
  teamMembers: number;
  averageMatchScore: number | null;
  pipeline: Record<string, number>;
  scoreDistribution: Record<string, number>;
  topSkills: { skill: string; count: number }[];
}

const PIPELINE_ORDER = ['SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED', 'REJECTED'];

// ---------------------------------------------------------------- recruiter
function RecruiterDashboard({ name }: { name: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ['jobs', 'recruiter-dash'],
    queryFn: async () => (await api.get<Page<Job>>('/jobs', { params: { size: 100 } })).data,
  });
  if (isLoading) return <PageLoader />;

  const jobs = data?.content || [];
  const count = (s: string) => jobs.filter((j) => j.status === s).length;

  return (
    <div>
      <PageHeader
        title={`Welcome, ${name}`}
        description="Create postings and manage your candidate pipelines"
        action={<Link to="/company/jobs/new" className="btn-primary"><Plus className="h-4 w-4" /> Post a job</Link>}
      />
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Published" value={count('PUBLISHED')} icon={<Briefcase className="h-5 w-5" />} accent="text-green-600 bg-green-50" />
        <StatCard label="Pending approval" value={count('PENDING_APPROVAL')} icon={<Clock className="h-5 w-5" />} accent="text-amber-600 bg-amber-50" />
        <StatCard label="Drafts" value={count('DRAFT')} icon={<FileText className="h-5 w-5" />} accent="text-slate-600 bg-slate-100" />
        <StatCard label="Total jobs" value={jobs.length} icon={<Briefcase className="h-5 w-5" />} />
      </div>
      <div className="mt-6 rounded-xl border border-brand-100 bg-brand-50 p-4 text-sm text-brand-800">
        Jobs you create start as <strong>drafts</strong>. Submit them for approval — a company administrator
        reviews and publishes them to the public job board.
      </div>
      <Link to="/company/jobs" className="mt-4 inline-flex items-center gap-1 text-sm font-medium text-brand-600 hover:text-brand-700">
        Go to your jobs <ArrowRight className="h-4 w-4" />
      </Link>
    </div>
  );
}

// -------------------------------------------------------------------- admin
function AdminDashboard({ name, companyName }: { name: string; companyName?: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ['analytics', 'company'],
    queryFn: async () => (await api.get<CompanyAnalytics>('/analytics/company')).data,
  });
  if (isLoading) return <PageLoader />;

  const dist = Object.entries(data?.scoreDistribution || {}).map(([range, count]) => ({ range, count }));
  const pipeline = PIPELINE_ORDER.map((s) => ({ stage: humanize(s), count: data?.pipeline?.[s] ?? 0 }));
  const pending = data?.pendingApprovalJobs ?? 0;

  return (
    <div>
      <PageHeader
        title={`Welcome, ${name}`}
        description={companyName}
        action={<Link to="/company/jobs/new" className="btn-primary"><Plus className="h-4 w-4" /> Post a job</Link>}
      />

      {pending > 0 && (
        <Link
          to="/company/approvals"
          className="mb-5 flex items-center justify-between rounded-xl border border-amber-200 bg-amber-50 p-4 hover:bg-amber-100"
        >
          <span className="flex items-center gap-2 text-sm font-medium text-amber-800">
            <ClipboardCheck className="h-5 w-5" />
            {pending} job{pending === 1 ? '' : 's'} awaiting your approval
          </span>
          <ArrowRight className="h-4 w-4 text-amber-700" />
        </Link>
      )}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Published jobs" value={data?.publishedJobs ?? 0} icon={<Briefcase className="h-5 w-5" />} accent="text-green-600 bg-green-50" />
        <StatCard label="Pending approval" value={pending} icon={<ClipboardCheck className="h-5 w-5" />} accent="text-amber-600 bg-amber-50" />
        <StatCard label="Total applications" value={data?.totalApplications ?? 0} icon={<FileText className="h-5 w-5" />} accent="text-blue-600 bg-blue-50" />
        <StatCard label="Avg. match score" value={data?.averageMatchScore != null ? `${data.averageMatchScore}` : '—'} icon={<Gauge className="h-5 w-5" />} accent="text-violet-600 bg-violet-50" />
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
                      <div className="h-2 rounded-full bg-brand-500" style={{ width: `${(p.count / max) * 100}%` }} />
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
              <span key={s.skill} className="badge bg-brand-50 text-brand-700">
                {s.skill} <span className="text-brand-400">· {s.count}</span>
              </span>
            ))}
          </div>
        </div>
      )}

      <div className="mt-5 flex flex-wrap gap-3">
        <Link to="/company/candidates" className="btn-secondary"><Users className="h-4 w-4" /> View all candidates</Link>
        <Link to="/company/team" className="btn-secondary"><Users className="h-4 w-4" /> Manage team</Link>
      </div>
    </div>
  );
}

export default function CompanyDashboard() {
  const { user } = useAuth();
  if (!user) return null;
  const firstName = user.fullName.split(' ')[0];
  return user.role === 'COMPANY_ADMIN'
    ? <AdminDashboard name={firstName} companyName={user.companyName} />
    : <RecruiterDashboard name={firstName} />;
}
