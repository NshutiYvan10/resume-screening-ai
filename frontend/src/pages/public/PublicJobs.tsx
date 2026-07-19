import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Search, MapPin, Briefcase, Building2, Clock } from 'lucide-react';
import { api } from '../../lib/api';
import { Spinner, EmptyState, Pagination } from '../../components/ui';
import { formatSalary, humanize, timeAgo } from '../../lib/format';
import type { EmploymentType, Page, PublicJob, WorkMode } from '../../types';

export default function PublicJobs() {
  const [search, setSearch] = useState('');
  const [location, setLocation] = useState('');
  const [employmentType, setEmploymentType] = useState<EmploymentType | ''>('');
  const [workMode, setWorkMode] = useState<WorkMode | ''>('');
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['public-jobs', 'browse', search, location, employmentType, workMode, page],
    queryFn: async () =>
      (await api.get<Page<PublicJob>>('/jobs/public', {
        params: {
          search: search || undefined,
          location: location || undefined,
          employmentType: employmentType || undefined,
          workMode: workMode || undefined,
          page,
          size: 12,
        },
      })).data,
  });

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="max-w-2xl">
        <h1 className="text-3xl font-bold text-slate-900">Find your next role</h1>
        <p className="mt-2 text-slate-500">
          Browse open positions across every company on ResumeAI. Create an account when you're ready to apply.
        </p>
      </div>

      <div className="mt-6 card p-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="relative sm:col-span-2 lg:col-span-1">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
            <input className="input pl-9" placeholder="Job title or company" value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
          </div>
          <input className="input" placeholder="Location" value={location}
            onChange={(e) => { setLocation(e.target.value); setPage(0); }} />
          <select className="input" value={employmentType}
            onChange={(e) => { setEmploymentType(e.target.value as EmploymentType | ''); setPage(0); }}>
            <option value="">Any type</option>
            <option value="FULL_TIME">Full-time</option>
            <option value="PART_TIME">Part-time</option>
            <option value="CONTRACT">Contract</option>
            <option value="INTERNSHIP">Internship</option>
          </select>
          <select className="input" value={workMode}
            onChange={(e) => { setWorkMode(e.target.value as WorkMode | ''); setPage(0); }}>
            <option value="">Any mode</option>
            <option value="ONSITE">On-site</option>
            <option value="REMOTE">Remote</option>
            <option value="HYBRID">Hybrid</option>
          </select>
        </div>
      </div>

      {isLoading ? (
        <div className="py-12 text-center"><Spinner className="mx-auto h-6 w-6 text-brand-600" /></div>
      ) : !data?.content.length ? (
        <EmptyState icon={<Briefcase className="h-12 w-12" />} title="No jobs match your search"
          description="Try adjusting your filters or check back soon." />
      ) : (
        <div className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.content.map((job) => {
            const salary = formatSalary(job.salaryMin, job.salaryMax, job.salaryCurrency);
            return (
              <Link key={job.id} to={`/jobs/${job.id}`}
                className="card flex flex-col p-5 transition-all hover:border-brand-300 hover:shadow-md">
                <div className="mb-3 flex items-center gap-2 text-xs text-slate-400">
                  {job.companyLogoUrl
                    ? <img src={job.companyLogoUrl} alt="" className="h-5 w-5 rounded object-cover" />
                    : <Building2 className="h-3.5 w-3.5" />}
                  <span>{job.companyName}</span>
                </div>
                <h3 className="font-semibold text-slate-800 line-clamp-2">{job.title}</h3>
                <p className="mt-2 line-clamp-2 flex-1 text-sm text-slate-500">{job.description}</p>
                <div className="mt-3 flex flex-wrap gap-1.5">
                  <span className="badge bg-slate-100 text-slate-600">{humanize(job.employmentType)}</span>
                  <span className="badge bg-slate-100 text-slate-600">{humanize(job.workMode)}</span>
                </div>
                <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-slate-100 pt-3 text-xs text-slate-400">
                  {job.location && <span className="flex items-center gap-1"><MapPin className="h-3.5 w-3.5" /> {job.location}</span>}
                  {salary && <span className="font-medium text-slate-600">{salary}</span>}
                  <span className="ml-auto flex items-center gap-1"><Clock className="h-3.5 w-3.5" /> {timeAgo(job.publishedAt)}</span>
                </div>
              </Link>
            );
          })}
        </div>
      )}
      {data && <Pagination page={page} totalPages={data.totalPages} onChange={setPage} />}
    </div>
  );
}
