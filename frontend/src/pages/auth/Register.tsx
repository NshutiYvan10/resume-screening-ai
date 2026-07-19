import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2 } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import { Field, Spinner } from '../../components/ui';
import AuthShell from './AuthShell';

export default function Register() {
  const toast = useToast();
  const [searchParams] = useSearchParams();
  const redirect = searchParams.get('redirect');
  // preserve the post-apply deep link through email verification -> sign in
  const loginTo = redirect && redirect.startsWith('/')
    ? `/login?redirect=${encodeURIComponent(redirect)}`
    : '/login';
  const [form, setForm] = useState({ fullName: '', email: '', password: '', phone: '' });
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const update = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post('/auth/register', {
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        phone: form.phone.trim() || undefined,
      });
      setDone(true);
    } catch (err) {
      toast(apiErrorMessage(err, 'Registration failed'), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return (
      <AuthShell title="Check your inbox">
        <div className="rounded-xl border border-green-200 bg-green-50 p-6 text-center">
          <CheckCircle2 className="mx-auto h-12 w-12 text-green-600" />
          <p className="mt-3 text-sm text-slate-700">
            We've sent a verification link to <strong>{form.email}</strong>. Click it to activate your
            account, then sign in.
          </p>
        </div>
        <Link to={loginTo} className="btn-primary mt-6 w-full">
          Back to sign in
        </Link>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Create your account" subtitle="Apply to jobs and track your applications">
      <form onSubmit={handleSubmit} className="space-y-4">
        <Field label="Full name" required>
          <input className="input" value={form.fullName} onChange={update('fullName')} required autoFocus />
        </Field>
        <Field label="Email" required>
          <input type="email" className="input" value={form.email} onChange={update('email')} required />
        </Field>
        <Field label="Phone" hint="Optional">
          <input className="input" value={form.phone} onChange={update('phone')} />
        </Field>
        <Field label="Password" required hint="At least 8 characters">
          <input
            type="password"
            className="input"
            value={form.password}
            onChange={update('password')}
            minLength={8}
            required
          />
        </Field>
        <button type="submit" className="btn-primary w-full" disabled={submitting}>
          {submitting && <Spinner className="h-4 w-4" />}
          Create account
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        Already have an account?{' '}
        <Link to={loginTo} className="font-medium text-brand-600 hover:text-brand-700">
          Sign in
        </Link>
      </p>
    </AuthShell>
  );
}
