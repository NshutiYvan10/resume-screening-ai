import { useState } from 'react';
import { Link, useNavigate, useLocation, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { apiErrorMessage } from '../../lib/api';
import { homeForRole } from '../../components/RouteGuards';
import { Field, Spinner } from '../../components/ui';
import AuthShell from './AuthShell';

export default function Login() {
  const { login } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const user = await login(email.trim(), password);
      // return the user where they came from: RequireAuth's `from`, then an explicit
      // ?redirect= (e.g. from a public job's "sign in to apply"), else their home.
      const from = (location.state as { from?: string })?.from;
      const redirect = searchParams.get('redirect');
      const safeRedirect = redirect && redirect.startsWith('/') ? redirect : null;
      const dest = (from && from !== '/login') ? from : (safeRedirect || homeForRole(user.role));
      navigate(dest, { replace: true });
    } catch (err) {
      toast(apiErrorMessage(err, 'Unable to sign in'), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell title="Welcome back" subtitle="Sign in to your ResumeAI account">
      <form onSubmit={handleSubmit} className="space-y-4">
        <Field label="Email" required>
          <input
            type="email"
            className="input"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@company.com"
            required
            autoFocus
          />
        </Field>
        <Field label="Password" required>
          <input
            type="password"
            className="input"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            required
          />
        </Field>
        <div className="flex justify-end">
          <Link to="/forgot-password" className="text-sm font-medium text-brand-600 hover:text-brand-700">
            Forgot password?
          </Link>
        </div>
        <button type="submit" className="btn-primary w-full" disabled={submitting}>
          {submitting && <Spinner className="h-4 w-4" />}
          Sign in
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-slate-500">
        Looking for a job?{' '}
        <Link
          to={(() => { const r = searchParams.get('redirect'); return r && r.startsWith('/') ? `/register?redirect=${encodeURIComponent(r)}` : '/register'; })()}
          className="font-medium text-brand-600 hover:text-brand-700"
        >
          Create a candidate account
        </Link>
      </p>
    </AuthShell>
  );
}
