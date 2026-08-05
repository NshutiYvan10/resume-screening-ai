import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  FileText, Download, Eye, Send, CheckCircle2, XCircle, RefreshCw,
  ShieldCheck, Clock, AlertTriangle, X, FilePlus2, ExternalLink,
} from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/PageHeader';
import {
  Spinner, PageLoader, EmptyState, Pagination, StatusPill, Modal, Field,
} from '../../components/ui';
import type {
  Page, ReportDetail, ReportStatus, ReportSummary, ReportTypeOption, Job,
} from '../../types';

const STATUS_STYLES: Record<ReportStatus, string> = {
  QUEUED: 'bg-slate-100 text-slate-600',
  GENERATING: 'bg-blue-50 text-blue-700',
  FAILED: 'bg-red-50 text-red-700',
  DRAFT: 'bg-amber-50 text-amber-700',
  PENDING_APPROVAL: 'bg-violet-50 text-violet-700',
  APPROVED: 'bg-green-50 text-green-700',
  REJECTED: 'bg-red-50 text-red-700',
};

const STATUS_LABEL: Record<ReportStatus, string> = {
  QUEUED: 'Queued',
  GENERATING: 'Generating',
  FAILED: 'Failed',
  DRAFT: 'Draft',
  PENDING_APPROVAL: 'Pending approval',
  APPROVED: 'Approved',
  REJECTED: 'Returned',
};

/** Statuses that mean the document is still being produced, so the list should poll. */
const IN_FLIGHT: ReportStatus[] = ['QUEUED', 'GENERATING'];

function formatBytes(n?: number) {
  if (!n) return '-';
  return n < 1024 * 1024 ? `${Math.round(n / 1024)} KB` : `${(n / 1024 / 1024).toFixed(1)} MB`;
}

function formatWhen(iso?: string) {
  if (!iso) return '-';
  return new Date(iso).toLocaleString(undefined, {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

export default function ReportCenter() {
  const qc = useQueryClient();
  const toast = useToast();

  const [statusFilter, setStatusFilter] = useState<'' | ReportStatus>('');
  const [typeFilter, setTypeFilter] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);

  const [generating, setGenerating] = useState(false);
  const [chosenType, setChosenType] = useState<ReportTypeOption | null>(null);
  const [chosenJob, setChosenJob] = useState('');

  const [preview, setPreview] = useState<{ report: ReportSummary; url: string } | null>(null);
  const [decision, setDecision] = useState<{ report: ReportSummary; approve: boolean } | null>(null);
  const [note, setNote] = useState('');
  const objectUrl = useRef<string | null>(null);

  // report types are role-scoped by the server, so the UI never has to know the mapping
  const { data: types } = useQuery({
    queryKey: ['reports', 'types'],
    queryFn: async () => (await api.get<ReportTypeOption[]>('/reports/types')).data,
  });

  // only fetched when a chosen type needs a job posting
  const { data: jobs } = useQuery({
    queryKey: ['reports', 'jobs'],
    queryFn: async () => (await api.get<Page<Job>>('/jobs?page=0&size=100')).data,
    enabled: !!chosenType?.requiresJob,
  });

  const listKey = ['reports', 'list', { statusFilter, typeFilter, from, to, page }];
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: listKey,
    queryFn: async () => {
      const params = new URLSearchParams({ page: String(page), size: '10' });
      if (statusFilter) params.set('status', statusFilter);
      if (typeFilter) params.set('type', typeFilter);
      if (from) params.set('from', new Date(from).toISOString());
      if (to) params.set('to', new Date(`${to}T23:59:59`).toISOString());
      return (await api.get<Page<ReportSummary>>(`/reports?${params}`)).data;
    },
    // generation is asynchronous: poll while anything is still rendering
    refetchInterval: (q) =>
      (q.state.data as Page<ReportSummary> | undefined)?.content
        ?.some((r) => IN_FLIGHT.includes(r.status)) ? 2000 : false,
  });

  useEffect(() => () => { if (objectUrl.current) URL.revokeObjectURL(objectUrl.current); }, []);

  const generate = useMutation({
    mutationFn: async () => {
      if (!chosenType) return;
      await api.post('/reports', {
        type: chosenType.type,
        jobId: chosenType.requiresJob ? chosenJob : undefined,
      });
    },
    onSuccess: () => {
      toast('Report queued — it will appear below once rendered', 'success');
      setGenerating(false); setChosenType(null); setChosenJob('');
      setPage(0); qc.invalidateQueries({ queryKey: ['reports', 'list'] });
    },
    onError: (e) => toast(apiErrorMessage(e), 'error'),
  });

  /** Fetched through the api client so the bearer token is attached; an iframe cannot. */
  const openPreview = async (report: ReportSummary) => {
    try {
      const res = await api.get(`/reports/${report.id}/file`, { responseType: 'blob' });
      if (objectUrl.current) URL.revokeObjectURL(objectUrl.current);
      const url = URL.createObjectURL(new Blob([res.data as Blob], { type: 'application/pdf' }));
      objectUrl.current = url;
      setPreview({ report, url });
    } catch (e) {
      toast(apiErrorMessage(e), 'error');
    }
  };

  const download = async (report: ReportSummary) => {
    try {
      const res = await api.get(`/reports/${report.id}/file?download=true`, { responseType: 'blob' });
      const url = URL.createObjectURL(new Blob([res.data as Blob], { type: 'application/pdf' }));
      const a = document.createElement('a');
      a.href = url; a.download = `${report.referenceNo}.pdf`; a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      toast(apiErrorMessage(e), 'error');
    }
  };

  const act = async (report: ReportSummary, path: string, body?: unknown, ok?: string) => {
    try {
      await api.post(`/reports/${report.id}/${path}`, body ?? {});
      toast(ok ?? 'Done', 'success');
      qc.invalidateQueries({ queryKey: ['reports'] });
      setDecision(null); setNote('');
    } catch (e) {
      toast(apiErrorMessage(e), 'error');
    }
  };

  const canGenerate = !!chosenType && (!chosenType.requiresJob || !!chosenJob);
  const grouped = useMemo(() => types ?? [], [types]);

  if (isLoading && !data) return <PageLoader />;

  return (
    <div>
      <PageHeader
        title="Reports"
        description="Generate formal PDF reports, review them here, and route them for approval"
        action={
          <button className="btn-primary" onClick={() => setGenerating(true)}>
            <FilePlus2 className="mr-1.5 h-4 w-4" /> New report
          </button>
        }
      />

      {/* ---------------- filters ---------------- */}
      <div className="card mb-4 grid gap-3 p-4 sm:grid-cols-2 lg:grid-cols-5">
        <label className="text-xs font-medium text-slate-500">
          Type
          <select className="input mt-1" value={typeFilter}
                  onChange={(e) => { setTypeFilter(e.target.value); setPage(0); }}>
            <option value="">All types</option>
            {grouped.map((t) => <option key={t.type} value={t.type}>{t.title}</option>)}
          </select>
        </label>
        <label className="text-xs font-medium text-slate-500">
          Status
          <select className="input mt-1" value={statusFilter}
                  onChange={(e) => { setStatusFilter(e.target.value as ReportStatus | ''); setPage(0); }}>
            <option value="">Any status</option>
            {(Object.keys(STATUS_LABEL) as ReportStatus[]).map((s) =>
              <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
          </select>
        </label>
        <label className="text-xs font-medium text-slate-500">
          From
          <input type="date" className="input mt-1" value={from}
                 onChange={(e) => { setFrom(e.target.value); setPage(0); }} />
        </label>
        <label className="text-xs font-medium text-slate-500">
          To
          <input type="date" className="input mt-1" value={to}
                 onChange={(e) => { setTo(e.target.value); setPage(0); }} />
        </label>
        <div className="flex items-end">
          <button className="btn-secondary w-full" onClick={() => refetch()}>
            <RefreshCw className="mr-1.5 h-4 w-4" /> Refresh
          </button>
        </div>
      </div>

      {/* ---------------- archive ---------------- */}
      {isError ? (
        <EmptyState icon={<AlertTriangle className="h-12 w-12" />} title="Couldn’t load reports"
                    action={<button className="btn-secondary" onClick={() => refetch()}>Try again</button>} />
      ) : !data || data.content.length === 0 ? (
        <EmptyState icon={<FileText className="h-12 w-12" />} title="No reports yet"
                    description="Generate your first report to build an archive."
                    action={<button className="btn-primary" onClick={() => setGenerating(true)}>New report</button>} />
      ) : (
        <>
          <div className="card overflow-x-auto">
            <table className="w-full min-w-[820px] text-sm">
              <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-5 py-3">Report</th>
                  <th className="px-5 py-3">Status</th>
                  <th className="px-5 py-3">Prepared by</th>
                  <th className="px-5 py-3">Approved by</th>
                  <th className="px-5 py-3">Document</th>
                  <th className="px-5 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((r) => (
                  <ReportRow key={r.id} report={r}
                             onPreview={() => openPreview(r)}
                             onDownload={() => download(r)}
                             onSubmit={() => act(r, 'submit', {}, 'Sent for approval')}
                             onDecide={(approve) => { setDecision({ report: r, approve }); setNote(''); }} />
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-4">
            <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
          </div>
        </>
      )}

      {/* ---------------- generate ---------------- */}
      <Modal open={generating} onClose={() => setGenerating(false)} title="Generate a report" maxWidth="max-w-2xl">
        <p className="mb-3 text-sm text-slate-500">
          Reports are built from live data when you generate them and saved as PDF documents.
        </p>
        <div className="max-h-72 space-y-2 overflow-y-auto">
          {grouped.map((t) => (
            <button key={t.type} type="button" onClick={() => setChosenType(t)}
              className={`w-full rounded-lg border p-3 text-left transition ${
                chosenType?.type === t.type
                  ? 'border-brand-500 bg-brand-50'
                  : 'border-slate-200 hover:border-brand-300'}`}>
              <div className="flex items-center justify-between">
                <span className="text-sm font-semibold text-slate-800">{t.title}</span>
                <span className="text-[10px] uppercase tracking-wide text-slate-400">{t.scope}</span>
              </div>
              <p className="mt-0.5 text-xs text-slate-500">{t.description}</p>
              <p className="mt-1 text-[11px] text-slate-400">
                {t.requiresApproval ? 'Requires approval before it is final' : 'Final as soon as it is generated'}
              </p>
            </button>
          ))}
        </div>

        {chosenType?.requiresJob && (
          <div className="mt-3">
            <Field label="Job posting" required>
              <select className="input" value={chosenJob} onChange={(e) => setChosenJob(e.target.value)}>
                <option value="">Select a job posting…</option>
                {jobs?.content.map((j) => <option key={j.id} value={j.id}>{j.title}</option>)}
              </select>
            </Field>
          </div>
        )}

        <div className="mt-4 flex justify-end gap-2">
          <button className="btn-secondary" onClick={() => setGenerating(false)}>Cancel</button>
          <button className="btn-primary" disabled={!canGenerate || generate.isPending}
                  onClick={() => generate.mutate()}>
            {generate.isPending ? <Spinner className="mr-1.5 h-4 w-4" /> : null}
            Generate
          </button>
        </div>
      </Modal>

      {/* ---------------- in-app preview ---------------- */}
      {preview && (
        <div className="fixed inset-0 z-50 flex flex-col bg-black/70 p-4">
          <div className="mb-2 flex items-center justify-between text-white">
            <div>
              <p className="text-sm font-semibold">{preview.report.title}</p>
              <p className="text-xs text-white/70">
                {preview.report.referenceNo} · {STATUS_LABEL[preview.report.status]}
                {preview.report.pageCount ? ` · ${preview.report.pageCount} pages` : ''}
              </p>
            </div>
            <div className="flex items-center gap-2">
              {/* some browsers refuse to render PDFs inside an iframe; this always works */}
              <a className="btn-secondary" href={preview.url} target="_blank" rel="noreferrer">
                <ExternalLink className="mr-1.5 h-4 w-4" /> Open in new tab
              </a>
              <button className="btn-secondary" onClick={() => download(preview.report)}>
                <Download className="mr-1.5 h-4 w-4" /> Download
              </button>
              <button className="rounded-lg bg-white/10 p-2 text-white hover:bg-white/20"
                      onClick={() => setPreview(null)} title="Close">
                <X className="h-5 w-5" />
              </button>
            </div>
          </div>
          <iframe title="Report preview" src={preview.url} className="flex-1 rounded-lg bg-white" />
        </div>
      )}

      {/* ---------------- approve / return ---------------- */}
      <Modal open={!!decision} onClose={() => setDecision(null)}
             title={decision?.approve ? 'Approve report' : 'Return report'}>
        <p className="mb-3 text-sm text-slate-500">
          {decision?.approve
            ? 'Approving records your name, role and the time on the document itself, and removes the draft watermark.'
            : 'Returning sends the report back to its author as a draft so they can regenerate or correct it.'}
        </p>
        <Field label="Note" hint={decision?.approve ? 'Optional' : 'Explain what needs changing'}>
          <textarea className="input" rows={3} value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
        <div className="mt-4 flex justify-end gap-2">
          <button className="btn-secondary" onClick={() => setDecision(null)}>Cancel</button>
          <button className={decision?.approve ? 'btn-primary' : 'btn-danger'}
                  onClick={() => decision && act(decision.report,
                    decision.approve ? 'approve' : 'reject',
                    { note: note || undefined },
                    decision.approve ? 'Report approved' : 'Report returned to author')}>
            {decision?.approve ? 'Approve' : 'Return'}
          </button>
        </div>
      </Modal>
    </div>
  );
}

/** One archive row; loads its own detail lazily so the list stays cheap. */
function ReportRow({ report, onPreview, onDownload, onSubmit, onDecide }: {
  report: ReportSummary;
  onPreview: () => void;
  onDownload: () => void;
  onSubmit: () => void;
  onDecide: (approve: boolean) => void;
}) {
  const ready = !['QUEUED', 'GENERATING', 'FAILED'].includes(report.status);
  const { data: detail } = useQuery({
    queryKey: ['reports', 'detail', report.id],
    queryFn: async () => (await api.get<ReportDetail>(`/reports/${report.id}`)).data,
    enabled: ready,
  });

  return (
    <tr className="hover:bg-slate-50">
      <td className="px-5 py-3.5">
        <p className="font-medium text-slate-800">{report.title}</p>
        <p className="text-xs text-slate-400">
          {report.referenceNo}
          {report.companyName ? ` · ${report.companyName}` : ''}
          {' · '}{formatWhen(report.generatedAt ?? report.createdAt)}
        </p>
        {report.failureReason && (
          <p className="mt-1 text-xs text-red-600">{report.failureReason}</p>
        )}
      </td>
      <td className="px-5 py-3.5">
        <StatusPill label={STATUS_LABEL[report.status]} className={STATUS_STYLES[report.status]} />
        {report.status === 'GENERATING' || report.status === 'QUEUED' ? (
          <span className="ml-2 inline-flex items-center text-xs text-slate-400">
            <Clock className="mr-1 h-3 w-3" /> rendering
          </span>
        ) : null}
      </td>
      <td className="px-5 py-3.5">
        <p className="text-slate-700">{report.generatedByName}</p>
        <p className="text-xs text-slate-400">{report.generatedByRole.replace('_', ' ')}</p>
      </td>
      <td className="px-5 py-3.5">
        {report.approvedByName ? (
          <>
            <p className="inline-flex items-center text-slate-700">
              <ShieldCheck className="mr-1 h-3.5 w-3.5 text-green-600" />{report.approvedByName}
            </p>
            <p className="text-xs text-slate-400">{formatWhen(report.approvedAt)}</p>
          </>
        ) : (
          <span className="text-xs text-slate-400">
            {report.requiresApproval ? 'Not yet approved' : 'Not required'}
          </span>
        )}
      </td>
      <td className="px-5 py-3.5 text-xs text-slate-500">
        {report.pageCount ? `${report.pageCount} pages · ` : ''}{formatBytes(report.fileSizeBytes)}
      </td>
      <td className="px-5 py-3.5">
        <div className="flex flex-wrap items-center justify-end gap-1.5">
          {ready && (
            <>
              <button className="btn-ghost" onClick={onPreview} title="View in app">
                <Eye className="h-4 w-4" />
              </button>
              <button className="btn-ghost" onClick={onDownload} title="Download PDF">
                <Download className="h-4 w-4" />
              </button>
            </>
          )}
          {detail?.canSubmitForApproval && (
            <button className="btn-secondary text-xs" onClick={onSubmit}>
              <Send className="mr-1 h-3.5 w-3.5" /> Send for approval
            </button>
          )}
          {detail?.canDecide && (
            <>
              <button className="btn-primary text-xs" onClick={() => onDecide(true)}>
                <CheckCircle2 className="mr-1 h-3.5 w-3.5" /> Approve
              </button>
              <button className="btn-danger text-xs" onClick={() => onDecide(false)}>
                <XCircle className="mr-1 h-3.5 w-3.5" /> Return
              </button>
            </>
          )}
        </div>
      </td>
    </tr>
  );
}
