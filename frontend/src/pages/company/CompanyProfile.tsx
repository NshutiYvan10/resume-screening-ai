import { useEffect, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Camera, ImagePlus, Trash2, Building2, Eye } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/PageHeader';
import TagInput from '../../components/TagInput';
import { Field, Spinner, PageLoader } from '../../components/ui';
import type { Company } from '../../types';

interface FormState {
  name: string;
  industry: string;
  website: string;
  companySize: string;
  location: string;
  description: string;
  tagline: string;
  foundedYear: string;
  mission: string;
  values: string[];
  benefits: string[];
  linkedinUrl: string;
  twitterUrl: string;
}

export default function CompanyProfile() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<FormState | null>(null);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState<'logo' | 'cover' | 'photo' | null>(null);
  const logoInput = useRef<HTMLInputElement>(null);
  const coverInput = useRef<HTMLInputElement>(null);
  const photoInput = useRef<HTMLInputElement>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['company', 'my'],
    queryFn: async () => (await api.get<Company>('/companies/my')).data,
  });

  useEffect(() => {
    if (data && !form) {
      setForm({
        name: data.name || '',
        industry: data.industry || '',
        website: data.website || '',
        companySize: data.companySize || '',
        location: data.location || '',
        description: data.description || '',
        tagline: data.tagline || '',
        foundedYear: data.foundedYear?.toString() || '',
        mission: data.mission || '',
        values: data.values || [],
        benefits: data.benefits || [],
        linkedinUrl: data.linkedinUrl || '',
        twitterUrl: data.twitterUrl || '',
      });
    }
  }, [data, form]);

  const set = <K extends keyof FormState>(k: K, v: FormState[K]) =>
    setForm((f) => (f ? { ...f, [k]: v } : f));

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['company', 'my'] });

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!data || !form) return;
    setSaving(true);
    try {
      await api.put(`/companies/${data.id}`, {
        name: form.name.trim(),
        industry: form.industry.trim() || undefined,
        website: form.website.trim() || undefined,
        companySize: form.companySize || undefined,
        location: form.location.trim() || undefined,
        description: form.description.trim() || undefined,
        tagline: form.tagline.trim() || undefined,
        foundedYear: form.foundedYear ? Number(form.foundedYear) : undefined,
        mission: form.mission.trim() || undefined,
        values: form.values,
        benefits: form.benefits,
        linkedinUrl: form.linkedinUrl.trim() || undefined,
        twitterUrl: form.twitterUrl.trim() || undefined,
      });
      toast('Company profile updated', 'success');
      refresh();
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    } finally {
      setSaving(false);
    }
  };

  const upload = async (kind: 'logo' | 'cover' | 'photo', file: File | undefined | null) => {
    if (!data || !file) return;
    setUploading(kind);
    const fd = new FormData();
    fd.append('file', file);
    const endpoint = kind === 'photo' ? `/companies/${data.id}/photos` : `/companies/${data.id}/${kind}`;
    try {
      await api.post(endpoint, fd, { headers: { 'Content-Type': 'multipart/form-data' } });
      toast(kind === 'photo' ? 'Photo added to gallery' : `Company ${kind} updated`, 'success');
      refresh();
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    } finally {
      setUploading(null);
    }
  };

  const deletePhoto = async (photoId: string) => {
    if (!data) return;
    try {
      await api.delete(`/companies/${data.id}/photos/${photoId}`);
      toast('Photo removed', 'success');
      refresh();
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    }
  };

  if (isLoading || !data || !form) return <PageLoader />;

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="Company profile"
        description="This is your public employer page — everything here is shown to candidates"
      />

      {/* ---------- branding ---------- */}
      <div className="card overflow-hidden">
        <div className="relative h-40 bg-gradient-to-r from-slate-800 via-brand-800 to-brand-600 sm:h-48">
          {data.coverUrl && (
            <img src={data.coverUrl} alt="Cover" className="h-full w-full object-cover" />
          )}
          <button
            type="button"
            onClick={() => coverInput.current?.click()}
            disabled={uploading === 'cover'}
            className="absolute right-3 top-3 flex items-center gap-1.5 rounded-lg bg-black/50 px-3 py-1.5 text-xs font-medium text-white backdrop-blur hover:bg-black/70"
          >
            {uploading === 'cover' ? <Spinner className="h-3.5 w-3.5" /> : <Camera className="h-3.5 w-3.5" />}
            {data.coverUrl ? 'Change cover' : 'Add cover photo'}
          </button>
          <input
            ref={coverInput} type="file" accept="image/png,image/jpeg,image/webp" className="hidden"
            onChange={(e) => { upload('cover', e.target.files?.[0]); e.target.value = ''; }}
          />
        </div>
        <div className="flex items-end gap-4 px-6 pb-5">
          <div className="relative -mt-10">
            <div className="flex h-20 w-20 items-center justify-center overflow-hidden rounded-2xl border-4 border-white bg-brand-100 text-2xl font-bold text-brand-700 shadow">
              {data.logoUrl ? (
                <img src={data.logoUrl} alt="Logo" className="h-full w-full object-cover" />
              ) : (
                data.name.charAt(0).toUpperCase()
              )}
            </div>
            <button
              type="button"
              onClick={() => logoInput.current?.click()}
              disabled={uploading === 'logo'}
              className="absolute -bottom-1.5 -right-1.5 flex h-7 w-7 items-center justify-center rounded-full bg-brand-600 text-white shadow hover:bg-brand-700"
              title="Change logo"
            >
              {uploading === 'logo' ? <Spinner className="h-3.5 w-3.5" /> : <Camera className="h-3.5 w-3.5" />}
            </button>
            <input
              ref={logoInput} type="file" accept="image/png,image/jpeg,image/webp" className="hidden"
              onChange={(e) => { upload('logo', e.target.files?.[0]); e.target.value = ''; }}
            />
          </div>
          <div className="pb-1">
            <p className="font-semibold text-slate-800">{data.name}</p>
            <p className="text-xs text-slate-400">Logo & cover appear on your public profile and job listings</p>
          </div>
        </div>
      </div>

      {/* ---------- gallery ---------- */}
      <div className="mt-5 card p-6">
        <div className="mb-4 flex items-center justify-between">
          <div>
            <h2 className="font-semibold text-slate-800">Life at {data.name}</h2>
            <p className="text-xs text-slate-400">Photos of your office, team and events — up to 12</p>
          </div>
          <button
            type="button"
            className="btn-secondary"
            onClick={() => photoInput.current?.click()}
            disabled={uploading === 'photo' || (data.photos?.length || 0) >= 12}
          >
            {uploading === 'photo' ? <Spinner className="h-4 w-4" /> : <ImagePlus className="h-4 w-4" />}
            Add photo
          </button>
          <input
            ref={photoInput} type="file" accept="image/png,image/jpeg,image/webp" className="hidden"
            onChange={(e) => { upload('photo', e.target.files?.[0]); e.target.value = ''; }}
          />
        </div>
        {!data.photos?.length ? (
          <div className="rounded-lg border border-dashed border-slate-300 py-10 text-center text-sm text-slate-400">
            No photos yet — bring your workplace to life for candidates
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {data.photos.map((p) => (
              <div key={p.id} className="group relative aspect-[4/3] overflow-hidden rounded-lg bg-slate-100">
                <img src={p.url} alt={p.caption || 'Company photo'} className="h-full w-full object-cover" />
                <button
                  type="button"
                  onClick={() => deletePhoto(p.id)}
                  className="absolute right-2 top-2 hidden h-7 w-7 items-center justify-center rounded-full bg-black/60 text-white hover:bg-red-600 group-hover:flex"
                  title="Remove photo"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ---------- details form ---------- */}
      <form onSubmit={save} className="mt-5 space-y-5">
        <div className="card space-y-4 p-6">
          <h2 className="font-semibold text-slate-800">Basics</h2>
          <Field label="Company name" required>
            <input className="input" value={form.name} onChange={(e) => set('name', e.target.value)} required />
          </Field>
          <Field label="Tagline" hint="One line that captures what you do — shown under your name">
            <input className="input" value={form.tagline} maxLength={200}
              placeholder="e.g. Building the tools that power modern hiring"
              onChange={(e) => set('tagline', e.target.value)} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Industry">
              <input className="input" value={form.industry} onChange={(e) => set('industry', e.target.value)} />
            </Field>
            <Field label="Founded year">
              <input type="number" min={1800} max={2100} className="input" value={form.foundedYear}
                onChange={(e) => set('foundedYear', e.target.value)} />
            </Field>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Company size">
              <select className="input" value={form.companySize} onChange={(e) => set('companySize', e.target.value)}>
                <option value="">Select…</option>
                <option>1-10</option>
                <option>11-50</option>
                <option>51-200</option>
                <option>201-500</option>
                <option>501-1000</option>
                <option>1000+</option>
              </select>
            </Field>
            <Field label="Location">
              <input className="input" value={form.location} placeholder="City, Country"
                onChange={(e) => set('location', e.target.value)} />
            </Field>
          </div>
        </div>

        <div className="card space-y-4 p-6">
          <h2 className="font-semibold text-slate-800">Your story</h2>
          <Field label="About the company" hint="What you do, who you serve, what makes you different">
            <textarea className="input min-h-32" value={form.description}
              onChange={(e) => set('description', e.target.value)} />
          </Field>
          <Field label="Mission" hint="Why your company exists">
            <textarea className="input min-h-20" value={form.mission}
              onChange={(e) => set('mission', e.target.value)} />
          </Field>
          <Field label="Company values" hint="e.g. Ownership, Transparency, Customer obsession">
            <TagInput value={form.values} onChange={(v) => set('values', v)} maxTags={12}
              placeholder="Type a value and press Enter" />
          </Field>
          <Field label="Benefits & perks" hint="e.g. Health insurance, Remote-friendly, Learning budget">
            <TagInput value={form.benefits} onChange={(v) => set('benefits', v)} maxTags={20}
              placeholder="Type a benefit and press Enter" />
          </Field>
        </div>

        <div className="card space-y-4 p-6">
          <h2 className="font-semibold text-slate-800">Links</h2>
          <Field label="Website">
            <input className="input" value={form.website} placeholder="https://"
              onChange={(e) => set('website', e.target.value)} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="LinkedIn">
              <input className="input" value={form.linkedinUrl} placeholder="https://linkedin.com/company/…"
                onChange={(e) => set('linkedinUrl', e.target.value)} />
            </Field>
            <Field label="X / Twitter">
              <input className="input" value={form.twitterUrl} placeholder="https://x.com/…"
                onChange={(e) => set('twitterUrl', e.target.value)} />
            </Field>
          </div>
        </div>

        <div className="flex items-center justify-between">
          <p className="flex items-center gap-1.5 text-xs text-slate-400">
            <Eye className="h-3.5 w-3.5" /> Candidates see this page when they view your jobs
          </p>
          <button className="btn-primary" disabled={saving}>
            {saving && <Spinner className="h-4 w-4" />}
            Save changes
          </button>
        </div>
      </form>
    </div>
  );
}
