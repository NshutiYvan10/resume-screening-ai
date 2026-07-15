import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, XCircle } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { Spinner } from '../../components/ui';
import AuthShell from './AuthShell';

export default function VerifyEmail() {
  const [params] = useSearchParams();
  const token = params.get('token') || '';
  const [state, setState] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('');
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;
    if (!token) {
      setState('error');
      setMessage('This verification link is invalid.');
      return;
    }
    (async () => {
      try {
        const { data } = await api.post<{ message: string }>('/auth/verify-email', { token });
        setState('success');
        setMessage(data.message);
      } catch (err) {
        setState('error');
        setMessage(apiErrorMessage(err, 'Verification failed'));
      }
    })();
  }, [token]);

  return (
    <AuthShell title="Email verification">
      <div className="rounded-xl border border-slate-200 bg-white p-8 text-center">
        {state === 'loading' && (
          <>
            <Spinner className="mx-auto h-10 w-10 text-brand-600" />
            <p className="mt-4 text-sm text-slate-600">Verifying your email…</p>
          </>
        )}
        {state === 'success' && (
          <>
            <CheckCircle2 className="mx-auto h-12 w-12 text-green-600" />
            <p className="mt-3 text-sm text-slate-700">{message}</p>
            <Link to="/login" className="btn-primary mt-6 w-full">
              Sign in
            </Link>
          </>
        )}
        {state === 'error' && (
          <>
            <XCircle className="mx-auto h-12 w-12 text-red-500" />
            <p className="mt-3 text-sm text-slate-700">{message}</p>
            <Link to="/login" className="btn-secondary mt-6 w-full">
              Back to sign in
            </Link>
          </>
        )}
      </div>
    </AuthShell>
  );
}
