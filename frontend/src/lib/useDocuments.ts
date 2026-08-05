import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, apiErrorMessage } from './api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import type { DocumentLibrary, ResumeInsights } from '../types';

export const LIBRARY_KEY = ['document-library'];
export const INSIGHTS_KEY = ['resume-insights'];

/** The candidate's saved résumés and cover letters. No-op for other roles. */
export function useDocumentLibrary(enabled = true) {
  const { user } = useAuth();
  return useQuery({
    queryKey: LIBRARY_KEY,
    enabled: enabled && user?.role === 'CANDIDATE',
    queryFn: async () => (await api.get<DocumentLibrary>('/documents')).data,
  });
}

export function useResumeInsights(enabled = true) {
  const { user } = useAuth();
  return useQuery({
    queryKey: INSIGHTS_KEY,
    enabled: enabled && user?.role === 'CANDIDATE',
    queryFn: async () => (await api.get<ResumeInsights>('/documents/insights')).data,
  });
}

export interface ResumePreview {
  /** Which library résumé is on screen, so the viewer can offer to download it. */
  id: string;
  url: string;
  ext: string;
  fileName: string;
}

/**
 * Opening and downloading a saved résumé. The file lives behind an authenticated
 * endpoint, so it cannot simply be an href: the bytes are fetched with the access token
 * and handed to the viewer as a blob URL, which is then released again.
 */
export function useResumeFile() {
  const toast = useToast();
  const [preview, setPreview] = useState<ResumePreview | null>(null);
  const [loading, setLoading] = useState<string | null>(null);

  const fetchBlob = async (id: string) =>
    (await api.get(`/documents/resumes/${id}/file`, { responseType: 'blob' })).data as Blob;

  const open = async (id: string, fileName: string) => {
    setLoading(id);
    try {
      const blob = await fetchBlob(id);
      const ext = (fileName.split('.').pop() || '').toLowerCase();
      // give the blob a viewer-friendly type, or the iframe offers a download instead
      const mime = ext === 'pdf' ? 'application/pdf' : ext === 'txt' ? 'text/plain' : blob.type;
      setPreview({
        id,
        url: URL.createObjectURL(new Blob([blob], { type: mime || 'application/octet-stream' })),
        ext,
        fileName,
      });
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    } finally {
      setLoading(null);
    }
  };

  const download = async (id: string, fileName: string) => {
    setLoading(id);
    try {
      const url = URL.createObjectURL(await fetchBlob(id));
      const a = document.createElement('a');
      a.href = url;
      a.download = fileName;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    } finally {
      setLoading(null);
    }
  };

  const close = () =>
    setPreview((p) => {
      if (p) URL.revokeObjectURL(p.url);
      return null;
    });

  // release the blob if the viewer is replaced or the page unmounts while it is open
  useEffect(() => () => {
    if (preview) URL.revokeObjectURL(preview.url);
  }, [preview]);

  return { preview, loading, open, download, close };
}

export function formatBytes(bytes?: number | null): string {
  if (bytes == null) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
