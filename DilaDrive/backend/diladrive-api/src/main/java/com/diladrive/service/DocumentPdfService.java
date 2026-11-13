package com.diladrive.service;

import com.diladrive.dto.DocumentDTO;
import com.diladrive.entity.Assignment;
import com.diladrive.entity.Booking;
import com.diladrive.entity.Document;
import com.diladrive.entity.DocumentType;
import com.diladrive.repository.AssignmentRepository;
import com.diladrive.repository.BookingRepository;
import com.diladrive.repository.DocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DocumentPdfService {

    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float PAGE_WIDTH = PAGE_SIZE.getWidth();
    private static final float PAGE_HEIGHT = PAGE_SIZE.getHeight();
    private static final float MARGIN = 42;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2;
    private static final float BOTTOM_LIMIT = 68;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private static final Color NAVY = new Color(23, 58, 96);
    private static final Color BLUE = new Color(33, 112, 184);
    private static final Color PALE_BLUE = new Color(244, 248, 252);
    private static final Color BORDER = new Color(217, 227, 237);
    private static final Color TEXT = new Color(36, 48, 62);
    private static final Color MUTED = new Color(99, 113, 130);
    private static final Color WHITE = Color.WHITE;
    private static final Color LIGHT_WATERMARK = new Color(235, 242, 249);

    private static final PDFont REGULAR =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final BookingRepository bookingRepository;
    private final AssignmentRepository assignmentRepository;
    private final DocumentRepository documentRepository;
    private final String companyName;
    private final String companyAddress;
    private final String companyEmail;
    private final String companyPhone;
    private final String companyWebsite;
    private final String quotationTerms;
    private final int quotationValidityDays;
    private final int invoiceDueDays;

    public DocumentPdfService(
            BookingRepository bookingRepository,
            AssignmentRepository assignmentRepository,
            DocumentRepository documentRepository,
            @Value("${app.documents.company-name:DilaDrive Transport Management System}")
            String companyName,
            @Value("${app.documents.company-address:}") String companyAddress,
            @Value("${app.documents.company-email:}") String companyEmail,
            @Value("${app.documents.company-phone:}") String companyPhone,
            @Value("${app.documents.company-website:}") String companyWebsite,
            @Value("${app.documents.quotation-terms:Prices are stated in Malaysian Ringgit (RM). Final service arrangements are subject to booking confirmation.}")
            String quotationTerms,
            @Value("${app.documents.quotation-validity-days:30}") int quotationValidityDays,
            @Value("${app.documents.invoice-due-days:30}") int invoiceDueDays) {
        this.bookingRepository = bookingRepository;
        this.assignmentRepository = assignmentRepository;
        this.documentRepository = documentRepository;
        this.companyName = companyName;
        this.companyAddress = companyAddress;
        this.companyEmail = companyEmail;
        this.companyPhone = companyPhone;
        this.companyWebsite = companyWebsite;
        this.quotationTerms = quotationTerms;
        if (quotationValidityDays < 1 || invoiceDueDays < 1) {
            throw new IllegalArgumentException(
                    "Quotation validity and invoice due days must be greater than zero.");
        }
        this.quotationValidityDays = quotationValidityDays;
        this.invoiceDueDays = invoiceDueDays;
    }

    public byte[] generate(DocumentDTO document) throws IOException {
        Booking booking = bookingRepository.findById(document.bookingId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "The booking linked to this document was not found."));
        Optional<Assignment> assignment = assignmentRepository
                .findTopByBookingBookingIdOrderByAssignmentDateDesc(document.bookingId());
        Document sourceDocument = document.sourceDocumentId() == null
                ? null
                : documentRepository.findById(document.sourceDocumentId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "The source document linked to this document was not found."));

        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            pdf.getDocumentInformation().setTitle(
                    document.documentType().name().replace('_', ' ') + " "
                            + document.documentNumber());
            pdf.getDocumentInformation().setAuthor("DilaDrive Transport Management System");
            pdf.getDocumentInformation().setSubject("DilaDrive business document");

            PageLayout layout = new PageLayout(pdf);
            layout.startPage();
            layout.drawTitle(document);
            layout.drawCard("CUSTOMER INFORMATION", List.of(
                    new PdfField("Customer Name", valueOr(document.custName(), "Not provided")),
                    new PdfField("Email", valueOr(document.custEmail(), "Not provided")),
                    new PdfField("Phone Number", valueOr(document.custPhone(), "Not provided")),
                    new PdfField("Address", "Not provided")));
            layout.drawCard("BOOKING INFORMATION", List.of(
                    new PdfField("Booking ID", valueOr(booking.getBookingId(), "Not recorded")),
                    new PdfField("Trip Date", formatDate(booking.getBookingTripdate())),
                    new PdfField("Pickup Time", formatTime(booking.getBookingPickuptime())),
                    new PdfField("Passenger Count", valueOr(booking.getBookingPassengercount(),
                            "Not recorded")),
                    new PdfField("Pickup Location", valueOr(
                            booking.getBookingPickupaddress(), "Not provided")),
                    new PdfField("Destination", valueOr(
                            booking.getBookingDestinationaddress(), "Not provided")),
                    new PdfField("Vehicle Type", valueOr(booking.getBookingType(),
                            "Not recorded"))));

            Assignment assigned = assignment.orElse(null);
            layout.drawCard("ASSIGNMENT INFORMATION", List.of(
                    new PdfField("Driver Name", assigned == null
                            ? "Not assigned" : valueOr(assigned.getDriver().getDriverName(),
                                    "Not recorded")),
                    new PdfField("Driver Phone Number", assigned == null
                            ? "Not assigned" : valueOr(assigned.getDriver().getDriverPhoneno(),
                                    "Not recorded")),
                    new PdfField("Vehicle Plate Number", assigned == null
                            ? "Not assigned" : valueOr(assigned.getVehicle().getVehiclePlateno(),
                                    "Not recorded")),
                    new PdfField("Vehicle Model", assigned == null
                            ? "Not assigned" : valueOr(assigned.getVehicle().getVehicleModel(),
                                    "Not recorded")),
                    new PdfField("Vehicle Capacity", assigned == null
                            ? "Not assigned" : formatCapacity(
                                    assigned.getVehicle().getVehicleCapacity()))));

            layout.drawCard("DOCUMENT DETAILS", documentFields(
                    document, sourceDocument));
            layout.drawAmountTable(document, booking);
            layout.drawParagraph("REMARKS",
                    valueOr(document.remarks(), "No remarks provided."));
            if (document.documentType() == DocumentType.QUOTATION) {
                layout.drawParagraph("QUOTATION TERMS & CONDITIONS",
                        valueOr(quotationTerms, "No additional terms provided."));
            }
            layout.drawApprovalArea();
            layout.finishPage();
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private List<PdfField> documentFields(DocumentDTO document, Document sourceDocument) {
        LocalDate issuedDate = document.createdAt() == null
                ? LocalDate.now()
                : document.createdAt().toLocalDate();
        return switch (document.documentType()) {
            case QUOTATION -> List.of(
                    new PdfField("Valid Until",
                            formatDate(issuedDate.plusDays(quotationValidityDays))),
                    new PdfField("Approval / Rejection Status",
                            valueOr(document.status(), "Not recorded")));
            case PURCHASE_ORDER -> List.of(
                    new PdfField("Source Quotation Number",
                            sourceDocument == null ? "Not linked"
                                    : sourceDocument.getDocumentNumber()),
                    new PdfField("Purchase Order Reference",
                            valueOr(document.documentNumber(), "Not recorded")));
            case INVOICE -> List.of(
                    new PdfField("Invoice Due Date",
                            formatDate(issuedDate.plusDays(invoiceDueDays))),
                    new PdfField("Payment Status",
                            valueOr(document.status(), "Not recorded")),
                    new PdfField("Payment Method", "Not recorded"));
            case RECEIPT -> List.of(
                    new PdfField("Receipt Date", formatDate(issuedDate)),
                    new PdfField("Payment Reference", document.paymentId() == null
                            ? "Not recorded" : "PAY-%06d".formatted(document.paymentId())),
                    new PdfField("Invoice Reference",
                            sourceDocument == null ? "Not linked"
                                    : sourceDocument.getDocumentNumber()),
                    new PdfField("Paid Amount", formatCurrency(document.amount())));
        };
    }

    private static String formatCapacity(Integer capacity) {
        return capacity == null ? "Not recorded" : capacity + " passengers";
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "Not recorded" : DATE_FORMAT.format(date);
    }

    private static String formatTime(LocalTime time) {
        return time == null ? "Not recorded" : TIME_FORMAT.format(time);
    }

    private static String valueOr(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private static String formatCurrency(BigDecimal amount) {
        BigDecimal normalized = amount == null
                ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        return "RM " + String.format(Locale.ROOT, "%,.2f", normalized);
    }

    private record PdfField(String label, String value) {
    }

    private final class PageLayout {
        private final PDDocument pdf;
        private PDPage page;
        private PDPageContentStream content;
        private float y;
        private int pageNumber;

        private PageLayout(PDDocument pdf) {
            this.pdf = pdf;
        }

        private void startPage() throws IOException {
            page = new PDPage(PAGE_SIZE);
            pdf.addPage(page);
            content = new PDPageContentStream(pdf, page);
            pageNumber++;
            drawWatermark();
            drawHeader();
            y = PAGE_HEIGHT - 116;
        }

        private void finishPage() throws IOException {
            content.setStrokingColor(BORDER);
            content.setLineWidth(0.7f);
            content.moveTo(MARGIN, 48);
            content.lineTo(PAGE_WIDTH - MARGIN, 48);
            content.stroke();
            drawText(content, "Generated by DilaDrive Transport Management System",
                    MARGIN, 31, 8, REGULAR, MUTED);
            drawRightText(content, "Page " + pageNumber,
                    PAGE_WIDTH - MARGIN, 31, 8, BOLD, NAVY);
            content.close();
        }

        private void drawHeader() throws IOException {
            float top = PAGE_HEIGHT - 30;
            drawLogo(MARGIN, top - 42);
            List<String> companyNameLines = wrapText(
                    companyName, BOLD, 11.5f, 226);
            float companyY = top - 17;
            for (String line : companyNameLines) {
                drawText(content, line, MARGIN + 56, companyY, 11.5f, BOLD, NAVY);
                companyY -= 13;
            }
            drawText(content, "TRANSPORT MANAGEMENT SYSTEM",
                    MARGIN + 56, top - 45, 7.5f, BOLD, BLUE);

            float right = PAGE_WIDTH - MARGIN;
            float contactY = top - 12;
            String[] companyDetails = {
                    displayCompanyValue(companyAddress, "Company address"),
                    displayCompanyValue(companyEmail, "Company email"),
                    displayCompanyValue(companyPhone, "Company phone"),
                    displayCompanyValue(companyWebsite, "Company website")
            };
            for (String detail : companyDetails) {
                for (String line : wrapText(detail, REGULAR, 7.5f, 230)) {
                    drawRightText(content, line, right, contactY, 7.5f, REGULAR, TEXT);
                    contactY -= 10;
                }
            }

            content.setStrokingColor(BLUE);
            content.setLineWidth(1.5f);
            content.moveTo(MARGIN, PAGE_HEIGHT - 84);
            content.lineTo(PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 84);
            content.stroke();
        }

        private void drawLogo(float x, float y) throws IOException {
            content.setNonStrokingColor(NAVY);
            content.addRect(x, y, 42, 42);
            content.fill();
            drawText(content, "D", x + 12.5f, y + 11, 24, BOLD, WHITE);
            content.setNonStrokingColor(new Color(71, 183, 219));
            content.addRect(x + 27, y + 5, 9, 3);
            content.fill();
        }

        private void drawWatermark() throws IOException {
            content.saveGraphicsState();
            content.transform(Matrix.getRotateInstance(
                    (float) Math.toRadians(34), PAGE_WIDTH * 0.3f, PAGE_HEIGHT * 0.43f));
            drawText(content, "DilaDrive", 0, 0, 46, BOLD, LIGHT_WATERMARK);
            content.restoreGraphicsState();
        }

        private void drawTitle(DocumentDTO document) throws IOException {
            Color titleColor = switch (document.documentType()) {
                case QUOTATION -> BLUE;
                case PURCHASE_ORDER -> NAVY;
                case INVOICE -> new Color(24, 112, 137);
                case RECEIPT -> new Color(36, 125, 91);
            };
            String title = document.documentType().name().replace('_', ' ');
            drawText(content, title, MARGIN, y, 23, BOLD, titleColor);
            drawStatusBadge(valueOr(document.status(), "UNKNOWN"),
                    PAGE_WIDTH - MARGIN, y + 5);
            y -= 22;
            drawText(content, "Document No.  " + valueOr(document.documentNumber(),
                            "Not assigned"),
                    MARGIN, y, 9, BOLD, TEXT);
            drawText(content, "Created Date  " + formatDateTime(document.createdAt()),
                    MARGIN + 220, y, 9, REGULAR, MUTED);
            y -= 19;
            content.setStrokingColor(BORDER);
            content.setLineWidth(0.7f);
            content.moveTo(MARGIN, y);
            content.lineTo(PAGE_WIDTH - MARGIN, y);
            content.stroke();
            y -= 12;
        }

        private void drawStatusBadge(String status, float right, float baseline)
                throws IOException {
            float fontSize = 8;
            float width = textWidth(status, BOLD, fontSize) + 18;
            float x = right - width;
            Color fill = "APPROVED".equalsIgnoreCase(status)
                    || "PAID".equalsIgnoreCase(status)
                    ? new Color(224, 245, 235)
                    : "REJECTED".equalsIgnoreCase(status)
                            ? new Color(253, 232, 232)
                            : "DRAFT".equalsIgnoreCase(status)
                                    ? new Color(255, 247, 220)
                                    : new Color(228, 240, 252);
            Color ink = "APPROVED".equalsIgnoreCase(status)
                    || "PAID".equalsIgnoreCase(status)
                    ? new Color(28, 112, 74)
                    : "REJECTED".equalsIgnoreCase(status)
                            ? new Color(164, 53, 53)
                            : "DRAFT".equalsIgnoreCase(status)
                                    ? new Color(143, 101, 14)
                                    : NAVY;
            content.setNonStrokingColor(fill);
            content.addRect(x, baseline - 5, width, 18);
            content.fill();
            drawText(content, status.toUpperCase(Locale.ROOT), x + 9,
                    baseline, fontSize, BOLD, ink);
        }

        private void drawCard(String heading, List<PdfField> fields) throws IOException {
            float columnWidth = (CONTENT_WIDTH - 36) / 2;
            List<List<String>> wrappedValues = new ArrayList<>();
            for (PdfField field : fields) {
                wrappedValues.add(wrapText(
                        valueOr(field.value(), "Not recorded"), REGULAR, 8.5f,
                        columnWidth - 14));
            }
            int rows = (fields.size() + 1) / 2;
            float height = 32;
            for (int row = 0; row < rows; row++) {
                int leftIndex = row * 2;
                int rightIndex = leftIndex + 1;
                int lineCount = wrappedValues.get(leftIndex).size();
                if (rightIndex < fields.size()) {
                    lineCount = Math.max(lineCount, wrappedValues.get(rightIndex).size());
                }
                height += Math.max(29, 15 + lineCount * 11);
            }
            height += 8;
            ensureSpace(height);

            float top = y;
            content.setLineWidth(0.7f);
            content.setStrokingColor(BORDER);
            content.setNonStrokingColor(PALE_BLUE);
            content.addRect(MARGIN, top - height, CONTENT_WIDTH, height);
            content.fillAndStroke();
            drawText(content, heading, MARGIN + 12, top - 18, 8, BOLD, BLUE);

            float rowTop = top - 34;
            float secondColumnX = MARGIN + 18 + columnWidth;
            for (int row = 0; row < rows; row++) {
                int leftIndex = row * 2;
                int rightIndex = leftIndex + 1;
                int maxLines = drawField(fields.get(leftIndex), wrappedValues.get(leftIndex),
                        MARGIN + 12, rowTop);
                if (rightIndex < fields.size()) {
                    maxLines = Math.max(maxLines, drawField(
                            fields.get(rightIndex), wrappedValues.get(rightIndex),
                            secondColumnX, rowTop));
                }
                rowTop -= Math.max(29, 15 + maxLines * 11);
            }
            y = top - height - 11;
        }

        private int drawField(
                PdfField field, List<String> values, float x, float top)
                throws IOException {
            drawText(content, field.label().toUpperCase(Locale.ROOT),
                    x, top, 6.7f, BOLD, MUTED);
            float valueY = top - 12;
            for (String line : values) {
                drawText(content, line, x, valueY, 8.5f, REGULAR, TEXT);
                valueY -= 11;
            }
            return values.size();
        }

        private void drawAmountTable(DocumentDTO document, Booking booking)
                throws IOException {
            float descriptionWidth = 300;
            float quantityWidth = 55;
            drawTableHeader(descriptionWidth, quantityWidth);
            String description = "Transportation service";
            if (booking.getBookingType() != null
                    && !booking.getBookingType().isBlank()) {
                description += " - " + booking.getBookingType();
            }
            drawTableRow(description, "1", formatCurrency(document.amount()),
                    descriptionWidth, quantityWidth);

            float summaryHeight = 99;
            if (ensureSpace(summaryHeight)) {
                drawTableHeader(descriptionWidth, quantityWidth);
            }
            float x = MARGIN;
            float summaryTop = y;
            drawSummaryLine("Subtotal", formatCurrency(document.amount()),
                    x, summaryTop - 16);
            drawSummaryLine("Tax", formatCurrency(BigDecimal.ZERO),
                    x, summaryTop - 37);

            content.setNonStrokingColor(NAVY);
            content.addRect(MARGIN, summaryTop - 77, CONTENT_WIDTH, 32);
            content.fill();
            drawText(content, "TOTAL AMOUNT", MARGIN + 12,
                    summaryTop - 65, 10, BOLD, WHITE);
            drawRightText(content, formatCurrency(document.amount()),
                    PAGE_WIDTH - MARGIN - 12, summaryTop - 65, 13, BOLD, WHITE);
            y = summaryTop - 89;
        }

        private void drawTableHeader(
                float descriptionWidth, float quantityWidth)
                throws IOException {
            float height = 26;
            ensureSpace(height + 28);
            content.setNonStrokingColor(NAVY);
            content.addRect(MARGIN, y - height, CONTENT_WIDTH, height);
            content.fill();
            drawText(content, "DESCRIPTION", MARGIN + 10, y - 17,
                    7.5f, BOLD, WHITE);
            drawCenteredText(content, "QTY", MARGIN + descriptionWidth,
                    quantityWidth, y - 17, 7.5f, BOLD, WHITE);
            drawRightText(content, "AMOUNT", PAGE_WIDTH - MARGIN - 10,
                    y - 17, 7.5f, BOLD, WHITE);
            content.setStrokingColor(WHITE);
            content.setLineWidth(0.5f);
            content.moveTo(MARGIN + descriptionWidth, y - height);
            content.lineTo(MARGIN + descriptionWidth, y);
            content.moveTo(MARGIN + descriptionWidth + quantityWidth, y - height);
            content.lineTo(MARGIN + descriptionWidth + quantityWidth, y);
            content.stroke();
            y -= height;
        }

        private void drawTableRow(
                String description,
                String quantity,
                String amount,
                float descriptionWidth,
                float quantityWidth) throws IOException {
            List<String> lines = wrapText(description, REGULAR, 8.5f,
                    descriptionWidth - 20);
            float height = Math.max(30, 13 + lines.size() * 11);
            if (ensureSpace(height + 25)) {
                drawTableHeader(descriptionWidth, quantityWidth);
            }
            content.setLineWidth(0.5f);
            content.setStrokingColor(BORDER);
            content.setNonStrokingColor(WHITE);
            content.addRect(MARGIN, y - height, CONTENT_WIDTH, height);
            content.fillAndStroke();
            content.setStrokingColor(BORDER);
            content.setLineWidth(0.5f);
            content.moveTo(MARGIN + descriptionWidth, y - height);
            content.lineTo(MARGIN + descriptionWidth, y);
            content.moveTo(MARGIN + descriptionWidth + quantityWidth, y - height);
            content.lineTo(MARGIN + descriptionWidth + quantityWidth, y);
            content.stroke();
            float lineY = y - 17;
            for (String line : lines) {
                drawText(content, line, MARGIN + 10, lineY, 8.5f, REGULAR, TEXT);
                lineY -= 11;
            }
            drawCenteredText(content, quantity, MARGIN + descriptionWidth,
                    quantityWidth, y - 18, 8.5f, REGULAR, TEXT);
            drawRightText(content, amount, PAGE_WIDTH - MARGIN - 10,
                    y - 18, 8.5f, REGULAR, TEXT);
            y -= height;
        }

        private void drawSummaryLine(
                String label, String amount, float x, float baseline)
                throws IOException {
            drawText(content, label, x + 10, baseline, 8.5f, REGULAR, TEXT);
            drawRightText(content, amount, PAGE_WIDTH - MARGIN - 10,
                    baseline, 8.5f, REGULAR, TEXT);
        }

        private void drawParagraph(String heading, String value) throws IOException {
            List<String> lines = wrapText(value, REGULAR, 8.5f, CONTENT_WIDTH - 24);
            float height = 26 + Math.max(1, lines.size()) * 12;
            ensureSpace(Math.min(height, PAGE_HEIGHT - 150));
            drawText(content, heading, MARGIN, y, 8, BOLD, BLUE);
            y -= 16;
            for (String line : lines) {
                ensureSpace(13);
                drawText(content, line, MARGIN, y, 8.5f, REGULAR, TEXT);
                y -= 12;
            }
            y -= 9;
        }

        private void drawApprovalArea() throws IOException {
            float height = 92;
            ensureSpace(height);
            float top = y;
            float gap = 12;
            float boxWidth = (CONTENT_WIDTH - 2 * gap) / 3;
            String[] labels = {"Prepared By", "Approved By", "Company Stamp"};
            for (int index = 0; index < labels.length; index++) {
                float x = MARGIN + index * (boxWidth + gap);
                content.setLineWidth(0.7f);
                content.setStrokingColor(BORDER);
                content.setNonStrokingColor(WHITE);
                content.addRect(x, top - height, boxWidth, height);
                content.fillAndStroke();
                drawText(content, labels[index].toUpperCase(Locale.ROOT),
                        x + 9, top - 16, 6.7f, BOLD, MUTED);
                content.setStrokingColor(MUTED);
                content.setLineWidth(0.6f);
                content.moveTo(x + 9, top - 64);
                content.lineTo(x + boxWidth - 9, top - 64);
                content.stroke();
                if (index < 2) {
                    drawText(content, "Signature / date",
                            x + 9, top - 77, 6.5f, REGULAR, MUTED);
                }
            }
            y = top - height - 12;
        }

        private boolean ensureSpace(float height) throws IOException {
            if (y - height >= BOTTOM_LIMIT) {
                return false;
            }
            finishPage();
            startPage();
            return true;
        }

        private void drawCenteredText(
                PDPageContentStream stream,
                String text,
                float left,
                float width,
                float baseline,
                float size,
                PDFont font,
                Color color) throws IOException {
            float x = left + Math.max(0, (width - textWidth(text, font, size)) / 2);
            drawText(stream, text, x, baseline, size, font, color);
        }
    }

    private static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null
                ? "Not recorded"
                : DATE_FORMAT.format(dateTime.toLocalDate());
    }

    private static String displayCompanyValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback + " not configured" : value.trim();
    }

    private static List<String> wrapText(String value, PDFont font, float size, float width) {
        List<String> lines = new ArrayList<>();
        String normalized = sanitize(value);
        for (String paragraph : normalized.split("\\R", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && textWidth(candidate, font, size) > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (textWidth(word, font, size) > width) {
                    if (!line.isEmpty()) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    StringBuilder segment = new StringBuilder();
                    for (char character : word.toCharArray()) {
                        String candidateSegment = segment.toString() + character;
                        if (!segment.isEmpty()
                                && textWidth(candidateSegment, font, size) > width) {
                            lines.add(segment.toString());
                            segment.setLength(0);
                        }
                        segment.append(character);
                    }
                    line.append(segment);
                } else {
                    if (!line.isEmpty()) {
                        line.append(' ');
                    }
                    line.append(word);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static float textWidth(String value, PDFont font, float size) {
        try {
            return font.getStringWidth(sanitize(value)) / 1000f * size;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to measure PDF text.", exception);
        }
    }

    private static String sanitize(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value,
                Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        return normalized.replaceAll("[^\\x20-\\x7E\\r\\n\\t]", "?");
    }

    private static void drawText(
            PDPageContentStream stream,
            String value,
            float x,
            float y,
            float size,
            PDFont font,
            Color color) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, y);
        stream.showText(sanitize(value));
        stream.endText();
    }

    private static void drawRightText(
            PDPageContentStream stream,
            String value,
            float right,
            float y,
            float size,
            PDFont font,
            Color color) throws IOException {
        drawText(stream, value, right - textWidth(value, font, size),
                y, size, font, color);
    }
}
