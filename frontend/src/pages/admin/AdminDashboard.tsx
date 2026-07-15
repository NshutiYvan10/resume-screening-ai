import { useQuery } from '@tanstack/react-query';
import { Building2, Users, Briefcase, FileText, UserCheck, Send } from 'lucide-react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import PageHeader from '../../components/PageHeader';
import StatCard from '../../components/StatCard';
import { PageLoader } from '../../components/ui';

export default function AdminDashboard() {
  const { data, isLoading } = useQuery({
    queryKey: ['analytics', 'platform'],
    queryFn: async () => (await api.get<Record<string, number>>('/analytics/platform')).data,
  });

  if (isLoading) return <PageLoader />;

  return (
    <div>
      <PageHeader
        title="Platform overview"
        description="Health and activity across all companies on ResumeAI"
        action={
          <Link to="/admin/companies" className="btn-primary">
            <Send className="h-4 w-4" /> Invite a company
          </Link>
        }
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="Companies" value={data?.totalCompanies ?? 0} icon={<Building2 className="h-5 w-5" />} />
        <StatCard label="Total users" value={data?.totalUsers ?? 0} icon={<Users className="h-5 w-5" />}
          accent="text-teal-600 bg-teal-50" />
        <StatCard label="Candidates" value={data?.totalCandidates ?? 0} icon={<UserCheck className="h-5 w-5" />}
          accent="text-violet-600 bg-violet-50" />
        <StatCard label="Recruiters" value={data?.totalRecruiters ?? 0} icon={<Users className="h-5 w-5" />}
          accent="text-amber-600 bg-amber-50" />
        <StatCard label="Jobs posted" value={data?.totalJobs ?? 0} icon={<Briefcase className="h-5 w-5" />} />
        <StatCard label="Published jobs" value={data?.publishedJobs ?? 0} icon={<Briefcase className="h-5 w-5" />}
          accent="text-green-600 bg-green-50" />
        <StatCard label="Applications" value={data?.totalApplications ?? 0} icon={<FileText className="h-5 w-5" />}
          accent="text-blue-600 bg-blue-50" />
      </div>

      <div className="mt-8 grid gap-4 sm:grid-cols-2">
        <Link to="/admin/companies" className="card p-6 hover:border-brand-300 transition-colors">
          <Building2 className="h-6 w-6 text-brand-600" />
          <h3 className="mt-3 font-semibold text-slate-800">Manage companies</h3>
          <p className="mt-1 text-sm text-slate-500">
            Invite new organizations, review onboarding status, suspend or reactivate accounts.
          </p>
        </Link>
        <Link to="/admin/audit" className="card p-6 hover:border-brand-300 transition-colors">
          <FileText className="h-6 w-6 text-brand-600" />
          <h3 className="mt-3 font-semibold text-slate-800">Audit trail</h3>
          <p className="mt-1 text-sm text-slate-500">
            Full record of administrative actions across the platform for compliance and security.
          </p>
        </Link>
      </div>
    </div>
  );
}
