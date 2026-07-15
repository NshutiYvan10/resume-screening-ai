import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { UserPlus, Users, Ban, CheckCircle2, Mail } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import { useAuth } from '../../context/AuthContext';
import PageHeader from '../../components/PageHeader';
import { Field, Modal, Spinner, EmptyState, StatusPill } from '../../components/ui';
import { humanize, timeAgo } from '../../lib/format';
import type { Invitation, Page, Role, User } from '../../types';

export default function CompanyTeam() {
  const { user } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<'members' | 'invitations'>('members');
  const [inviteOpen, setInviteOpen] = useState(false);
  const [invite, setInvite] = useState<{ email: string; role: Role }>({ email: '', role: 'RECRUITER' });

  const members = useQuery({
    queryKey: ['team', 'members'],
    queryFn: async () =>
      (await api.get<Page<User>>('/users', { params: { size: 100 } })).data,
    enabled: tab === 'members',
  });

  const invitations = useQuery({
    queryKey: ['team', 'invitations'],
    queryFn: async () => (await api.get<Page<Invitation>>('/invitations?size=50')).data,
    enabled: tab === 'invitations',
  });

  const sendInvite = useMutation({
    mutationFn: async () => api.post('/invitations/team', invite),
    onSuccess: () => {
      toast('Invitation sent', 'success');
      setInviteOpen(false);
      setInvite({ email: '', role: 'RECRUITER' });
      queryClient.invalidateQueries({ queryKey: ['team'] });
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const setStatus = useMutation({
    mutationFn: async ({ id, action }: { id: string; action: 'enable' | 'disable' }) =>
      api.post(`/users/${id}/${action}`),
    onSuccess: () => {
      toast('Team member updated', 'success');
      queryClient.invalidateQueries({ queryKey: ['team'] });
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const invAction = useMutation({
    mutationFn: async ({ id, action }: { id: string; action: 'resend' | 'revoke' }) =>
      api.post(`/invitations/${id}/${action}`),
    onSuccess: () => {
      toast('Invitation updated', 'success');
      queryClient.invalidateQueries({ queryKey: ['team'] });
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const teamMembers = members.data?.content.filter(
    (u) => u.role === 'COMPANY_ADMIN' || u.role === 'RECRUITER'
  );

  const invStatusStyle: Record<string, string> = {
    PENDING: 'bg-amber-100 text-amber-700',
    ACCEPTED: 'bg-green-100 text-green-700',
    REVOKED: 'bg-slate-100 text-slate-500',
    EXPIRED: 'bg-red-100 text-red-600',
  };

  return (
    <div>
      <PageHeader
        title="Team"
        description="Invite and manage recruiters and administrators"
        action={
          <button className="btn-primary" onClick={() => setInviteOpen(true)}>
            <UserPlus className="h-4 w-4" /> Invite member
          </button>
        }
      />

      <div className="mb-5 flex gap-1 border-b border-slate-200">
        {(['members', 'invitations'] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors ${
              tab === t
                ? 'border-brand-600 text-brand-600'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            {t === 'members' ? 'Members' : 'Pending invitations'}
          </button>
        ))}
      </div>

      {tab === 'members' &&
        (members.isLoading ? (
          <div className="py-12 text-center"><Spinner className="mx-auto h-6 w-6 text-brand-600" /></div>
        ) : !teamMembers?.length ? (
          <EmptyState icon={<Users className="h-12 w-12" />} title="No team members yet" />
        ) : (
          <div className="card overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-5 py-3 font-medium">Member</th>
                  <th className="px-5 py-3 font-medium">Role</th>
                  <th className="px-5 py-3 font-medium">Status</th>
                  <th className="px-5 py-3 font-medium">Last login</th>
                  <th className="px-5 py-3"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {teamMembers.map((m) => (
                  <tr key={m.id} className="hover:bg-slate-50">
                    <td className="px-5 py-3.5">
                      <div className="font-medium text-slate-800">
                        {m.fullName}
                        {m.id === user?.id && <span className="ml-2 text-xs text-slate-400">(you)</span>}
                      </div>
                      <div className="text-xs text-slate-400">{m.email}</div>
                    </td>
                    <td className="px-5 py-3.5">
                      <StatusPill
                        label={humanize(m.role)}
                        className={m.role === 'COMPANY_ADMIN' ? 'bg-brand-100 text-brand-700' : 'bg-teal-100 text-teal-700'}
                      />
                    </td>
                    <td className="px-5 py-3.5">
                      <StatusPill
                        label={humanize(m.status)}
                        className={m.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}
                      />
                    </td>
                    <td className="px-5 py-3.5 text-slate-500">
                      {m.lastLoginAt ? timeAgo(m.lastLoginAt) : 'Never'}
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      {m.id !== user?.id &&
                        (m.status === 'DISABLED' ? (
                          <button
                            onClick={() => setStatus.mutate({ id: m.id, action: 'enable' })}
                            className="inline-flex items-center gap-1 text-xs font-medium text-green-600 hover:text-green-700"
                          >
                            <CheckCircle2 className="h-3.5 w-3.5" /> Enable
                          </button>
                        ) : (
                          <button
                            onClick={() => setStatus.mutate({ id: m.id, action: 'disable' })}
                            className="inline-flex items-center gap-1 text-xs font-medium text-red-600 hover:text-red-700"
                          >
                            <Ban className="h-3.5 w-3.5" /> Disable
                          </button>
                        ))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}

      {tab === 'invitations' &&
        (invitations.isLoading ? (
          <div className="py-12 text-center"><Spinner className="mx-auto h-6 w-6 text-brand-600" /></div>
        ) : !invitations.data?.content.length ? (
          <EmptyState icon={<Mail className="h-12 w-12" />} title="No pending invitations" />
        ) : (
          <div className="card overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-5 py-3 font-medium">Email</th>
                  <th className="px-5 py-3 font-medium">Role</th>
                  <th className="px-5 py-3 font-medium">Status</th>
                  <th className="px-5 py-3 font-medium">Sent</th>
                  <th className="px-5 py-3"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {invitations.data.content.map((inv) => (
                  <tr key={inv.id} className="hover:bg-slate-50">
                    <td className="px-5 py-3.5 text-slate-700">{inv.email}</td>
                    <td className="px-5 py-3.5">{humanize(inv.role)}</td>
                    <td className="px-5 py-3.5">
                      <StatusPill label={humanize(inv.status)} className={invStatusStyle[inv.status]} />
                    </td>
                    <td className="px-5 py-3.5 text-slate-500">{timeAgo(inv.createdAt)}</td>
                    <td className="px-5 py-3.5 text-right">
                      {(inv.status === 'PENDING' || inv.status === 'EXPIRED') && (
                        <div className="flex justify-end gap-2">
                          <button
                            onClick={() => invAction.mutate({ id: inv.id, action: 'resend' })}
                            className="text-xs font-medium text-brand-600 hover:text-brand-700"
                          >
                            Resend
                          </button>
                          <button
                            onClick={() => invAction.mutate({ id: inv.id, action: 'revoke' })}
                            className="text-xs font-medium text-red-600 hover:text-red-700"
                          >
                            Revoke
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}

      <Modal open={inviteOpen} onClose={() => setInviteOpen(false)} title="Invite a team member">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            sendInvite.mutate();
          }}
          className="space-y-4"
        >
          <Field label="Email" required>
            <input
              type="email"
              className="input"
              value={invite.email}
              onChange={(e) => setInvite({ ...invite, email: e.target.value })}
              required
            />
          </Field>
          <Field label="Role" required>
            <select
              className="input"
              value={invite.role}
              onChange={(e) => setInvite({ ...invite, role: e.target.value as Role })}
            >
              <option value="RECRUITER">Recruiter — post jobs & review applicants</option>
              <option value="COMPANY_ADMIN">Company Admin — full company access</option>
            </select>
          </Field>
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" className="btn-secondary" onClick={() => setInviteOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn-primary" disabled={sendInvite.isPending}>
              {sendInvite.isPending && <Spinner className="h-4 w-4" />}
              Send invitation
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
