package com.resumeai.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a report's data into a real paginated PDF: Thymeleaf renders the document as
 * XHTML, then openhtmltopdf lays it out using CSS paged media so every page carries the
 * running header, footer and page numbers.
 *
 * <p>This runs on a background thread outside any transaction, so it is handed plain
 * values only - never a JPA entity with lazy associations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReportPdfRenderer {

    /** Everything the shared document chrome needs, flattened out of the entity graph. */
    public record Meta(String referenceNo,
                       String title,
                       String description,
                       String scope,
                       String statusLabel,
                       boolean draft,
                       String preparedByName,
                       String preparedByRole,
                       Instant generatedAt,
                       String approvedByName,
                       String approvedByRole,
                       Instant approvedAt,
                       String companyName,
                       String companyLogoDataUri,
                       LocalDate periodStart,
                       LocalDate periodEnd) {

        /**
         * True when the signer is the document's own author, i.e. a platform or personal
         * report that has no superior to counter-sign it. The sign-off block then reads
         * "Acknowledged by" rather than claiming an independent approval.
         */
        public boolean selfAcknowledged() {
            return approvedByName != null && approvedByName.equals(preparedByName)
                    && approvedByRole != null && approvedByRole.equals(preparedByRole);
        }
    }

    public record Rendered(byte[] bytes, int pageCount) {
    }

    private final TemplateEngine templateEngine;

    public Rendered render(Meta meta, String fragment, Map<String, Object> data) {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("meta", meta);
        ctx.setVariable("fragment", fragment);
        ctx.setVariable("d", data);
        ctx.setVariable("chart", new ReportCharts());
        String html = templateEngine.process("reports/report", ctx);

        byte[] pdf;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // no baseUri needed: the only external asset (the company logo) is inlined
            // as a data URI, which keeps rendering hermetic and offline-safe
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            pdf = out.toByteArray();
        } catch (Exception e) {
            log.error("Report rendering failed for {}", meta.referenceNo(), e);
            throw new IllegalStateException("Could not render the report document: " + e.getMessage(), e);
        }
        return new Rendered(pdf, pageCount(pdf));
    }

    private int pageCount(byte[] pdf) {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return doc.getNumberOfPages();
        } catch (Exception e) {
            return 0;
        }
    }
}
