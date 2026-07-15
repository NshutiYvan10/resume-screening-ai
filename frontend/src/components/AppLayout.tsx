import { ReactNode, useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard, Building2, Users, ClipboardList, ClipboardCheck, Briefcase, FileText,
  ShieldCheck, LogOut, Menu, X, ChevronDown, Search,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import NotificationBell from './NotificationBell';
import { humanize } from '../lib/format';
import type { Role } from '../types';

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
}

const NAV: Record<Role, NavItem[]> = {
  SUPER_ADMIN: [
    { to: '/admin', label: 'Dashboard', icon: <LayoutDashboard className="h-5 w-5" /> },
    { to: '/admin/companies', label: 'Companies', icon: <Building2 className="h-5 w-5" /> },
    { to: '/admin/users', label: 'Users', icon: <Users className="h-5 w-5" /> },
    { to: '/admin/audit', label: 'Audit Trail', icon: <ShieldCheck className="h-5 w-5" /> },
  ],
  COMPANY_ADMIN: [
    { to: '/company', label: 'Dashboard', icon: <LayoutDashboard className="h-5 w-5" /> },
    { to: '/company/approvals', label: 'Approvals', icon: <ClipboardCheck className="h-5 w-5" /> },
    { to: '/company/jobs', label: 'Jobs', icon: <Briefcase className="h-5 w-5" /> },
    { to: '/company/candidates', label: 'Candidates', icon: <ClipboardList className="h-5 w-5" /> },
    { to: '/company/team', label: 'Team', icon: <Users className="h-5 w-5" /> },
    { to: '/company/profile', label: 'Company Profile', icon: <Building2 className="h-5 w-5" /> },
    { to: '/company/audit', label: 'Audit Trail', icon: <ShieldCheck className="h-5 w-5" /> },
  ],
  RECRUITER: [
    { to: '/company', label: 'Dashboard', icon: <LayoutDashboard className="h-5 w-5" /> },
    { to: '/company/jobs', label: 'Jobs', icon: <Briefcase className="h-5 w-5" /> },
  ],
  CANDIDATE: [
    { to: '/candidate', label: 'Browse Jobs', icon: <Search className="h-5 w-5" /> },
    { to: '/candidate/applications', label: 'My Applications', icon: <ClipboardList className="h-5 w-5" /> },
  ],
};

const ROLE_LABEL: Record<Role, string> = {
  SUPER_ADMIN: 'Platform Admin',
  COMPANY_ADMIN: 'Company Admin',
  RECRUITER: 'Recruiter',
  CANDIDATE: 'Candidate',
};

export default function AppLayout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  if (!user) return null;
  const items = NAV[user.role];

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const sidebar = (
    <div className="flex h-full flex-col">
      <div className="flex h-16 items-center gap-2 px-6 border-b border-slate-800">
        <span className="text-xl font-extrabold text-white">
          Resume<span className="text-brand-400">AI</span>
        </span>
      </div>
      <nav className="flex-1 space-y-1 px-3 py-4">
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/admin' || item.to === '/company' || item.to === '/candidate'}
            onClick={() => setMobileOpen(false)}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-brand-600 text-white'
                  : 'text-slate-300 hover:bg-slate-800 hover:text-white'
              }`
            }
          >
            {item.icon}
            {item.label}
          </NavLink>
        ))}
      </nav>
      <div className="border-t border-slate-800 p-3">
        <div className="flex items-center gap-2 px-3 py-2 text-xs text-slate-400">
          <FileText className="h-4 w-4" />
          {user.companyName || 'ResumeAI Platform'}
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-slate-50">
      {/* desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-64 bg-slate-900 lg:block">
        {sidebar}
      </aside>

      {/* mobile sidebar */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-slate-900/50" onClick={() => setMobileOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-64 bg-slate-900">
            <button
              onClick={() => setMobileOpen(false)}
              className="absolute right-3 top-4 text-slate-400 hover:text-white"
            >
              <X className="h-5 w-5" />
            </button>
            {sidebar}
          </aside>
        </div>
      )}

      <div className="lg:pl-64">
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-slate-200 bg-white/80 px-4 backdrop-blur sm:px-6">
          <button
            onClick={() => setMobileOpen(true)}
            className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 lg:hidden"
          >
            <Menu className="h-5 w-5" />
          </button>
          <div className="flex-1" />
          <div className="flex items-center gap-2">
            <NotificationBell />
            <div className="relative">
              <button
                onClick={() => setMenuOpen((v) => !v)}
                className="flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-slate-100"
              >
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-100 text-sm font-semibold text-brand-700">
                  {user.fullName.charAt(0).toUpperCase()}
                </div>
                <div className="hidden text-left sm:block">
                  <p className="text-sm font-medium leading-tight text-slate-800">{user.fullName}</p>
                  <p className="text-xs leading-tight text-slate-400">{ROLE_LABEL[user.role]}</p>
                </div>
                <ChevronDown className="h-4 w-4 text-slate-400" />
              </button>
              {menuOpen && (
                <>
                  <div className="fixed inset-0 z-30" onClick={() => setMenuOpen(false)} />
                  <div className="absolute right-0 z-40 mt-2 w-52 card py-1">
                    <div className="border-b border-slate-100 px-4 py-2">
                      <p className="truncate text-sm font-medium text-slate-800">{user.email}</p>
                    </div>
                    <Link
                      to="/settings"
                      onClick={() => setMenuOpen(false)}
                      className="block px-4 py-2 text-sm text-slate-600 hover:bg-slate-50"
                    >
                      Account settings
                    </Link>
                    <button
                      onClick={handleLogout}
                      className="flex w-full items-center gap-2 px-4 py-2 text-sm text-red-600 hover:bg-red-50"
                    >
                      <LogOut className="h-4 w-4" /> Sign out
                    </button>
                  </div>
                </>
              )}
            </div>
          </div>
        </header>

        <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">{children}</main>
      </div>
    </div>
  );
}
