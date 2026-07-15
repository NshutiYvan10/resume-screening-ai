import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Users, Search, Ban, CheckCircle2 } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/PageHeader';
import { Spinner, EmptyState, Pagination, StatusPill } from '../../components/ui';
import { formatDate, humanize, timeAgo } from '../../lib/format';
import type { Page, Role, User, UserStatus } from '../../types';

const ROLE_STYLE: Record<Role, string> = {
  SUPER_ADMIN: 'bg-slate-800 text-white',
  COMPANY_ADMIN: 'bg-brand-100 text-brand-700',
  RECRUITER: 'bg-teal-100 text-teal-700',
  CANDIDATE: 'bg-violet-100 text-violet-700',
};

const STATUS_STYLE: Record<UserStatus, string> = {
  ACTIVE: 'bg-green-100 text-green-700',
  DISABLED: 'bg-red-100 text-red-600',
  PENDING_VERIFICATION: 'bg-amber-100 text-amber-700',
};

export default function AdminUsers() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [role, setRole] = useState<Role | ''>('');
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['users', search, role, page],
    queryFn: async () =>
      (await api.get<Page<User>>('/users', {
        params: { search, role: role || undefined, page, size: 15 },
      })).data,
  });

  const setStatus = useMutation({
    mutationFn: async ({ id, action }: { id: string; action: 'enable' | 'disable' }) =>
      api.post(`/users/${id}/${action}`),
    onSuccess: () => {
      toast('User updated', 'success');
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  return (
    <div>
      <PageHeader title="Users" description="Everyone across the platform" />

      <div className="mb-4 flex flex-wrap gap-3">
        <div className="relative flex-1 min-w-56">
          <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
          <input
            className="input pl-9"
            placeholder="Search by name or email…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <select
          className="input max-w-48"
          value={role}
          onChange={(e) => {
            setRole(e.target.value as Role | '');
            setPage(0);
          }}
        >
          <option value="">All roles</option>
          <option value="COMPANY_ADMIN">Company Admin</option>
          <option value="RECRUITER">Recruiter</option>
          <option value="CANDIDATE">Candidate</option>
          <option value="SUPER_ADMIN">Super Admin</option>
        </select>
      </div>

      {isLoading ? (
        <div className="py-12 text-center"><Spinner className="mx-auto h-6 w-6 text-brand-600" /></div>
      ) : !data?.content.length ? (
        <EmptyState icon={<Users className="h-12 w-12" />} title="No users found" />
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3 font-medium">User</th>
                <th className="px-5 py-3 font-medium">Role</th>
                <th className="px-5 py-3 font-medium">Company</th>
                <th className="px-5 py-3 font-medium">Status</th>
                <th className="px-5 py-3 font-medium">Last login</th>
                <th className="px-5 py-3"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((u) => (
                <tr key={u.id} className="hover:bg-slate-50">
                  <td className="px-5 py-3.5">
                    <div className="font-medium text-slate-800">{u.fullName}</div>
                    <div className="text-xs text-slate-400">{u.email}</div>
                  </td>
                  <td className="px-5 py-3.5">
                    <StatusPill label={humanize(u.role)} className={ROLE_STYLE[u.role]} />
                  </td>
                  <td className="px-5 py-3.5 text-slate-600">{u.companyName || '—'}</td>
                  <td className="px-5 py-3.5">
                    <StatusPill label={humanize(u.status)} className={STATUS_STYLE[u.status]} />
                  </td>
                  <td className="px-5 py-3.5 text-slate-500">
                    {u.lastLoginAt ? timeAgo(u.lastLoginAt) : 'Never'}
                  </td>
                  <td className="px-5 py-3.5 text-right">
                    {u.role !== 'SUPER_ADMIN' &&
                      (u.status === 'DISABLED' ? (
                        <button
                          onClick={() => setStatus.mutate({ id: u.id, action: 'enable' })}
                          className="inline-flex items-center gap-1 text-xs font-medium text-green-600 hover:text-green-700"
                        >
                          <CheckCircle2 className="h-3.5 w-3.5" /> Enable
                        </button>
                      ) : (
                        <button
                          onClick={() => setStatus.mutate({ id: u.id, action: 'disable' })}
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
      )}
      {data && <Pagination page={page} totalPages={data.totalPages} onChange={setPage} />}
    </div>
  );
}
