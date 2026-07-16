import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Plus, Trash2, ArrowLeft, Info } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import { Field, Spinner, PageLoader } from '../../components/ui';
import type { EducationLevel, EmploymentType, Job, Qualification, WorkMode } from '../../types';

interface FormState {
  title: string;
  department: string;
  location: string;
  employmentType: EmploymentType;
  workMode: WorkMode;
  description: string;
  responsibilities: string;
  minExperienceYears: string;
  educationLevel: EducationLevel | '';
  salaryMin: string;
  salaryMax: string;
  salaryCurrency: string;
  deadline: string;
  qualifications: Qualification[];
}

const EMPTY: FormState = {
  title: '', department: '', location: '', employmentType: 'FULL_TIME', workMode: 'ONSITE',
  description: '', responsibilities: '', minExperienceYears: '', educationLevel: '',
  salaryMin: '', salaryMax: '', salaryCurrency: 'USD', deadline: '',
  qualifications: [{ skill: '', weight: 1, required: true }],
};

export default function JobEditor() {
  const { jobId } = useParams();
  const isEdit = !!jobId;
  const navigate = useNavigate();
  const toast = useToast();
  const [form, setForm] = useState<FormState>(EMPTY);
  const [saving, setSaving] = useState(false);

  const { data: existing, isLoading } = useQuery({
    queryKey: ['job', jobId],
    queryFn: async () => (await api.get<Job>(`/jobs/${jobId}`)).data,
    enabled: isEdit,
  });

  useEffect(() => {
    if (existing) {
      setForm({
        title: existing.title,
        department: existing.department || '',
        location: existing.location || '',
        employmentType: existing.employmentType,
        workMode: existing.workMode,
        description: existing.description,
        responsibilities: existing.responsibilities || '',
        minExperienceYears: existing.minExperienceYears?.toString() || '',
        educationLevel: existing.educationLevel || '',
        salaryMin: existing.salaryMin?.toString() || '',
        salaryMax: existing.salaryMax?.toString() || '',
        salaryCurrency: existing.salaryCurrency || 'USD',
        deadline: existing.deadline || '',
        qualifications: existing.qualifications.length
          ? existing.qualifications.map((q) => ({ skill: q.skill, weight: q.weight, required: q.required }))
          : EMPTY.qualifications,
      });
    }
  }, [existing]);

  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setForm((f) => ({ ...f, [k]: v }));

  const updateQual = (idx: number, patch: Partial<Qualification>) =>
    setForm((f) => ({
      ...f,
      qualifications: f.qualifications.map((q, i) => (i === idx ? { ...q, ...patch } : q)),
    }));

  const addQual = () =>
    setForm((f) => ({ ...f, qualifications: [...f.qualifications, { skill: '', weight: 1, required: false }] }));

  const removeQual = (idx: number) =>
    setForm((f) => ({ ...f, qualifications: f.qualifications.filter((_, i) => i !== idx) }));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const quals = form.qualifications.filter((q) => q.skill.trim());
    if (!quals.length) {
      toast('Add at least one qualification for AI scoring', 'error');
      return;
    }
    setSaving(true);
    const payload = {
      title: form.title.trim(),
      department: form.department.trim() || undefined,
      location: form.location.trim() || undefined,
      employmentType: form.employmentType,
      workMode: form.workMode,
      description: form.description.trim(),
      responsibilities: form.responsibilities.trim() || undefined,
      minExperienceYears: form.minExperienceYears ? Number(form.minExperienceYears) : undefined,
      educationLevel: form.educationLevel || undefined,
      salaryMin: form.salaryMin ? Number(form.salaryMin) : undefined,
      salaryMax: form.salaryMax ? Number(form.salaryMax) : undefined,
      salaryCurrency: form.salaryCurrency,
      deadline: form.deadline || undefined,
      qualifications: quals.map((q) => ({ skill: q.skill.trim(), weight: Number(q.weight), required: q.required })),
    };
    try {
      if (isEdit) {
        await api.put(`/jobs/${jobId}`, payload);
        toast('Job updated', 'success');
      } else {
        await api.post('/jobs', payload);
        toast('Job created as draft', 'success');
      }
      navigate('/company/jobs');
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    } finally {
      setSaving(false);
    }
  };

  if (isEdit && isLoading) return <PageLoader />;

  return (
    <div className="mx-auto max-w-3xl">
      <button
        onClick={() => navigate('/company/jobs')}
        className="mb-4 flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="h-4 w-4" /> Back to jobs
      </button>
      <h1 className="text-2xl font-bold text-slate-900">{isEdit ? 'Edit job' : 'Post a new job'}</h1>
      <p className="mt-1 text-sm text-slate-500">
        New jobs are saved as drafts. Publish when you're ready to accept applications.
      </p>

      <form onSubmit={submit} className="mt-6 space-y-6">
        <div className="card space-y-4 p-6">
          <h2 className="font-semibold text-slate-800">Basics</h2>
          <Field label="Job title" required>
            <input className="input" value={form.title} onChange={(e) => set('title', e.target.value)} required />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Department">
              <input className="input" value={form.department} onChange={(e) => set('department', e.target.value)} />
            </Field>
            <Field label="Location">
              <input className="input" value={form.location} onChange={(e) => set('location', e.target.value)}
                placeholder="City, Country" />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Employment type" required>
              <select className="input" value={form.employmentType}
                onChange={(e) => set('employmentType', e.target.value as EmploymentType)}>
                <option value="FULL_TIME">Full-time</option>
                <option value="PART_TIME">Part-time</option>
                <option value="CONTRACT">Contract</option>
                <option value="INTERNSHIP">Internship</option>
              </select>
            </Field>
            <Field label="Work mode" required>
              <select className="input" value={form.workMode}
                onChange={(e) => set('workMode', e.target.value as WorkMode)}>
                <option value="ONSITE">On-site</option>
                <option value="REMOTE">Remote</option>
                <option value="HYBRID">Hybrid</option>
              </select>
            </Field>
          </div>
        </div>

        <div className="card space-y-4 p-6">
          <h2 className="font-semibold text-slate-800">Description</h2>
          <Field label="Job description" required>
            <textarea className="input min-h-32" value={form.description}
              onChange={(e) => set('description', e.target.value)} required
              placeholder="Describe the role, the team, and what success looks like…" />
          </Field>
          <Field label="Key responsibilities">
            <textarea className="input min-h-24" value={form.responsibilities}
              onChange={(e) => set('responsibilities', e.target.value)} />
          </Field>
        </div>

        <div className="card space-y-4 p-6">
          <h2 className="font-semibold text-slate-800">Requirements</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Minimum experience (years)">
              <input type="number" min="0" step="0.5" className="input" value={form.minExperienceYears}
                onChange={(e) => set('minExperienceYears', e.target.value)} />
            </Field>
            <Field label="Minimum education">
              <select className="input" value={form.educationLevel}
                onChange={(e) => set('educationLevel', e.target.value as EducationLevel | '')}>
                <option value="">Any</option>
                <option value="CERTIFICATE">Certificate</option>
                <option value="DIPLOMA">Diploma</option>
                <option value="BACHELORS">Bachelor's</option>
                <option value="MASTERS">Master's</option>
                <option value="PHD">PhD</option>
              </select>
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Salary min">
              <input type="number" min="0" className="input" value={form.salaryMin}
                onChange={(e) => set('salaryMin', e.target.value)} />
            </Field>
            <Field label="Salary max">
              <input type="number" min="0" className="input" value={form.salaryMax}
                onChange={(e) => set('salaryMax', e.target.value)} />
            </Field>
            <Field label="Currency">
              <input className="input" value={form.salaryCurrency}
                onChange={(e) => set('salaryCurrency', e.target.value)} maxLength={10} />
            </Field>
          </div>
          <Field label="Application deadline" required
            hint="Every posting needs an end date — it closes automatically when this passes">
            <input type="date" className="input" value={form.deadline}
              min={new Date().toISOString().split('T')[0]}
              onChange={(e) => set('deadline', e.target.value)} required />
          </Field>
        </div>

        <div className="card space-y-4 p-6">
          <div>
            <h2 className="font-semibold text-slate-800">AI scoring matrix</h2>
            <div className="mt-1.5 flex items-start gap-1.5 rounded-lg bg-brand-50 p-3 text-xs text-brand-700">
              <Info className="h-4 w-4 shrink-0 mt-0.5" />
              <span>
                Each skill's <strong>weight</strong> (0.1–10) sets its relative importance in the AI match score.
                Mark a skill <strong>required</strong> to penalize candidates who lack it.
              </span>
            </div>
          </div>

          <div className="space-y-2">
            <div className="hidden grid-cols-12 gap-2 px-1 text-xs font-medium text-slate-400 sm:grid">
              <div className="col-span-6">Skill / qualification</div>
              <div className="col-span-3">Weight</div>
              <div className="col-span-2">Required</div>
              <div className="col-span-1"></div>
            </div>
            {form.qualifications.map((q, idx) => (
              <div key={idx} className="grid grid-cols-12 items-center gap-2">
                <input
                  className="input col-span-6"
                  placeholder="e.g. React, Python, Project Management"
                  value={q.skill}
                  onChange={(e) => updateQual(idx, { skill: e.target.value })}
                />
                <input
                  type="number" min="0.1" max="10" step="0.1"
                  className="input col-span-3"
                  value={q.weight}
                  onChange={(e) => updateQual(idx, { weight: Number(e.target.value) })}
                />
                <label className="col-span-2 flex items-center gap-2 text-sm text-slate-600">
                  <input
                    type="checkbox"
                    className="h-4 w-4 rounded border-slate-300 text-brand-600"
                    checked={q.required}
                    onChange={(e) => updateQual(idx, { required: e.target.checked })}
                  />
                  Required
                </label>
                <button
                  type="button"
                  onClick={() => removeQual(idx)}
                  className="col-span-1 flex justify-center text-slate-400 hover:text-red-500"
                  disabled={form.qualifications.length === 1}
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            ))}
          </div>
          <button type="button" onClick={addQual} className="btn-secondary">
            <Plus className="h-4 w-4" /> Add qualification
          </button>
        </div>

        <div className="flex justify-end gap-2">
          <button type="button" className="btn-secondary" onClick={() => navigate('/company/jobs')}>
            Cancel
          </button>
          <button type="submit" className="btn-primary" disabled={saving}>
            {saving && <Spinner className="h-4 w-4" />}
            {isEdit ? 'Save changes' : 'Create draft'}
          </button>
        </div>
      </form>
    </div>
  );
}
