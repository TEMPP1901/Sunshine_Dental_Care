package sunshine_dental_care.services.doctor;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import sunshine_dental_care.entities.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Set;

public class MedicalRecordPDFExporter {
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
    private static final Font NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
    
    private final MedicalRecord medicalRecord;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DateTimeFormatter fullDateFormatter = DateTimeFormatter.ofPattern("dd 'tháng' MM 'năm' yyyy");

    public MedicalRecordPDFExporter(MedicalRecord medicalRecord) {
        this.medicalRecord = medicalRecord;
    }

    public byte[] export() throws DocumentException, IOException {
        Document document = new Document(PageSize.A4);
        document.setMargins(50, 50, 50, 50);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, outputStream);
        
        document.open();
        
        // Write header
        writeHeader(document);
        
        // Write patient and visit details
        writePatientDetails(document);
        
        // Write diagnosis section
        writeDiagnosis(document);
        
        // Write treatment plan section
        writeTreatmentPlan(document);
        
        // Write financial summary (only if appointment exists)
        if (medicalRecord.getAppointment() != null) {
            writeFinancialSummary(document);
            // Write service details table (only if appointment exists)
            writeServiceDetailsTable(document);
        } else {
            // Write service from MedicalRecord if exists
            writeServiceFromRecord(document);
        }
        
        // Write prescription
        writePrescription(document);
        
        // Write note if exists
        writeNote(document);
        
        // Write X-ray/images section
        writeXRaySection(document);
        
        // Write signature blocks
        writeSignatureBlocks(document);
        
        document.close();
        
        return outputStream.toByteArray();
    }

    private void writeHeader(Document document) throws DocumentException {
        // Clinic name (centered, large)
        Paragraph clinicNamePara = new Paragraph();
        clinicNamePara.setAlignment(Element.ALIGN_CENTER);
        clinicNamePara.add(new Chunk(getClinicName(), TITLE_FONT));
        document.add(clinicNamePara);
        
        // Contact information (right aligned)
        Paragraph contactPara = new Paragraph();
        contactPara.setAlignment(Element.ALIGN_RIGHT);
        Clinic clinic = medicalRecord.getClinic();
        if (clinic != null) {
            if (clinic.getAddress() != null) {
                contactPara.add(new Chunk("Địa chỉ: " + clinic.getAddress() + "\n", NORMAL_FONT));
            }
            if (clinic.getPhone() != null) {
                contactPara.add(new Chunk("Phone: " + clinic.getPhone() + "\n", NORMAL_FONT));
            }
            if (clinic.getEmail() != null) {
                contactPara.add(new Chunk("Email: " + clinic.getEmail(), NORMAL_FONT));
            }
        }
        document.add(contactPara);
        
        // Document title
        Paragraph titlePara = new Paragraph("HỒ SƠ ĐIỀU TRỊ RĂNG", HEADER_FONT);
        titlePara.setAlignment(Element.ALIGN_CENTER);
        titlePara.setSpacingBefore(10);
        titlePara.setSpacingAfter(5);
        document.add(titlePara);
        
        // Date
        Paragraph datePara = new Paragraph();
        datePara.setAlignment(Element.ALIGN_CENTER);
        String dateStr = "Ngày " + (medicalRecord.getRecordDate() != null 
            ? medicalRecord.getRecordDate().format(fullDateFormatter) 
            : LocalDate.now().format(fullDateFormatter));
        datePara.add(new Chunk(dateStr, NORMAL_FONT));
        document.add(datePara);
        
        // Patient ID (top right)
        Patient patient = medicalRecord.getPatient();
        if (patient != null && patient.getPatientCode() != null) {
            Paragraph patientIdPara = new Paragraph();
            patientIdPara.setAlignment(Element.ALIGN_RIGHT);
            patientIdPara.add(new Chunk("Mã BN: " + patient.getPatientCode(), BOLD_FONT));
            patientIdPara.setSpacingBefore(-30);
            document.add(patientIdPara);
        }
        
        document.add(new Paragraph("\n"));
    }

    private void writePatientDetails(Document document) throws DocumentException {
        Patient patient = medicalRecord.getPatient();
        User doctor = medicalRecord.getDoctor();
        Appointment appointment = medicalRecord.getAppointment();
        
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 3, 2, 3});
        
        // Row 1: Full Name
        addTableCell(table, "Họ và tên:", BOLD_FONT, Element.ALIGN_LEFT);
        addTableCell(table, patient != null ? patient.getFullName() : "", NORMAL_FONT, Element.ALIGN_LEFT);
        addTableCell(table, "Điện thoại:", BOLD_FONT, Element.ALIGN_LEFT);
        addTableCell(table, patient != null && patient.getPhone() != null ? patient.getPhone() : "", NORMAL_FONT, Element.ALIGN_LEFT);
        
        // Row 2: Address
        addTableCell(table, "Địa chỉ:", BOLD_FONT, Element.ALIGN_LEFT);
        addTableCell(table, patient != null && patient.getAddress() != null ? patient.getAddress() : "", NORMAL_FONT, Element.ALIGN_LEFT);
        addTableCell(table, "Tuổi:", BOLD_FONT, Element.ALIGN_LEFT);
        String age = calculateAge(patient);
        addTableCell(table, age, NORMAL_FONT, Element.ALIGN_LEFT);
        
        // Row 3: Doctor
        addTableCell(table, "Bác sĩ điều trị:", BOLD_FONT, Element.ALIGN_LEFT);
        addTableCell(table, doctor != null ? doctor.getFullName() : "", NORMAL_FONT, Element.ALIGN_LEFT);
        addTableCell(table, "", BOLD_FONT, Element.ALIGN_LEFT);
        addTableCell(table, "", NORMAL_FONT, Element.ALIGN_LEFT);
        
        // Row 4: Re-examination date (if appointment exists)
        if (appointment != null && appointment.getStartDateTime() != null) {
            addTableCell(table, "Ngày tái khám:", BOLD_FONT, Element.ALIGN_LEFT);
            String reExamDate = "";
            try {
                LocalDate date = appointment.getStartDateTime().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                reExamDate = date.format(dateFormatter);
            } catch (Exception e) {
                // Keep empty if parsing fails
            }
            PdfPCell dateCell = new PdfPCell(new Phrase(reExamDate, NORMAL_FONT));
            dateCell.setColspan(3);
            table.addCell(dateCell);
        }
        
        document.add(table);
        document.add(new Paragraph("\n"));
    }

    private void writeDiagnosis(Document document) throws DocumentException {
        if (medicalRecord.getDiagnosis() != null && !medicalRecord.getDiagnosis().trim().isEmpty()) {
            Paragraph titlePara = new Paragraph("CHẨN ĐOÁN:", BOLD_FONT);
            titlePara.setSpacingBefore(5);
            document.add(titlePara);
            
            Paragraph diagnosisPara = new Paragraph(medicalRecord.getDiagnosis(), NORMAL_FONT);
            diagnosisPara.setSpacingAfter(10);
            document.add(diagnosisPara);
        }
    }

    private void writeTreatmentPlan(Document document) throws DocumentException {
        if (medicalRecord.getTreatmentPlan() != null && !medicalRecord.getTreatmentPlan().trim().isEmpty()) {
            Paragraph titlePara = new Paragraph("KẾ HOẠCH ĐIỀU TRỊ:", BOLD_FONT);
            titlePara.setSpacingBefore(5);
            document.add(titlePara);
            
            Paragraph treatmentPara = new Paragraph(medicalRecord.getTreatmentPlan(), NORMAL_FONT);
            treatmentPara.setSpacingAfter(10);
            document.add(treatmentPara);
        }
    }

    private void writeNote(Document document) throws DocumentException {
        if (medicalRecord.getNote() != null && !medicalRecord.getNote().trim().isEmpty()) {
            Paragraph titlePara = new Paragraph("GHI CHÚ:", BOLD_FONT);
            titlePara.setSpacingBefore(5);
            document.add(titlePara);
            
            Paragraph notePara = new Paragraph(medicalRecord.getNote(), NORMAL_FONT);
            notePara.setSpacingAfter(10);
            document.add(notePara);
        }
    }

    private void writeServiceFromRecord(Document document) throws DocumentException {
        Service service = medicalRecord.getService();
        if (service != null) {
            Paragraph titlePara = new Paragraph("DỊCH VỤ:", BOLD_FONT);
            titlePara.setSpacingBefore(10);
            document.add(titlePara);
            
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2, 5});
            
            addTableCell(table, "Tên dịch vụ:", BOLD_FONT, Element.ALIGN_LEFT);
            addTableCell(table, service.getServiceName() != null ? service.getServiceName() : "", NORMAL_FONT, Element.ALIGN_LEFT);
            
            if (service.getDescription() != null) {
                addTableCell(table, "Mô tả:", BOLD_FONT, Element.ALIGN_LEFT);
                addTableCell(table, service.getDescription(), NORMAL_FONT, Element.ALIGN_LEFT);
            }
            
            document.add(table);
            document.add(new Paragraph("\n"));
        }
    }

    private void writeFinancialSummary(Document document) throws DocumentException {
        Appointment appointment = medicalRecord.getAppointment();
        if (appointment == null || appointment.getAppointmentServices() == null || appointment.getAppointmentServices().isEmpty()) {
            return;
        }
        
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        
        // Calculate from appointment.appointmentServices only
        for (AppointmentService apptService : appointment.getAppointmentServices()) {
            BigDecimal unitPrice = apptService.getUnitPrice() != null ? apptService.getUnitPrice() : BigDecimal.ZERO;
            Integer quantity = apptService.getQuantity() != null ? apptService.getQuantity() : 1;
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            
            BigDecimal discountAmt = BigDecimal.ZERO;
            if (apptService.getDiscountPct() != null && unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                discountAmt = subtotal.multiply(apptService.getDiscountPct().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }
            
            totalCost = totalCost.add(subtotal);
            discount = discount.add(discountAmt);
        }
        
        BigDecimal amountPaid = totalCost.subtract(discount);
        BigDecimal remaining = BigDecimal.ZERO;
        BigDecimal due = BigDecimal.ZERO;
        
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(60);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        addTableCell(table, "Tổng tiền:", BOLD_FONT, Element.ALIGN_LEFT);
        addTableCell(table, formatCurrency(totalCost) + " (" + numberToVietnameseWords(totalCost) + ")", NORMAL_FONT, Element.ALIGN_RIGHT);
        
        addTableCell(table, "Giảm giá:", BOLD_FONT, Element.ALIGN_LEFT);
        addTableCell(table, formatCurrency(discount) + " (" + numberToVietnameseWords(discount) + ")", NORMAL_FONT, Element.ALIGN_RIGHT);
        
        addTableCell(table, "Thanh toán:", BOLD_FONT, Element.ALIGN_LEFT);
        addTableCell(table, formatCurrency(amountPaid) + " (" + numberToVietnameseWords(amountPaid) + ")", NORMAL_FONT, Element.ALIGN_RIGHT);
        
        addTableCell(table, "Còn thừa:", BOLD_FONT, Element.ALIGN_LEFT);
        addTableCell(table, formatCurrency(remaining), NORMAL_FONT, Element.ALIGN_RIGHT);
        
        addTableCell(table, "Còn thiếu:", BOLD_FONT, Element.ALIGN_LEFT);
        addTableCell(table, formatCurrency(due), NORMAL_FONT, Element.ALIGN_RIGHT);
        
        document.add(table);
        document.add(new Paragraph("\n"));
    }

    private void writeServiceDetailsTable(Document document) throws DocumentException {
        Appointment appointment = medicalRecord.getAppointment();
        if (appointment == null || appointment.getAppointmentServices() == null || appointment.getAppointmentServices().isEmpty()) {
            return;
        }
        
        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.5f, 1.5f, 3f, 0.8f, 2f, 1.5f, 2f});
        
        // Header
        addTableCell(table, "STT", BOLD_FONT, Element.ALIGN_CENTER, Color.LIGHT_GRAY);
        addTableCell(table, "Răng", BOLD_FONT, Element.ALIGN_CENTER, Color.LIGHT_GRAY);
        addTableCell(table, "Tên dịch vụ", BOLD_FONT, Element.ALIGN_CENTER, Color.LIGHT_GRAY);
        addTableCell(table, "SL", BOLD_FONT, Element.ALIGN_CENTER, Color.LIGHT_GRAY);
        addTableCell(table, "Đơn Giá", BOLD_FONT, Element.ALIGN_CENTER, Color.LIGHT_GRAY);
        addTableCell(table, "Giảm Giá", BOLD_FONT, Element.ALIGN_CENTER, Color.LIGHT_GRAY);
        addTableCell(table, "Thành tiền", BOLD_FONT, Element.ALIGN_CENTER, Color.LIGHT_GRAY);
        
        // Data rows - only from appointment.appointmentServices
        int stt = 1;
        for (AppointmentService apptService : appointment.getAppointmentServices()) {
            Service service = apptService.getService();
            ServiceVariant variant = apptService.getServiceVariant();
            String serviceName = service != null ? service.getServiceName() : "";
            if (variant != null && variant.getVariantName() != null) {
                serviceName += " " + variant.getVariantName();
            }
            
            String tooth = apptService.getNote() != null ? apptService.getNote() : "";
            Integer quantity = apptService.getQuantity() != null ? apptService.getQuantity() : 1;
            BigDecimal unitPrice = apptService.getUnitPrice() != null ? apptService.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal discount = BigDecimal.ZERO;
            if (apptService.getDiscountPct() != null && unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
                discount = subtotal.multiply(apptService.getDiscountPct().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).subtract(discount);
            
            addTableCell(table, String.valueOf(stt++), NORMAL_FONT, Element.ALIGN_CENTER);
            addTableCell(table, tooth, NORMAL_FONT, Element.ALIGN_LEFT);
            addTableCell(table, serviceName, NORMAL_FONT, Element.ALIGN_LEFT);
            addTableCell(table, String.valueOf(quantity), NORMAL_FONT, Element.ALIGN_CENTER);
            addTableCell(table, formatCurrency(unitPrice), NORMAL_FONT, Element.ALIGN_RIGHT);
            addTableCell(table, formatCurrency(discount), NORMAL_FONT, Element.ALIGN_RIGHT);
            addTableCell(table, formatCurrency(subtotal), NORMAL_FONT, Element.ALIGN_RIGHT);
        }
        
        document.add(table);
        document.add(new Paragraph("\n"));
    }

    private void writePrescription(Document document) throws DocumentException {
        if (medicalRecord.getPrescriptionNote() != null && !medicalRecord.getPrescriptionNote().isEmpty()) {
            Paragraph titlePara = new Paragraph("ĐƠN THUỐC:", BOLD_FONT);
            titlePara.setSpacingBefore(10);
            document.add(titlePara);
            
            Paragraph prescriptionPara = new Paragraph(medicalRecord.getPrescriptionNote(), NORMAL_FONT);
            prescriptionPara.setSpacingAfter(10);
            document.add(prescriptionPara);
        }
    }

    private void writeXRaySection(Document document) throws DocumentException {
        Set<MedicalRecordImage> images = medicalRecord.getMedicalRecordImages();
        if (images != null && !images.isEmpty()) {
            Paragraph titlePara = new Paragraph("PHIM CHỤP XQUANG", BOLD_FONT);
            titlePara.setSpacingBefore(10);
            document.add(titlePara);
            
            // Note: In a real implementation, you would need to download images from URLs
            // and embed them. For now, we'll just list the image URLs/descriptions
            for (MedicalRecordImage image : images) {
                if (image.getDescription() != null) {
                    Paragraph descPara = new Paragraph("- " + image.getDescription(), NORMAL_FONT);
                    document.add(descPara);
                }
                if (image.getImageUrl() != null) {
                    Paragraph urlPara = new Paragraph("  URL: " + image.getImageUrl(), SMALL_FONT);
                    urlPara.setIndentationLeft(20);
                    document.add(urlPara);
                }
            }
            document.add(new Paragraph("\n"));
        }
    }

    private void writeSignatureBlocks(Document document) throws DocumentException {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1, 1});
        table.setSpacingBefore(30);
        
        // Empty cells for signatures
        addTableCell(table, "Khách hàng\n\n\n(Ký, ghi rõ họ tên)", NORMAL_FONT, Element.ALIGN_CENTER);
        addTableCell(table, "Nhân viên thu ngân\n\n\n(Ký, ghi rõ họ tên)", NORMAL_FONT, Element.ALIGN_CENTER);
        addTableCell(table, "Bác sĩ điều trị\n\n\n(Ký, ghi rõ họ tên)", NORMAL_FONT, Element.ALIGN_CENTER);
        
        document.add(table);
    }

    // Helper methods
    private void addTableCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text, Font font, int alignment, Color backgroundColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setBackgroundColor(backgroundColor);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private String getClinicName() {
        Clinic clinic = medicalRecord.getClinic();
        return clinic != null && clinic.getClinicName() != null 
            ? clinic.getClinicName() 
            : "NHA KHOA";
    }

    private String calculateAge(Patient patient) {
        if (patient == null || patient.getDateOfBirth() == null) {
            return "";
        }
        try {
            long years = ChronoUnit.YEARS.between(patient.getDateOfBirth(), LocalDate.now());
            return String.valueOf(years);
        } catch (Exception e) {
            return "";
        }
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return String.format("%,.0f", amount.doubleValue()).replace(",", ".");
    }

    private String numberToVietnameseWords(BigDecimal number) {
        if (number == null || number.compareTo(BigDecimal.ZERO) == 0) {
            return "Không";
        }
        
        long num = number.longValue();
        if (num == 0) return "Không";
        
        String[] ones = {"", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"};
        String[] tens = {"", "mười", "hai mươi", "ba mươi", "bốn mươi", "năm mươi", 
                         "sáu mươi", "bảy mươi", "tám mươi", "chín mươi"};
        String[] hundreds = {"", "một trăm", "hai trăm", "ba trăm", "bốn trăm", "năm trăm",
                            "sáu trăm", "bảy trăm", "tám trăm", "chín trăm"};
        
        if (num < 10) return ones[(int)num];
        if (num < 100) {
            int ten = (int)(num / 10);
            int one = (int)(num % 10);
            if (ten == 1) {
                return one == 5 ? "mười lăm" : (one == 0 ? "mười" : "mười " + ones[one]);
            }
            return one == 5 ? tens[ten] + " lăm" : 
                   (one == 0 ? tens[ten] : tens[ten] + " " + ones[one]);
        }
        if (num < 1000) {
            int hundred = (int)(num / 100);
            int remainder = (int)(num % 100);
            return remainder == 0 ? hundreds[hundred] : 
                   hundreds[hundred] + " " + numberToVietnameseWords(BigDecimal.valueOf(remainder));
        }
        if (num < 1000000) {
            int thousand = (int)(num / 1000);
            int remainder = (int)(num % 1000);
            String result = numberToVietnameseWords(BigDecimal.valueOf(thousand)) + " nghìn";
            if (remainder > 0) {
                if (remainder < 100) result += " không trăm";
                result += " " + numberToVietnameseWords(BigDecimal.valueOf(remainder));
            }
            return result;
        }
        if (num < 1000000000) {
            int million = (int)(num / 1000000);
            int remainder = (int)(num % 1000000);
            String result = numberToVietnameseWords(BigDecimal.valueOf(million)) + " triệu";
            if (remainder > 0) {
                result += " " + numberToVietnameseWords(BigDecimal.valueOf(remainder));
            }
            return result;
        }
        return number.toString() + " đồng";
    }
}
