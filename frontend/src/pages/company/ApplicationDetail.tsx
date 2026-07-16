import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft, Download, AlertTriangle, Mail, Phone, RefreshCw, GraduationCap, Briefcase,
  CheckCircle2, XCircle, MinusCircle,
} from 'lucide-react';
import { api, apiErrorMessage, tokenStore } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import { PageLoader, StatusPill, Modal, Field, Spinner } from '../../components/ui';
import { ScoreRing, ScoreBar, ScreeningStatusBadge } from '../../components/ScoreBadge';
import { APPLICATION_STATUS_STYLES, formatDateTime, humanize, timeAgo } from '../../lib/format';
import type { Application, ApplicationStatus } from '../../types';

const NEXT_STATUSES: ApplicationStatus[] = [
  'UNDER_REVIEW', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED', 'REJECTED',
];

export default function ApplicationDetail() {
  const { applicationId } = useParams();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [statusModal, setStatusModal] = useState<ApplicationStatus | null>(null);
  const [note, setNote] = useState('');

  const { data: app, isLoading } = useQuery({
    queryKey: ['application', applicationId],
    queryFn: async () => (await api.get<Application>(`/applications/${applicationId}`)).data,
    refetchInterval: (q) =>
      q.state.data?.screening?.status === 'PENDING' || q.state.data?.screening?.status === 'PROCESSING'
        ? 5000
        : false,
  });

  const updateStatus = useMutation({
    mutationFn: async () => api.put(`/applications/${applicationId}/status`, { status: statusModal, note }),
    onSuccess: () => {
      toast('Status updated — candidate notified', 'success');
      setStatusModal(null);
      setNote('');
      queryClient.invalidateQueries({ queryKey: ['application', applicationId] });
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const rescreen = useMutation({
    mutationFn: async () => api.post(`/applications/${applicationId}/rescreen`),
    onSuccess: () => {
      toast('Re-screening queued', 'success');
      queryClient.invalidateQueries({ queryKey: ['application', applicationId] });
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const downloadResume = async () => {
    try {
      const res = await api.get(`/applications/${applicationId}/resume`, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data as Blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = app?.resumeFileName || 'resume';
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    }
  };

  if (isLoading || !app) return <PageLoader />;
  const s = app.screening;

  return (
    <div className="mx-auto max-w-4xl">
      <Link
        to={`/company/jobs/${app.jobId}/applications`}
        className="mb-4 flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="h-4 w-4" /> Back to applications
      </Link>

      <div className="card p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex items-center gap-4">
            <ScoreRing score={s?.matchScore} size={72} />
            <div>
              <h1 className="text-xl font-bold text-slate-900">{app.candidateName}</h1>
              <p className="text-sm text-slate-500">
                Applied for <span className="font-medium text-slate-700">{app.jobTitle}</span>
              </p>
              <div className="mt-1.5 flex items-center gap-2">
                <StatusPill label={humanize(app.status)} className={APPLICATION_STATUS_STYLES[app.status]} />
                <ScreeningStatusBadge status={s?.status} />
              </div>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <button onClick={downloadResume} className="btn-secondary">
              <Download className="h-4 w-4" /> Resume
            </button>
            <button
              onClick={() => rescreen.mutate()}
              className="btn-secondary"
              disabled={rescreen.isPending || s?.status === 'PROCESSING'}
            >
              <RefreshCw className={`h-4 w-4 ${rescreen.isPending ? 'animate-spin' : ''}`} /> Re-screen
            </button>
          </div>
        </div>

        <div className="mt-4 flex flex-wrap gap-x-6 gap-y-1 border-t border-slate-100 pt-4 text-sm text-slate-500">
          {app.candidateEmail && (
            <span className="flex items-center gap-1.5">
              <Mail className="h-4 w-4" /> {app.candidateEmail}
            </span>
          )}
          {app.candidatePhone && (
            <span className="flex items-center gap-1.5">
              <Phone className="h-4 w-4" /> {app.candidatePhone}
            </span>
          )}
          <span>Applied {timeAgo(app.appliedAt)}</span>
        </div>
      </div>

      <div className="mt-5 grid gap-5 lg:grid-cols-3">
        {/* AI analysis */}
        <div className="lg:col-span-2 space-y-5">
          <div className="card p-6">
            <h2 className="mb-4 font-semibold text-slate-800">AI screening analysis</h2>
            {s?.status === 'COMPLETED' ? (
              <>
                {/* parse-quality warning: a poorly-extracted resume means the
                    score below cannot be trusted without a human read */}
                {(s.parseQuality === 'poor' || s.parseQuality === 'partial') && (
                  <div className={`mb-4 rounded-lg border p-3 ${
                    s.parseQuality === 'poor'
                      ? 'border-red-200 bg-red-50' : 'border-amber-200 bg-amber-50'
                  }`}>
                    <p className={`flex items-center gap-2 text-sm font-semibold ${
                      s.parseQuality === 'poor' ? 'text-red-700' : 'text-amber-800'
                    }`}>
                      <AlertTriangle className="h-4 w-4" />
                      {s.parseQuality === 'poor'
                        ? 'Low confidence — the resume could not be read well. Review the file manually.'
                        : 'Partial extraction — some details could not be read from the resume.'}
                    </p>
                    {!!s.parseWarnings?.length && (
                      <ul className={`mt-1.5 list-disc pl-6 text-xs ${
                        s.parseQuality === 'poor' ? 'text-red-600' : 'text-amber-700'
                      }`}>
                        {s.parseWarnings.map((w) => <li key={w}>{w}</li>)}
                      </ul>
                    )}
                  </div>
                )}

                <div className="grid gap-4 sm:grid-cols-3">
                  <ScoreBar label="Skills match" value={s.skillsScore} />
                  <ScoreBar label="Experience" value={s.experienceScore} />
                  <ScoreBar label="Education" value={s.educationScore} />
                </div>

                {/* why this score */}
                {s.reasoning && (
                  <div className="mt-5 rounded-lg border border-brand-100 bg-brand-50/60 p-4">
                    <p className="text-sm font-semibold text-brand-800">Why this score</p>
                    <p className="mt-1 text-sm leading-relaxed text-brand-900/80">{s.reasoning}</p>
                  </div>
                )}

                {/* per-qualification evidence table */}
                {(s.matchedSkills || s.missingRequired || s.missingOptional) && (
                  <div className="mt-5">
                    <p className="mb-2 text-sm font-medium text-slate-700">Qualification match</p>
                    <div className="overflow-hidden rounded-lg border border-slate-200">
                      {s.matchedSkills?.map((skill) => (
                        <div key={`m-${skill}`} className="flex items-center gap-2 border-b border-slate-100 bg-green-50/40 px-3 py-2 last:border-0">
                          <CheckCircle2 className="h-4 w-4 shrink-0 text-green-600" />
                          <span className="text-sm text-slate-700">{skill}</span>
                          <span className="ml-auto text-xs font-medium text-green-700">Found in resume</span>
                        </div>
                      ))}
                      {s.missingRequired?.map((skill) => (
                        <div key={`r-${skill}`} className="flex items-center gap-2 border-b border-slate-100 bg-red-50/40 px-3 py-2 last:border-0">
                          <XCircle className="h-4 w-4 shrink-0 text-red-500" />
                          <span className="text-sm text-slate-700">{skill}</span>
                          <span className="ml-auto text-xs font-semibold text-red-600">Missing · required</span>
                        </div>
                      ))}
                      {s.missingOptional?.map((skill) => (
                        <div key={`o-${skill}`} className="flex items-center gap-2 border-b border-slate-100 px-3 py-2 last:border-0">
                          <MinusCircle className="h-4 w-4 shrink-0 text-slate-400" />
                          <span className="text-sm text-slate-500">{skill}</span>
                          <span className="ml-auto text-xs text-slate-400">Missing · optional</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                <div className="mt-6 grid gap-4 sm:grid-cols-2">
                  <div className="rounded-lg bg-slate-50 p-4">
                    <div className="flex items-center gap-2 text-sm font-medium text-slate-700">
                      <GraduationCap className="h-4 w-4" /> Education
                    </div>
                    <p className="mt-1 text-sm text-slate-600">{s.extractedEducation || 'Not detected'}</p>
                  </div>
                  <div className="rounded-lg bg-slate-50 p-4">
                    <div className="flex items-center gap-2 text-sm font-medium text-slate-700">
                      <Briefcase className="h-4 w-4" /> Experience
                    </div>
                    <p className="mt-1 text-sm text-slate-600">
                      {s.extractedExperienceYears != null ? `${s.extractedExperienceYears} years detected` : 'Not detected'}
                    </p>
                  </div>
                </div>

                {!!s.extractedSkills?.length && (
                  <div className="mt-5">
                    <p className="mb-2 text-sm font-medium text-slate-700">Detected skills</p>
                    <div className="flex flex-wrap gap-1.5">
                      {s.extractedSkills.map((skill) => (
                        <span key={skill} className="badge bg-brand-50 text-brand-700">{skill}</span>
                      ))}
                    </div>
                  </div>
                )}
              </>
            ) : s?.status === 'FAILED' ? (
              <div className="rounded-lg bg-red-50 p-4 text-sm text-red-700">
                Screening failed: {s.errorMessage || 'unknown error'}. Try re-screening.
              </div>
            ) : (
              <div className="flex items-center gap-2 py-6 text-sm text-slate-500">
                <Spinner className="h-4 w-4 text-brand-600" />
                AI screening in progress — this page updates automatically.
              </div>
            )}
          </div>

          {s?.biasFlag && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-4">
              <div className="flex items-start gap-2">
                <AlertTriangle className="h-5 w-5 shrink-0 text-amber-500" />
                <div>
                  <p className="text-sm font-semibold text-amber-800">Advisory: potential bias indicators</p>
                  <p className="mt-1 text-sm text-amber-700">{s.biasFlagReason}</p>
                </div>
              </div>
            </div>
          )}

          {app.coverLetter && (
            <div className="card p-6">
              <h2 className="mb-3 font-semibold text-slate-800">Cover letter</h2>
              <p className="whitespace-pre-wrap text-sm text-slate-600">{app.coverLetter}</p>
            </div>
          )}
        </div>

        {/* decision panel */}
        <div className="space-y-5">
          <div className="card p-6">
            <h2 className="mb-3 font-semibold text-slate-800">Move candidate</h2>
            <div className="space-y-2">
              {NEXT_STATUSES.filter((st) => st !== app.status).map((st) => (
                <button
                  key={st}
                  onClick={() => setStatusModal(st)}
                  disabled={app.status === 'HIRED' || app.status === 'WITHDRAWN'}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-left text-sm font-medium text-slate-700 hover:border-brand-300 hover:bg-brand-50 disabled:opacity-40"
                >
                  {humanize(st)}
                </button>
              ))}
            </div>
            {(app.status === 'HIRED' || app.status === 'WITHDRAWN') && (
              <p className="mt-3 text-xs text-slate-400">
                This application is {humanize(app.status).toLowerCase()} and can no longer be changed.
              </p>
            )}
          </div>

          {app.recruiterNote && (
            <div className="card p-5">
              <p className="text-xs font-medium text-slate-400">Latest recruiter note</p>
              <p className="mt-1 text-sm text-slate-600">{app.recruiterNote}</p>
              {app.statusUpdatedAt && (
                <p className="mt-2 text-xs text-slate-400">{formatDateTime(app.statusUpdatedAt)}</p>
              )}
            </div>
          )}
        </div>
      </div>

      <Modal
        open={!!statusModal}
        onClose={() => setStatusModal(null)}
        title={`Move to ${humanize(statusModal || '')}`}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            updateStatus.mutate();
          }}
          className="space-y-4"
        >
          <p className="text-sm text-slate-500">
            The candidate will be notified of this change by email and in-app notification.
          </p>
          <Field label="Note to attach" hint="Optional — visible to your team">
            <textarea className="input min-h-24" value={note} onChange={(e) => setNote(e.target.value)} />
          </Field>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setStatusModal(null)}>
              Cancel
            </button>
            <button type="submit" className="btn-primary" disabled={updateStatus.isPending}>
              {updateStatus.isPending && <Spinner className="h-4 w-4" />}
              Confirm
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
