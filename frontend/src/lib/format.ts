import { formatDistanceToNow, format } from 'date-fns';
import type { ApplicationStatus, JobStatus } from '../types';

export function timeAgo(date?: string): string {
  if (!date) return '—';
  return formatDistanceToNow(new Date(date), { addSuffix: true });
}

export function formatDate(date?: string): string {
  if (!date) return '—';
  return format(new Date(date), 'MMM d, yyyy');
}

export function formatDateTime(date?: string): string {
  if (!date) return '—';
  return format(new Date(date), 'MMM d, yyyy · HH:mm');
}

export function humanize(value?: string): string {
  if (!value) return '';
  return value
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

export function formatSalary(min?: number, max?: number, currency = 'USD'): string | null {
  if (!min && !max) return null;
  const fmt = (n: number) =>
    new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 0 }).format(n);
  if (min && max) return `${fmt(min)} – ${fmt(max)}`;
  return fmt((min || max)!);
}

export const APPLICATION_STATUS_STYLES: Record<ApplicationStatus, string> = {
  SUBMITTED: 'bg-slate-100 text-slate-700',
  UNDER_REVIEW: 'bg-blue-100 text-blue-700',
  SHORTLISTED: 'bg-violet-100 text-violet-700',
  INTERVIEW: 'bg-amber-100 text-amber-700',
  OFFERED: 'bg-teal-100 text-teal-700',
  HIRED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-red-100 text-red-700',
  WITHDRAWN: 'bg-slate-100 text-slate-500',
};

export const JOB_STATUS_STYLES: Record<JobStatus, string> = {
  DRAFT: 'bg-slate-100 text-slate-600',
  PENDING_APPROVAL: 'bg-amber-100 text-amber-700',
  PUBLISHED: 'bg-green-100 text-green-700',
  CLOSED: 'bg-orange-100 text-orange-700',
  ARCHIVED: 'bg-slate-100 text-slate-500',
};

export function scoreColor(score?: number): string {
  if (score == null) return 'text-slate-400';
  if (score >= 70) return 'text-green-600';
  if (score >= 45) return 'text-amber-600';
  return 'text-red-500';
}

export function scoreBg(score?: number): string {
  if (score == null) return 'bg-slate-300';
  if (score >= 70) return 'bg-green-500';
  if (score >= 45) return 'bg-amber-500';
  return 'bg-red-500';
}
