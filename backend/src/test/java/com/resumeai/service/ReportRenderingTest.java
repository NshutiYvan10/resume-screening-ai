package com.resumeai.service;

import com.resumeai.domain.Company;
import com.resumeai.domain.Job;
import com.resumeai.domain.Report;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.ReportScope;
import com.resumeai.domain.enums.ReportType;
import com.resumeai.domain.enums.Role;
import com.resumeai.repository.CompanyRepository;
import com.resumeai.repository.JobRepository;
import com.resumeai.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders every report type in the catalogue against the real database.
 *
 * <p>This is the regression net for the templates: a typo in one Thymeleaf fragment or a
 * renamed analytics key only shows up at render time, and a report type that nobody
 * generates by hand would otherwise stay broken until a user tried it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "app.ai-service.auto-start=false",
                "app.mail.enabled=false",
                "spring.thymeleaf.cache=false"
        })
class ReportRenderingTest {

    @Autowired
    ReportDataService reportDataService;
    @Autowired
    ReportPdfRenderer renderer;
    @Autowired
    CompanyRepository companyRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JobRepository jobRepository;

    @Test
    void everyReportTypeRendersARealPdf() {
        Company company = companyRepository.findAll().stream().findFirst().orElse(null);
        assertNotNull(company, "seed data needs at least one company");
        Job job = jobRepository.findAll().stream().findFirst().orElse(null);
        User recruiter = firstWithRole(Role.RECRUITER, Role.COMPANY_ADMIN);
        User candidate = firstWithRole(Role.CANDIDATE);

        StringBuilder summary = new StringBuilder();
        for (ReportType type : ReportType.values()) {
            ReportCatalog.Definition def = ReportCatalog.require(type);

            Map<String, Object> parameters = new HashMap<>();
            if (def.requiresJob()) {
                assertNotNull(job, "seed data needs at least one job for " + type);
                parameters.put("jobId", job.getId().toString());
            }
            User subject = switch (type) {
                case CANDIDATE_APPLICATION_HISTORY -> candidate;
                case RECRUITER_ACTIVITY -> recruiter;
                default -> null;
            };
            if (def.scope() == ReportScope.PERSONAL) {
                assertNotNull(subject, "seed data needs a subject user for " + type);
            }

            Report report = Report.builder()
                    .referenceNo("RPT-TEST-" + type.ordinal())
                    .type(type)
                    .scope(def.scope())
                    .title(def.title())
                    .company(def.scope() == ReportScope.PLATFORM ? null : company)
                    .subjectUser(subject)
                    .parameters(parameters)
                    .generatedByName("Test Runner")
                    .generatedByRole(Role.SUPER_ADMIN.name())
                    .build();

            Map<String, Object> data = reportDataService.build(report);
            assertNotNull(data, type + " produced no data");

            ReportPdfRenderer.Meta meta = new ReportPdfRenderer.Meta(
                    report.getReferenceNo(), def.title(), def.description(), "Test",
                    "Draft - pending approval", true,
                    "Test Runner", "Platform Administrator", Instant.now(),
                    null, null, null,
                    company.getName(), null, null, null);

            ReportPdfRenderer.Rendered pdf = renderer.render(meta, def.fragment(), data);

            assertTrue(pdf.bytes().length > 1000, type + " rendered a suspiciously small PDF");
            assertTrue(pdf.pageCount() >= 1, type + " rendered no pages");
            assertTrue(startsWithPdfHeader(pdf.bytes()), type + " did not produce a PDF");
            summary.append(String.format("  %-34s %d pages, %d bytes%n",
                    type, pdf.pageCount(), pdf.bytes().length));
        }
        System.out.println("Rendered every report type:\n" + summary);
        assertFalse(summary.isEmpty());
    }

    private User firstWithRole(Role... roles) {
        return userRepository.findAll().stream()
                .filter(u -> List.of(roles).contains(u.getRole()))
                .findFirst().orElse(null);
    }

    private static boolean startsWithPdfHeader(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }
}
