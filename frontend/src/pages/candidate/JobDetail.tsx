import { useEffect, useState } from 'react';
import axios from 'axios';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft, MapPin, Building2, Briefcase, GraduationCap, Calendar,
  CheckCircle2, RotateCcw,
} from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import { useMyApplicationsMap } from '../../lib/useMyApplications';
import { useDocumentLibrary, LIBRARY_KEY } from '../../lib/useDocuments';
import ApplyDocuments, { ApplyChoice, initialChoice } from '../../components/candidate/ApplyDocuments';
import { PageLoader, Modal, Spinner, StatusPill } from '../../components/ui';
import { APPLICATION_STATUS_STYLES, formatSalary, formatDate, humanize } from '../../lib/format';
import type { PublicJob, SavedResume } from '../../types';

export default function JobDetail() {
  const { jobId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [applyOpen, setApplyOpen] = useState(false);
  const [choice, setChoice] = useState<ApplyChoice>(() => initialChoice());
  const [submitting, setSubmitting] = useState(false);

  const { data: job, isLoading } = useQuery({
    queryKey: ['public-job', jobId],
    queryFn: async () => (await api.get<PublicJob>(`/jobs/public/${jobId}`)).data,
  });

  const { data: library } = useDocumentLibrary();

  // The library may still be in flight when the modal is opened, which would otherwise
  // show upload-only to a candidate who has saved résumés. Seed the moment it lands, but
  // never over a choice already in progress.
  useEffect(() => {
    if (library) setChoice((c) => (c.documentId || c.file ? c : initialChoice(library)));
  }, [library]);

  const openApply = () => {
    setChoice(initialChoice(library));
    setApplyOpen(true);
  };

  const { data: appliedMap } = useMyApplicationsMap();
  const applied = jobId ? appliedMap?.[jobId] : undefined;
  // A withdrawn application can be re-submitted; any other status is locked.
  const canApply = !applied || applied.status === 'WITHDRAWN';
  const isReapply = applied?.status === 'WITHDRAWN';

  const submitApplication = async (e: React.FormEvent) => {
    e.preventDefault();
    const savedResume = choice.resumeMode === 'saved' ? choice.documentId : null;
    if (!savedResume && !choice.file) {
      toast('Choose a saved résumé or upload a new one', 'error');
      return;
    }
    setSubmitting(true);
    try {
      let documentId = savedResume;

      // A new upload the candidate asked to keep: file it in the library first and apply
      // from that entry, so the two can't drift apart. Filing is only a convenience, so
      // if it fails the application still goes out with the file attached directly.
      if (!documentId && choice.alsoSave && choice.file) {
        try {
          const fd = new FormData();
          fd.append('file', choice.file);
          const { data } = await api.post<SavedResume>('/documents/resumes', fd, {
            headers: { 'Content-Type': 'multipart/form-data' },
          });
          documentId = data.id;
          queryClient.invalidateQueries({ queryKey: LIBRARY_KEY });
        } catch {
          // library full, or the file was rejected there — carry on with a plain upload
        }
      }

      const formData = new FormData();
      if (documentId) formData.append('sourceDocumentId', documentId);
      else if (choice.file) formData.append('resume', choice.file);

      const text = choice.letterMode === 'none' ? '' : choice.letterText.trim();
      const template = choice.letterMode === 'saved'
        ? library?.coverLetters.find((c) => c.id === choice.coverLetterId)
        : undefined;
      // untouched template: record where the text came from. Edited: send what they wrote.
      if (template && text === template.body.trim()) {
        formData.append('sourceCoverLetterId', template.id);
      } else if (text) {
        formData.append('coverLetter', text);
      }

      await api.post(`/applications/jobs/${jobId}/apply`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      toast('Application submitted! Track it under My Applications.', 'success');
      setApplyOpen(false);
      queryClient.invalidateQueries({ queryKey: ['my-applications-map'] });
      queryClient.invalidateQueries({ queryKey: ['my-applications'] });
      navigate('/candidate/applications');
    } catch (err) {
      // The applied-status map is capped, so a candidate with a very large history
      // could still hit "already applied" (409). Refresh the map so the UI corrects
      // itself to the already-applied panel instead of showing a bare error.
      if (axios.isAxiosError(err) && err.response?.status === 409) {
        setApplyOpen(false);
        queryClient.invalidateQueries({ queryKey: ['my-applications-map'] });
      }
      toast(apiErrorMessage(err), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  if (isLoading || !job) return <PageLoader />;
  const salary = formatSalary(job.salaryMin, job.salaryMax, job.salaryCurrency);

  return (
    <div className="mx-auto max-w-3xl">
      <Link to="/candidate" className="mb-4 flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700">
        <ArrowLeft className="h-4 w-4" /> Back to jobs
      </Link>

      <div className="card p-6 sm:p-8">
        <div className="flex items-center gap-2 text-sm text-slate-400">
          <Building2 className="h-4 w-4" />
          <Link to={`/candidate/companies/${job.companyId}`} className="font-medium text-brand-600 hover:text-brand-700">
            {job.companyName}
          </Link>
          {job.companyIndustry && <span>· {job.companyIndustry}</span>}
        </div>
        <h1 className="mt-2 text-2xl font-bold text-slate-900">{job.title}</h1>

        <div className="mt-4 flex flex-wrap gap-2">
          <span className="badge bg-brand-50 text-brand-700">{humanize(job.employmentType)}</span>
          <span className="badge bg-brand-50 text-brand-700">{humanize(job.workMode)}</span>
          {job.department && <span className="badge bg-slate-100 text-slate-600">{job.department}</span>}
        </div>

        <div className="mt-5 grid gap-3 rounded-lg bg-slate-50 p-4 text-sm sm:grid-cols-2">
          {job.location && (
            <div className="flex items-center gap-2 text-slate-600">
              <MapPin className="h-4 w-4 text-slate-400" /> {job.location}
            </div>
          )}
          {salary && (
            <div className="flex items-center gap-2 text-slate-600">
              <Briefcase className="h-4 w-4 text-slate-400" /> {salary}
            </div>
          )}
          {job.minExperienceYears != null && (
            <div className="flex items-center gap-2 text-slate-600">
              <Briefcase className="h-4 w-4 text-slate-400" /> {job.minExperienceYears}+ years experience
            </div>
          )}
          {job.educationLevel && (
            <div className="flex items-center gap-2 text-slate-600">
              <GraduationCap className="h-4 w-4 text-slate-400" /> {humanize(job.educationLevel)}
            </div>
          )}
          {job.deadline && (
            <div className="flex items-center gap-2 text-slate-600">
              <Calendar className="h-4 w-4 text-slate-400" /> Apply by {formatDate(job.deadline)}
            </div>
          )}
        </div>

        <div className="mt-6">
          <h2 className="font-semibold text-slate-800">About the role</h2>
          <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-slate-600">{job.description}</p>
        </div>

        {job.responsibilities && (
          <div className="mt-6">
            <h2 className="font-semibold text-slate-800">Responsibilities</h2>
            <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-slate-600">{job.responsibilities}</p>
          </div>
        )}

        {!!job.skills?.length && (
          <div className="mt-6">
            <h2 className="font-semibold text-slate-800">Skills we're looking for</h2>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {job.skills.map((s) => (
                <span key={s} className="badge bg-slate-100 text-slate-600">{s}</span>
              ))}
            </div>
          </div>
        )}

        <div className="mt-8 border-t border-slate-100 pt-6">
          {applied && !canApply ? (
            <div className="flex flex-col gap-3 rounded-xl bg-slate-50 p-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-center gap-3">
                <CheckCircle2 className="h-5 w-5 shrink-0 text-green-600" />
                <div>
                  <p className="text-sm font-medium text-slate-800">
                    You applied on {formatDate(applied.appliedAt)}
                  </p>
                  <p className="mt-0.5 flex items-center gap-1.5 text-xs text-slate-500">
                    Current status:
                    <StatusPill
                      label={humanize(applied.status)}
                      className={APPLICATION_STATUS_STYLES[applied.status]}
                    />
                  </p>
                </div>
              </div>
              <Link to="/candidate/applications" className="btn-secondary shrink-0">
                Track your application
              </Link>
            </div>
          ) : (
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              {isReapply ? (
                <p className="flex items-center gap-1.5 text-sm text-slate-500">
                  <RotateCcw className="h-4 w-4" /> You withdrew a previous application — you can re-apply.
                </p>
              ) : (
                <span />
              )}
              <button className="btn-primary shrink-0" onClick={openApply}>
                {isReapply ? 'Re-apply for this position' : 'Apply for this position'}
              </button>
            </div>
          )}
        </div>
      </div>

      <Modal open={applyOpen} onClose={() => setApplyOpen(false)} title={`Apply — ${job.title}`}
             maxWidth="max-w-xl">
        <form onSubmit={submitApplication} className="space-y-4">
          <ApplyDocuments library={library} value={choice} onChange={setChoice} />
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setApplyOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn-primary" disabled={submitting}>
              {submitting && <Spinner className="h-4 w-4" />}
              Submit application
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
