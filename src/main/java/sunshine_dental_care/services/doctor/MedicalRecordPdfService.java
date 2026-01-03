package sunshine_dental_care.services.doctor;

// Note: PdfFontFactory may need font module dependency for full support
// For now, we'll use default fonts (may not support Vietnamese fully)
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sunshine_dental_care.entities.*;



import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLConnection;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedicalRecordPdfService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm");
    
    // Font hỗ trợ tiếng Việt - sử dụng font mặc định có hỗ trợ Unicode
    // Note: Standard fonts không hỗ trợ tiếng Việt, cần font file hoặc pdfCalligraph
    // Tạm thời sử dụng font mặc định và đảm bảo encoding UTF-8
    private PdfFont normalFont;
    private PdfFont italicFont;
    private PdfFont boldFont;


    public byte[] generatePdf(MedicalRecord record) throws IOException {
         normalFont = PdfFontFactory.createFont(
                "fonts/NotoSans-VariableFont_wdth,wght.ttf",
                PdfEncodings.IDENTITY_H,
                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
        );

        boldFont = PdfFontFactory.createFont(
                "fonts/NotoSans-BoldItalic.ttf",
                PdfEncodings.IDENTITY_H,
                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
        );

        italicFont = PdfFontFactory.createFont(
                "fonts/NotoSans-Italic-VariableFont_wdth,wght.ttf",
                PdfEncodings.IDENTITY_H,
                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
        );
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.A4);
        document.setFont(normalFont);
        document.setMargins(50, 50, 50, 50);
        


        try {
            // Header Section
            addHeader(document, record.getClinic());
            
            // Title
            Paragraph title = new Paragraph("DENTAL TREATMENT RECORD");
            if (boldFont != null) title.setFont(boldFont);
            title.setFontSize(18)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10);
            document.add(title);

            // Date and Patient ID
            Paragraph dateInfo = new Paragraph();
            if (normalFont != null) dateInfo.setFont(normalFont);
            if (record.getRecordDate() != null) {
                dateInfo.add("Date: " + record.getRecordDate().format(DATE_FORMATTER));
            }
            if (record.getPatient() != null && record.getPatient().getPatientCode() != null) {
                dateInfo.add(" | Patient ID: " + record.getPatient().getPatientCode());
            }
            dateInfo.setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(dateInfo);

            // Patient Information Section
            addPatientInfo(document, record.getPatient());
            
            // Doctor Information
            addDoctorInfo(document, record.getDoctor());

            // Appointment Information (if available)
            if (record.getAppointment() != null) {
                addAppointmentInfo(document, record.getAppointment());
            }

            // Service and Variant Information
            addServiceInfo(document, record);

            // Diagnosis Section
            if (record.getDiagnosis() != null && !record.getDiagnosis().trim().isEmpty()) {
                addSection(document, "Diagnosis", record.getDiagnosis());
            }

            // Treatment Plan Section
            if (record.getTreatmentPlan() != null && !record.getTreatmentPlan().trim().isEmpty()) {
                addSection(document, "Treatment Plan", record.getTreatmentPlan());
            }

            // Prescription Section
            if (record.getPrescriptionNote() != null && !record.getPrescriptionNote().trim().isEmpty()) {
                addSection(document, "Prescription", record.getPrescriptionNote());
            }

            // Notes Section
            if (record.getNote() != null && !record.getNote().trim().isEmpty()) {
                addSection(document, "Notes", record.getNote());
            }

            // Images Section
            log.info("[PDF] Checking for images in medical record. Images collection: {}", 
                    record.getMedicalRecordImages() != null ? "not null" : "null");
            if (record.getMedicalRecordImages() != null) {
                log.info("[PDF] Images collection size: {}", record.getMedicalRecordImages().size());
                if (!record.getMedicalRecordImages().isEmpty()) {
                    addImagesSection(document, record.getMedicalRecordImages());
                } else {
                    log.warn("[PDF] Images collection is empty");
                }
            } else {
                log.warn("[PDF] Images collection is null");
            }

            // Footer with signatures
//            addSignatureSection(document);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            document.close();
            throw new IOException("Failed to generate PDF", e);
        }
    }

    private void addHeader(Document document, Clinic clinic) {
        if (clinic != null) {
            Paragraph clinicName = new Paragraph(clinic.getClinicName() != null ? clinic.getClinicName() : "DENTAL CLINIC");
            if (boldFont != null) clinicName.setFont(boldFont);
            clinicName.setFontSize(16)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(5);
            document.add(clinicName);

            if (clinic.getAddress() != null) {
                Paragraph address = new Paragraph("Address: " + clinic.getAddress());
                if (normalFont != null) address.setFont(normalFont);
                address.setFontSize(10)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(2);
                document.add(address);
            }

            if (clinic.getPhone() != null) {
                Paragraph phone = new Paragraph("Phone: " + clinic.getPhone());
                if (normalFont != null) phone.setFont(normalFont);
                phone.setFontSize(10)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(2);
                document.add(phone);
            }

            if (clinic.getEmail() != null) {
                Paragraph email = new Paragraph("Email: " + clinic.getEmail());
                if (normalFont != null) email.setFont(normalFont);
                email.setFontSize(10)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(15);
                document.add(email);
            }
        }
    }

    private void addPatientInfo(Document document, Patient patient) {
        if (patient == null) return;

        Paragraph sectionTitle = new Paragraph("PATIENT INFORMATION");
        if (boldFont != null) sectionTitle.setFont(boldFont);
        sectionTitle.setFontSize(12)
                .setBold()
                .setMarginTop(10)
                .setMarginBottom(5);
        document.add(sectionTitle);

        Table table = new Table(2).useAllAvailableWidth();
        table.setMarginBottom(10);

        addTableRow(table, "Full Name:", patient.getFullName() != null ? patient.getFullName() : "N/A");
        
        if (patient.getGender() != null) {
            addTableRow(table, "Gender:", patient.getGender());
        }
        
        if (patient.getDateOfBirth() != null) {
            addTableRow(table, "Date of Birth:", patient.getDateOfBirth().format(DATE_FORMATTER));
        }
        
        if (patient.getPhone() != null) {
            addTableRow(table, "Phone:", patient.getPhone());
        }
        
        if (patient.getEmail() != null) {
            addTableRow(table, "Email:", patient.getEmail());
        }
        
        if (patient.getAddress() != null) {
            addTableRow(table, "Address:", patient.getAddress());
        }

        document.add(table);
    }

    private void addDoctorInfo(Document document, User doctor) {
        if (doctor == null) return;

        Paragraph sectionTitle = new Paragraph("TREATING DOCTOR");
        if (boldFont != null) sectionTitle.setFont(boldFont);
        sectionTitle.setFontSize(12)
                .setBold()
                .setMarginTop(10)
                .setMarginBottom(5);
        document.add(sectionTitle);

        Table table = new Table(2).useAllAvailableWidth();
        table.setMarginBottom(10);

        addTableRow(table, "Doctor Name:", doctor.getFullName() != null ? doctor.getFullName() : "N/A");
        
        if (doctor.getCode() != null) {
            addTableRow(table, "Doctor Code:", doctor.getCode());
        }
        
        if (doctor.getEmail() != null) {
            addTableRow(table, "Email:", doctor.getEmail());
        }
        
        if (doctor.getPhone() != null) {
            addTableRow(table, "Phone:", doctor.getPhone());
        }

        document.add(table);
    }

    private void addAppointmentInfo(Document document, Appointment appointment) {
        Paragraph sectionTitle = new Paragraph("APPOINTMENT INFORMATION");
        if (boldFont != null) sectionTitle.setFont(boldFont);
        sectionTitle.setFontSize(12)
                .setBold()
                .setMarginTop(10)
                .setMarginBottom(5);
        document.add(sectionTitle);

        Table table = new Table(2).useAllAvailableWidth();
        table.setMarginBottom(10);

        if (appointment.getStartDateTime() != null) {
            String formattedDate = appointment.getStartDateTime()
                    .atZone(ZoneId.systemDefault())
                    .format(DATETIME_FORMATTER);
            addTableRow(table, "Appointment Date:", formattedDate);
        }
        
        if (appointment.getStatus() != null) {
            addTableRow(table, "Status:", appointment.getStatus());
        }
        
        if (appointment.getNote() != null) {
            addTableRow(table, "Note:", appointment.getNote());
        }

        document.add(table);
    }

    private void addServiceInfo(Document document, MedicalRecord record) {
        sunshine_dental_care.entities.Service service = record.getService();
        ServiceVariant serviceVariant = record.getServiceVariant();
        AppointmentService appointmentService = record.getAppointmentService();

        // If appointmentService exists, use it for service and variant info
        if (appointmentService != null) {
            service = appointmentService.getService();
            serviceVariant = appointmentService.getServiceVariant();
        }

        if (service == null && serviceVariant == null) {
            return;
        }

        Paragraph sectionTitle = new Paragraph("SERVICE INFORMATION");
        if (boldFont != null) sectionTitle.setFont(boldFont);
        sectionTitle.setFontSize(12)
                .setBold()
                .setMarginTop(10)
                .setMarginBottom(5);
        document.add(sectionTitle);

        Table table = new Table(2).useAllAvailableWidth();
        table.setMarginBottom(10);

        if (service != null) {
            if (service.getServiceName() != null) {
                addTableRow(table, "Service Name:", service.getServiceName());
            }
            if (service.getCategory() != null) {
                addTableRow(table, "Category:", service.getCategory());
            }
            if (service.getDescription() != null) {
                addTableRow(table, "Service Description:", service.getDescription());
            }
            if (service.getDefaultDuration() != null) {
                addTableRow(table, "Default Duration (minutes):", String.valueOf(service.getDefaultDuration()));
            }
        }

        if (serviceVariant != null) {
            if (serviceVariant.getVariantName() != null) {
                addTableRow(table, "Variant Name:", serviceVariant.getVariantName());
            }
            if (serviceVariant.getDescription() != null) {
                addTableRow(table, "Variant Description:", serviceVariant.getDescription());
            }
            if (serviceVariant.getDuration() != null) {
                addTableRow(table, "Duration (minutes):", String.valueOf(serviceVariant.getDuration()));
            }
            if (serviceVariant.getPrice() != null) {
                String currency = serviceVariant.getCurrency() != null ? serviceVariant.getCurrency() : "VND";
                addTableRow(table, "Price:", formatPrice(serviceVariant.getPrice(), currency));
            }
        }

        if (appointmentService != null) {
            if (appointmentService.getQuantity() != null) {
                addTableRow(table, "Quantity:", String.valueOf(appointmentService.getQuantity()));
            }
            if (appointmentService.getUnitPrice() != null) {
                addTableRow(table, "Unit Price:", formatPrice(appointmentService.getUnitPrice(), "VND"));
            }
            if (appointmentService.getDiscountPct() != null) {
                addTableRow(table, "Discount (%):", String.valueOf(appointmentService.getDiscountPct()));
            }
            if (appointmentService.getNote() != null) {
                addTableRow(table, "Service Note:", appointmentService.getNote());
            }
        }

        document.add(table);
    }

    private void addSection(Document document, String title, String content) {
        Paragraph sectionTitle = new Paragraph(title.toUpperCase());
        if (boldFont != null) sectionTitle.setFont(boldFont);
        sectionTitle.setFontSize(12)
                .setBold()
                .setMarginTop(10)
                .setMarginBottom(5);
        document.add(sectionTitle);

        Paragraph contentPara = new Paragraph(content);
        if (normalFont != null) contentPara.setFont(normalFont
        );
        contentPara.setFontSize(10)
                .setMarginBottom(10);
        document.add(contentPara);
    }

    /**
     * Add images section as a clean 2-column grid.
     * - Auto scale images
     * - Handle odd number of images
     * - Prevent table breaking across pages
     */
    private void addImagesSection(Document document, Set<MedicalRecordImage> images) throws IOException {
        if (images == null || images.isEmpty()) {
            log.warn("[PDF] No images to add");
            return;
        }

        log.info("[PDF] Adding {} images to PDF", images.size());

        // ===== SECTION TITLE =====
        Paragraph sectionTitle = new Paragraph("X-RAY FILMS / IMAGES")
                .setFontSize(12)
                .setBold()
                .setMarginTop(15)
                .setMarginBottom(10);
        document.add(sectionTitle);

        // ===== TABLE CONFIG =====
        int columnCount = 2;
        Table imageTable = new Table(columnCount).useAllAvailableWidth();


        // ===== LAYOUT CONSTANTS =====
        float fixedCellHeight = 320f;   // TẤT CẢ CELL CÙNG CHIỀU CAO
        float cellPadding = 6f;

        int cellCount = 0;

        for (MedicalRecordImage imageRecord : images) {

            if (imageRecord.getImageUrl() == null || imageRecord.getImageUrl().isBlank()) {
                log.warn("[PDF] Image URL is empty, skipping");
                continue;
            }

            try {
                // ===== DOWNLOAD IMAGE =====
                URL url = new URL(imageRecord.getImageUrl().trim());
                URLConnection conn = url.openConnection();
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                byte[] imageBytes;
                try (InputStream is = conn.getInputStream();
                     ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                    byte[] data = new byte[8192];
                    int nRead;
                    while ((nRead = is.read(data)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    imageBytes = buffer.toByteArray();
                }

                if (imageBytes.length == 0) {
                    throw new IOException("Image data is empty");
                }

                // ===== CREATE IMAGE =====
                ImageData imageData = ImageDataFactory.create(imageBytes);
                Image pdfImage = new Image(imageData);

                // Scale ảnh để vừa cell (giữ tỷ lệ)
                float maxImageHeight = fixedCellHeight - 40;
                pdfImage.scaleToFit(240, maxImageHeight);
                pdfImage.setHorizontalAlignment(HorizontalAlignment.CENTER);

                // ===== CREATE CELL =====
                Cell imageCell = new Cell()
                        .setHeight(fixedCellHeight)
                        .setPadding(cellPadding)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE);

                imageCell.add(pdfImage);

                // Optional: description
                if (imageRecord.getDescription() != null && !imageRecord.getDescription().isBlank()) {
                    Paragraph desc = new Paragraph(imageRecord.getDescription())
                            .setFontSize(8)
                            .setItalic()
                            .setTextAlignment(TextAlignment.CENTER)
                            .setMarginTop(5);
                    imageCell.add(desc);
                }

                imageTable.addCell(imageCell);
                cellCount++;

                log.info("[PDF] Image added successfully: {}", imageRecord.getImageUrl());

            } catch (Exception e) {
                log.error("[PDF] Failed to load image: {}", imageRecord.getImageUrl(), e);

                // ===== ERROR CELL =====
                Cell errorCell = new Cell()
                        .setHeight(fixedCellHeight)
                        .setPadding(cellPadding)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE);

                Paragraph errorText = new Paragraph("Image could not be loaded")
                        .setFontSize(9)
                        .setFontColor(ColorConstants.RED)
                        .setTextAlignment(TextAlignment.CENTER);

                errorCell.add(errorText);
                imageTable.addCell(errorCell);
                cellCount++;
            }
        }

        // ===== FILL EMPTY CELL IF ODD =====
        if (cellCount > 0 && cellCount % columnCount != 0) {
            imageTable.addCell(
                    new Cell()
                            .setHeight(fixedCellHeight)
                            .setBorder(null)
            );
            cellCount++;
        }

        // ===== ADD TABLE =====
        if (cellCount > 0) {
            document.add(imageTable);
            log.info("[PDF] Image table added with {} cells", cellCount);
        } else {
            log.warn("[PDF] No images were rendered");
        }
    }

//    private void addSignatureSection(Document document) {
//        document.add(new Paragraph().setMarginTop(30));
//
//        Table signatureTable = new Table(3).useAllAvailableWidth();
//        signatureTable.setMarginTop(20);
//
//        // Customer signature
//        Paragraph customerLabel = new Paragraph("Customer")
//                .setFontSize(10)
//                .setBold()
//                .setTextAlignment(TextAlignment.CENTER);
//        signatureTable.addCell(customerLabel);
//
//        // Cashier signature
//        Paragraph cashierLabel = new Paragraph("Cashier")
//                .setFontSize(10)
//                .setBold()
//                .setTextAlignment(TextAlignment.CENTER);
//        signatureTable.addCell(cashierLabel);
//
//        // Doctor signature
//        Paragraph doctorLabel = new Paragraph("Treating Doctor")
//                .setFontSize(10)
//                .setBold()
//                .setTextAlignment(TextAlignment.CENTER);
//        signatureTable.addCell(doctorLabel);
//
//        // Signature lines
//        Paragraph line1 = new Paragraph("_________________________")
//                .setFontSize(10)
//                .setTextAlignment(TextAlignment.CENTER)
//                .setMarginTop(30);
//        signatureTable.addCell(line1);
//
//        Paragraph line2 = new Paragraph("_________________________")
//                .setFontSize(10)
//                .setTextAlignment(TextAlignment.CENTER)
//                .setMarginTop(30);
//        signatureTable.addCell(line2);
//
//        Paragraph line3 = new Paragraph("_________________________")
//                .setFontSize(10)
//                .setTextAlignment(TextAlignment.CENTER)
//                .setMarginTop(30);
//        signatureTable.addCell(line3);
//
//        // Name labels
//        Paragraph name1 = new Paragraph("(Signature, Full Name)")
//                .setFontSize(9)
//                .setTextAlignment(TextAlignment.CENTER)
//                .setMarginTop(5);
//        signatureTable.addCell(name1);
//
//        Paragraph name2 = new Paragraph("(Signature, Full Name)")
//                .setFontSize(9)
//                .setTextAlignment(TextAlignment.CENTER)
//                .setMarginTop(5);
//        signatureTable.addCell(name2);
//
//        Paragraph name3 = new Paragraph("(Signature, Full Name)")
//                .setFontSize(9)
//                .setTextAlignment(TextAlignment.CENTER)
//                .setMarginTop(5);
//        signatureTable.addCell(name3);
//
//        document.add(signatureTable);
//    }

    private void addTableRow(Table table, String label, String value) {
        Paragraph labelPara = new Paragraph(label);
        if (boldFont != null) labelPara.setFont(boldFont);
        labelPara.setFontSize(10)
                .setBold();
        table.addCell(labelPara);

        Paragraph valuePara = new Paragraph(value != null ? value : "N/A");
        if (normalFont != null) valuePara.setFont(normalFont);
        valuePara.setFontSize(10);
        table.addCell(valuePara);
    }

    private String formatPrice(BigDecimal price, String currency) {
        if (price == null) return "N/A";
        return String.format("%,.0f %s", price.doubleValue(), currency);
    }
}

