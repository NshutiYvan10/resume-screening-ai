import { ReactNode, useState } from 'react';
import { Loader2 } from 'lucide-react';
import clsx from 'clsx';

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={clsx('animate-spin', className)} />;
}

/**
 * Renders a remote image, falling back to `children` when there is no src or the
 * browser cannot decode it. Without this, an unreadable image leaves a broken-image
 * icon on the page with no hint that anything went wrong.
 */
export function ImageWithFallback({
  src,
  alt = '',
  className,
  children,
}: {
  src?: string | null;
  alt?: string;
  className?: string;
  children?: ReactNode;
}) {
  // track the failed URL rather than a boolean, so a newly uploaded image is retried
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  if (!src || failedSrc === src) {
    return <>{children}</>;
  }
  return <img src={src} alt={alt} className={className} onError={() => setFailedSrc(src)} />;
}

export function PageLoader() {
  return (
    <div className="flex items-center justify-center py-24">
      <Spinner className="h-8 w-8 text-brand-600" />
    </div>
  );
}

export function Badge({ children, className }: { children: ReactNode; className?: string }) {
  return <span className={clsx('badge', className)}>{children}</span>;
}

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-white py-16 px-6 text-center">
      {icon && <div className="mb-4 text-slate-300">{icon}</div>}
      <h3 className="text-base font-semibold text-slate-800">{title}</h3>
      {description && <p className="mt-1 max-w-md text-sm text-slate-500">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

export function Field({
  label,
  error,
  children,
  hint,
  required,
}: {
  label: string;
  error?: string;
  children: ReactNode;
  hint?: string;
  required?: boolean;
}) {
  return (
    <div>
      <label className="label">
        {label}
        {required && <span className="text-red-500"> *</span>}
      </label>
      {children}
      {hint && !error && <p className="mt-1 text-xs text-slate-400">{hint}</p>}
      {error && <p className="mt-1 text-xs text-red-500">{error}</p>}
    </div>
  );
}

export function Modal({
  open,
  onClose,
  title,
  children,
  maxWidth = 'max-w-lg',
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  maxWidth?: string;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-slate-900/50 backdrop-blur-sm" onClick={onClose} />
      <div className={clsx('relative w-full card p-6 max-h-[90vh] overflow-y-auto', maxWidth)}>
        <h2 className="text-lg font-semibold text-slate-900 mb-4">{title}</h2>
        {children}
      </div>
    </div>
  );
}

export function StatusPill({ label, className }: { label: string; className: string }) {
  return <span className={clsx('badge', className)}>{label}</span>;
}

export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-between pt-4">
      <p className="text-sm text-slate-500">
        Page {page + 1} of {totalPages}
      </p>
      <div className="flex gap-2">
        <button
          className="btn-secondary py-1.5 px-3"
          disabled={page === 0}
          onClick={() => onChange(page - 1)}
        >
          Previous
        </button>
        <button
          className="btn-secondary py-1.5 px-3"
          disabled={page >= totalPages - 1}
          onClick={() => onChange(page + 1)}
        >
          Next
        </button>
      </div>
    </div>
  );
}

/**
 * A person, everywhere. Falls back to initials so a missing or unreadable photo still
 * reads as a human rather than a broken image.
 *
 * Candidate photos are deliberately NOT rendered in screening and shortlist views -
 * showing a face next to a hiring decision is a known bias vector, and the platform
 * already flags bias elsewhere. Pass a photo only where identity is legitimately useful.
 */
export function Avatar({
  name, photoUrl, size = 'md', className = '',
}: {
  name?: string;
  photoUrl?: string | null;
  size?: 'xs' | 'sm' | 'md' | 'lg';
  className?: string;
}) {
  const dims = { xs: 'h-6 w-6 text-[10px]', sm: 'h-8 w-8 text-xs', md: 'h-10 w-10 text-sm', lg: 'h-16 w-16 text-lg' }[size];
  const initials = (name || '?')
    .split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join('') || '?';
  return (
    <span className={clsx('inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-brand-100 font-semibold text-brand-700', dims, className)}
          title={name}>
      <ImageWithFallback src={photoUrl} alt={name ? `${name} profile photo` : ''}
                        className="h-full w-full object-cover">
        <span>{initials}</span>
      </ImageWithFallback>
    </span>
  );
}
