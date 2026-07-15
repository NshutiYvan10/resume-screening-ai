import { useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import { Field, Spinner } from '../../components/ui';
import AuthShell from './AuthShell';

export default function ResetPassword() {
  const [params] = useSearchParams();
  const token = params.get('token') || '';
  const toast = useToast();
  const navigate = useNavigate();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password !== confirm) {
      toast('Passwords do not match', 'error');
      return;
    }
    setSubmitting(true);
    try {
      await api.post('/auth/reset-password', { token, newPassword: password });
      toast('Password reset. Please sign in.', 'success');
      navigate('/login', { replace: true });
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  if (!token) {
    return (
      <AuthShell title="Invalid link">
        <p className="text-sm text-slate-600">
          This password reset link is invalid or incomplete.
        </p>
        <Link to="/forgot-password" className="btn-primary mt-6 w-full">
          Request a new link
        </Link>
      </AuthShell>
    );
  }

  return (
    <AuthShell title="Choose a new password" subtitle="Enter and confirm your new password">
      <form onSubmit={handleSubmit} className="space-y-4">
        <Field label="New password" required hint="At least 8 characters">
          <input
            type="password"
            className="input"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={8}
            required
            autoFocus
          />
        </Field>
        <Field label="Confirm password" required>
          <input
            type="password"
            className="input"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            minLength={8}
            required
          />
        </Field>
        <button type="submit" className="btn-primary w-full" disabled={submitting}>
          {submitting && <Spinner className="h-4 w-4" />}
          Reset password
        </button>
      </form>
    </AuthShell>
  );
}
