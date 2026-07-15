import { useState } from 'react';
import { api, apiErrorMessage } from '../lib/api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { Field, Spinner } from '../components/ui';
import { humanize } from '../lib/format';

export default function Settings() {
  const { user, setUser } = useAuth();
  const toast = useToast();
  const [profile, setProfile] = useState({ fullName: user?.fullName || '', phone: user?.phone || '' });
  const [pwd, setPwd] = useState({ currentPassword: '', newPassword: '', confirm: '' });
  const [savingProfile, setSavingProfile] = useState(false);
  const [savingPwd, setSavingPwd] = useState(false);

  const saveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingProfile(true);
    try {
      const { data } = await api.put('/auth/me', {
        fullName: profile.fullName.trim(),
        phone: profile.phone.trim() || undefined,
      });
      setUser(data);
      toast('Profile updated', 'success');
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    } finally {
      setSavingProfile(false);
    }
  };

  const changePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pwd.newPassword !== pwd.confirm) {
      toast('New passwords do not match', 'error');
      return;
    }
    setSavingPwd(true);
    try {
      await api.put('/auth/me/password', {
        currentPassword: pwd.currentPassword,
        newPassword: pwd.newPassword,
      });
      toast('Password changed', 'success');
      setPwd({ currentPassword: '', newPassword: '', confirm: '' });
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    } finally {
      setSavingPwd(false);
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Account settings</h1>
        <p className="mt-1 text-sm text-slate-500">Manage your profile and password</p>
      </div>

      <div className="card p-6">
        <h2 className="text-base font-semibold text-slate-800">Profile</h2>
        <div className="mt-1 mb-5 flex gap-2 text-sm text-slate-500">
          <span>{user?.email}</span>
          <span>·</span>
          <span>{humanize(user?.role)}</span>
        </div>
        <form onSubmit={saveProfile} className="space-y-4">
          <Field label="Full name" required>
            <input
              className="input"
              value={profile.fullName}
              onChange={(e) => setProfile({ ...profile, fullName: e.target.value })}
              required
            />
          </Field>
          <Field label="Phone">
            <input
              className="input"
              value={profile.phone}
              onChange={(e) => setProfile({ ...profile, phone: e.target.value })}
            />
          </Field>
          <button className="btn-primary" disabled={savingProfile}>
            {savingProfile && <Spinner className="h-4 w-4" />}
            Save profile
          </button>
        </form>
      </div>

      <div className="card p-6">
        <h2 className="text-base font-semibold text-slate-800 mb-5">Change password</h2>
        <form onSubmit={changePassword} className="space-y-4">
          <Field label="Current password" required>
            <input
              type="password"
              className="input"
              value={pwd.currentPassword}
              onChange={(e) => setPwd({ ...pwd, currentPassword: e.target.value })}
              required
            />
          </Field>
          <Field label="New password" required hint="At least 8 characters">
            <input
              type="password"
              className="input"
              value={pwd.newPassword}
              onChange={(e) => setPwd({ ...pwd, newPassword: e.target.value })}
              minLength={8}
              required
            />
          </Field>
          <Field label="Confirm new password" required>
            <input
              type="password"
              className="input"
              value={pwd.confirm}
              onChange={(e) => setPwd({ ...pwd, confirm: e.target.value })}
              minLength={8}
              required
            />
          </Field>
          <button className="btn-primary" disabled={savingPwd}>
            {savingPwd && <Spinner className="h-4 w-4" />}
            Change password
          </button>
        </form>
      </div>
    </div>
  );
}
