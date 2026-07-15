import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import { PageLoader } from './components/ui';
import { RequireAuth, RedirectIfAuthed, homeForRole } from './components/RouteGuards';
import AppLayout from './components/AppLayout';

import Login from './pages/auth/Login';
import Register from './pages/auth/Register';
import ForgotPassword from './pages/auth/ForgotPassword';
import ResetPassword from './pages/auth/ResetPassword';
import VerifyEmail from './pages/auth/VerifyEmail';
import AcceptInvitation from './pages/auth/AcceptInvitation';
import Settings from './pages/Settings';

import AdminDashboard from './pages/admin/AdminDashboard';
import AdminCompanies from './pages/admin/AdminCompanies';
import AdminUsers from './pages/admin/AdminUsers';
import AdminAudit from './pages/admin/AdminAudit';

import CompanyDashboard from './pages/company/CompanyDashboard';
import CompanyApprovals from './pages/company/CompanyApprovals';
import CompanyPipeline from './pages/company/CompanyPipeline';
import CompanyJobs from './pages/company/CompanyJobs';
import JobEditor from './pages/company/JobEditor';
import JobApplications from './pages/company/JobApplications';
import ApplicationDetail from './pages/company/ApplicationDetail';
import CompanyTeam from './pages/company/CompanyTeam';
import CompanyProfile from './pages/company/CompanyProfile';
import CompanyAudit from './pages/company/CompanyAudit';

import BrowseJobs from './pages/candidate/BrowseJobs';
import JobDetail from './pages/candidate/JobDetail';
import MyApplications from './pages/candidate/MyApplications';
import CompanyProfilePublic from './pages/candidate/CompanyProfilePublic';

function Shell({ children }: { children: React.ReactNode }) {
  return <AppLayout>{children}</AppLayout>;
}

function RootRedirect() {
  const { user, loading } = useAuth();
  if (loading) return <PageLoader />;
  return <Navigate to={user ? homeForRole(user.role) : '/login'} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<RootRedirect />} />

      {/* public auth routes */}
      <Route path="/login" element={<RedirectIfAuthed><Login /></RedirectIfAuthed>} />
      <Route path="/register" element={<RedirectIfAuthed><Register /></RedirectIfAuthed>} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/verify-email" element={<VerifyEmail />} />
      <Route path="/accept-invitation" element={<AcceptInvitation />} />

      {/* super admin */}
      <Route path="/admin" element={<RequireAuth roles={['SUPER_ADMIN']}><Shell><AdminDashboard /></Shell></RequireAuth>} />
      <Route path="/admin/companies" element={<RequireAuth roles={['SUPER_ADMIN']}><Shell><AdminCompanies /></Shell></RequireAuth>} />
      <Route path="/admin/users" element={<RequireAuth roles={['SUPER_ADMIN']}><Shell><AdminUsers /></Shell></RequireAuth>} />
      <Route path="/admin/audit" element={<RequireAuth roles={['SUPER_ADMIN']}><Shell><AdminAudit /></Shell></RequireAuth>} />

      {/* company (admin + recruiter) */}
      <Route path="/company" element={<RequireAuth roles={['COMPANY_ADMIN', 'RECRUITER']}><Shell><CompanyDashboard /></Shell></RequireAuth>} />
      <Route path="/company/approvals" element={<RequireAuth roles={['COMPANY_ADMIN']}><Shell><CompanyApprovals /></Shell></RequireAuth>} />
      <Route path="/company/candidates" element={<RequireAuth roles={['COMPANY_ADMIN']}><Shell><CompanyPipeline /></Shell></RequireAuth>} />
      <Route path="/company/jobs" element={<RequireAuth roles={['COMPANY_ADMIN', 'RECRUITER']}><Shell><CompanyJobs /></Shell></RequireAuth>} />
      <Route path="/company/jobs/new" element={<RequireAuth roles={['COMPANY_ADMIN', 'RECRUITER']}><Shell><JobEditor /></Shell></RequireAuth>} />
      <Route path="/company/jobs/:jobId/edit" element={<RequireAuth roles={['COMPANY_ADMIN', 'RECRUITER']}><Shell><JobEditor /></Shell></RequireAuth>} />
      <Route path="/company/jobs/:jobId/applications" element={<RequireAuth roles={['COMPANY_ADMIN', 'RECRUITER']}><Shell><JobApplications /></Shell></RequireAuth>} />
      <Route path="/company/applications/:applicationId" element={<RequireAuth roles={['COMPANY_ADMIN', 'RECRUITER']}><Shell><ApplicationDetail /></Shell></RequireAuth>} />
      <Route path="/company/team" element={<RequireAuth roles={['COMPANY_ADMIN']}><Shell><CompanyTeam /></Shell></RequireAuth>} />
      <Route path="/company/profile" element={<RequireAuth roles={['COMPANY_ADMIN']}><Shell><CompanyProfile /></Shell></RequireAuth>} />
      <Route path="/company/audit" element={<RequireAuth roles={['COMPANY_ADMIN']}><Shell><CompanyAudit /></Shell></RequireAuth>} />

      {/* candidate */}
      <Route path="/candidate" element={<RequireAuth roles={['CANDIDATE']}><Shell><BrowseJobs /></Shell></RequireAuth>} />
      <Route path="/candidate/jobs/:jobId" element={<RequireAuth roles={['CANDIDATE']}><Shell><JobDetail /></Shell></RequireAuth>} />
      <Route path="/candidate/companies/:companyId" element={<RequireAuth roles={['CANDIDATE']}><Shell><CompanyProfilePublic /></Shell></RequireAuth>} />
      <Route path="/candidate/applications" element={<RequireAuth roles={['CANDIDATE']}><Shell><MyApplications /></Shell></RequireAuth>} />

      {/* shared */}
      <Route path="/settings" element={<RequireAuth><Shell><Settings /></Shell></RequireAuth>} />

      <Route path="*" element={<RootRedirect />} />
    </Routes>
  );
}
