import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/PageHeader';
import { Field, Spinner, PageLoader } from '../../components/ui';
import type { Company } from '../../types';

export default function CompanyProfile() {
  const toast = useToast();
  const [form, setForm] = useState<Partial<Company>>({});
  const [saving, setSaving] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['company', 'my'],
    queryFn: async () => (await api.get<Company>('/companies/my')).data,
  });

  useEffect(() => {
    if (data) setForm(data);
  }, [data]);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!data) return;
    setSaving(true);
    try {
      await api.put(`/companies/${data.id}`, {
        name: form.name,
        industry: form.industry || undefined,
        website: form.website || undefined,
        companySize: form.companySize || undefined,
        location: form.location || undefined,
        description: form.description || undefined,
      });
      toast('Company profile updated', 'success');
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    } finally {
      setSaving(false);
    }
  };

  if (isLoading) return <PageLoader />;

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader title="Company profile" description="This information appears on your public job listings" />
      <form onSubmit={save} className="card space-y-4 p-6">
        <Field label="Company name" required>
          <input className="input" value={form.name || ''} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Industry">
            <input className="input" value={form.industry || ''} onChange={(e) => setForm({ ...form, industry: e.target.value })} />
          </Field>
          <Field label="Company size">
            <select className="input" value={form.companySize || ''} onChange={(e) => setForm({ ...form, companySize: e.target.value })}>
              <option value="">Select…</option>
              <option>1-10</option>
              <option>11-50</option>
              <option>51-200</option>
              <option>201-500</option>
              <option>501-1000</option>
              <option>1000+</option>
            </select>
          </Field>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Website">
            <input className="input" value={form.website || ''} onChange={(e) => setForm({ ...form, website: e.target.value })} placeholder="https://" />
          </Field>
          <Field label="Location">
            <input className="input" value={form.location || ''} onChange={(e) => setForm({ ...form, location: e.target.value })} placeholder="City, Country" />
          </Field>
        </div>
        <Field label="About">
          <textarea className="input min-h-28" value={form.description || ''} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </Field>
        <div className="flex justify-end">
          <button className="btn-primary" disabled={saving}>
            {saving && <Spinner className="h-4 w-4" />}
            Save changes
          </button>
        </div>
      </form>
    </div>
  );
}
