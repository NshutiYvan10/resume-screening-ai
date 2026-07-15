import { useState } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeft, MapPin, Building2, Briefcase, GraduationCap, Calendar, Upload, FileText, X,
} from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import { PageLoader, Field, Modal, Spinner } from '../../components/ui';
import { formatSalary, formatDate, humanize } from '../../lib/format';
import type { PublicJob } from '../../types';

export default function JobDetail() {
  const { jobId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [applyOpen, setApplyOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [coverLetter, setCoverLetter] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const { data: job, isLoading } = useQuery({
    queryKey: ['public-job', jobId],
    queryFn: async () => (await api.get<PublicJob>(`/jobs/public/${jobId}`)).data,
  });

  const submitApplication = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      toast('Please attach your resume', 'error');
      return;
    }
    setSubmitting(true);
    const formData = new FormData();
    formData.append('resume', file);
    if (coverLetter.trim()) formData.append('coverLetter', coverLetter.trim());
    try {
      await api.post(`/applications/jobs/${jobId}/apply`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      toast('Application submitted! Track it under My Applications.', 'success');
      setApplyOpen(false);
      navigate('/candidate/applications');
    } catch (err) {
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
          <Building2 className="h-4 w-4" /> {job.companyName}
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

        <div className="mt-8 flex justify-end border-t border-slate-100 pt-6">
          <button className="btn-primary" onClick={() => setApplyOpen(true)}>
            Apply for this position
          </button>
        </div>
      </div>

      <Modal open={applyOpen} onClose={() => setApplyOpen(false)} title={`Apply — ${job.title}`}>
        <form onSubmit={submitApplication} className="space-y-4">
          <Field label="Resume" required hint="PDF, DOCX, DOC or TXT · max 10MB">
            {file ? (
              <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5">
                <span className="flex items-center gap-2 text-sm text-slate-700">
                  <FileText className="h-4 w-4 text-brand-600" /> {file.name}
                </span>
                <button type="button" onClick={() => setFile(null)} className="text-slate-400 hover:text-red-500">
                  <X className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <label className="flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed border-slate-300 py-8 hover:border-brand-400 hover:bg-brand-50/30">
                <Upload className="h-6 w-6 text-slate-400" />
                <span className="mt-2 text-sm text-slate-500">Click to upload your resume</span>
                <input
                  type="file"
                  accept=".pdf,.docx,.doc,.txt"
                  className="hidden"
                  onChange={(e) => setFile(e.target.files?.[0] || null)}
                />
              </label>
            )}
          </Field>
          <Field label="Cover letter" hint="Optional">
            <textarea
              className="input min-h-28"
              value={coverLetter}
              onChange={(e) => setCoverLetter(e.target.value)}
              placeholder="Tell the hiring team why you're a great fit…"
            />
          </Field>
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
