import { useQuery } from '@tanstack/react-query';
import { api } from './api';
import { useAuth } from '../context/AuthContext';
import type { Application, ApplicationStatus, Page } from '../types';

export interface AppliedInfo {
  applicationId: string;
  status: ApplicationStatus;
  appliedAt: string;
}

/**
 * Loads the signed-in candidate's applications as a jobId -> status map so job
 * listings and the job detail page can reflect what they've already applied to.
 * No-op for non-candidates.
 */
export function useMyApplicationsMap() {
  const { user } = useAuth();
  return useQuery({
    queryKey: ['my-applications-map'],
    enabled: user?.role === 'CANDIDATE',
    queryFn: async () => {
      const { data } = await api.get<Page<Application>>('/applications/my', {
        params: { size: 200 },
      });
      const map: Record<string, AppliedInfo> = {};
      for (const a of data.content) {
        map[a.jobId] = { applicationId: a.id, status: a.status, appliedAt: a.appliedAt };
      }
      return map;
    },
  });
}
