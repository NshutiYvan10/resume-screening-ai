import { useState } from 'react';
import { Link } from 'react-router-dom';
import { MailCheck } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import { Field, Spinner } from '../../components/ui';
import AuthShell from './AuthShell';

export default function ForgotPassword() {
  const toast = useToast();
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      await api.post('/auth/forgot-password', { email: email.trim() });
      setSent(true);
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell title="Reset your password" subtitle="We'll email you a secure reset link">
      {sent ? (
        <div className="rounded-xl border border-green-200 bg-green-50 p-6 text-center">
          <MailCheck className="mx-auto h-12 w-12 text-green-600" />
          <p className="mt-3 text-sm text-slate-700">
            If an account exists for <strong>{email}</strong>, a reset link is on its way. Check your inbox.
          </p>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <Field label="Email" required>
            <input
              type="email"
              className="input"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoFocus
            />
          </Field>
          <button type="submit" className="btn-primary w-full" disabled={submitting}>
            {submitting && <Spinner className="h-4 w-4" />}
            Send reset link
          </button>
        </form>
      )}
      <p className="mt-6 text-center text-sm text-slate-500">
        <Link to="/login" className="font-medium text-brand-600 hover:text-brand-700">
          Back to sign in
        </Link>
      </p>
    </AuthShell>
  );
}
