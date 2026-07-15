"""
Comprehensive skills dictionary for resume screening
Organized by domain with 300+ skills across multiple fields
"""
from typing import List

# Skills organized by domain
SKILLS = {
    "programming": [
        "Python", "JavaScript", "Java", "C++", "C#", "PHP", "Ruby", "Go", "Rust",
        "TypeScript", "Swift", "Kotlin", "R", "MATLAB", "Scala", "Perl", "Shell",
        "Bash", "PowerShell", "SQL", "NoSQL", "GraphQL", "Assembly"
    ],
    "web": [
        "React", "Vue", "Angular", "Node.js", "Express", "Django", "Flask",
        "FastAPI", "HTML", "CSS", "Tailwind", "Bootstrap", "Next.js", "Nuxt",
        "jQuery", "SASS", "LESS", "Webpack", "Vite", "Redux", "MobX", "GraphQL",
        "REST API", "SOAP", "Microservices", "WebSockets"
    ],
    "databases": [
        "PostgreSQL", "MySQL", "MongoDB", "Redis", "SQLite", "Oracle",
        "SQL Server", "Cassandra", "DynamoDB", "Elasticsearch", "Firebase",
        "Supabase", "PlanetScale", "Neon", "CouchDB", "MariaDB", "DB2"
    ],
    "data_science": [
        "Machine Learning", "Deep Learning", "TensorFlow", "PyTorch", "scikit-learn",
        "pandas", "NumPy", "Matplotlib", "Data Analysis", "Statistics", "NLP",
        "Computer Vision", "Data Visualization", "Tableau", "Power BI", "Spark",
        "Hadoop", "Kafka", "Airflow", "MLflow", "Jupyter", "RStudio", "SAS",
        "SPSS", "Data Mining", "Predictive Analytics", "A/B Testing"
    ],
    "devops": [
        "Docker", "Kubernetes", "AWS", "Azure", "GCP", "CI/CD", "Jenkins",
        "GitHub Actions", "Terraform", "Ansible", "Linux", "Bash", "Git",
        "GitLab", "Bitbucket", "Nginx", "Apache", "Load Balancer", "Monitoring",
        "Prometheus", "Grafana", "ELK Stack", "CloudFormation", "Pulumi"
    ],
    "agriculture": [
        "Agriculture", "Plantation Management", "Crop Monitoring", "Field Operations",
        "Irrigation", "Fertilizer Management", "Soil Science", "Agronomy",
        "Pest Control", "Harvest Management", "Tea Cultivation", "Coffee Farming",
        "Yield Optimization", "Staff Supervision", "Farm Management",
        "Agricultural Extension", "Crop Rotation", "Organic Farming", "Hydroponics",
        "Precision Agriculture", "Agricultural Economics", "Food Security"
    ],
    "engineering": [
        "Civil Engineering", "Structural Engineering", "AutoCAD", "REVIT",
        "Project Management", "Blueprint Reading", "Construction Management",
        "Mechanical Engineering", "Electrical Engineering", "Design Management",
        "Site Inspection", "Cost Estimation", "Quality Control", "STAAD.Pro",
        "ETABS", "SAP2000", "Primavera", "MS Project", "BIM", "Surveying",
        "Geotechnical Engineering", "Transportation Engineering", "Water Resources"
    ],
    "management": [
        "Team Leadership", "Project Management", "Strategic Planning", "Budgeting",
        "Stakeholder Management", "Risk Management", "Communication",
        "Problem Solving", "Decision Making", "Report Writing", "Agile", "Scrum",
        "Kanban", "Lean", "Six Sigma", "Change Management", "Conflict Resolution",
        "Performance Management", "Resource Allocation", "Team Building"
    ],
    "finance": [
        "Accounting", "Financial Analysis", "Budgeting", "Auditing", "Taxation",
        "QuickBooks", "SAP", "Excel", "Financial Modeling", "Forecasting",
        "Cost Accounting", "Management Accounting", "Financial Reporting",
        "Payroll", "Accounts Payable", "Accounts Receivable", "Bookkeeping",
        "Sage", "Xero", "Tally", "Financial Planning", "Investment Analysis"
    ],
    "soft_skills": [
        "Communication", "Teamwork", "Leadership", "Time Management",
        "Critical Thinking", "Adaptability", "Problem Solving", "Creativity",
        "Attention to Detail", "Customer Service", "Interpersonal Skills",
        "Negotiation", "Presentation Skills", "Public Speaking", "Writing Skills",
        "Active Listening", "Empathy", "Emotional Intelligence", "Conflict Resolution",
        "Collaboration", "Self-Motivation", "Work Ethic", "Professionalism"
    ],
    "marketing": [
        "Digital Marketing", "SEO", "SEM", "Google Analytics", "Social Media Marketing",
        "Content Marketing", "Email Marketing", "PPC", "Facebook Ads", "Google Ads",
        "Marketing Strategy", "Brand Management", "Market Research", "Copywriting",
        "Marketing Automation", "HubSpot", "Mailchimp", "Hootsuite", "Canva",
        "Video Marketing", "Influencer Marketing", "Affiliate Marketing"
    ],
    "design": [
        "UI/UX Design", "Figma", "Adobe Photoshop", "Adobe Illustrator",
        "Adobe XD", "Sketch", "InVision", "Prototyping", "Wireframing",
        "User Research", "Usability Testing", "Design Thinking", "Typography",
        "Color Theory", "Layout Design", "Graphic Design", "Motion Design",
        "Web Design", "Mobile Design", "Responsive Design", "Accessibility"
    ],
    "security": [
        "Cybersecurity", "Network Security", "Information Security", "Penetration Testing",
        "Ethical Hacking", "Firewall", "VPN", "Encryption", "SSL/TLS", "OWASP",
        "Security Auditing", "Incident Response", "Threat Analysis", "SIEM",
        "SOC", "ISO 27001", "GDPR", "HIPAA", "PCI DSS", "Zero Trust"
    ],
    "networking": [
        "TCP/IP", "DNS", "DHCP", "LAN", "WAN", "VPN", "Firewall", "Router",
        "Switch", "Cisco", "Juniper", "Network Troubleshooting", "Cabling",
        "Wireless Networking", "VoIP", "SD-WAN", "Network Monitoring",
        "Load Balancing", "Content Delivery", "Cloud Networking"
    ],
    "testing": [
        "Unit Testing", "Integration Testing", "System Testing", "Acceptance Testing",
        "Regression Testing", "Performance Testing", "Load Testing", "Stress Testing",
        "JUnit", "Jest", "Mocha", "Selenium", "Cypress", "Playwright", "Postman",
        "TestNG", "PyTest", "RSpec", "Cucumber", "BDD", "TDD", "Code Coverage"
    ],
    "mobile": [
        "iOS", "Android", "React Native", "Flutter", "Swift", "Kotlin", "Xamarin",
        "Ionic", "Cordova", "Mobile Development", "App Store", "Google Play",
        "Mobile UI/UX", "Push Notifications", "Mobile Security", "PWA",
        "SwiftUI", "Jetpack Compose", "Mobile Testing", "App Optimization"
    ],
    "cloud": [
        "AWS", "Azure", "GCP", "Cloud Computing", "IaaS", "PaaS", "SaaS",
        "Serverless", "Lambda", "Azure Functions", "Cloud Architecture",
        "Cloud Migration", "Cloud Security", "Multi-Cloud", "Hybrid Cloud",
        "Cloud Cost Management", "Cloud Monitoring", "Cloud Storage", "CDN"
    ],
    "data_engineering": [
        "ETL", "Data Pipeline", "Data Warehouse", "Data Lake", "Apache Spark",
        "Apache Kafka", "Apache Airflow", "dbt", "Snowflake", "Databricks",
        "Data Modeling", "Data Integration", "Data Governance", "Data Quality",
        "Apache Flink", "Apache Beam", "Apache NiFi", "Talend", "Informatica"
    ],
    "business_intelligence": [
        "Business Intelligence", "Power BI", "Tableau", "Looker", "QlikView",
        "DAX", "SQL Reporting", "Dashboard Design", "KPI", "Metrics",
        "Data Storytelling", "Executive Reporting", "OLAP", "Data Mart",
        "Self-Service BI", "Embedded Analytics", "Data Discovery"
    ],
    "hr": [
        "Recruitment", "Talent Management", "Performance Management", "Training",
        "Employee Relations", "HRIS", "Payroll", "Benefits Administration",
        "Onboarding", "Offboarding", "Succession Planning", "Workforce Planning",
        "Compensation", "Employee Engagement", "Diversity & Inclusion", "HR Analytics"
    ],
    "project_methods": [
        "Agile", "Scrum", "Kanban", "Waterfall", "Hybrid", "PRINCE2", "PMBOK",
        "PMP", "CAPM", "ITIL", "DevOps", "SAFe", "LeSS", "Scaled Agile",
        "Critical Path Method", "Gantt Chart", "Risk Management", "Stakeholder Management"
    ],
    "languages": [
        "English", "French", "German", "Spanish", "Portuguese", "Italian",
        "Chinese", "Mandarin", "Japanese", "Korean", "Arabic", "Hindi",
        "Russian", "Dutch", "Swedish", "Norwegian", "Danish", "Finnish",
        "Polish", "Turkish", "Greek", "Hebrew", "Thai", "Vietnamese"
    ],
    "certifications": [
        "PMP", "CAPM", "AWS Certified", "Azure Certified", "Google Cloud Certified",
        "CISSP", "CISA", "CISM", "CompTIA Security+", "CEH", "OSCP",
        "CCNA", "CCNP", "CCIE", "Network+", "Security+", "A+",
        "Scrum Master", "CSM", "PSM", "PMI-ACP", "ITIL", "COBIT",
        "CPA", "CMA", "CFA", "FRM", "CIA", "CISA"
    ],
    "tools": [
        "Jira", "Confluence", "Trello", "Asana", "Monday.com", "Notion",
        "Slack", "Microsoft Teams", "Zoom", "Webex", "Google Workspace",
        "Microsoft Office", "Office 365", "Google Suite", "Confluence",
        "SharePoint", "Salesforce", "HubSpot", "Zendesk", "ServiceNow"
    ]
}

# Flatten all skills into a single list for fast lookup
ALL_SKILLS = [skill for domain in SKILLS.values() for skill in domain]

# Create a lowercase mapping for case-insensitive matching
SKILLS_LOWER = {skill.lower(): skill for skill in ALL_SKILLS}


def get_all_skills() -> List[str]:
    """Return flat list of all skills"""
    return ALL_SKILLS


def get_skills_by_domain(domain: str) -> List[str]:
    """Return skills for a specific domain"""
    return SKILLS.get(domain, [])


def normalize_skill(skill: str) -> str:
    """Normalize skill name to proper case from dictionary"""
    return SKILLS_LOWER.get(skill.lower(), skill)