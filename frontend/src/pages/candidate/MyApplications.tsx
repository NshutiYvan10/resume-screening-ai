import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ClipboardList, Building2, Search, X } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/PageHeader';
import { Spinner, EmptyState, StatusPill } from '../../components/ui';
import { APPLICATION_STATUS_STYLES, humanize, timeAgo } from '../../lib/format';
import type { Application, Page } from '../../types';

const STATUS_HINT: Record<string, string> = {
  SUBMITTED: 'Your application has been received and is being screened.',
  UNDER_REVIEW: 'A recruiter is reviewing your application.',
  SHORTLISTED: "Great news — you've been shortlisted!",
  INTERVIEW: 'You have advanced to the interview stage.',
  OFFERED: 'An offer has been extended. Congratulations!',
  HIRED: "You've been hired. Welcome aboard!",
  REJECTED: 'This application was not selected to move forward.',
  WITHDRAWN: 'You withdrew this application.',
};

export default function MyApplications() {
  const toast = useToast();
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['my-applications'],
    queryFn: async () => (await api.get<Page<Application>>('/applications/my?size=50')).data,
  });

  const withdraw = useMutation({
    mutationFn: async (id: string) => api.post(`/applications/${id}/withdraw`),
    onSuccess: () => {
      toast('Application withdrawn', 'success');
      queryClient.invalidateQueries({ queryKey: ['my-applications'] });
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  return (
    <div>
      <PageHeader
        title="My applications"
        description="Track the status of every job you've applied to"
        action={
          <Link to="/candidate" className="btn-secondary">
            <Search className="h-4 w-4" /> Browse more jobs
          </Link>
        }
      />

      {isLoading ? (
        <div className="py-12 text-center"><Spinner className="mx-auto h-6 w-6 text-brand-600" /></div>
      ) : !data?.content.length ? (
        <EmptyState
          icon={<ClipboardList className="h-12 w-12" />}
          title="No applications yet"
          description="Browse open roles and submit your first application."
          action={
            <Link to="/candidate" className="btn-primary">
              <Search className="h-4 w-4" /> Browse jobs
            </Link>
          }
        />
      ) : (
        <div className="space-y-3">
          {data.content.map((app) => {
            const canWithdraw = app.status !== 'WITHDRAWN' && app.status !== 'HIRED' && app.status !== 'REJECTED';
            return (
              <div key={app.id} className="card p-5">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <h3 className="font-semibold text-slate-800">{app.jobTitle}</h3>
                    <div className="mt-0.5 flex items-center gap-1.5 text-sm text-slate-400">
                      <Building2 className="h-3.5 w-3.5" /> {app.companyName}
                    </div>
                  </div>
                  <StatusPill label={humanize(app.status)} className={APPLICATION_STATUS_STYLES[app.status]} />
                </div>

                <p className="mt-3 text-sm text-slate-500">{STATUS_HINT[app.status]}</p>

                <div className="mt-4 flex items-center justify-between border-t border-slate-100 pt-3">
                  <span className="text-xs text-slate-400">Applied {timeAgo(app.appliedAt)}</span>
                  {canWithdraw && (
                    <button
                      onClick={() => {
                        if (confirm('Withdraw this application?')) withdraw.mutate(app.id);
                      }}
                      className="flex items-center gap-1 text-xs font-medium text-red-600 hover:text-red-700"
                    >
                      <X className="h-3.5 w-3.5" /> Withdraw
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
