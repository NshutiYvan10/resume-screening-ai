-- Platform and personal reports have no superior to counter-sign them, so they are
-- acknowledged by their author at generation time. V7 shipped with approval keyed off
-- "scope is not PERSONAL", which left PLATFORM reports as drafts awaiting an approval
-- nobody was able to give. Finalise any row stranded that way.
UPDATE reports r
SET status            = 'APPROVED',
    approved_by       = r.generated_by,
    approved_by_name  = r.generated_by_name,
    approved_by_role  = r.generated_by_role,
    approved_at       = COALESCE(r.generated_at, r.created_at)
WHERE r.status = 'DRAFT'
  AND r.scope IN ('PLATFORM', 'PERSONAL')
  AND r.generated_by_name IS NOT NULL;

-- Record the self-sign-off in the trail so the finalisation is auditable rather than
-- appearing out of nowhere in the approved_by columns.
INSERT INTO report_approvals (report_id, action, actor_id, actor_name, actor_role, note, created_at)
SELECT r.id, 'ACKNOWLEDGED', r.generated_by, r.generated_by_name, r.generated_by_role,
       'Finalised by migration V8: platform and personal reports need no counter-signature',
       COALESCE(r.approved_at, now())
FROM reports r
WHERE r.scope IN ('PLATFORM', 'PERSONAL')
  AND r.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM report_approvals a
                  WHERE a.report_id = r.id AND a.action = 'ACKNOWLEDGED');
