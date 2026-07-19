import { useState, useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft, Download, AlertTriangle, Mail, Phone, RefreshCw, GraduationCap, Briefcase,
  CheckCircle2, XCircle, MinusCircle, ArrowRight, RotateCcw, Undo2, PartyPopper,
  Eye, ShieldAlert, ShieldCheck, FileText,
} from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import { PageLoader, StatusPill, Modal, Field, Spinner } from '../../components/ui';
import { ScoreRing, ScoreBar, ScreeningStatusBadge } from '../../components/ScoreBadge';
import StageProgress from '../../components/pipeline/StageProgress';
import InterviewsCard from '../../components/pipeline/InterviewsCard';
import OfferCard from '../../components/pipeline/OfferCard';
import TimelineCard from '../../components/pipeline/TimelineCard';
import { APPLICATION_STATUS_STYLES, formatDateTime, humanize, timeAgo } from '../../lib/format';
import type { Application, Pipeline, RejectionReason, Screening } from '../../types';

const REJECTION_REASONS: RejectionReason[] = [
  'MISSING_REQUIRED_SKILLS', 'INSUFFICIENT_EXPERIENCE', 'EDUCATION_REQUIREMENTS',
  'FAILED_INTERVIEW', 'BETTER_CANDIDATE_SELECTED', 'SALARY_EXPECTATIONS',
  'POSITION_CLOSED', 'UNRESPONSIVE', 'OTHER',
];

// human labels for the next stage, per current stage
const ADVANCE_LABEL: Record<string, string> = {
  SUBMITTED: 'Move to Under Review',
  UNDER_REVIEW: 'Shortlist candidate',
  SHORTLISTED: 'Move to Interview',
};

// friendly labels for the AI's advisory identity flags
const IDENTITY_FLAG_LABELS: Record<string, string> = {
  NAME_MISMATCH: 'Name on the résumé does not match the applicant account',
  DUPLICATE_RESUME: 'This résumé was also submitted by a different applicant',
  EMAIL_MISMATCH: 'Résumé email differs from the applicant account',
  PHONE_MISMATCH: 'Résumé phone differs from the applicant account',
  NAME_NOT_FOUND: 'No candidate name could be read from the résumé',
};
const STRONG_IDENTITY_FLAGS = ['NAME_MISMATCH', 'DUPLICATE_RESUME'];

/** Advisory identity/authenticity result from the AI screening (never affects the score). */
function IdentityVerificationCard({ s }: { s: Screening }) {
  const flags = s.identityFlags || [];
  const verified = s.identityVerified !== false;
  // hide entirely for legacy screenings that predate identity checks
  if (!flags.length && !s.extractedName && verified) return null;
  const strong = flags.some((f) => STRONG_IDENTITY_FLAGS.includes(f));
  const alert = !verified || strong;
  const tone = alert
    ? { box: 'border-red-200 bg-red-50', title: 'text-red-800', body: 'text-red-700' }
    : flags.length
      ? { box: 'border-amber-200 bg-amber-50', title: 'text-amber-800', body: 'text-amber-700' }
      : { box: 'border-green-200 bg-green-50', title: 'text-green-800', body: 'text-green-700' };
  return (
    <div className={`rounded-xl border p-4 ${tone.box}`}>
      <div className="flex items-start gap-2">
        {alert || flags.length
          ? <ShieldAlert className={`h-5 w-5 shrink-0 ${alert ? 'text-red-500' : 'text-amber-500'}`} />
          : <ShieldCheck className="h-5 w-5 shrink-0 text-green-600" />}
        <div className="min-w-0 flex-1">
          <p className={`text-sm font-semibold ${tone.title}`}>
            {alert ? 'Identity check — needs review'
              : flags.length ? 'Identity check — minor discrepancies'
              : 'Identity check passed'}
          </p>
          {s.identitySummary && <p className={`mt-1 text-sm ${tone.body}`}>{s.identitySummary}</p>}
          {!verified && (
            <p className={`mt-1 text-sm ${tone.body}`}>
              Please verify this candidate’s identity against the original résumé before proceeding.
            </p>
          )}
          {!!flags.length && (
            <ul className="mt-2 space-y-1">
              {flags.map((f) => (
                <li key={f} className="flex items-center gap-1.5 text-xs text-slate-600">
                  <span className={`h-1.5 w-1.5 rounded-full ${STRONG_IDENTITY_FLAGS.includes(f) ? 'bg-red-500' : 'bg-amber-500'}`} />
                  {IDENTITY_FLAG_LABELS[f] || humanize(f)}
                </li>
              ))}
            </ul>
          )}
          {(s.extractedName || s.extractedEmail || s.extractedPhone) && (
            <p className="mt-2 text-xs text-slate-500">
              Read from résumé: {[s.extractedName, s.extractedEmail, s.extractedPhone].filter(Boolean).join(' · ')}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

export default function ApplicationDetail() {
  const { applicationId } = useParams();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [rejectOpen, setRejectOpen] = useState(false);
  const [reject, setReject] = useState({
    reason: 'BETTER_CANDIDATE_SELECTED' as RejectionReason, internalNote: '', candidateMessage: '',
  });
  const [resumeView, setResumeView] = useState<{ url: string; ext: string } | null>(null);
  const [loadingView, setLoadingView] = useState(false);

  const { data: app, isLoading } = useQuery({
    queryKey: ['application', applicationId],
    queryFn: async () => (await api.get<Application>(`/applications/${applicationId}`)).data,
    refetchInterval: (q) =>
      q.state.data?.screening?.status === 'PENDING' || q.state.data?.screening?.status === 'PROCESSING'
        ? 5000
        : false,
  });

  const { data: pipeline } = useQuery({
    queryKey: ['pipeline', applicationId],
    queryFn: async () => (await api.get<Pipeline>(`/applications/${applicationId}/pipeline`)).data,
  });

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['application', applicationId] });
    queryClient.invalidateQueries({ queryKey: ['pipeline', applicationId] });
  };

  const stageAction = useMutation({
    mutationFn: async (verb: 'advance' | 'backtrack' | 'reopen' | 'hire') =>
      api.post(`/applications/${applicationId}/${verb}`),
    onSuccess: (_d, verb) => {
      toast(verb === 'hire' ? 'Candidate marked hired — onboarding handoff sent' : 'Stage updated', 'success');
      refresh();
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const doReject = useMutation({
    mutationFn: async () =>
      api.post(`/applications/${applicationId}/reject`, {
        reason: reject.reason,
        internalNote: reject.internalNote.trim() || undefined,
        candidateMessage: reject.candidateMessage.trim() || undefined,
      }),
    onSuccess: () => {
      toast('Application rejected — candidate notified', 'success');
      setRejectOpen(false);
      refresh();
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

  // open the original submitted file in an in-app viewer (authenticated blob fetch,
  // so the recruiter reads the exact document the candidate uploaded)
  const openResume = async () => {
    setLoadingView(true);
    try {
      const res = await api.get(`/applications/${applicationId}/resume`, { responseType: 'blob' });
      const ext = (app?.resumeFileName?.split('.').pop() || '').toLowerCase();
      // give the blob a viewer-friendly type so the iframe renders inline
      const mime = ext === 'pdf' ? 'application/pdf' : ext === 'txt' ? 'text/plain' : (res.data as Blob).type;
      const url = URL.createObjectURL(new Blob([res.data as Blob], { type: mime || 'application/octet-stream' }));
      setResumeView({ url, ext });
    } catch (err) {
      toast(apiErrorMessage(err), 'error');
    } finally {
      setLoadingView(false);
    }
  };

  const closeResume = () => {
    setResumeView((v) => {
      if (v) URL.revokeObjectURL(v.url);
      return null;
    });
  };

  // release the résumé blob URL if the user navigates away with the viewer still open
  useEffect(() => () => {
    if (resumeView) URL.revokeObjectURL(resumeView.url);
  }, [resumeView]);

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
            <button onClick={openResume} className="btn-primary" disabled={loadingView}>
              {loadingView ? <Spinner className="h-4 w-4" /> : <Eye className="h-4 w-4" />} View résumé
            </button>
            <button onClick={downloadResume} className="btn-secondary">
              <Download className="h-4 w-4" /> Download
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

      {/* pipeline progress */}
      <div className="mt-5">
        <StageProgress status={app.status} />
      </div>

      {/* rejection record */}
      {app.status === 'REJECTED' && app.rejectionReason && (
        <div className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4">
          <p className="text-sm font-semibold text-red-700">
            Rejection reason: {humanize(app.rejectionReason)}
          </p>
          {app.rejectionNote && (
            <p className="mt-1 text-sm text-red-600">Internal note: {app.rejectionNote}</p>
          )}
        </div>
      )}

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

          {s?.status === 'COMPLETED' && <IdentityVerificationCard s={s} />}

          {/* interviews: shown once the candidate reaches the interview stage or has history */}
          {(app.status === 'INTERVIEW' || !!pipeline?.interviews.length) && (
            <InterviewsCard
              applicationId={applicationId!}
              interviews={pipeline?.interviews || []}
              canSchedule={!!pipeline?.allowedActions.includes('SCHEDULE_INTERVIEW')}
              onChanged={refresh}
            />
          )}

          {app.coverLetter && (
            <div className="card p-6">
              <h2 className="mb-3 font-semibold text-slate-800">Cover letter</h2>
              <p className="whitespace-pre-wrap text-sm text-slate-600">{app.coverLetter}</p>
            </div>
          )}
        </div>

        {/* pipeline action + offer + timeline panel */}
        <div className="space-y-5">
          <div className="card p-6">
            <h2 className="mb-3 font-semibold text-slate-800">Actions</h2>
            <div className="space-y-2">
              {pipeline?.allowedActions.includes('ADVANCE') && (
                <button
                  onClick={() => stageAction.mutate('advance')}
                  disabled={stageAction.isPending}
                  className="btn-primary w-full justify-start"
                >
                  <ArrowRight className="h-4 w-4" />
                  {ADVANCE_LABEL[app.status] || 'Advance'}
                </button>
              )}
              {pipeline?.allowedActions.includes('MARK_HIRED') && (
                <button
                  onClick={() => stageAction.mutate('hire')}
                  disabled={stageAction.isPending}
                  className="btn-primary w-full justify-start"
                >
                  <PartyPopper className="h-4 w-4" /> Mark hired
                </button>
              )}
              {pipeline?.allowedActions.includes('REJECT') && (
                <button
                  onClick={() => setRejectOpen(true)}
                  className="btn-secondary w-full justify-start text-red-600"
                >
                  <XCircle className="h-4 w-4" /> Reject…
                </button>
              )}
              {pipeline?.allowedActions.includes('REOPEN') && (
                <button
                  onClick={() => stageAction.mutate('reopen')}
                  disabled={stageAction.isPending}
                  className="btn-secondary w-full justify-start"
                >
                  <RotateCcw className="h-4 w-4" /> Reopen application
                </button>
              )}
              {pipeline?.allowedActions.includes('BACKTRACK') && (
                <button
                  onClick={() => stageAction.mutate('backtrack')}
                  disabled={stageAction.isPending}
                  className="btn-ghost w-full justify-start text-xs"
                >
                  <Undo2 className="h-3.5 w-3.5" /> Move back one stage (admin)
                </button>
              )}
              {app.status === 'INTERVIEW' && (
                <p className="pt-1 text-xs text-slate-400">
                  Moving to Offered happens by extending an approved offer below.
                </p>
              )}
              {app.status === 'HIRED' && (
                <p className="flex items-center gap-1.5 text-sm text-green-700">
                  <CheckCircle2 className="h-4 w-4" />
                  Hired{app.hiredAt ? ` on ${formatDateTime(app.hiredAt)}` : ''} — onboarding handoff sent.
                </p>
              )}
              {app.status === 'WITHDRAWN' && (
                <p className="text-xs text-slate-400">The candidate withdrew — no actions available.</p>
              )}
            </div>
          </div>

          {/* offer workflow: relevant from the interview stage onward */}
          {(app.status === 'INTERVIEW' || app.status === 'OFFERED' || app.status === 'HIRED' || pipeline?.offer) && (
            <OfferCard
              applicationId={applicationId!}
              offer={pipeline?.offer}
              canCreate={!!pipeline?.allowedActions.includes('CREATE_OFFER')}
              onChanged={refresh}
            />
          )}

          <TimelineCard
            applicationId={applicationId!}
            events={pipeline?.timeline || []}
            canAddNote={!!pipeline?.allowedActions.includes('ADD_NOTE')}
            onChanged={refresh}
          />
        </div>
      </div>

      {/* reject modal: standardized reason is mandatory */}
      <Modal open={rejectOpen} onClose={() => setRejectOpen(false)} title="Reject application">
        <form
          onSubmit={(e) => {
            e.preventDefault();
            doReject.mutate();
          }}
          className="space-y-4"
        >
          <Field label="Reason" required hint="Internal — kept for compliance and consistency analysis, never sent to the candidate">
            <select
              className="input"
              value={reject.reason}
              onChange={(e) => setReject({ ...reject, reason: e.target.value as RejectionReason })}
            >
              {REJECTION_REASONS.map((r) => (
                <option key={r} value={r}>{humanize(r)}</option>
              ))}
            </select>
          </Field>
          <Field label="Internal note" hint="Visible to your team on the timeline">
            <textarea className="input min-h-16" value={reject.internalNote}
              onChange={(e) => setReject({ ...reject, internalNote: e.target.value })} />
          </Field>
          <Field label="Message to candidate" hint="Optional — appended to the standard rejection email. Keep it brief and job-related.">
            <textarea className="input min-h-16" value={reject.candidateMessage}
              onChange={(e) => setReject({ ...reject, candidateMessage: e.target.value })} />
          </Field>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setRejectOpen(false)}>Cancel</button>
            <button type="submit" className="btn-danger" disabled={doReject.isPending}>
              {doReject.isPending && <Spinner className="h-4 w-4" />}
              Reject & notify candidate
            </button>
          </div>
        </form>
      </Modal>

      {/* original résumé viewer: reads the exact file the candidate submitted */}
      <Modal open={!!resumeView} onClose={closeResume} title="Original résumé" maxWidth="max-w-4xl">
        <div className="space-y-4">
          <div className="flex items-center justify-between gap-3">
            <p className="flex items-center gap-2 truncate text-sm text-slate-500">
              <FileText className="h-4 w-4 shrink-0" /> {app.resumeFileName}
            </p>
            <button onClick={downloadResume} className="btn-secondary shrink-0">
              <Download className="h-4 w-4" /> Download
            </button>
          </div>
          {resumeView && (resumeView.ext === 'pdf' || resumeView.ext === 'txt') ? (
            <iframe
              src={resumeView.url}
              title="Résumé preview"
              className="h-[70vh] w-full rounded-lg border border-slate-200 bg-white"
            />
          ) : (
            <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 py-12 text-center">
              <FileText className="mx-auto h-10 w-10 text-slate-300" />
              <p className="mt-3 text-sm font-medium text-slate-700">
                In-browser preview isn’t available for {resumeView?.ext ? `.${resumeView.ext}` : 'this'} files
              </p>
              <p className="mt-1 text-sm text-slate-500">
                Download the file to open it in Word or your document viewer.
              </p>
              <button onClick={downloadResume} className="btn-primary mx-auto mt-4">
                <Download className="h-4 w-4" /> Download résumé
              </button>
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
}
