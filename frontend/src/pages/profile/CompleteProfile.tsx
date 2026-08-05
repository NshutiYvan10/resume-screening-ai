import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Camera, Check, ChevronLeft, ChevronRight, ShieldCheck, Trash2, User as UserIcon } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { Field, PageLoader, Spinner, ImageWithFallback } from '../../components/ui';
import TagInput from '../../components/TagInput';
import { homeForRole } from '../../components/RouteGuards';
import type { MyProfile } from '../../types';

/** A wizard step: a title, the fields it owns, and which required labels it can satisfy. */
interface Step {
  key: string;
  title: string;
  blurb: string;
  /** Labels from completion.missingRequired that this step is able to fix. */
  covers: string[];
}

const PHOTO_STEP: Step = {
  key: 'photo',
  title: 'Your photo',
  blurb: 'A real face makes the platform feel like a team rather than a database.',
  covers: ['Profile photo'],
};

const STAFF_STEPS: Step[] = [
  PHOTO_STEP,
  { key: 'role', title: 'Your role', blurb: 'How colleagues and candidates see you.', covers: ['Full name', 'Job title', 'Location'] },
  { key: 'about', title: 'About you', blurb: 'Optional, but it makes your profile feel human.', covers: [] },
];

const CANDIDATE_STEPS: Step[] = [
  PHOTO_STEP,
  { key: 'basics', title: 'The basics', blurb: 'How recruiters will first see you.', covers: ['Full name', 'Professional headline', 'Location'] },
  { key: 'skills', title: 'Skills', blurb: 'What you want to be found for.', covers: ['At least one skill'] },
  { key: 'preferences', title: 'Preferences', blurb: 'Optional — helps match you to the right roles.', covers: [] },
];

export default function CompleteProfile() {
  const { user, refreshUser } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const photoInput = useRef<HTMLInputElement>(null);

  const [step, setStep] = useState(0);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [form, setForm] = useState<Record<string, unknown>>({});

  const isCandidate = user?.role === 'CANDIDATE';
  const steps = isCandidate ? CANDIDATE_STEPS : STAFF_STEPS;

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['profile', 'me'],
    queryFn: async () => (await api.get<MyProfile>('/profile')).data,
  });

  // seed the form once the server state arrives, so a returning user resumes
  useEffect(() => {
    if (!data) return;
    setForm({
      fullName: data.fullName ?? '',
      phone: data.phone ?? '',
      jobTitle: data.jobTitle ?? '',
      department: data.department ?? '',
      location: data.location ?? '',
      timeZone: data.timeZone || Intl.DateTimeFormat().resolvedOptions().timeZone || '',
      locale: data.locale ?? '',
      bio: data.bio ?? '',
      linkedinUrl: data.linkedinUrl ?? '',
      specializations: data.specializations ?? [],
      yearsExperience: data.yearsExperience ?? '',
      headline: data.candidate?.headline ?? '',
      summary: data.candidate?.summary ?? '',
      skills: data.candidate?.skills ?? [],
      workAuthorization: data.candidate?.workAuthorization ?? '',
      workArrangement: data.candidate?.workArrangement ?? '',
      availability: data.candidate?.availability ?? '',
      salaryMin: data.candidate?.salaryMin ?? '',
      salaryMax: data.candidate?.salaryMax ?? '',
      githubUrl: data.candidate?.githubUrl ?? '',
      portfolioUrl: data.candidate?.portfolioUrl ?? '',
      preferredCategories: data.candidate?.preferredCategories ?? [],
    });
  }, [data]);

  const completion = data?.completion;
  const missing = useMemo(() => new Set(completion?.missingRequired ?? []), [completion]);
  const set = (k: string, v: unknown) => setForm((f) => ({ ...f, [k]: v }));

  if (isLoading || !data) return <PageLoader />;

  const num = (v: unknown) => (v === '' || v == null ? undefined : Number(v));
  const str = (v: unknown) => {
    const s = String(v ?? '').trim();
    return s === '' ? undefined : s;
  };

  /** Persist everything typed so far. Each step saves, so progress is never lost. */
  const save = async (): Promise<MyProfile | null> => {
    setSaving(true);
    try {
      const body = isCandidate
        ? {
            fullName: str(form.fullName), phone: str(form.phone), location: str(form.location),
            timeZone: str(form.timeZone), linkedinUrl: str(form.linkedinUrl),
            headline: str(form.headline), summary: str(form.summary),
            workAuthorization: str(form.workAuthorization),
            skills: (form.skills as string[]) ?? [],
            preferredCategories: (form.preferredCategories as string[]) ?? [],
            workArrangement: str(form.workArrangement), availability: str(form.availability),
            salaryMin: num(form.salaryMin), salaryMax: num(form.salaryMax),
            githubUrl: str(form.githubUrl), portfolioUrl: str(form.portfolioUrl),
          }
        : {
            fullName: str(form.fullName), phone: str(form.phone), jobTitle: str(form.jobTitle),
            department: str(form.department), location: str(form.location),
            timeZone: str(form.timeZone), locale: str(form.locale), bio: str(form.bio),
            linkedinUrl: str(form.linkedinUrl),
            specializations: (form.specializations as string[]) ?? [],
            yearsExperience: num(form.yearsExperience),
          };
      const res = await api.put<MyProfile>(isCandidate ? '/profile/candidate' : '/profile/staff', body);
      await refetch();
      return res.data;
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
      return null;
    } finally {
      setSaving(false);
    }
  };

  const uploadPhoto = async (file?: File | null) => {
    if (!file) return;
    setUploading(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      await api.post('/profile/photo', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
      await refetch();
      toast('Photo updated', 'success');
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    } finally {
      setUploading(false);
    }
  };

  const removePhoto = async () => {
    try {
      await api.delete('/profile/photo');
      await refetch();
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    }
  };

  const next = async () => {
    const saved = await save();
    if (!saved) return;
    if (step < steps.length - 1) {
      setStep(step + 1);
      return;
    }
    await finish(saved);
  };

  const finish = async (saved?: MyProfile) => {
    const state = saved ?? data;
    if (!state.completion.complete) {
      toast(`Still needed: ${state.completion.missingRequired.join(', ')}`, 'error');
      return;
    }
    // the gate reads user.profileComplete, so refresh auth before navigating or
    // RequireAuth would bounce straight back here
    await refreshUser();
    toast('Profile complete — welcome aboard', 'success');
    navigate(homeForRole(user?.role), { replace: true });
  };

  const current = steps[step];
  const pct = completion?.percentage ?? 0;

  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <div className="mb-6">
        <p className="text-xs font-semibold uppercase tracking-wide text-brand-600">Welcome</p>
        <h1 className="mt-1 text-2xl font-bold text-slate-900">Complete your profile</h1>
        <p className="mt-1 text-sm text-slate-500">
          {isCandidate
            ? 'A complete profile means you never retype the same details for every application.'
            : 'This is how your team and candidates will see you.'}
        </p>
      </div>

      {/* progress */}
      <div className="card mb-6 p-5">
        <div className="mb-2 flex items-center justify-between text-sm">
          <span className="font-medium text-slate-700">{pct}% complete</span>
          <span className="text-slate-400">Step {step + 1} of {steps.length}</span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-slate-100">
          <div className="h-2 rounded-full bg-brand-500 transition-all" style={{ width: `${pct}%` }} />
        </div>
        <div className="mt-3 flex flex-wrap gap-1.5">
          {steps.map((s, i) => {
            const outstanding = s.covers.some((c) => missing.has(c));
            // only a step that actually owns required fields can be "done"; an
            // all-optional step is neutral, never a green tick it hasn't earned
            const satisfied = s.covers.length > 0 && !outstanding;
            return (
              <button key={s.key} type="button" onClick={() => setStep(i)}
                className={`rounded-full px-2.5 py-1 text-xs ${
                  i === step ? 'bg-brand-600 text-white'
                    : outstanding ? 'bg-amber-50 text-amber-700'
                    : satisfied ? 'bg-green-50 text-green-700'
                    : 'bg-slate-100 text-slate-500'
                }`}>
                {satisfied && i !== step && <Check className="mr-1 inline h-3 w-3" />}
                {s.title}
              </button>
            );
          })}
        </div>
        {completion && completion.missingRequired.length > 0 && (
          <p className="mt-3 text-xs text-amber-700">
            Still required: {completion.missingRequired.join(' · ')}
          </p>
        )}
      </div>

      <div className="card p-6">
        <h2 className="font-semibold text-slate-800">{current.title}</h2>
        <p className="mt-0.5 text-xs text-slate-400">{current.blurb}</p>

        <div className="mt-5 space-y-4">
          {current.key === 'photo' && (
            <div className="flex items-center gap-5">
              <div className="flex h-24 w-24 items-center justify-center overflow-hidden rounded-2xl border border-slate-200 bg-brand-50 text-3xl font-bold text-brand-600">
                <ImageWithFallback src={data.photoUrl} alt="" className="h-full w-full object-cover">
                  <UserIcon className="h-10 w-10 text-brand-300" />
                </ImageWithFallback>
              </div>
              <div className="space-y-2">
                <button type="button" onClick={() => photoInput.current?.click()}
                  disabled={uploading} className="btn-primary">
                  {uploading ? <Spinner className="mr-1.5 h-4 w-4" /> : <Camera className="mr-1.5 h-4 w-4" />}
                  {data.photoUrl ? 'Change photo' : 'Upload photo'}
                </button>
                {data.photoUrl && (
                  <button type="button" onClick={removePhoto} className="btn-ghost block text-xs text-slate-500">
                    <Trash2 className="mr-1 inline h-3.5 w-3.5" /> Remove
                  </button>
                )}
                <p className="text-xs text-slate-400">PNG, JPG or WEBP · up to 5MB</p>
                <input ref={photoInput} type="file" accept="image/png,image/jpeg,image/webp"
                  className="hidden"
                  onChange={(e) => { uploadPhoto(e.target.files?.[0]); e.target.value = ''; }} />
              </div>
            </div>
          )}

          {current.key === 'role' && (
            <>
              <Field label="Full name" required>
                <input className="input" value={String(form.fullName ?? '')}
                  onChange={(e) => set('fullName', e.target.value)} />
              </Field>
              <Field label="Job title" required hint="e.g. Head of Talent, Technical Recruiter">
                <input className="input" value={String(form.jobTitle ?? '')}
                  onChange={(e) => set('jobTitle', e.target.value)} />
              </Field>
              <Field label="Location" required hint="City, country">
                <input className="input" value={String(form.location ?? '')}
                  onChange={(e) => set('location', e.target.value)} />
              </Field>
              <Field label="Department">
                <input className="input" value={String(form.department ?? '')}
                  onChange={(e) => set('department', e.target.value)} />
              </Field>
            </>
          )}

          {current.key === 'about' && (
            <>
              <Field label="Short professional bio">
                <textarea className="input min-h-[110px]" value={String(form.bio ?? '')}
                  onChange={(e) => set('bio', e.target.value)} />
              </Field>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Contact phone">
                  <input className="input" value={String(form.phone ?? '')}
                    onChange={(e) => set('phone', e.target.value)} />
                </Field>
                <Field label="Time zone">
                  <input className="input" value={String(form.timeZone ?? '')}
                    onChange={(e) => set('timeZone', e.target.value)} />
                </Field>
                <Field label="Preferred language">
                  <input className="input" placeholder="en" value={String(form.locale ?? '')}
                    onChange={(e) => set('locale', e.target.value)} />
                </Field>
                <Field label="LinkedIn">
                  <input className="input" value={String(form.linkedinUrl ?? '')}
                    onChange={(e) => set('linkedinUrl', e.target.value)} />
                </Field>
              </div>
              {user?.role === 'RECRUITER' && (
                <>
                  <Field label="Areas of specialization">
                    <TagInput value={(form.specializations as string[]) ?? []}
                      onChange={(v) => set('specializations', v)} placeholder="Add a specialization" />
                  </Field>
                  <Field label="Years of recruiting experience">
                    <input className="input" type="number" min={0} step="0.5"
                      value={String(form.yearsExperience ?? '')}
                      onChange={(e) => set('yearsExperience', e.target.value)} />
                  </Field>
                </>
              )}
            </>
          )}

          {current.key === 'basics' && (
            <>
              <Field label="Full name" required>
                <input className="input" value={String(form.fullName ?? '')}
                  onChange={(e) => set('fullName', e.target.value)} />
              </Field>
              <Field label="Professional headline" required hint="e.g. Senior Backend Engineer">
                <input className="input" value={String(form.headline ?? '')}
                  onChange={(e) => set('headline', e.target.value)} />
              </Field>
              <Field label="Location" required hint="City, country">
                <input className="input" value={String(form.location ?? '')}
                  onChange={(e) => set('location', e.target.value)} />
              </Field>
              <Field label="Professional summary">
                <textarea className="input min-h-[110px]" value={String(form.summary ?? '')}
                  onChange={(e) => set('summary', e.target.value)} />
              </Field>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Phone">
                  <input className="input" value={String(form.phone ?? '')}
                    onChange={(e) => set('phone', e.target.value)} />
                </Field>
                <Field label="Right to work" hint="e.g. EU work permit">
                  <input className="input" value={String(form.workAuthorization ?? '')}
                    onChange={(e) => set('workAuthorization', e.target.value)} />
                </Field>
              </div>
            </>
          )}

          {current.key === 'skills' && (
            <>
              <Field label="Skills" required hint="These are what recruiters search on">
                <TagInput value={(form.skills as string[]) ?? []}
                  onChange={(v) => set('skills', v)} placeholder="Add a skill" />
              </Field>
              <Field label="Preferred job categories">
                <TagInput value={(form.preferredCategories as string[]) ?? []}
                  onChange={(v) => set('preferredCategories', v)} placeholder="e.g. Backend" />
              </Field>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="LinkedIn">
                  <input className="input" value={String(form.linkedinUrl ?? '')}
                    onChange={(e) => set('linkedinUrl', e.target.value)} />
                </Field>
                <Field label="GitHub">
                  <input className="input" value={String(form.githubUrl ?? '')}
                    onChange={(e) => set('githubUrl', e.target.value)} />
                </Field>
              </div>
            </>
          )}

          {current.key === 'preferences' && (
            <>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Preferred work arrangement">
                  <select className="input" value={String(form.workArrangement ?? '')}
                    onChange={(e) => set('workArrangement', e.target.value)}>
                    <option value="">No preference</option>
                    <option value="REMOTE">Remote</option>
                    <option value="HYBRID">Hybrid</option>
                    <option value="ONSITE">Onsite</option>
                    <option value="FLEXIBLE">Flexible</option>
                  </select>
                </Field>
                <Field label="Availability">
                  <select className="input" value={String(form.availability ?? '')}
                    onChange={(e) => set('availability', e.target.value)}>
                    <option value="">Not specified</option>
                    <option value="IMMEDIATE">Immediately</option>
                    <option value="WITHIN_A_MONTH">Within a month</option>
                    <option value="WITHIN_THREE_MONTHS">Within three months</option>
                    <option value="NOT_LOOKING">Not actively looking</option>
                  </select>
                </Field>
                <Field label="Salary expectation from">
                  <input className="input" type="number" min={0} value={String(form.salaryMin ?? '')}
                    onChange={(e) => set('salaryMin', e.target.value)} />
                </Field>
                <Field label="to">
                  <input className="input" type="number" min={0} value={String(form.salaryMax ?? '')}
                    onChange={(e) => set('salaryMax', e.target.value)} />
                </Field>
              </div>
              <Field label="Portfolio or personal site">
                <input className="input" value={String(form.portfolioUrl ?? '')}
                  onChange={(e) => set('portfolioUrl', e.target.value)} />
              </Field>
              <div className="flex items-start gap-2 rounded-lg bg-slate-50 p-3 text-xs text-slate-500">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
                <span>
                  You can optionally share demographic information from your profile settings later.
                  It is stored separately, is never shown to recruiters, and is only ever used for
                  aggregate diversity reporting.
                </span>
              </div>
            </>
          )}
        </div>

        <div className="mt-6 flex items-center justify-between border-t border-slate-100 pt-4">
          <button type="button" onClick={() => setStep(Math.max(0, step - 1))}
            disabled={step === 0} className="btn-ghost disabled:opacity-40">
            <ChevronLeft className="mr-1 h-4 w-4" /> Back
          </button>
          <div className="flex items-center gap-2">
            {completion?.complete && (
              <button type="button" onClick={() => finish()} className="btn-secondary">
                Skip the rest
              </button>
            )}
            <button type="button" onClick={next} disabled={saving} className="btn-primary">
              {saving ? <Spinner className="mr-1.5 h-4 w-4" /> : null}
              {step === steps.length - 1 ? 'Finish' : 'Save and continue'}
              {step < steps.length - 1 && <ChevronRight className="ml-1 h-4 w-4" />}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
