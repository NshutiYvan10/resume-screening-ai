import { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { PageLoader } from './ui';
import type { Role } from '../types';

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
  return <>{children}</>;
}

export function RedirectIfAuthed({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <PageLoader />;
  if (user) return <Navigate to={homeForRole(user.role)} replace />;
  return <>{children}</>;
}
