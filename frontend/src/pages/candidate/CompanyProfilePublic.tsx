import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeft, Building2, MapPin, Users, Globe, Briefcase, Calendar, Clock,
  Target, HeartHandshake, Sparkles, Linkedin, Twitter, X, ChevronLeft, ChevronRight,
} from 'lucide-react';
import { CheckCircle2 } from 'lucide-react';
import { api } from '../../lib/api';
import { useMyApplicationsMap } from '../../lib/useMyApplications';
import { PageLoader, EmptyState, StatusPill } from '../../components/ui';
import { APPLICATION_STATUS_STYLES, formatSalary, humanize, timeAgo } from '../../lib/format';
import type { Page, PublicCompany, PublicJob } from '../../types';

export default function CompanyProfilePublic() {
  const { companyId } = useParams();
  const [lightbox, setLightbox] = useState<number | null>(null);

  const { data: company, isLoading, isError } = useQuery({
    queryKey: ['public-company', companyId],
    queryFn: async () => (await api.get<PublicCompany>(`/companies/public/${companyId}`)).data,
  });

  const { data: jobs } = useQuery({
    queryKey: ['public-company-jobs', companyId],
    queryFn: async () =>
      (await api.get<Page<PublicJob>>('/jobs/public', { params: { companyId, size: 50 } })).data,
    enabled: !!companyId,
  });

  const { data: appliedMap } = useMyApplicationsMap();

  if (isLoading) return <PageLoader />;

  if (isError || !company) {
    return (
      <div className="mx-auto max-w-3xl">
        <Link to="/candidate" className="mb-4 flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700">
          <ArrowLeft className="h-4 w-4" /> Back to jobs
        </Link>
        <EmptyState
          icon={<Building2 className="h-12 w-12" />}
          title="Company not found"
          description="This company profile is unavailable."
        />
      </div>
    );
  }

  const photos = company.photos || [];

  const externalUrl = (url: string) => (url.startsWith('http') ? url : `https://${url}`);

  return (
    <div className="mx-auto max-w-4xl">
      <Link to="/candidate" className="mb-4 flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700">
        <ArrowLeft className="h-4 w-4" /> Back to jobs
      </Link>

      {/* ---------- hero ---------- */}
      <div className="card overflow-hidden">
        <div className="relative h-44 sm:h-56 bg-gradient-to-r from-slate-900 via-brand-800 to-brand-600">
          {company.coverUrl && (
            <img src={company.coverUrl} alt="" className="h-full w-full object-cover" />
          )}
          <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />
        </div>

        <div className="px-6 pb-6 sm:px-8">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div className="flex items-end gap-4">
              <div className="-mt-12 flex h-24 w-24 shrink-0 items-center justify-center overflow-hidden rounded-2xl border-4 border-white bg-brand-100 text-4xl font-bold text-brand-700 shadow-lg">
                {company.logoUrl ? (
                  <img src={company.logoUrl} alt={`${company.name} logo`} className="h-full w-full object-cover" />
                ) : (
                  company.name.charAt(0).toUpperCase()
                )}
              </div>
              <div className="pb-1">
                <h1 className="text-2xl font-bold text-slate-900">{company.name}</h1>
                {company.tagline && <p className="mt-0.5 text-sm text-slate-500">{company.tagline}</p>}
              </div>
            </div>
            <div className="flex items-center gap-2 pb-1">
              {company.website && (
                <a href={externalUrl(company.website)} target="_blank" rel="noopener noreferrer"
                  className="btn-secondary py-2" title="Website">
                  <Globe className="h-4 w-4" /> Website
                </a>
              )}
              {company.linkedinUrl && (
                <a href={externalUrl(company.linkedinUrl)} target="_blank" rel="noopener noreferrer"
                  className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-300 text-slate-500 hover:bg-slate-50" title="LinkedIn">
                  <Linkedin className="h-4 w-4" />
                </a>
              )}
              {company.twitterUrl && (
                <a href={externalUrl(company.twitterUrl)} target="_blank" rel="noopener noreferrer"
                  className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-300 text-slate-500 hover:bg-slate-50" title="X / Twitter">
                  <Twitter className="h-4 w-4" />
                </a>
              )}
            </div>
          </div>

          {/* stats strip */}
          <div className="mt-6 grid grid-cols-2 gap-3 border-t border-slate-100 pt-5 sm:grid-cols-4">
            <div>
              <p className="flex items-center gap-1.5 text-xs text-slate-400"><Briefcase className="h-3.5 w-3.5" /> Open roles</p>
              <p className="mt-0.5 font-semibold text-slate-800">{company.openJobs}</p>
            </div>
            {company.companySize && (
              <div>
                <p className="flex items-center gap-1.5 text-xs text-slate-400"><Users className="h-3.5 w-3.5" /> Team size</p>
                <p className="mt-0.5 font-semibold text-slate-800">{company.companySize}</p>
              </div>
            )}
            {company.foundedYear && (
              <div>
                <p className="flex items-center gap-1.5 text-xs text-slate-400"><Calendar className="h-3.5 w-3.5" /> Founded</p>
                <p className="mt-0.5 font-semibold text-slate-800">{company.foundedYear}</p>
              </div>
            )}
            {(company.industry || company.location) && (
              <div>
                <p className="flex items-center gap-1.5 text-xs text-slate-400"><MapPin className="h-3.5 w-3.5" /> {company.industry || 'Location'}</p>
                <p className="mt-0.5 font-semibold text-slate-800">{company.location || company.industry}</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ---------- about + mission ---------- */}
      {(company.description || company.mission) && (
        <div className="mt-5 grid gap-5 lg:grid-cols-3">
          {company.description && (
            <div className={`card p-6 ${company.mission ? 'lg:col-span-2' : 'lg:col-span-3'}`}>
              <h2 className="flex items-center gap-2 font-semibold text-slate-800">
                <Building2 className="h-4 w-4 text-brand-600" /> About {company.name}
              </h2>
              <p className="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-slate-600">{company.description}</p>
            </div>
          )}
          {company.mission && (
            <div className="card bg-gradient-to-br from-brand-600 to-brand-800 p-6 text-white">
              <h2 className="flex items-center gap-2 font-semibold">
                <Target className="h-4 w-4" /> Our mission
              </h2>
              <p className="mt-3 text-sm leading-relaxed text-brand-50">{company.mission}</p>
            </div>
          )}
        </div>
      )}

      {/* ---------- values + benefits ---------- */}
      {(!!company.values?.length || !!company.benefits?.length) && (
        <div className="mt-5 grid gap-5 sm:grid-cols-2">
          {!!company.values?.length && (
            <div className="card p-6">
              <h2 className="flex items-center gap-2 font-semibold text-slate-800">
                <Sparkles className="h-4 w-4 text-brand-600" /> Our values
              </h2>
              <div className="mt-3 flex flex-wrap gap-2">
                {company.values.map((v) => (
                  <span key={v} className="badge bg-brand-50 px-3 py-1 text-sm text-brand-700">{v}</span>
                ))}
              </div>
            </div>
          )}
          {!!company.benefits?.length && (
            <div className="card p-6">
              <h2 className="flex items-center gap-2 font-semibold text-slate-800">
                <HeartHandshake className="h-4 w-4 text-brand-600" /> Benefits & perks
              </h2>
              <ul className="mt-3 grid gap-2">
                {company.benefits.map((b) => (
                  <li key={b} className="flex items-center gap-2 text-sm text-slate-600">
                    <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-brand-500" /> {b}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* ---------- photo gallery ---------- */}
      {!!photos.length && (
        <div className="mt-5 card p-6">
          <h2 className="font-semibold text-slate-800">Life at {company.name}</h2>
          <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
            {photos.map((p, i) => (
              <button
                key={p.id}
                onClick={() => setLightbox(i)}
                className="group relative aspect-[4/3] overflow-hidden rounded-lg bg-slate-100"
              >
                <img
                  src={p.url}
                  alt={p.caption || `${company.name} photo`}
                  className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
                />
                {p.caption && (
                  <span className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent px-2 pb-1.5 pt-4 text-left text-xs text-white">
                    {p.caption}
                  </span>
                )}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* ---------- open positions ---------- */}
      <div className="mt-5">
        <h2 className="mb-3 font-semibold text-slate-800">
          Open positions at {company.name} ({company.openJobs})
        </h2>
        {!jobs?.content.length ? (
          <EmptyState
            icon={<Briefcase className="h-10 w-10" />}
            title="No open positions right now"
            description={`${company.name} has no published jobs at the moment. Check back soon.`}
          />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            {jobs.content.map((job) => {
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
                    <span className="ml-auto flex items-center gap-1"><Clock className="h-3.5 w-3.5" /> {timeAgo(job.publishedAt)}</span>
                  </div>
                </Link>
              );
            })}
          </div>
        )}
      </div>

      {/* ---------- lightbox ---------- */}
      {lightbox !== null && photos[lightbox] && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/90 p-4"
          onClick={() => setLightbox(null)}>
          <button className="absolute right-4 top-4 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
            onClick={() => setLightbox(null)}>
            <X className="h-5 w-5" />
          </button>
          {photos.length > 1 && (
            <button
              className="absolute left-3 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
              onClick={(e) => { e.stopPropagation(); setLightbox((lightbox - 1 + photos.length) % photos.length); }}
            >
              <ChevronLeft className="h-6 w-6" />
            </button>
          )}
          <figure className="max-h-[85vh] max-w-4xl" onClick={(e) => e.stopPropagation()}>
            <img src={photos[lightbox].url} alt={photos[lightbox].caption || ''}
              className="max-h-[80vh] w-auto rounded-lg object-contain" />
            {photos[lightbox].caption && (
              <figcaption className="mt-2 text-center text-sm text-slate-300">{photos[lightbox].caption}</figcaption>
            )}
          </figure>
          {photos.length > 1 && (
            <button
              className="absolute right-3 rounded-full bg-white/10 p-2 text-white hover:bg-white/20"
              onClick={(e) => { e.stopPropagation(); setLightbox((lightbox + 1) % photos.length); }}
            >
              <ChevronRight className="h-6 w-6" />
            </button>
          )}
        </div>
      )}
    </div>
  );
}
