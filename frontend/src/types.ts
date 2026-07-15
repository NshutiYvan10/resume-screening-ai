export type Role = 'SUPER_ADMIN' | 'COMPANY_ADMIN' | 'RECRUITER' | 'CANDIDATE';
export type UserStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'DISABLED';
export type CompanyStatus = 'ACTIVE' | 'SUSPENDED';
export type InvitationType = 'COMPANY' | 'TEAM_MEMBER';
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED';
export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'INTERNSHIP';
export type WorkMode = 'ONSITE' | 'REMOTE' | 'HYBRID';
export type JobStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED';
export type EducationLevel = 'CERTIFICATE' | 'DIPLOMA' | 'BACHELORS' | 'MASTERS' | 'PHD';
export type ApplicationStatus =
  | 'SUBMITTED' | 'UNDER_REVIEW' | 'SHORTLISTED' | 'INTERVIEW'
  | 'OFFERED' | 'HIRED' | 'REJECTED' | 'WITHDRAWN';
export type ScreeningStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface User {
  id: string;
  email: string;
  fullName: string;
  phone?: string;
  role: Role;
  status: UserStatus;
  emailVerified: boolean;
  companyId?: string;
  companyName?: string;
  lastLoginAt?: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string | null;
  user: User;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CompanyPhoto {
  id: string;
  url: string;
  caption?: string;
}

export interface Company {
  id: string;
  name: string;
  industry?: string;
  website?: string;
  companySize?: string;
  location?: string;
  description?: string;
  tagline?: string;
  foundedYear?: number;
  mission?: string;
  values?: string[];
  benefits?: string[];
  linkedinUrl?: string;
  twitterUrl?: string;
  logoUrl?: string;
  coverUrl?: string;
  photos?: CompanyPhoto[];
  status: CompanyStatus;
  createdAt: string;
}

export interface CompanySummary {
  id: string;
  name: string;
  industry?: string;
  location?: string;
  status: CompanyStatus;
  userCount: number;
  jobCount: number;
  createdAt: string;
}

export interface Invitation {
  id: string;
  email: string;
  type: InvitationType;
  role?: Role;
  companyName?: string;
  companyId?: string;
  status: InvitationStatus;
  invitedByName?: string;
  expiresAt: string;
  acceptedAt?: string;
  createdAt: string;
}

export interface PublicInvitation {
  email: string;
  type: InvitationType;
  role?: Role;
  companyName?: string;
  status: InvitationStatus;
  expired: boolean;
}

export interface Qualification {
  id?: string;
  skill: string;
  weight: number;
  required: boolean;
}

export interface Job {
  id: string;
  companyId: string;
  companyName: string;
  title: string;
  department?: string;
  location?: string;
  employmentType: EmploymentType;
  workMode: WorkMode;
  description: string;
  responsibilities?: string;
  minExperienceYears?: number;
  educationLevel?: EducationLevel;
  salaryMin?: number;
  salaryMax?: number;
  salaryCurrency?: string;
  deadline?: string;
  status: JobStatus;
  publishedAt?: string;
  createdAt: string;
  createdByName?: string;
  submittedByName?: string;
  submittedAt?: string;
  approvedByName?: string;
  approvedAt?: string;
  rejectionReason?: string;
  qualifications: Qualification[];
  applicationCount?: number;
}

export interface PublicCompany {
  id: string;
  name: string;
  industry?: string;
  website?: string;
  companySize?: string;
  location?: string;
  description?: string;
  tagline?: string;
  foundedYear?: number;
  mission?: string;
  values?: string[];
  benefits?: string[];
  linkedinUrl?: string;
  twitterUrl?: string;
  logoUrl?: string;
  coverUrl?: string;
  photos?: CompanyPhoto[];
  openJobs: number;
  createdAt: string;
}

export interface PublicJob {
  id: string;
  companyId: string;
  companyName: string;
  companyIndustry?: string;
  companyLogoUrl?: string;
  title: string;
  department?: string;
  location?: string;
  employmentType: EmploymentType;
  workMode: WorkMode;
  description: string;
  responsibilities?: string;
  minExperienceYears?: number;
  educationLevel?: EducationLevel;
  salaryMin?: number;
  salaryMax?: number;
  salaryCurrency?: string;
  deadline?: string;
  publishedAt?: string;
  skills: string[];
}

export interface Screening {
  status: ScreeningStatus;
  matchScore?: number;
  skillsScore?: number;
  experienceScore?: number;
  educationScore?: number;
  extractedSkills?: string[];
  extractedEducation?: string;
  extractedExperienceYears?: number;
  biasFlag: boolean;
  biasFlagReason?: string;
  errorMessage?: string;
  screenedAt?: string;
}

export interface Application {
  id: string;
  jobId: string;
  jobTitle: string;
  companyId: string;
  companyName: string;
  candidateId: string;
  candidateName: string;
  candidateEmail?: string;
  candidatePhone?: string;
  status: ApplicationStatus;
  coverLetter?: string;
  resumeFileName: string;
  recruiterNote?: string;
  appliedAt: string;
  statusUpdatedAt?: string;
  screening?: Screening;
}

export interface Notification {
  id: string;
  type: string;
  title: string;
  message: string;
  link?: string;
  read: boolean;
  createdAt: string;
}

export interface AuditLog {
  id: number;
  actorId?: string;
  actorEmail?: string;
  actorRole?: string;
  companyId?: string;
  action: string;
  entityType?: string;
  entityId?: string;
  details?: Record<string, unknown>;
  ipAddress?: string;
  createdAt: string;
}
