import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { BadgeDollarSign, CheckCircle2, Send, ThumbsDown, ThumbsUp, PenLine } from 'lucide-react';
import { api, apiErrorMessage } from '../../lib/api';
import { useToast } from '../../context/ToastContext';
import { useAuth } from '../../context/AuthContext';
import { Field, Modal, Spinner, StatusPill } from '../ui';
import { formatDate, formatDateTime, humanize } from '../../lib/format';
import type { Offer, OfferStatus } from '../../types';

const STATUS_STYLE: Record<OfferStatus, string> = {
  PENDING_APPROVAL: 'bg-amber-100 text-amber-700',
  APPROVED: 'bg-blue-100 text-blue-700',
  EXTENDED: 'bg-violet-100 text-violet-700',
  ACCEPTED: 'bg-green-100 text-green-700',
  DECLINED: 'bg-red-100 text-red-600',
};

export default function OfferCard({
  applicationId,
  offer,
  canCreate,
  onChanged,
}: {
  applicationId: string;
  offer?: Offer;
  canCreate: boolean;
  onChanged: () => void;
}) {
  const toast = useToast();
  const { user } = useAuth();
  const isAdmin = user?.role === 'COMPANY_ADMIN';
  const [formOpen, setFormOpen] = useState(false);
  const [isRevision, setIsRevision] = useState(false);
  const [form, setForm] = useState({ salary: '', currency: 'USD', startDate: '', expiresAt: '', notes: '' });

  const openForm = (revision: boolean) => {
    setIsRevision(revision);
    if (revision && offer) {
      setForm({
        salary: String(offer.salary), currency: offer.currency,
        startDate: offer.startDate || '', expiresAt: offer.expiresAt || '', notes: offer.notes || '',
      });
    } else {
      setForm({ salary: '', currency: 'USD', startDate: '', expiresAt: '', notes: '' });
    }
    setFormOpen(true);
  };

  const save = useMutation({
    mutationFn: async () => {
      const body = {
        salary: Number(form.salary),
        currency: form.currency.trim(),
        startDate: form.startDate || undefined,
        expiresAt: form.expiresAt || undefined,
        notes: form.notes.trim() || undefined,
      };
      return isRevision
        ? api.put(`/applications/offers/${offer!.id}`, body)
        : api.post(`/applications/${applicationId}/offer`, body);
    },
    onSuccess: () => {
      toast(isRevision
        ? 'Offer revised — prior approval voided where applicable'
        : isAdmin ? 'Offer created and approved' : 'Offer created — awaiting admin approval', 'success');
      setFormOpen(false);
      onChanged();
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const act = useMutation({
    mutationFn: async (verb: 'approve' | 'extend') =>
      api.post(`/applications/offers/${offer!.id}/${verb}`),
    onSuccess: (_d, verb) => {
      toast(verb === 'approve' ? 'Offer approved' : 'Offer extended — candidate notified by email', 'success');
      onChanged();
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  const outcome = useMutation({
    mutationFn: async (status: 'ACCEPTED' | 'DECLINED') =>
      api.post(`/applications/offers/${offer!.id}/outcome`, { status }),
    onSuccess: () => {
      toast('Offer outcome recorded', 'success');
      onChanged();
    },
    onError: (err) => toast(apiErrorMessage(err), 'error'),
  });

  return (
    <div className="card p-6">
      <div className="mb-4 flex items-center justify-between">
        <h2 className="flex items-center gap-2 font-semibold text-slate-800">
          <BadgeDollarSign className="h-4 w-4 text-brand-600" /> Offer
        </h2>
        {offer && <StatusPill label={humanize(offer.status)} className={STATUS_STYLE[offer.status]} />}
      </div>

      {!offer ? (
        <>
          <p className="text-sm text-slate-400">
            No offer yet.{canCreate ? ' Draft one when the interviews support a hire.' : ''}
          </p>
          {canCreate && (
            <button className="btn-primary mt-3 py-2" onClick={() => openForm(false)}>
              Create offer
            </button>
          )}
        </>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <p className="text-xs text-slate-400">Salary</p>
              <p className="font-semibold text-slate-800">{offer.salary} {offer.currency}</p>
            </div>
            <div>
              <p className="text-xs text-slate-400">Start date</p>
              <p className="font-medium text-slate-700">{offer.startDate ? formatDate(offer.startDate) : '—'}</p>
            </div>
            <div>
              <p className="text-xs text-slate-400">Respond by</p>
              <p className="font-medium text-slate-700">{offer.expiresAt ? formatDate(offer.expiresAt) : '—'}</p>
            </div>
            <div>
              <p className="text-xs text-slate-400">Drafted by</p>
              <p className="font-medium text-slate-700">{offer.createdByName || '—'}</p>
            </div>
          </div>
          {offer.notes && <p className="mt-2 text-xs text-slate-500">{offer.notes}</p>}

          <div className="mt-3 space-y-1 border-t border-slate-100 pt-3 text-xs text-slate-500">
            {offer.approvedByName && (
              <p>Approved by <strong>{offer.approvedByName}</strong> · {formatDateTime(offer.approvedAt)}</p>
            )}
            {offer.extendedAt && <p>Extended to candidate · {formatDateTime(offer.extendedAt)}</p>}
            {offer.respondedAt && <p>Candidate responded · {formatDateTime(offer.respondedAt)}</p>}
          </div>

          <div className="mt-4 flex flex-wrap gap-2">
            {offer.status === 'PENDING_APPROVAL' && isAdmin && (
              <button className="btn-primary py-1.5" onClick={() => act.mutate('approve')} disabled={act.isPending}>
                <CheckCircle2 className="h-4 w-4" /> Approve offer
              </button>
            )}
            {offer.status === 'PENDING_APPROVAL' && !isAdmin && (
              <p className="text-xs text-amber-600">Awaiting company-admin approval before it can be extended.</p>
            )}
            {offer.status === 'APPROVED' && (
              <button className="btn-primary py-1.5" onClick={() => act.mutate('extend')} disabled={act.isPending}>
                <Send className="h-4 w-4" /> Extend to candidate
              </button>
            )}
            {offer.status === 'EXTENDED' && (
              <>
                <button className="btn-primary py-1.5" onClick={() => outcome.mutate('ACCEPTED')} disabled={outcome.isPending}>
                  <ThumbsUp className="h-4 w-4" /> Candidate accepted
                </button>
                <button className="btn-secondary py-1.5 text-red-600" onClick={() => outcome.mutate('DECLINED')} disabled={outcome.isPending}>
                  <ThumbsDown className="h-4 w-4" /> Candidate declined
                </button>
              </>
            )}
            {offer.status !== 'ACCEPTED' && (
              <button className="btn-ghost py-1.5 text-xs" onClick={() => openForm(true)}>
                <PenLine className="h-3.5 w-3.5" /> Revise{offer.status === 'EXTENDED' ? ' (counter-offer)' : ''}
              </button>
            )}
          </div>
        </>
      )}

      <Modal open={formOpen} onClose={() => setFormOpen(false)}
        title={isRevision ? 'Revise offer' : 'Create offer'}>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate();
          }}
          className="space-y-4"
        >
          {isRevision && (
            <p className="rounded-lg bg-amber-50 p-3 text-xs text-amber-700">
              Revising voids any existing approval — a company admin must re-approve before the
              revised offer can be extended.
            </p>
          )}
          <div className="grid grid-cols-2 gap-3">
            <Field label="Salary" required>
              <input type="number" min="0" step="0.01" className="input" value={form.salary}
                onChange={(e) => setForm({ ...form, salary: e.target.value })} required />
            </Field>
            <Field label="Currency" required>
              <input className="input" maxLength={10} value={form.currency}
                onChange={(e) => setForm({ ...form, currency: e.target.value })} required />
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Start date">
              <input type="date" className="input" value={form.startDate}
                onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
            </Field>
            <Field label="Respond by">
              <input type="date" className="input" value={form.expiresAt}
                onChange={(e) => setForm({ ...form, expiresAt: e.target.value })} />
            </Field>
          </div>
          <Field label="Notes" hint="Included in the offer email to the candidate">
            <textarea className="input min-h-16" value={form.notes}
              onChange={(e) => setForm({ ...form, notes: e.target.value })} />
          </Field>
          <div className="flex justify-end gap-2">
            <button type="button" className="btn-secondary" onClick={() => setFormOpen(false)}>Cancel</button>
            <button type="submit" className="btn-primary" disabled={save.isPending}>
              {save.isPending && <Spinner className="h-4 w-4" />}
              {isRevision ? 'Save revision' : 'Create offer'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
