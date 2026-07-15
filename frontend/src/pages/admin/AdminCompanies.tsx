import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Building2, Send, Search, MoreVertical, Ban, CheckCircle2, Mail, Clock } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/PageHeader';
import { Field, Modal, Spinner, EmptyState, Pagination, StatusPill } from '../../components/ui';
import { formatDate, timeAgo, humanize } from '../../lib/format';
import type { CompanySummary, Invitation, Page } from '../../types';

export default function AdminCompanies() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<'companies' | 'invitations'>('companies');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [invite, setInvite] = useState({ email: '', companyName: '' });
  const [menuId, setMenuId] = useState<string | null>(null);

  const companies = useQuery({
    queryKey: ['companies', search, page],
    queryFn: async () =>
      (await api.get<Page<CompanySummary>>('/companies', { params: { search, page, size: 10 } })).data,
    enabled: tab === 'companies',
  });

  const invitations = useQuery({
    queryKey: ['invitations', 'company'],
    queryFn: async () => (await api.get<Page<Invitation>>('/invitations?size=50')).data,
    enabled: tab === 'invitations',
  });

  const sendInvite = useMutation({
    mutationFn: async () => api.post('/invitations/company', invite),
    onSuccess: () => {
      toast('Invitation sent to ' + invite.email, 'success');
      setInviteOpen(false);
      setInvite({ email: '', companyName: '' });
      queryClient.invalidateQueries({ queryKey: ['invitations'] });
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const setStatus = useMutation({
    mutationFn: async ({ id, action }: { id: string; action: 'suspend' | 'activate' }) =>
      api.post(`/companies/${id}/${action}`),
    onSuccess: () => {
      toast('Company updated', 'success');
      setMenuId(null);
      queryClient.invalidateQueries({ queryKey: ['companies'] });
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const invAction = useMutation({
    mutationFn: async ({ id, action }: { id: string; action: 'resend' | 'revoke' }) =>
      api.post(`/invitations/${id}/${action}`),
    onSuccess: (_d, v) => {
      toast(v.action === 'resend' ? 'Invitation resent' : 'Invitation revoked', 'success');
      queryClient.invalidateQueries({ queryKey: ['invitations'] });
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const invStatusStyle: Record<string, string> = {
    PENDING: 'bg-amber-100 text-amber-700',
    ACCEPTED: 'bg-green-100 text-green-700',
    REVOKED: 'bg-slate-100 text-slate-500',
    EXPIRED: 'bg-red-100 text-red-600',
  };

  return (
    <div>
      <PageHeader
        title="Companies"
        description="Invite organizations and manage their accounts"
        action={
          <button className="btn-primary" onClick={() => setInviteOpen(true)}>
            <Send className="h-4 w-4" /> Invite company
          </button>
        }
      />

      <div className="mb-5 flex gap-1 border-b border-slate-200">
        {(['companies', 'invitations'] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2.5 text-sm font-medium border-b-2 -mb-px transition-colors ${
              tab === t
                ? 'border-brand-600 text-brand-600'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            {t === 'companies' ? 'Active companies' : 'Invitations'}
          </button>
        ))}
      </div>

      {tab === 'companies' && (
        <>
          <div className="mb-4 relative max-w-sm">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
            <input
              className="input pl-9"
              placeholder="Search companies…"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </div>

          {companies.isLoading ? (
            <div className="py-12 text-center"><Spinner className="mx-auto h-6 w-6 text-brand-600" /></div>
          ) : !companies.data?.content.length ? (
            <EmptyState
              icon={<Building2 className="h-12 w-12" />}
              title="No companies yet"
              description="Invite your first organization to get started."
            />
          ) : (
            <div className="card overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-5 py-3 font-medium">Company</th>
                    <th className="px-5 py-3 font-medium">Users</th>
                    <th className="px-5 py-3 font-medium">Jobs</th>
                    <th className="px-5 py-3 font-medium">Status</th>
                    <th className="px-5 py-3 font-medium">Onboarded</th>
                    <th className="px-5 py-3"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {companies.data.content.map((c) => (
                    <tr key={c.id} className="hover:bg-slate-50">
                      <td className="px-5 py-3.5">
                        <div className="font-medium text-slate-800">{c.name}</div>
                        <div className="text-xs text-slate-400">
                          {[c.industry, c.location].filter(Boolean).join(' · ') || '—'}
                        </div>
                      </td>
                      <td className="px-5 py-3.5 text-slate-600">{c.userCount}</td>
                      <td className="px-5 py-3.5 text-slate-600">{c.jobCount}</td>
                      <td className="px-5 py-3.5">
                        <StatusPill
                          label={humanize(c.status)}
                          className={c.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}
                        />
                      </td>
                      <td className="px-5 py-3.5 text-slate-500">{formatDate(c.createdAt)}</td>
                      <td className="px-5 py-3.5 text-right relative">
                        <button
                          onClick={() => setMenuId(menuId === c.id ? null : c.id)}
                          className="rounded p-1.5 text-slate-400 hover:bg-slate-100"
                        >
                          <MoreVertical className="h-4 w-4" />
                        </button>
                        {menuId === c.id && (
                          <>
                            <div className="fixed inset-0 z-20" onClick={() => setMenuId(null)} />
                            <div className="absolute right-5 z-30 mt-1 w-44 card py-1 text-left">
                              {c.status === 'ACTIVE' ? (
                                <button
                                  onClick={() => setStatus.mutate({ id: c.id, action: 'suspend' })}
                                  className="flex w-full items-center gap-2 px-4 py-2 text-sm text-red-600 hover:bg-red-50"
                                >
                                  <Ban className="h-4 w-4" /> Suspend company
                                </button>
                              ) : (
                                <button
                                  onClick={() => setStatus.mutate({ id: c.id, action: 'activate' })}
                                  className="flex w-full items-center gap-2 px-4 py-2 text-sm text-green-600 hover:bg-green-50"
                                >
                                  <CheckCircle2 className="h-4 w-4" /> Reactivate
                                </button>
                              )}
                            </div>
                          </>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {companies.data && (
            <Pagination page={page} totalPages={companies.data.totalPages} onChange={setPage} />
          )}
        </>
      )}

      {tab === 'invitations' && (
        <>
          {invitations.isLoading ? (
            <div className="py-12 text-center"><Spinner className="mx-auto h-6 w-6 text-brand-600" /></div>
          ) : !invitations.data?.content.length ? (
            <EmptyState
              icon={<Mail className="h-12 w-12" />}
              title="No invitations sent"
              description="Company invitations you send will appear here."
            />
          ) : (
            <div className="card overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-5 py-3 font-medium">Company</th>
                    <th className="px-5 py-3 font-medium">Email</th>
                    <th className="px-5 py-3 font-medium">Status</th>
                    <th className="px-5 py-3 font-medium">Sent</th>
                    <th className="px-5 py-3"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {invitations.data.content.map((inv) => (
                    <tr key={inv.id} className="hover:bg-slate-50">
                      <td className="px-5 py-3.5 font-medium text-slate-800">{inv.companyName}</td>
                      <td className="px-5 py-3.5 text-slate-600">{inv.email}</td>
                      <td className="px-5 py-3.5">
                        <StatusPill label={humanize(inv.status)} className={invStatusStyle[inv.status]} />
                      </td>
                      <td className="px-5 py-3.5 text-slate-500">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3.5 w-3.5" /> {timeAgo(inv.createdAt)}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-right">
                        {inv.status === 'PENDING' || inv.status === 'EXPIRED' ? (
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
                        ) : (
                          <span className="text-xs text-slate-400">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      <Modal open={inviteOpen} onClose={() => setInviteOpen(false)} title="Invite a company">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            sendInvite.mutate();
          }}
          className="space-y-4"
        >
          <p className="text-sm text-slate-500">
            We'll email an onboarding link. The recipient completes their company profile and becomes its
            administrator.
          </p>
          <Field label="Company name" required>
            <input
              className="input"
              value={invite.companyName}
              onChange={(e) => setInvite({ ...invite, companyName: e.target.value })}
              required
            />
          </Field>
          <Field label="Administrator email" required>
            <input
              type="email"
              className="input"
              value={invite.email}
              onChange={(e) => setInvite({ ...invite, email: e.target.value })}
              required
            />
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
