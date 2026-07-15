import { ReactNode } from 'react';
import { Sparkles, ShieldCheck, BarChart3 } from 'lucide-react';

export default function AuthShell({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex min-h-screen">
      {/* left brand panel */}
      <div className="hidden w-1/2 flex-col justify-between bg-slate-900 p-12 text-white lg:flex">
        <div className="text-2xl font-extrabold">
          Resume<span className="text-brand-400">AI</span>
        </div>
        <div>
          <h1 className="text-3xl font-bold leading-tight">
            Screen resumes with AI.<br />Hire the best, faster.
          </h1>
          <p className="mt-4 max-w-md text-slate-300">
            An intelligent, unbiased recruitment platform that parses, scores and ranks candidates
            against every role — so your team focuses on people, not paperwork.
          </p>
          <div className="mt-10 space-y-4">
            {[
              { icon: <Sparkles className="h-5 w-5" />, text: 'AI-driven skill & experience matching' },
              { icon: <ShieldCheck className="h-5 w-5" />, text: 'Bias-aware, fair evaluations' },
              { icon: <BarChart3 className="h-5 w-5" />, text: 'Actionable hiring analytics' },
            ].map((f, i) => (
              <div key={i} className="flex items-center gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-600/30 text-brand-300">
                  {f.icon}
                </div>
                <span className="text-sm text-slate-200">{f.text}</span>
              </div>
            ))}
          </div>
        </div>
        <p className="text-xs text-slate-500">© {new Date().getFullYear()} ResumeAI. All rights reserved.</p>
      </div>

      {/* right form panel */}
      <div className="flex w-full items-center justify-center p-6 lg:w-1/2">
        <div className="w-full max-w-md">
          <div className="mb-8 lg:hidden">
            <span className="text-2xl font-extrabold text-slate-900">
              Resume<span className="text-brand-600">AI</span>
            </span>
          </div>
          <h2 className="text-2xl font-bold text-slate-900">{title}</h2>
          {subtitle && <p className="mt-2 text-sm text-slate-500">{subtitle}</p>}
          <div className="mt-8">{children}</div>
        </div>
      </div>
    </div>
  );
}
