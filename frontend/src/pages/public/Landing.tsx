import { useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowRight, Sparkles, ShieldCheck, BarChart3, FileSearch, Users2, GaugeCircle,
  Search, UploadCloud, CheckCircle2, Briefcase, MapPin, Building2, Star,
} from 'lucide-react';
import { api } from '../../lib/api';
import { humanize, formatSalary, timeAgo } from '../../lib/format';
import type { Page, PublicJob } from '../../types';

export default function Landing() {
  const location = useLocation();

  // smooth-scroll to #how / #employers when arriving via a hash link
  useEffect(() => {
    if (location.hash) {
      const el = document.getElementById(location.hash.slice(1));
      if (el) setTimeout(() => el.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50);
    } else {
      window.scrollTo(0, 0);
    }
  }, [location]);

  const { data: jobs } = useQuery({
    queryKey: ['public-jobs', 'landing'],
    queryFn: async () => (await api.get<Page<PublicJob>>('/jobs/public', { params: { size: 6 } })).data,
  });

  return (
    <div>
      {/* ============================ hero ============================ */}
      <section className="relative overflow-hidden bg-slate-900">
        <div className="absolute inset-0 bg-gradient-to-br from-slate-900 via-brand-900 to-brand-700" />
        <div className="absolute -right-24 -top-24 h-96 w-96 rounded-full bg-brand-500/20 blur-3xl" />
        <div className="absolute -bottom-32 -left-24 h-96 w-96 rounded-full bg-brand-400/10 blur-3xl" />
        <div className="relative mx-auto grid max-w-7xl items-center gap-12 px-4 py-20 sm:px-6 lg:grid-cols-2 lg:py-28 lg:px-8">
          <div>
            <span className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-3 py-1 text-xs font-medium text-brand-100">
              <Sparkles className="h-3.5 w-3.5" /> AI-powered résumé screening
            </span>
            <h1 className="mt-5 text-4xl font-extrabold leading-tight text-white sm:text-5xl">
              Hire smarter.<br />Get hired faster.
            </h1>
            <p className="mt-5 max-w-xl text-lg leading-relaxed text-slate-300">
              ResumeAI matches candidates to roles with explainable, bias-aware AI. Candidates
              discover jobs and apply in minutes; companies screen, rank and hire — without the pile
              of paperwork.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              <Link to="/jobs" className="btn-primary px-5 py-3 text-base">
                <Search className="h-5 w-5" /> Browse open jobs
              </Link>
              <a href="#employers" className="btn px-5 py-3 text-base border border-white/25 bg-white/5 text-white hover:bg-white/10">
                For companies <ArrowRight className="h-5 w-5" />
              </a>
            </div>
            <dl className="mt-10 flex gap-8">
              {[
                { k: 'Fair', v: 'Bias-aware scoring' },
                { k: 'Fast', v: 'Minutes, not weeks' },
                { k: 'Clear', v: 'Explainable results' },
              ].map((s) => (
                <div key={s.k}>
                  <dt className="text-2xl font-bold text-white">{s.k}</dt>
                  <dd className="text-sm text-slate-400">{s.v}</dd>
                </div>
              ))}
            </dl>
          </div>

          {/* product visual: a mock screening result */}
          <div className="relative lg:justify-self-end">
            <HeroCard />
          </div>
        </div>
      </section>

      {/* ====================== how it works ====================== */}
      <section id="how" className="mx-auto max-w-7xl scroll-mt-20 px-4 py-20 sm:px-6 lg:px-8">
        <SectionHeading
          eyebrow="How it works"
          title="One platform, two sides of hiring"
          subtitle="Whether you're looking for your next role or your next hire, ResumeAI makes the process clear and quick."
        />
        <div className="mt-12 grid gap-8 lg:grid-cols-2">
          <StepColumn
            tone="candidate"
            heading="For candidates"
            steps={[
              { icon: <Search className="h-5 w-5" />, title: 'Discover roles', text: 'Browse jobs from every company on the platform — no account needed to look.' },
              { icon: <UploadCloud className="h-5 w-5" />, title: 'Apply in minutes', text: 'Upload your résumé once and apply. Our AI reads it and matches you to the role.' },
              { icon: <CheckCircle2 className="h-5 w-5" />, title: 'Track everything', text: 'Follow your application status, interviews and offers in one place.' },
            ]}
          />
          <StepColumn
            tone="company"
            heading="For companies"
            steps={[
              { icon: <Briefcase className="h-5 w-5" />, title: 'Post a role', text: 'Publish a job with the skills and experience that actually matter for it.' },
              { icon: <FileSearch className="h-5 w-5" />, title: 'AI screens & ranks', text: 'Every applicant is parsed, scored and ranked against the role — with reasoning you can audit.' },
              { icon: <Users2 className="h-5 w-5" />, title: 'Interview & hire', text: 'Run structured interviews, scorecards and offers through a guided pipeline.' },
            ]}
          />
        </div>
      </section>

      {/* ======================== features ======================== */}
      <section className="bg-slate-50 py-20">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <SectionHeading
            eyebrow="Why ResumeAI"
            title="Screening you can actually trust"
            subtitle="Accuracy and fairness aren't optional in hiring. Everything here is built to be explainable and defensible."
          />
          <div className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {[
              { icon: <GaugeCircle className="h-6 w-6" />, title: 'Accurate matching', text: 'Skills, experience and education are matched against each role — not keyword guesswork.' },
              { icon: <ShieldCheck className="h-6 w-6" />, title: 'Bias-aware', text: 'Advisory bias flags and identity checks keep evaluations fair and consistent.' },
              { icon: <FileSearch className="h-6 w-6" />, title: 'Explainable scores', text: 'Every score comes with a "why" — matched skills, gaps and a plain-English rationale.' },
              { icon: <Users2 className="h-6 w-6" />, title: 'Full hiring pipeline', text: 'From application to offer: interviews, scorecards, approvals and compliance built in.' },
              { icon: <BarChart3 className="h-6 w-6" />, title: 'Real reporting', text: 'Pipeline funnels, time-to-hire and score trends for candidates, recruiters and admins.' },
              { icon: <ShieldCheck className="h-6 w-6" />, title: 'Original documents', text: 'Recruiters can always view and download the exact résumé a candidate submitted.' },
            ].map((f) => (
              <div key={f.title} className="card p-6 transition-shadow hover:shadow-md">
                <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
                  {f.icon}
                </div>
                <h3 className="mt-4 font-semibold text-slate-800">{f.title}</h3>
                <p className="mt-1.5 text-sm leading-relaxed text-slate-500">{f.text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ==================== public jobs preview ==================== */}
      <section className="mx-auto max-w-7xl px-4 py-20 sm:px-6 lg:px-8">
        <div className="flex items-end justify-between gap-4">
          <SectionHeading align="left" eyebrow="Open roles" title="Explore jobs hiring now" subtitle="Browse the latest openings — sign in only when you're ready to apply." />
          <Link to="/jobs" className="hidden shrink-0 items-center gap-1 text-sm font-semibold text-brand-600 hover:text-brand-700 sm:flex">
            View all jobs <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
        <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {(jobs?.content || []).map((job) => (
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
                {formatSalary(job.salaryMin, job.salaryMax, job.salaryCurrency) && (
                  <span className="font-medium text-slate-600">{formatSalary(job.salaryMin, job.salaryMax, job.salaryCurrency)}</span>
                )}
                <span className="ml-auto">{timeAgo(job.publishedAt)}</span>
              </div>
            </Link>
          ))}
        </div>
        {!jobs?.content.length && (
          <p className="mt-10 text-center text-sm text-slate-400">No open roles right now — check back soon.</p>
        )}
        <div className="mt-8 text-center sm:hidden">
          <Link to="/jobs" className="btn-secondary">View all jobs <ArrowRight className="h-4 w-4" /></Link>
        </div>
      </section>

      {/* ====================== employer CTA ====================== */}
      <section id="employers" className="scroll-mt-20 bg-gradient-to-br from-brand-700 to-brand-900 py-20">
        <div className="mx-auto max-w-4xl px-4 text-center sm:px-6 lg:px-8">
          <Star className="mx-auto h-8 w-8 text-brand-200" />
          <h2 className="mt-4 text-3xl font-bold text-white">Hiring? Put AI to work on your pipeline.</h2>
          <p className="mx-auto mt-4 max-w-2xl text-lg text-brand-100">
            Post roles, let ResumeAI screen and rank every applicant, and run your whole hiring
            process — interviews, scorecards and offers — in one place.
          </p>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link to="/login" className="btn px-5 py-3 text-base bg-white text-brand-700 hover:bg-brand-50">
              Company sign in
            </Link>
            <a href="mailto:rhabinka@gmail.com?subject=ResumeAI%20for%20our%20company"
              className="btn px-5 py-3 text-base border border-white/30 bg-white/5 text-white hover:bg-white/10">
              Request access
            </a>
          </div>
          <p className="mt-4 text-sm text-brand-200/80">
            New companies are onboarded by our team — request access and we'll set you up.
          </p>
        </div>
      </section>
    </div>
  );
}

// ------------------------------------------------------------- pieces

function SectionHeading({ eyebrow, title, subtitle, align = 'center' }: {
  eyebrow: string; title: string; subtitle?: string; align?: 'center' | 'left';
}) {
  return (
    <div className={align === 'center' ? 'mx-auto max-w-2xl text-center' : 'max-w-2xl'}>
      <p className="text-sm font-semibold uppercase tracking-wide text-brand-600">{eyebrow}</p>
      <h2 className="mt-2 text-3xl font-bold text-slate-900">{title}</h2>
      {subtitle && <p className="mt-3 text-base leading-relaxed text-slate-500">{subtitle}</p>}
    </div>
  );
}

function StepColumn({ tone, heading, steps }: {
  tone: 'candidate' | 'company';
  heading: string;
  steps: { icon: React.ReactNode; title: string; text: string }[];
}) {
  const accent = tone === 'candidate' ? 'bg-brand-600' : 'bg-slate-800';
  return (
    <div className="card p-8">
      <h3 className="text-lg font-bold text-slate-900">{heading}</h3>
      <ol className="mt-6 space-y-6">
        {steps.map((s, i) => (
          <li key={s.title} className="flex gap-4">
            <div className="relative flex flex-col items-center">
              <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${accent} text-white`}>
                {s.icon}
              </div>
              {i < steps.length - 1 && <div className="mt-1 h-full w-px flex-1 bg-slate-200" />}
            </div>
            <div className="pb-2">
              <p className="font-semibold text-slate-800">{s.title}</p>
              <p className="mt-1 text-sm leading-relaxed text-slate-500">{s.text}</p>
            </div>
          </li>
        ))}
      </ol>
    </div>
  );
}

function HeroCard() {
  const bars = [
    { label: 'Skills match', value: 95 },
    { label: 'Experience', value: 88 },
    { label: 'Education', value: 100 },
  ];
  return (
    <div className="w-full max-w-md rounded-2xl border border-white/10 bg-white p-6 shadow-2xl">
      <div className="flex items-center gap-4">
        <ScoreRing value={92} />
        <div className="min-w-0">
          <p className="truncate text-base font-bold text-slate-900">Alex Mwangi</p>
          <p className="truncate text-sm text-slate-500">Senior Software Engineer</p>
          <span className="mt-1 inline-flex items-center gap-1 rounded-full bg-green-50 px-2 py-0.5 text-xs font-medium text-green-700">
            <CheckCircle2 className="h-3 w-3" /> Strong match
          </span>
        </div>
      </div>
      <div className="mt-6 space-y-3">
        {bars.map((b) => (
          <div key={b.label}>
            <div className="mb-1 flex justify-between text-xs">
              <span className="text-slate-500">{b.label}</span>
              <span className="font-semibold text-slate-700">{b.value}</span>
            </div>
            <div className="h-1.5 rounded-full bg-slate-100">
              <div className="h-1.5 rounded-full bg-brand-500" style={{ width: `${b.value}%` }} />
            </div>
          </div>
        ))}
      </div>
      <div className="mt-5 rounded-lg border border-brand-100 bg-brand-50/60 p-3">
        <p className="text-xs font-semibold text-brand-800">Why this score</p>
        <p className="mt-1 text-xs leading-relaxed text-brand-900/70">
          Matches all 5 required skills (Python, PostgreSQL, React…), 6 yrs experience vs 5 required,
          and a BSc in Computer Science.
        </p>
      </div>
      <div className="mt-4 flex flex-wrap gap-1.5">
        {['Python', 'React', 'PostgreSQL', 'AWS'].map((s) => (
          <span key={s} className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">{s}</span>
        ))}
      </div>
    </div>
  );
}

function ScoreRing({ value }: { value: number }) {
  const size = 64, r = (size - 6) / 2, circ = 2 * Math.PI * r, dash = (value / 100) * circ;
  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="#e2e8f0" strokeWidth={5} />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="#16a34a" strokeWidth={5}
          strokeDasharray={`${dash} ${circ}`} strokeLinecap="round" />
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">
        <span className="text-base font-bold text-green-600">{value}</span>
      </div>
    </div>
  );
}
