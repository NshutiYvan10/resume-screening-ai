import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeft, MapPin, Building2, Briefcase, GraduationCap, Calendar, LogIn, UserPlus, Lock,
} from 'lucide-react';
import { api } from '../../lib/api';
import { useAuth } from '../../context/AuthContext';
import { PageLoader, EmptyState } from '../../components/ui';
import { formatSalary, formatDate, humanize } from '../../lib/format';
import type { PublicJob } from '../../types';

export default function PublicJobDetail() {
  const { jobId } = useParams();
  const { user } = useAuth();

  const { data: job, isLoading, isError } = useQuery({
    queryKey: ['public-job', jobId],
    queryFn: async () => (await api.get<PublicJob>(`/jobs/public/${jobId}`)).data,
  });

  if (isLoading) return <div className="py-16"><PageLoader /></div>;
  if (isError || !job) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-12">
        <Link to="/jobs" className="mb-4 flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700">
          <ArrowLeft className="h-4 w-4" /> Back to jobs
        </Link>
        <EmptyState icon={<Briefcase className="h-12 w-12" />} title="Job not found"
          description="This position may have been closed or is no longer accepting applications." />
      </div>
    );
  }

  const salary = formatSalary(job.salaryMin, job.salaryMax, job.salaryCurrency);
  // deep-link back to the in-app apply page after authentication
  const applyPath = `/candidate/jobs/${job.id}`;
  const redirect = encodeURIComponent(applyPath);

  return (
    <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6 lg:px-8">
      <Link to="/jobs" className="mb-4 flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700">
        <ArrowLeft className="h-4 w-4" /> Back to jobs
      </Link>

      <div className="card p-6 sm:p-8">
        <div className="flex items-center gap-2 text-sm text-slate-400">
          <Building2 className="h-4 w-4" />
          <span className="font-medium text-slate-600">{job.companyName}</span>
          {job.companyIndustry && <span>· {job.companyIndustry}</span>}
        </div>
        <h1 className="mt-2 text-2xl font-bold text-slate-900">{job.title}</h1>

        <div className="mt-4 flex flex-wrap gap-2">
          <span className="badge bg-brand-50 text-brand-700">{humanize(job.employmentType)}</span>
          <span className="badge bg-brand-50 text-brand-700">{humanize(job.workMode)}</span>
          {job.department && <span className="badge bg-slate-100 text-slate-600">{job.department}</span>}
        </div>

        <div className="mt-5 grid gap-3 rounded-lg bg-slate-50 p-4 text-sm sm:grid-cols-2">
          {job.location && (
            <div className="flex items-center gap-2 text-slate-600"><MapPin className="h-4 w-4 text-slate-400" /> {job.location}</div>
          )}
          {salary && (
            <div className="flex items-center gap-2 text-slate-600"><Briefcase className="h-4 w-4 text-slate-400" /> {salary}</div>
          )}
          {job.minExperienceYears != null && (
            <div className="flex items-center gap-2 text-slate-600"><Briefcase className="h-4 w-4 text-slate-400" /> {job.minExperienceYears}+ years experience</div>
          )}
          {job.educationLevel && (
            <div className="flex items-center gap-2 text-slate-600"><GraduationCap className="h-4 w-4 text-slate-400" /> {humanize(job.educationLevel)}</div>
          )}
          {job.deadline && (
            <div className="flex items-center gap-2 text-slate-600"><Calendar className="h-4 w-4 text-slate-400" /> Apply by {formatDate(job.deadline)}</div>
          )}
        </div>

        <div className="mt-6">
          <h2 className="font-semibold text-slate-800">About the role</h2>
          <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-slate-600">{job.description}</p>
        </div>

        {job.responsibilities && (
          <div className="mt-6">
            <h2 className="font-semibold text-slate-800">Responsibilities</h2>
            <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-slate-600">{job.responsibilities}</p>
          </div>
        )}

        {!!job.skills?.length && (
          <div className="mt-6">
            <h2 className="font-semibold text-slate-800">Skills we're looking for</h2>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {job.skills.map((s) => <span key={s} className="badge bg-slate-100 text-slate-600">{s}</span>)}
            </div>
          </div>
        )}

        {/* -------- apply gate -------- */}
        <div className="mt-8 border-t border-slate-100 pt-6">
          {user?.role === 'CANDIDATE' ? (
            <Link to={applyPath} className="btn-primary">Apply for this position</Link>
          ) : user ? (
            <p className="flex items-center gap-2 rounded-lg bg-slate-50 px-4 py-3 text-sm text-slate-500">
              <Lock className="h-4 w-4" /> You're signed in as a {humanize(user.role).toLowerCase()}. Applications are made from a candidate account.
            </p>
          ) : (
            <div className="rounded-xl border border-brand-100 bg-brand-50/50 p-5">
              <p className="text-sm font-semibold text-slate-800">Interested in this role?</p>
              <p className="mt-1 text-sm text-slate-500">
                Create a free candidate account or sign in to apply — we'll bring you right back to this job.
              </p>
              <div className="mt-4 flex flex-wrap gap-3">
                <Link to={`/register?redirect=${redirect}`} className="btn-primary">
                  <UserPlus className="h-4 w-4" /> Create account & apply
                </Link>
                <Link to={`/login?redirect=${redirect}`} className="btn-secondary">
                  <LogIn className="h-4 w-4" /> Sign in
                </Link>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
