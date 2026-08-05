import { Link } from 'react-router-dom';
import { FileText, Upload, Star, X, Eye, FolderOpen } from 'lucide-react';
import clsx from 'clsx';
import { Field, Spinner } from '../ui';
import ResumeViewer from './ResumeViewer';
import { formatBytes, useResumeFile } from '../../lib/useDocuments';
import type { DocumentLibrary } from '../../types';

/** What the candidate chose to send. The parent turns this into the request. */
export interface ApplyChoice {
  resumeMode: 'saved' | 'upload';
  documentId: string | null;
  file: File | null;
  /** Save a freshly uploaded file to the library as well, so next time is one click. */
  alsoSave: boolean;
  letterMode: 'saved' | 'write' | 'none';
  coverLetterId: string | null;
  letterText: string;
}

export function initialChoice(library?: DocumentLibrary): ApplyChoice {
  const defaultResume = library?.resumes.find((r) => r.isDefault) ?? library?.resumes[0];
  const defaultLetter = library?.coverLetters.find((c) => c.isDefault);
  return {
    resumeMode: defaultResume ? 'saved' : 'upload',
    documentId: defaultResume?.id ?? null,
    file: null,
    alsoSave: true,
    letterMode: defaultLetter ? 'saved' : 'none',
    coverLetterId: defaultLetter?.id ?? null,
    letterText: defaultLetter?.body ?? '',
  };
}

/**
 * Résumé and cover-letter selection for an application: reuse something saved, or send
 * something new. When a saved template is selected and left untouched we send its id so
 * the application records where the text came from; once it is edited we send the edited
 * text instead, because that is what the candidate actually wrote.
 */
export default function ApplyDocuments({
  library,
  value,
  onChange,
}: {
  library?: DocumentLibrary;
  value: ApplyChoice;
  onChange: (next: ApplyChoice) => void;
}) {
  const { preview, loading, open, download, close } = useResumeFile();
  const set = (patch: Partial<ApplyChoice>) => onChange({ ...value, ...patch });

  const resumes = library?.resumes ?? [];
  const letters = library?.coverLetters ?? [];
  const canSaveMore = !!library && resumes.length < library.resumeLimit;
  const selectedLetter = letters.find((c) => c.id === value.coverLetterId);

  return (
    <>
      {/* ------------------------------------------------------------ résumé */}
      <Field label="Résumé" required>
        {resumes.length > 0 && (
          <div className="mb-3 flex gap-1 rounded-lg bg-slate-100 p-1">
            <Segment active={value.resumeMode === 'saved'} onClick={() => set({ resumeMode: 'saved' })}>
              <FolderOpen className="h-3.5 w-3.5" /> Saved résumé
            </Segment>
            <Segment active={value.resumeMode === 'upload'} onClick={() => set({ resumeMode: 'upload' })}>
              <Upload className="h-3.5 w-3.5" /> Upload a new file
            </Segment>
          </div>
        )}

        {value.resumeMode === 'saved' && resumes.length > 0 ? (
          <div className="space-y-2">
            {resumes.map((r) => (
              <label
                key={r.id}
                className={clsx(
                  'flex cursor-pointer items-center gap-3 rounded-lg border px-3 py-2.5 transition-colors',
                  value.documentId === r.id
                    ? 'border-brand-500 bg-brand-50/50 ring-1 ring-brand-500'
                    : 'border-slate-200 hover:border-slate-300',
                )}
              >
                <input
                  type="radio"
                  name="saved-resume"
                  className="h-4 w-4 shrink-0 accent-brand-600"
                  checked={value.documentId === r.id}
                  onChange={() => set({ documentId: r.id })}
                />
                <FileText className="h-4 w-4 shrink-0 text-brand-600" />
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-1.5">
                    <span className="truncate text-sm font-medium text-slate-800">{r.label}</span>
                    {r.isDefault && <Star className="h-3 w-3 shrink-0 text-brand-500" />}
                  </span>
                  <span className="block truncate text-xs text-slate-400">
                    {r.fileName}
                    {r.sizeBytes ? ` · ${formatBytes(r.sizeBytes)}` : ''}
                  </span>
                </span>
                <button
                  type="button"
                  className="flex shrink-0 items-center gap-1 text-xs font-medium text-slate-500 hover:text-brand-600"
                  onClick={(e) => {
                    e.preventDefault();
                    open(r.id, r.fileName);
                  }}
                >
                  {loading === r.id ? <Spinner className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
                  View
                </button>
              </label>
            ))}
            <p className="text-xs text-slate-400">
              The employer receives a copy of this file. Changing it later in{' '}
              <Link to="/candidate/documents" className="text-brand-600 hover:underline">My documents</Link>{' '}
              won’t affect applications you’ve already sent.
            </p>
          </div>
        ) : (
          <div>
            {value.file ? (
              <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5">
                <span className="flex min-w-0 items-center gap-2 text-sm text-slate-700">
                  <FileText className="h-4 w-4 shrink-0 text-brand-600" />
                  <span className="truncate">{value.file.name}</span>
                </span>
                <button type="button" onClick={() => set({ file: null })} className="text-slate-400 hover:text-red-500">
                  <X className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <label className="flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-300 py-8 hover:border-brand-400 hover:bg-brand-50/30">
                <Upload className="h-6 w-6 text-slate-400" />
                <span className="mt-2 text-sm text-slate-500">Click to upload your résumé</span>
                <span className="mt-0.5 text-xs text-slate-400">PDF, DOCX or TXT · max 10MB</span>
                <input
                  type="file"
                  accept=".pdf,.docx,.txt"
                  className="hidden"
                  onChange={(e) => set({ file: e.target.files?.[0] || null })}
                />
              </label>
            )}
            {value.file && canSaveMore && (
              <label className="mt-2 flex cursor-pointer items-start gap-2 text-xs text-slate-600">
                <input
                  type="checkbox"
                  className="mt-0.5 h-3.5 w-3.5 accent-brand-600"
                  checked={value.alsoSave}
                  onChange={(e) => set({ alsoSave: e.target.checked })}
                />
                Keep this in my document library, so I don’t have to upload it again
              </label>
            )}
          </div>
        )}
      </Field>

      {/* ------------------------------------------------------ cover letter */}
      <Field label="Cover letter" hint={value.letterMode === 'none' ? 'Optional' : undefined}>
        <div className="mb-3 flex gap-1 rounded-lg bg-slate-100 p-1">
          {letters.length > 0 && (
            <Segment
              active={value.letterMode === 'saved'}
              onClick={() => {
                const pick = letters.find((c) => c.id === value.coverLetterId)
                  ?? letters.find((c) => c.isDefault) ?? letters[0];
                set({ letterMode: 'saved', coverLetterId: pick.id, letterText: pick.body });
              }}
            >
              <FolderOpen className="h-3.5 w-3.5" /> Saved
            </Segment>
          )}
          <Segment
            active={value.letterMode === 'write'}
            onClick={() => set({ letterMode: 'write', coverLetterId: null })}
          >
            Write one
          </Segment>
          <Segment
            active={value.letterMode === 'none'}
            onClick={() => set({ letterMode: 'none', coverLetterId: null, letterText: '' })}
          >
            Skip
          </Segment>
        </div>

        {value.letterMode === 'saved' && letters.length > 0 && (
          <div className="space-y-2">
            <select
              className="input"
              value={value.coverLetterId ?? ''}
              onChange={(e) => {
                const pick = letters.find((c) => c.id === e.target.value);
                if (pick) set({ coverLetterId: pick.id, letterText: pick.body });
              }}
            >
              {letters.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.label}
                  {c.isDefault ? ' (default)' : ''}
                </option>
              ))}
            </select>
            <textarea
              className="input min-h-40"
              value={value.letterText}
              onChange={(e) => set({ letterText: e.target.value })}
            />
            <p className="text-xs text-slate-400">
              {selectedLetter && value.letterText === selectedLetter.body
                ? 'Tailoring this to the role usually helps — edits here don’t change your saved template.'
                : 'Edited for this application. Your saved template is untouched.'}
            </p>
          </div>
        )}

        {value.letterMode === 'write' && (
          <textarea
            className="input min-h-40"
            value={value.letterText}
            onChange={(e) => set({ letterText: e.target.value })}
            placeholder="Tell the hiring team why you’re a great fit…"
          />
        )}

        {value.letterMode === 'none' && (
          <p className="rounded-lg bg-slate-50 px-3 py-2.5 text-sm text-slate-500">
            Applying without a cover letter.
            {letters.length === 0 && (
              <>
                {' '}You can save reusable templates in{' '}
                <Link to="/candidate/documents" className="text-brand-600 hover:underline">My documents</Link>.
              </>
            )}
          </p>
        )}
      </Field>

      <ResumeViewer
        preview={preview}
        onClose={close}
        onDownload={preview ? () => download(preview.id, preview.fileName) : undefined}
      />
    </>
  );
}

function Segment({
  active, onClick, children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={clsx(
        'flex flex-1 items-center justify-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold transition-colors',
        active ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700',
      )}
    >
      {children}
    </button>
  );
}
