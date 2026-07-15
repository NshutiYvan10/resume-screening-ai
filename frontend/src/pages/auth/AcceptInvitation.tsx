import { useEffect, useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { XCircle } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { homeForRole } from '../../components/RouteGuards';
import { Field, Spinner, PageLoader } from '../../components/ui';
import { humanize } from '../../lib/format';
import type { AuthResponse, PublicInvitation } from '../../types';
import AuthShell from './AuthShell';

export default function AcceptInvitation() {
  const [params] = useSearchParams();
  const token = params.get('token') || '';
  const { applySession } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();

  const [invitation, setInvitation] = useState<PublicInvitation | null>(null);
  const [loadError, setLoadError] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const [account, setAccount] = useState({ fullName: '', password: '', confirm: '', phone: '' });
  const [company, setCompany] = useState({
    name: '', industry: '', website: '', companySize: '', location: '', description: '',
  });

  useEffect(() => {
    if (!token) {
      setLoadError('This invitation link is invalid.');
      setLoading(false);
      return;
    }
    (async () => {
      try {
        const { data } = await api.get<PublicInvitation>(`/invitations/public/${token}`);
        setInvitation(data);
        if (data.companyName) setCompany((c) => ({ ...c, name: data.companyName || '' }));
      } catch (err) {
        setLoadError(apiErrorMessage(err, 'Invitation not found'));
      } finally {
        setLoading(false);
      }
    })();
  }, [token]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (account.password !== account.confirm) {
      toast('Passwords do not match', 'error');
      return;
    }
    setSubmitting(true);
    try {
      let auth: AuthResponse;
      if (invitation!.type === 'COMPANY') {
        const { data } = await api.post<AuthResponse>('/invitations/accept-company', {
          token,
          company: {
            name: company.name,
            industry: company.industry || undefined,
            website: company.website || undefined,
            companySize: company.companySize || undefined,
            location: company.location || undefined,
            description: company.description || undefined,
          },
          admin: {
            fullName: account.fullName,
            password: account.password,
            phone: account.phone || undefined,
          },
        });
        auth = data;
      } else {
        const { data } = await api.post<AuthResponse>('/invitations/accept-team', {
          token,
          fullName: account.fullName,
          password: account.password,
          phone: account.phone || undefined,
        });
        auth = data;
      }
      applySession(auth);
      toast('Welcome to ResumeAI!', 'success');
      navigate(homeForRole(auth.user.role), { replace: true });
    } catch (err) {
      toast(apiErrorMessage(err, 'Could not accept invitation'), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <PageLoader />;

  if (loadError || !invitation) {
    return (
      <AuthShell title="Invitation problem">
        <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center">
          <XCircle className="mx-auto h-12 w-12 text-red-500" />
          <p className="mt-3 text-sm text-slate-700">{loadError || 'Invitation not found'}</p>
        </div>
        <Link to="/login" className="btn-secondary mt-6 w-full">Back to sign in</Link>
      </AuthShell>
    );
  }

  if (invitation.status !== 'PENDING' || invitation.expired) {
    const reason =
      invitation.status === 'ACCEPTED'
        ? 'This invitation has already been accepted.'
        : invitation.status === 'REVOKED'
          ? 'This invitation has been revoked.'
          : 'This invitation has expired.';
    return (
      <AuthShell title="Invitation unavailable">
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-6 text-center">
          <p className="text-sm text-slate-700">{reason}</p>
          <p className="mt-2 text-xs text-slate-500">
            Please ask your administrator to send a new invitation.
          </p>
        </div>
        <Link to="/login" className="btn-secondary mt-6 w-full">Back to sign in</Link>
      </AuthShell>
    );
  }

  const isCompany = invitation.type === 'COMPANY';

  return (
    <AuthShell
      title={isCompany ? 'Set up your company' : 'Complete your account'}
      subtitle={
        isCompany
          ? `You're invited to onboard your organization as its administrator.`
          : `You've been invited to join ${invitation.companyName} as a ${humanize(invitation.role)}.`
      }
    >
      <form onSubmit={submit} className="space-y-5">
        <div className="rounded-lg bg-slate-50 px-4 py-3 text-sm">
          <span className="text-slate-500">Invited email: </span>
          <span className="font-medium text-slate-800">{invitation.email}</span>
        </div>

        {isCompany && (
          <div className="space-y-4">
            <h3 className="text-sm font-semibold text-slate-700">Company details</h3>
            <Field label="Company name" required>
              <input
                className="input"
                value={company.name}
                onChange={(e) => setCompany({ ...company, name: e.target.value })}
                required
              />
            </Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Industry">
                <input
                  className="input"
                  value={company.industry}
                  onChange={(e) => setCompany({ ...company, industry: e.target.value })}
                  placeholder="e.g. Technology"
                />
              </Field>
              <Field label="Company size">
                <select
                  className="input"
                  value={company.companySize}
                  onChange={(e) => setCompany({ ...company, companySize: e.target.value })}
                >
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
            <div className="grid grid-cols-2 gap-3">
              <Field label="Website">
                <input
                  className="input"
                  value={company.website}
                  onChange={(e) => setCompany({ ...company, website: e.target.value })}
                  placeholder="https://"
                />
              </Field>
              <Field label="Location">
                <input
                  className="input"
                  value={company.location}
                  onChange={(e) => setCompany({ ...company, location: e.target.value })}
                  placeholder="City, Country"
                />
              </Field>
            </div>
            <Field label="About the company">
              <textarea
                className="input min-h-20"
                value={company.description}
                onChange={(e) => setCompany({ ...company, description: e.target.value })}
              />
            </Field>
          </div>
        )}

        <div className="space-y-4">
          <h3 className="text-sm font-semibold text-slate-700">Your account</h3>
          <Field label="Full name" required>
            <input
              className="input"
              value={account.fullName}
              onChange={(e) => setAccount({ ...account, fullName: e.target.value })}
              required
            />
          </Field>
          <Field label="Phone" hint="Optional">
            <input
              className="input"
              value={account.phone}
              onChange={(e) => setAccount({ ...account, phone: e.target.value })}
            />
          </Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Password" required hint="Min 8 chars">
              <input
                type="password"
                className="input"
                value={account.password}
                onChange={(e) => setAccount({ ...account, password: e.target.value })}
                minLength={8}
                required
              />
            </Field>
            <Field label="Confirm" required>
              <input
                type="password"
                className="input"
                value={account.confirm}
                onChange={(e) => setAccount({ ...account, confirm: e.target.value })}
                minLength={8}
                required
              />
            </Field>
          </div>
        </div>

        <button type="submit" className="btn-primary w-full" disabled={submitting}>
          {submitting && <Spinner className="h-4 w-4" />}
          {isCompany ? 'Create company & account' : 'Create account'}
        </button>
      </form>
    </AuthShell>
  );
}
