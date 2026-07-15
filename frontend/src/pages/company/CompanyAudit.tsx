import PageHeader from '../../components/PageHeader';
import AuditTrail from '../../components/AuditTrail';

export default function CompanyAudit() {
  return (
    <div>
      <PageHeader
        title="Audit trail"
        description="Actions taken within your company — job changes, applicant decisions, team and access events"
      />
      <AuditTrail scopedToCompany />
    </div>
  );
}
