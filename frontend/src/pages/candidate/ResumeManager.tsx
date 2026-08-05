import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import {
  FileText, Upload, Star, Trash2, Pencil, RefreshCw, Eye, Download, Plus, Search,
  AlertTriangle, TrendingUp, Target, Sparkles, Info,
} from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/PageHeader';
import { EmptyState, Field, Modal, PageLoader, Spinner, Badge } from '../../components/ui';
import ResumeViewer from '../../components/candidate/ResumeViewer';
import {
  useDocumentLibrary, useResumeInsights, useResumeFile, formatBytes,
  LIBRARY_KEY, INSIGHTS_KEY,
} from '../../lib/useDocuments';
import { formatDate } from '../../lib/format';
import type { ResumeInsight, ResumeInsights, SavedCoverLetter, SavedResume } from '../../types';

/** What the screener said about how readable a file was. */
const PARSE_HINT: Record<string, { label: string; tone: string; detail: string }> = {
  poor: {
    label: 'Hard to read',
    tone: 'bg-red-50 text-red-700',
    detail: 'The screener could barely extract text from this file. A text-based PDF reads far better than a scan or an image.',
  },
  partial: {
    label: 'Partly readable',
    tone: 'bg-amber-50 text-amber-800',
    detail: 'Some details could not be extracted from this file, so parts of your experience may not have been counted.',
  },
};

export default function ResumeManager() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const replaceInput = useRef<HTMLInputElement>(null);

  const { data: library, isLoading } = useDocumentLibrary();
  const { data: insights } = useResumeInsights();
  const { preview, loading: fileLoading, open, download, close } = useResumeFile();

  const [uploadOpen, setUploadOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [label, setLabel] = useState('');
  const [renaming, setRenaming] = useState<SavedResume | null>(null);
  const [replacingId, setReplacingId] = useState<string | null>(null);
  const [editing, setEditing] = useState<Partial<SavedCoverLetter> | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: LIBRARY_KEY });
    queryClient.invalidateQueries({ queryKey: INSIGHTS_KEY });
  };

  /** One wrapper for every mutation here: they all refresh and all report the same way. */
  const run = async (work: () => Promise<unknown>, success: string) => {
    setBusy(true);
    try {
      await work();
      refresh();
      toast(success, 'success');
      return true;
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
      return false;
    } finally {
      setBusy(false);
    }
  };

  const upload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      toast('Choose a file first', 'error');
      return;
    }
    const fd = new FormData();
    fd.append('file', file);
    if (label.trim()) fd.append('label', label.trim());
    const ok = await run(
      () => api.post('/documents/resumes', fd, { headers: { 'Content-Type': 'multipart/form-data' } }),
      'Résumé saved to your library',
    );
    if (ok) {
      setUploadOpen(false);
      setFile(null);
      setLabel('');
    }
  };

  const replace = async (files: FileList | null) => {
    const chosen = files?.[0];
    if (!chosen || !replacingId) return;
    const fd = new FormData();
    fd.append('file', chosen);
    await run(
      () => api.post(`/documents/resumes/${replacingId}/replace`, fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }),
      'File replaced — applications you already submitted are unchanged',
    );
    setReplacingId(null);
    if (replaceInput.current) replaceInput.current.value = '';
  };

  const rename = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!renaming) return;
    const ok = await run(
      () => api.patch(`/documents/resumes/${renaming.id}`, { label: renaming.label }),
      'Renamed',
    );
    if (ok) setRenaming(null);
  };

  const saveCoverLetter = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editing?.label?.trim() || !editing?.body?.trim()) {
      toast('A cover letter needs a name and some text', 'error');
      return;
    }
    const body = { label: editing.label.trim(), body: editing.body };
    const ok = await run(
      () => (editing.id
        ? api.put(`/documents/cover-letters/${editing.id}`, body)
        : api.post('/documents/cover-letters', body)),
      editing.id ? 'Cover letter updated' : 'Cover letter saved',
    );
    if (ok) setEditing(null);
  };

  const setDefault = (kind: 'resumes' | 'cover-letters', id: string) =>
    run(() => api.post(`/documents/${kind}/${id}/default`), 'Default updated');

  const remove = (kind: 'resumes' | 'cover-letters', id: string, what: string) => {
    if (!confirm(`Delete ${what}? Applications you've already submitted keep their own copy and won't change.`)) return;
    return run(() => api.delete(`/documents/${kind}/${id}`), 'Deleted');
  };

  if (isLoading || !library) return <PageLoader />;

  const insightFor = (id: string) => insights?.resumes.find((r) => r.documentId === id);
  const atResumeLimit = library.resumes.length >= library.resumeLimit;
  const atLetterLimit = library.coverLetters.length >= library.coverLetterLimit;

  return (
    <div>
      <PageHeader
        title="My documents"
        description="Keep your résumés and cover letters here, then apply without uploading them again"
        action={
          <Link to="/candidate" className="btn-secondary">
            <Search className="h-4 w-4" /> Browse jobs
          </Link>
        }
      />

      {/* ---------------------------------------------------------- résumés */}
      <section>
        <div className="mb-3 flex items-end justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Résumés</h2>
            <p className="text-sm text-slate-500">
              {library.resumes.length} of {library.resumeLimit} saved
            </p>
          </div>
          <button
            className="btn-primary"
            disabled={atResumeLimit}
            title={atResumeLimit ? 'Delete one to add another' : undefined}
            onClick={() => setUploadOpen(true)}
          >
            <Upload className="h-4 w-4" /> Upload résumé
          </button>
        </div>

        {!library.resumes.length ? (
          <EmptyState
            icon={<FileText className="h-12 w-12" />}
            title="No résumés saved yet"
            description="Upload one here and every future application can reuse it — no more attaching the same file to every job."
            action={
              <button className="btn-primary" onClick={() => setUploadOpen(true)}>
                <Upload className="h-4 w-4" /> Upload your résumé
              </button>
            }
          />
        ) : (
          <div className="space-y-3">
            {library.resumes.map((r) => (
              <ResumeCard
                key={r.id}
                resume={r}
                insight={insightFor(r.id)}
                busy={busy}
                fileLoading={fileLoading === r.id}
                onPreview={() => open(r.id, r.fileName)}
                onDownload={() => download(r.id, r.fileName)}
                onRename={() => setRenaming(r)}
                onReplace={() => {
                  setReplacingId(r.id);
                  replaceInput.current?.click();
                }}
                onMakeDefault={() => setDefault('resumes', r.id)}
                onDelete={() => remove('resumes', r.id, `“${r.label}”`)}
              />
            ))}
          </div>
        )}
        {/* one hidden input for every card's Replace action */}
        <input
          ref={replaceInput}
          type="file"
          accept=".pdf,.docx,.txt"
          className="hidden"
          onChange={(e) => replace(e.target.files)}
        />
      </section>

      {/* --------------------------------------------------------- insights */}
      {insights && <InsightsPanel insights={insights} />}

      {/* ---------------------------------------------------- cover letters */}
      <section className="mt-8">
        <div className="mb-3 flex items-end justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Cover letters</h2>
            <p className="text-sm text-slate-500">
              Reusable text you can adapt per job · {library.coverLetters.length} of{' '}
              {library.coverLetterLimit} saved
            </p>
          </div>
          <button
            className="btn-secondary"
            disabled={atLetterLimit}
            title={atLetterLimit ? 'Delete one to add another' : undefined}
            onClick={() => setEditing({ label: '', body: '' })}
          >
            <Plus className="h-4 w-4" /> New cover letter
          </button>
        </div>

        {!library.coverLetters.length ? (
          <div className="card p-8 text-center">
            <Pencil className="mx-auto h-8 w-8 text-slate-300" />
            <p className="mt-3 text-sm font-medium text-slate-700">No saved cover letters</p>
            <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
              Save a template you can start from, then tailor it to each role before you send it.
            </p>
          </div>
        ) : (
          <div className="grid gap-3 lg:grid-cols-2">
            {library.coverLetters.map((c) => (
              <div key={c.id} className="card flex flex-col p-5">
                <div className="flex items-start justify-between gap-3">
                  <h3 className="font-semibold text-slate-800">{c.label}</h3>
                  {c.isDefault && (
                    <Badge className="shrink-0 bg-brand-50 text-brand-700">
                      <Star className="h-3 w-3" /> Default
                    </Badge>
                  )}
                </div>
                <p className="mt-2 line-clamp-4 flex-1 whitespace-pre-wrap text-sm text-slate-500">{c.body}</p>
                <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-slate-100 pt-3">
                  <span className="text-xs text-slate-400">
                    {c.body.trim().split(/\s+/).filter(Boolean).length} words · updated {formatDate(c.updatedAt)}
                  </span>
                  <div className="ml-auto flex items-center gap-3">
                    <CardAction icon={<Pencil className="h-3.5 w-3.5" />} label="Edit"
                      onClick={() => setEditing(c)} disabled={busy} />
                    {!c.isDefault && (
                      <CardAction icon={<Star className="h-3.5 w-3.5" />} label="Make default"
                        onClick={() => setDefault('cover-letters', c.id)} disabled={busy} />
                    )}
                    <CardAction icon={<Trash2 className="h-3.5 w-3.5" />} label="Delete" danger
                      onClick={() => remove('cover-letters', c.id, `“${c.label}”`)} disabled={busy} />
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {/* ----------------------------------------------------------- modals */}
      <Modal open={uploadOpen} onClose={() => setUploadOpen(false)} title="Upload a résumé">
        <form onSubmit={upload} className="space-y-4">
          <Field label="File" required hint="PDF, DOCX or TXT · max 10MB">
            {file ? (
              <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5">
                <span className="flex min-w-0 items-center gap-2 text-sm text-slate-700">
                  <FileText className="h-4 w-4 shrink-0 text-brand-600" />
                  <span className="truncate">{file.name}</span>
                </span>
                <button type="button" onClick={() => setFile(null)} className="text-xs text-slate-500 hover:text-red-600">
                  Change
                </button>
              </div>
            ) : (
              <label className="flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-300 py-8 hover:border-brand-400 hover:bg-brand-50/30">
                <Upload className="h-6 w-6 text-slate-400" />
                <span className="mt-2 text-sm text-slate-500">Click to choose a file</span>
                <input type="file" accept=".pdf,.docx,.txt" className="hidden"
                  onChange={(e) => setFile(e.target.files?.[0] || null)} />
              </label>
            )}
          </Field>
          <Field label="Name it" hint="Optional — helps when you keep more than one version, e.g. “Backend CV 2026”">
            <input className="input" value={label} onChange={(e) => setLabel(e.target.value)}
              placeholder={file ? file.name.replace(/\.[^.]+$/, '') : 'Backend CV'} />
          </Field>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setUploadOpen(false)}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={busy}>
              {busy && <Spinner className="h-4 w-4" />} Save to library
            </button>
          </div>
        </form>
      </Modal>

      <Modal open={!!renaming} onClose={() => setRenaming(null)} title="Rename résumé">
        <form onSubmit={rename} className="space-y-4">
          <Field label="Name" required hint="Only the name changes — the file itself stays exactly as it is">
            <input className="input" autoFocus value={renaming?.label ?? ''}
              onChange={(e) => setRenaming((r) => (r ? { ...r, label: e.target.value } : r))} />
          </Field>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setRenaming(null)}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={busy || !renaming?.label.trim()}>
              {busy && <Spinner className="h-4 w-4" />} Save
            </button>
          </div>
        </form>
      </Modal>

      <Modal open={!!editing} onClose={() => setEditing(null)}
        title={editing?.id ? 'Edit cover letter' : 'New cover letter'} maxWidth="max-w-2xl">
        <form onSubmit={saveCoverLetter} className="space-y-4">
          <Field label="Name" required hint="For your own reference — employers never see it">
            <input className="input" autoFocus value={editing?.label ?? ''}
              onChange={(e) => setEditing((c) => ({ ...c, label: e.target.value }))}
              placeholder="e.g. Backend roles — general" />
          </Field>
          <Field label="Text" required hint="You can still adapt it per job when you apply">
            <textarea className="input min-h-64" value={editing?.body ?? ''}
              onChange={(e) => setEditing((c) => ({ ...c, body: e.target.value }))}
              placeholder="Dear hiring team, …" />
          </Field>
          {editing?.id && (
            <p className="flex items-start gap-2 rounded-lg bg-slate-50 p-3 text-xs text-slate-500">
              <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" />
              Editing this changes what future applications start from. Letters you have already
              sent keep the text you sent.
            </p>
          )}
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setEditing(null)}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={busy}>
              {busy && <Spinner className="h-4 w-4" />} {editing?.id ? 'Save changes' : 'Save cover letter'}
            </button>
          </div>
        </form>
      </Modal>

      <ResumeViewer preview={preview} onClose={close}
        onDownload={preview ? () => download(preview.id, preview.fileName) : undefined} />
    </div>
  );
}

// ------------------------------------------------------------------ pieces

function CardAction({
  icon, label, onClick, disabled, danger,
}: {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  disabled?: boolean;
  danger?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`flex items-center gap-1 text-xs font-medium disabled:opacity-40 ${
        danger ? 'text-red-600 hover:text-red-700' : 'text-slate-500 hover:text-brand-600'
      }`}
    >
      {icon} {label}
    </button>
  );
}

function ResumeCard({
  resume, insight, busy, fileLoading,
  onPreview, onDownload, onRename, onReplace, onMakeDefault, onDelete,
}: {
  resume: SavedResume;
  insight?: ResumeInsight;
  busy: boolean;
  fileLoading: boolean;
  onPreview: () => void;
  onDownload: () => void;
  onRename: () => void;
  onReplace: () => void;
  onMakeDefault: () => void;
  onDelete: () => void;
}) {
  const parse = insight?.parseQuality ? PARSE_HINT[insight.parseQuality] : undefined;
  return (
    <div className="card p-5">
      <div className="flex items-start gap-4">
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
          <FileText className="h-5 w-5" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-semibold text-slate-800">{resume.label}</h3>
            {resume.isDefault && (
              <Badge className="bg-brand-50 text-brand-700"><Star className="h-3 w-3" /> Default</Badge>
            )}
            {parse && <Badge className={parse.tone}><AlertTriangle className="h-3 w-3" /> {parse.label}</Badge>}
          </div>
          <p className="mt-0.5 truncate text-sm text-slate-400">
            {resume.fileName}
            {resume.sizeBytes ? ` · ${formatBytes(resume.sizeBytes)}` : ''} · added {formatDate(resume.createdAt)}
          </p>

          {/* real numbers only: a résumé nobody has screened says so */}
          {insight && insight.applications > 0 && (
            <p className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
              <span className="text-slate-600">
                <strong className="font-semibold text-slate-800">{insight.applications}</strong>{' '}
                {insight.applications === 1 ? 'application' : 'applications'}
              </span>
              {insight.averageMatchScore != null && (
                <span className="text-slate-600">
                  avg match{' '}
                  {/* fixed to one decimal so 78 and 88.5 don't sit side by side */}
                  <strong className="font-semibold text-slate-800">
                    {insight.averageMatchScore.toFixed(1)}
                  </strong>
                </span>
              )}
              {insight.interviews > 0 && (
                <span className="text-teal-700">
                  {insight.interviews} {insight.interviews === 1 ? 'interview' : 'interviews'}
                </span>
              )}
              {insight.offers > 0 && (
                <span className="text-green-700">
                  {insight.offers} {insight.offers === 1 ? 'offer' : 'offers'}
                </span>
              )}
            </p>
          )}
          {insight && insight.applications === 0 && (
            <p className="mt-2 text-sm text-slate-400">Not used in an application yet</p>
          )}

          {/* Warnings are worth showing even when the file read cleanly overall - "no
              contact email detected" is actionable regardless of the verdict. */}
          {(parse || !!insight?.parseWarnings.length) && (
            <p className="mt-2 rounded-lg bg-slate-50 p-2.5 text-xs text-slate-600">
              {parse?.detail}
              {!!insight?.parseWarnings.length && (
                <span className={parse ? 'mt-1 block text-slate-500' : undefined}>
                  What the screener couldn’t find: {insight.parseWarnings.join('; ')}
                </span>
              )}
            </p>
          )}
        </div>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-slate-100 pt-3">
        <CardAction icon={fileLoading ? <Spinner className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
          label="Preview" onClick={onPreview} disabled={fileLoading} />
        <CardAction icon={<Download className="h-3.5 w-3.5" />} label="Download" onClick={onDownload} disabled={fileLoading} />
        <CardAction icon={<Pencil className="h-3.5 w-3.5" />} label="Rename" onClick={onRename} disabled={busy} />
        <CardAction icon={<RefreshCw className="h-3.5 w-3.5" />} label="Replace file" onClick={onReplace} disabled={busy} />
        {!resume.isDefault && (
          <CardAction icon={<Star className="h-3.5 w-3.5" />} label="Make default" onClick={onMakeDefault} disabled={busy} />
        )}
        <CardAction icon={<Trash2 className="h-3.5 w-3.5" />} label="Delete" danger onClick={onDelete} disabled={busy} />
      </div>
    </div>
  );
}

/**
 * What screenings actually said. Every figure traces back to a completed screening, so
 * the panel stays hidden until there is at least one - an empty chart teaches nothing.
 */
function InsightsPanel({ insights }: { insights: ResumeInsights }) {
  const hasSignal = insights.skillGaps.length > 0 || insights.skillStrengths.length > 0;
  if (!hasSignal) return null;

  return (
    <section className="mt-8">
      <div className="mb-3">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-slate-900">
          <Sparkles className="h-5 w-5 text-brand-600" /> What screenings say about your résumés
        </h2>
        <p className="text-sm text-slate-500">
          Taken from the {insights.attributedApplications}{' '}
          {insights.attributedApplications === 1 ? 'application' : 'applications'} you sent from this library
          {insights.unattributedApplications > 0 && (
            <>
              {' '}· {insights.unattributedApplications} earlier{' '}
              {insights.unattributedApplications === 1 ? 'application used' : 'applications used'} a one-off
              upload and can’t be traced to a saved résumé
            </>
          )}
        </p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="card p-5">
          <h3 className="flex items-center gap-2 font-semibold text-slate-800">
            <Target className="h-4 w-4 text-amber-600" /> Skills the roles wanted but couldn’t find
          </h3>
          <p className="mt-1 text-sm text-slate-500">
            Required skills the screener couldn’t evidence from your résumé. A skill you do have
            is worth naming explicitly.
          </p>
          {insights.skillGaps.length ? (
            <div className="mt-3 flex flex-wrap gap-2">
              {insights.skillGaps.map((s) => (
                <span key={s.skill} className="badge bg-amber-50 text-amber-800">
                  {s.skill}
                  <span className="text-amber-600">×{s.occurrences}</span>
                </span>
              ))}
            </div>
          ) : (
            <p className="mt-3 text-sm text-slate-400">
              Nothing recurring — your résumé covered the required skills of the roles you applied to.
            </p>
          )}
        </div>

        <div className="card p-5">
          <h3 className="flex items-center gap-2 font-semibold text-slate-800">
            <TrendingUp className="h-4 w-4 text-green-600" /> Skills you’re being credited for
          </h3>
          <p className="mt-1 text-sm text-slate-500">
            What screeners matched you on most often — your strongest signals.
          </p>
          {insights.skillStrengths.length ? (
            <div className="mt-3 flex flex-wrap gap-2">
              {insights.skillStrengths.map((s) => (
                <span key={s.skill} className="badge bg-green-50 text-green-800">
                  {s.skill}
                  <span className="text-green-600">×{s.occurrences}</span>
                </span>
              ))}
            </div>
          ) : (
            <p className="mt-3 text-sm text-slate-400">No matches recorded yet.</p>
          )}
        </div>
      </div>
    </section>
  );
}
