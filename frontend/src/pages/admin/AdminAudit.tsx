import PageHeader from '../../components/PageHeader';
import AuditTrail from '../../components/AuditTrail';

export default function AdminAudit() {
  return (
    <div>
      <PageHeader
        title="Audit trail"
        description="Complete record of administrative and security-relevant actions across the platform"
      />
      <AuditTrail />
    </div>
  );
}
