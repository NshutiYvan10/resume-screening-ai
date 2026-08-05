import { FileText, Download } from 'lucide-react';
import { Modal } from '../ui';
import type { ResumePreview } from '../../lib/useDocuments';

/**
 * In-app viewer for a saved résumé. PDF and TXT render inline; anything else (DOCX)
 * cannot be displayed by the browser, so the honest answer is a download button rather
 * than an empty frame.
 */
export default function ResumeViewer({
  preview,
  onClose,
  onDownload,
}: {
  preview: ResumePreview | null;
  onClose: () => void;
  onDownload?: () => void;
}) {
  const inline = preview?.ext === 'pdf' || preview?.ext === 'txt';
  return (
    <Modal open={!!preview} onClose={onClose} title="Résumé preview" maxWidth="max-w-4xl">
      {preview && (
        <>
          <div className="mb-3 flex items-center justify-between gap-3">
            <p className="flex min-w-0 items-center gap-2 text-sm text-slate-500">
              <FileText className="h-4 w-4 shrink-0" />
              <span className="truncate">{preview.fileName}</span>
            </p>
            {onDownload && (
              <button className="btn-secondary shrink-0 px-3 py-1.5" onClick={onDownload}>
                <Download className="h-4 w-4" /> Download
              </button>
            )}
          </div>
          {inline ? (
            <iframe src={preview.url} title="Résumé" className="h-[70vh] w-full rounded-lg border border-slate-200" />
          ) : (
            <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 py-14 text-center">
              <FileText className="mx-auto h-10 w-10 text-slate-300" />
              <p className="mt-3 text-sm text-slate-600">
                Browsers can’t preview .{preview.ext || 'this'} files
              </p>
              <p className="mt-1 text-xs text-slate-400">
                Download it to check the exact document recruiters receive.
              </p>
            </div>
          )}
        </>
      )}
    </Modal>
  );
}
