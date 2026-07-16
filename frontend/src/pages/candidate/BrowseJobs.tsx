import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Search, MapPin, Briefcase, Building2, Clock, CheckCircle2 } from 'lucide-react';
import { api } from '../../lib/api';
import PageHeader from '../../components/PageHeader';
import { useMyApplicationsMap } from '../../lib/useMyApplications';
import { Spinner, EmptyState, Pagination, StatusPill } from '../../components/ui';
import { APPLICATION_STATUS_STYLES, formatDate, formatSalary, humanize, timeAgo } from '../../lib/format';
import type { EmploymentType, Page, PublicJob, WorkMode } from '../../types';

export default function BrowseJobs() {
  const navigate = useNavigate();
  const { data: appliedMap } = useMyApplicationsMap();
  const [search, setSearch] = useState('');
  const [location, setLocation] = useState('');
  const [employmentType, setEmploymentType] = useState<EmploymentType | ''>('');
  const [workMode, setWorkMode] = useState<WorkMode | ''>('');
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['public-jobs', search, location, employmentType, workMode, page],
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
    <div>
      <PageHeader title="Find your next role" description="Browse open positions and apply in minutes" />

      <div className="mb-6 card p-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="relative sm:col-span-2 lg:col-span-1">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
            <input
              className="input pl-9"
              placeholder="Job title or company"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </div>
          <input
            className="input"
            placeholder="Location"
            value={location}
            onChange={(e) => {
              setLocation(e.target.value);
              setPage(0);
            }}
          />
          <select className="input" value={employmentType} onChange={(e) => { setEmploymentType(e.target.value as EmploymentType | ''); setPage(0); }}>
            <option value="">Any type</option>
            <option value="FULL_TIME">Full-time</option>
            <option value="PART_TIME">Part-time</option>
            <option value="CONTRACT">Contract</option>
            <option value="INTERNSHIP">Internship</option>
          </select>
          <select className="input" value={workMode} onChange={(e) => { setWorkMode(e.target.value as WorkMode | ''); setPage(0); }}>
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
        <EmptyState
          icon={<Briefcase className="h-12 w-12" />}
          title="No jobs match your search"
          description="Try adjusting your filters or check back soon."
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.content.map((job) => {
            const salary = formatSalary(job.salaryMin, job.salaryMax, job.salaryCurrency);
            const applied = appliedMap?.[job.id];
            const showApplied = applied && applied.status !== 'WITHDRAWN';
            return (
              <Link
                key={job.id}
                to={`/candidate/jobs/${job.id}`}
                className="card relative flex flex-col p-5 transition-all hover:border-brand-300 hover:shadow-md"
              >
                {showApplied && (
                  <span className="absolute right-3 top-3 flex items-center gap-1 rounded-full bg-green-50 px-2 py-0.5 text-[11px] font-medium text-green-700">
                    <CheckCircle2 className="h-3 w-3" /> Applied
                  </span>
                )}
                <div className="mb-3 flex items-center gap-2 text-xs text-slate-400">
                  {job.companyLogoUrl ? (
                    <img src={job.companyLogoUrl} alt="" className="h-5 w-5 rounded object-cover" />
                  ) : (
                    <Building2 className="h-3.5 w-3.5" />
                  )}
                  <span
                    role="link"
                    tabIndex={0}
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      navigate(`/candidate/companies/${job.companyId}`);
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        e.stopPropagation();
                        navigate(`/candidate/companies/${job.companyId}`);
                      }
                    }}
                    className="hover:text-brand-600 hover:underline"
                  >
                    {job.companyName}
                  </span>
                </div>
                <h3 className="font-semibold text-slate-800 line-clamp-2">{job.title}</h3>
                <p className="mt-2 line-clamp-2 flex-1 text-sm text-slate-500">{job.description}</p>

                <div className="mt-3 flex flex-wrap gap-1.5">
                  <span className="badge bg-slate-100 text-slate-600">{humanize(job.employmentType)}</span>
                  <span className="badge bg-slate-100 text-slate-600">{humanize(job.workMode)}</span>
                  {showApplied && applied && (
                    <StatusPill
                      label={humanize(applied.status)}
                      className={APPLICATION_STATUS_STYLES[applied.status]}
                    />
                  )}
                </div>

                <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-slate-100 pt-3 text-xs text-slate-400">
                  {job.location && (
                    <span className="flex items-center gap-1"><MapPin className="h-3.5 w-3.5" /> {job.location}</span>
                  )}
                  {salary && <span className="font-medium text-slate-600">{salary}</span>}
                  {job.deadline && (
                    <span className="font-medium text-amber-600">Apply by {formatDate(job.deadline)}</span>
                  )}
                  <span className="flex items-center gap-1 ml-auto"><Clock className="h-3.5 w-3.5" /> {timeAgo(job.publishedAt)}</span>
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
