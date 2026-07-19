import { ReactNode } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell,
  AreaChart, Area, PieChart, Pie, CartesianGrid, Legend,
} from 'recharts';
import { Printer, Download } from 'lucide-react';
import { humanize } from '../../lib/format';

/** Brand palette reused across every chart so reports read consistently. */
export const CHART_COLORS = ['#2563eb', '#7c3aed', '#0891b2', '#f59e0b', '#16a34a', '#ef4444', '#64748b', '#db2777'];

export function scoreBucketColor(low: number): string {
  return low >= 70 ? '#16a34a' : low >= 45 ? '#f59e0b' : '#ef4444';
}

// ---------------------------------------------------------------- layout

export function ReportSection({
  title, subtitle, action, children, className = '',
}: {
  title: string;
  subtitle?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={`card p-6 ${className}`}>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-slate-800">{title}</h3>
          {subtitle && <p className="mt-0.5 text-xs text-slate-400">{subtitle}</p>}
        </div>
        {action}
      </div>
      {children}
    </div>
  );
}

/**
 * Branded report header. Company reports pass the company's name/logo (feels
 * branded to them); platform/candidate reports fall back to the ResumeAI mark.
 */
export function ReportHeader({
  title, description, brandName, brandLogoUrl, action,
}: {
  title: string;
  description?: string;
  brandName?: string;
  brandLogoUrl?: string;
  action?: ReactNode;
}) {
  return (
    <div className="mb-6 overflow-hidden rounded-2xl border border-slate-200 bg-gradient-to-r from-slate-900 via-brand-800 to-brand-600 p-6 text-white">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-4">
          {brandLogoUrl ? (
            <img src={brandLogoUrl} alt="" className="h-12 w-12 rounded-xl border border-white/30 bg-white object-cover" />
          ) : (
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-white/15 text-lg font-extrabold">
              {(brandName || 'R').charAt(0).toUpperCase()}
            </div>
          )}
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-brand-100">
              {brandName ? `${brandName} · ResumeAI` : 'ResumeAI Analytics'}
            </p>
            <h1 className="text-2xl font-bold">{title}</h1>
            {description && <p className="mt-0.5 text-sm text-brand-50/90">{description}</p>}
          </div>
        </div>
        {action && <div className="flex shrink-0 gap-2 print:hidden">{action}</div>}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- charts

/** AI match-score distribution across 10-point buckets. */
export function ScoreDistributionChart({ data }: { data?: Record<string, number> }) {
  const rows = Object.entries(data || {}).map(([range, count]) => ({ range, count }));
  const total = rows.reduce((a, b) => a + b.count, 0);
  if (!total) return <EmptyChart label="No screening data yet" />;
  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={rows}>
        <XAxis dataKey="range" tick={{ fontSize: 10 }} interval={0} angle={-30} textAnchor="end" height={50} />
        <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
        <Tooltip />
        <Bar dataKey="count" radius={[4, 4, 0, 0]}>
          {rows.map((d, i) => (
            <Cell key={i} fill={scoreBucketColor(parseInt(d.range.split('-')[0], 10))} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

/** Monthly trend area chart from [{month, count}]. */
export function TrendChart({ data, color = '#2563eb', label = 'Count' }: {
  data?: { month: string; count: number }[];
  color?: string;
  label?: string;
}) {
  const rows = (data || []).map((d) => ({ ...d, label: d.month }));
  if (!rows.length) return <EmptyChart label="Not enough history yet" />;
  return (
    <ResponsiveContainer width="100%" height={240}>
      <AreaChart data={rows} margin={{ top: 4, right: 8, left: -20, bottom: 0 }}>
        <defs>
          <linearGradient id={`grad-${color}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor={color} stopOpacity={0.35} />
            <stop offset="95%" stopColor={color} stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#eef2f7" />
        <XAxis dataKey="month" tick={{ fontSize: 10 }} />
        <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
        <Tooltip />
        <Area type="monotone" dataKey="count" name={label} stroke={color} strokeWidth={2}
          fill={`url(#grad-${color})`} />
      </AreaChart>
    </ResponsiveContainer>
  );
}

/** Donut for a status/role breakdown map, skipping zero entries. */
export function BreakdownDonut({ data, humanizeKeys = true }: {
  data?: Record<string, number>;
  humanizeKeys?: boolean;
}) {
  const rows = Object.entries(data || {})
    .filter(([, v]) => v > 0)
    .map(([name, value]) => ({ name: humanizeKeys ? humanize(name) : name, value }));
  if (!rows.length) return <EmptyChart label="No data yet" />;
  return (
    <ResponsiveContainer width="100%" height={240}>
      <PieChart>
        <Pie data={rows} dataKey="value" nameKey="name" innerRadius={55} outerRadius={90} paddingAngle={2}>
          {rows.map((_, i) => <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />)}
        </Pie>
        <Tooltip />
        <Legend wrapperStyle={{ fontSize: 12 }} />
      </PieChart>
    </ResponsiveContainer>
  );
}

/** Horizontal funnel bars for the hiring pipeline (data-normalized to the max). */
export function PipelineFunnel({ data, order }: {
  data?: Record<string, number>;
  order: string[];
}) {
  const rows = order.map((s) => ({ stage: humanize(s), count: data?.[s] ?? 0 }));
  const max = Math.max(...rows.map((r) => r.count), 1);
  const total = rows.reduce((a, b) => a + b.count, 0);
  if (!total) return <EmptyChart label="No applications yet" />;
  return (
    <div className="space-y-3">
      {rows.map((p) => (
        <div key={p.stage}>
          <div className="mb-1 flex justify-between text-sm">
            <span className="text-slate-600">{p.stage}</span>
            <span className="font-medium text-slate-800">{p.count}</span>
          </div>
          <div className="h-2.5 rounded-full bg-slate-100">
            <div className="h-2.5 rounded-full bg-brand-500" style={{ width: `${(p.count / max) * 100}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
}

export function EmptyChart({ label }: { label: string }) {
  return <p className="py-12 text-center text-sm text-slate-400">{label}</p>;
}

// ---------------------------------------------------------------- actions

export function PrintButton() {
  return (
    <button onClick={() => window.print()} className="btn-secondary">
      <Printer className="h-4 w-4" /> Print
    </button>
  );
}

export function downloadCsv(filename: string, headers: string[], rows: (string | number | null | undefined)[][]) {
  const esc = (v: string | number | null | undefined) => {
    const s = String(v ?? '');
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const csv = [headers.join(','), ...rows.map((r) => r.map(esc).join(','))].join('\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export function ExportCsvButton({ onClick }: { onClick: () => void }) {
  return (
    <button onClick={onClick} className="btn-secondary">
      <Download className="h-4 w-4" /> Export CSV
    </button>
  );
}
