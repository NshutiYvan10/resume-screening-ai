import { ReactNode, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Menu, X, Linkedin, Twitter, Github, Sparkles } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { homeForRole } from '../RouteGuards';

const NAV_LINKS = [
  { label: 'Browse jobs', to: '/jobs' },
  { label: 'How it works', to: '/#how' },
  { label: 'For companies', to: '/#employers' },
];

function Logo({ onClick }: { onClick?: () => void }) {
  return (
    <Link to="/" onClick={onClick} className="flex items-center gap-1 text-xl font-extrabold text-slate-900">
      <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 text-white">
        <Sparkles className="h-4 w-4" />
      </span>
      Resume<span className="-ml-1 text-brand-600">AI</span>
    </Link>
  );
}

export default function PublicLayout({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  const authArea = user ? (
    <button onClick={() => navigate(homeForRole(user.role))} className="btn-primary">
      Go to dashboard
    </button>
  ) : (
    <>
      <Link to="/login" className="text-sm font-semibold text-slate-600 hover:text-slate-900">
        Log in
      </Link>
      <Link to="/register" className="btn-primary">Sign up</Link>
    </>
  );

  return (
    <div className="flex min-h-screen flex-col bg-white">
      {/* ---------------- header ---------------- */}
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/80 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <div className="flex items-center gap-8">
            <Logo />
            <nav className="hidden items-center gap-6 md:flex">
              {NAV_LINKS.map((l) => (
                <Link key={l.to} to={l.to} className="text-sm font-medium text-slate-600 hover:text-brand-600">
                  {l.label}
                </Link>
              ))}
            </nav>
          </div>
          <div className="hidden items-center gap-4 md:flex">{authArea}</div>
          <button className="rounded-lg p-2 text-slate-600 hover:bg-slate-100 md:hidden" onClick={() => setOpen((v) => !v)} aria-label="Menu">
            {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>
        {open && (
          <div className="border-t border-slate-200 bg-white px-4 py-3 md:hidden">
            <nav className="flex flex-col gap-1">
              {NAV_LINKS.map((l) => (
                <Link key={l.to} to={l.to} onClick={() => setOpen(false)}
                  className="rounded-lg px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">
                  {l.label}
                </Link>
              ))}
              <div className="mt-2 flex items-center gap-3 border-t border-slate-100 pt-3" onClick={() => setOpen(false)}>
                {authArea}
              </div>
            </nav>
          </div>
        )}
      </header>

      {/* ---------------- page ---------------- */}
      <main className="flex-1">{children}</main>

      {/* ---------------- footer ---------------- */}
      <footer className="border-t border-slate-200 bg-slate-900 text-slate-300">
        <div className="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
          <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <span className="text-xl font-extrabold text-white">
                Resume<span className="text-brand-400">AI</span>
              </span>
              <p className="mt-3 max-w-xs text-sm text-slate-400">
                The AI-powered hiring platform that screens résumés fairly, ranks candidates
                accurately, and helps great people meet great companies.
              </p>
              <div className="mt-4 flex gap-3">
                <a href="#" aria-label="LinkedIn" className="text-slate-400 hover:text-white"><Linkedin className="h-5 w-5" /></a>
                <a href="#" aria-label="Twitter" className="text-slate-400 hover:text-white"><Twitter className="h-5 w-5" /></a>
                <a href="#" aria-label="GitHub" className="text-slate-400 hover:text-white"><Github className="h-5 w-5" /></a>
              </div>
            </div>
            <FooterCol title="Product" links={[
              { label: 'Browse jobs', to: '/jobs' },
              { label: 'How it works', to: '/#how' },
              { label: 'For companies', to: '/#employers' },
            ]} />
            <FooterCol title="Account" links={[
              { label: 'Log in', to: '/login' },
              { label: 'Create account', to: '/register' },
            ]} />
            <FooterCol title="Company" links={[
              { label: 'About', to: '/#how' },
              { label: 'Privacy', to: '#' },
              { label: 'Terms', to: '#' },
            ]} />
          </div>
          <div className="mt-10 flex flex-col items-center justify-between gap-3 border-t border-slate-800 pt-6 text-xs text-slate-500 sm:flex-row">
            <p>© {new Date().getFullYear()} ResumeAI. All rights reserved.</p>
            <p>Built for fair, transparent hiring.</p>
          </div>
        </div>
      </footer>
    </div>
  );
}

function FooterCol({ title, links }: { title: string; links: { label: string; to: string }[] }) {
  return (
    <div>
      <h4 className="text-sm font-semibold text-white">{title}</h4>
      <ul className="mt-3 space-y-2">
        {links.map((l) => (
          <li key={l.label}>
            <Link to={l.to} className="text-sm text-slate-400 hover:text-white">{l.label}</Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
