import { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { PageLoader } from './ui';
import type { Role } from '../types';

/** Reachable while the profile is still incomplete, so the gate can never trap anyone. */
const EXEMPT_WHILE_INCOMPLETE = ['/complete-profile', '/settings'];

export function homeForRole(role?: Role): string {
  switch (role) {
    case 'SUPER_ADMIN':
      return '/admin';
    case 'COMPANY_ADMIN':
    case 'RECRUITER':
      return '/company';
    case 'CANDIDATE':
      return '/candidate';
    default:
      return '/login';
  }
}

export function RequireAuth({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) return <PageLoader />;
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  if (roles && !roles.includes(user.role)) {
    return <Navigate to={homeForRole(user.role)} replace />;
  }

  // Onboarding gate. This sits in RequireAuth because RequireAuth wraps every
  // authenticated route, so one check covers all of them. The gate page itself and
  // Settings are exempt, or the redirect would loop and lock the user out of the only
  // pages that can clear it.
  if (!user.profileComplete && !EXEMPT_WHILE_INCOMPLETE.includes(location.pathname)) {
    return <Navigate to="/complete-profile" state={{ from: location.pathname }} replace />;
  }
  return <>{children}</>;
}

export function RedirectIfAuthed({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <PageLoader />;
  if (user) return <Navigate to={homeForRole(user.role)} replace />;
  return <>{children}</>;
}
