import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ShieldCheck, Search } from 'lucide-react';
import { api } from '../lib/api';
import { Spinner, EmptyState, Pagination, StatusPill } from './ui';
import { formatDateTime, humanize } from '../lib/format';
import type { AuditLog, Page } from '../types';

function actionStyle(action: string): string {
  if (/FAILED|REVOKED|SUSPENDED|DISABLED|DELETED|REJECTED/.test(action)) return 'bg-red-100 text-red-600';
  if (/CREATED|ONBOARDED|INVITED|ACCEPTED|ENABLED|ACTIVATED|SUBMITTED/.test(action))
    return 'bg-green-100 text-green-700';
  if (/UPDATED|CHANGED|RESET|RESENT/.test(action)) return 'bg-blue-100 text-blue-700';
  return 'bg-slate-100 text-slate-600';
}

export default function AuditTrail({ scopedToCompany = false }: { scopedToCompany?: boolean }) {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['audit', search, page, scopedToCompany],
    queryFn: async () =>
      (await api.get<Page<AuditLog>>('/audit', { params: { search, page, size: 25 } })).data,
  });

  return (
    <div>
      <div className="mb-4 relative max-w-sm">
        <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
        <input
          className="input pl-9"
          placeholder="Search by actor or entity…"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
        />
      </div>

      {isLoading ? (
        <div className="py-12 text-center"><Spinner className="mx-auto h-6 w-6 text-brand-600" /></div>
      ) : !data?.content.length ? (
        <EmptyState icon={<ShieldCheck className="h-12 w-12" />} title="No audit records" />
      ) : (
        <div className="card overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3 font-medium">When</th>
                <th className="px-5 py-3 font-medium">Actor</th>
                <th className="px-5 py-3 font-medium">Action</th>
                <th className="px-5 py-3 font-medium">Entity</th>
                <th className="px-5 py-3 font-medium">Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.content.map((log) => (
                <tr key={log.id} className="hover:bg-slate-50 align-top">
                  <td className="whitespace-nowrap px-5 py-3.5 text-slate-500">
                    {formatDateTime(log.createdAt)}
                  </td>
                  <td className="px-5 py-3.5">
                    <div className="font-medium text-slate-700">{log.actorEmail || 'system'}</div>
                    <div className="text-xs text-slate-400">{humanize(log.actorRole)}</div>
                  </td>
                  <td className="px-5 py-3.5">
                    <StatusPill label={humanize(log.action)} className={actionStyle(log.action)} />
                  </td>
                  <td className="px-5 py-3.5 text-slate-600">
                    {log.entityType ? humanize(log.entityType) : '—'}
                  </td>
                  <td className="px-5 py-3.5 text-xs text-slate-500 max-w-xs">
                    {log.details && Object.keys(log.details).length ? (
                      <div className="space-y-0.5">
                        {Object.entries(log.details).slice(0, 4).map(([k, v]) => (
                          <div key={k}>
                            <span className="text-slate-400">{k}:</span> {String(v)}
                          </div>
                        ))}
                      </div>
                    ) : (
                      '—'
                    )}
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
