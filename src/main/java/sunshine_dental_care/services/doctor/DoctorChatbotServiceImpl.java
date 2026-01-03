package sunshine_dental_care.services.doctor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sunshine_dental_care.dto.doctorDTO.ChatbotRequest;
import sunshine_dental_care.dto.doctorDTO.ChatbotResponse;
import sunshine_dental_care.entities.Appointment;
import sunshine_dental_care.entities.AppointmentService;
import sunshine_dental_care.entities.MedicalRecord;
import sunshine_dental_care.entities.MedicalRecordImage;
import sunshine_dental_care.entities.Patient;
import sunshine_dental_care.repositories.auth.PatientRepo;
import sunshine_dental_care.repositories.doctor.PatientInsightRepository;
import sunshine_dental_care.services.impl.hr.schedule.GeminiApiClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorChatbotServiceImpl implements DoctorChatbotService {

    private final PatientInsightRepository insightRepository;
    private final PatientRepo patientRepo;
    private final GeminiApiClient geminiApiClient;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneOffset.UTC);

    @Override
    @Transactional(readOnly = true)
    public ChatbotResponse processQuery(ChatbotRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ChatbotResponse.error(null, "Question must not be empty");
        }

        String question = request.question();

        // Kiểm tra intent truy vấn trước
        QueryIntent intent = detectQueryIntent(question);

        // Xử lý các truy vấn cụ thể không cần context phức tạp
        if (intent != QueryIntent.GENERAL) {
            String specializedAnswer = processSpecializedQuery(intent, question, request);
            if (specializedAnswer != null) {
                return ChatbotResponse.success(specializedAnswer, question);
            }
        }

        String extractedPatientCode = extractPatientCodeFromQuestion(question);
        String patientCodeInput = normalizePatientCode(
                request.patientCode(),
                extractedPatientCode
        );

        String emailInput = extractEmailFromQuestion(question);
        String phoneInput = extractPhoneFromQuestion(question);
        boolean hasContact = (emailInput != null && !emailInput.isBlank())
                || (phoneInput != null && !phoneInput.isBlank());
        Integer appointmentId = coalesce(
                request.appointmentId(),
                extractAppointmentIdFromQuestion(question)
        );
        Integer recordId = coalesce(
                request.recordId(),
                extractRecordIdFromQuestion(question)
        );

        // CẬP NHẬT: Tìm kiếm bệnh nhân theo tên (không cần patientCode/email/phone chính xác)
        String patientNameKeyword = extractPatientNameFromQuestion(question);
        boolean isPatientNameSearch = intent == QueryIntent.PATIENT_SEARCH &&
                patientNameKeyword != null &&
                !patientNameKeyword.isBlank();

        boolean needPatient = requiresPatient(question)
                || (patientCodeInput != null && !patientCodeInput.isBlank())
                || hasContact
                || (intent == QueryIntent.PATIENT_SEARCH && !isPatientNameSearch); // Chỉ cần patientCode khi KHÔNG phải tìm theo tên

        Patient patient = null;
        if (needPatient) {
            patient = resolvePatient(patientCodeInput, emailInput, phoneInput);
            if (patient == null) {
                String reason;
                if (patientCodeInput != null && !patientCodeInput.isBlank()) {
                    reason = "Patient not found with patientCode: " + patientCodeInput;
                } else if (emailInput != null && !emailInput.isBlank()) {
                    reason = "Patient not found with email: " + emailInput;
                } else if (phoneInput != null && !phoneInput.isBlank()) {
                    reason = "Patient not found with phone: " + phoneInput;
                } else {
                    reason = "Please provide patientCode, email, or phone to identify the patient.";
                }
                return ChatbotResponse.error(question, reason);
            }
        }

        String context;
        if (needPatient) {
            context = buildPatientContext(patient, appointmentId, recordId, intent);
        } else if (isPatientNameSearch) {
            // Xử lý tìm kiếm theo tên riêng
            List<Patient> patients = insightRepository.findPatientsByName(patientNameKeyword);
            context = buildPatientSearchResultContext(patients, patientNameKeyword);
        } else if (appointmentId != null) {
            context = buildAppointmentOnlyContext(appointmentId, intent);
        } else if (recordId != null) {
            context = buildRecordOnlyContext(recordId, intent);
        } else if (intent == QueryIntent.SERVICE_SEARCH) {
            context = buildServiceSearchContext(question);
        } else if (intent == QueryIntent.MEDICAL_RECORD_SEARCH) {
            context = buildMedicalRecordSearchContext(question);
        } else {
            context = buildGeneralContext();
        }

        try {
            String prompt = buildPrompt(question, context, intent);
            String answer = geminiApiClient.generateContent(prompt);
            return ChatbotResponse.success(answer, question);
        } catch (Exception ex) {
            log.error("Failed to generate chatbot response", ex);
            return ChatbotResponse.error(
                    question,
                    "Unable to generate an answer, please try again."
            );
        }
    }

    /* ===================== NEW HELPER METHOD ===================== */

    private String extractPatientNameFromQuestion(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }

        String q = question.toLowerCase();

        // Pattern: "bệnh nhân [tên]" hoặc "patient [name]" hoặc "tên [tên]"
        Pattern[] patterns = {
                Pattern.compile("bệnh nhân (?:tên )?['\"]?([^'\"]+)['\"]?", Pattern.CASE_INSENSITIVE),
                Pattern.compile("patient (?:named )?['\"]?([^'\"]+)['\"]?", Pattern.CASE_INSENSITIVE),
                Pattern.compile("tên ['\"]?([^'\"]+)['\"]?", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(q);
            if (matcher.find()) {
                String name = matcher.group(1).trim();
                // Loại bỏ các từ dư thừa
                name = name.replaceAll("^(có|tên|named|bệnh nhân|patient)\\s+", "");
                if (!name.isBlank()) {
                    return name;
                }
            }
        }

        return null;
    }

    private String buildPatientSearchResultContext(List<Patient> patients, String searchKeyword) {
        StringBuilder ctx = new StringBuilder();

        if (patients.isEmpty()) {
            ctx.append("No patients found with name containing: ").append(searchKeyword).append("\n");
            return ctx.toString();
        }

        ctx.append("=== PATIENT SEARCH RESULTS ===\n");
        ctx.append("Found ").append(patients.size()).append(" patient(s) with name containing '")
                .append(searchKeyword).append("':\n\n");

        for (int i = 0; i < patients.size(); i++) {
            Patient p = patients.get(i);
            ctx.append(i + 1).append(". ").append(p.getFullName()).append("\n");
            ctx.append("   Patient Code: ").append(p.getPatientCode()).append("\n");
            ctx.append("   Patient ID: ").append(p.getId()).append("\n");
            if (p.getPhone() != null && !p.getPhone().isBlank()) {
                ctx.append("   Phone: ").append(p.getPhone()).append("\n");
            }
            if (p.getEmail() != null && !p.getEmail().isBlank()) {
                ctx.append("   Email: ").append(p.getEmail()).append("\n");
            }
            ctx.append("\n");
        }

        if (patients.size() == 1) {
            ctx.append("Only one patient found. You can ask about this patient's:\n");
            ctx.append("- medical history\n");
            ctx.append("- appointments\n");
            ctx.append("- used services\n");
            ctx.append("- diagnosis\n");
        } else {
            ctx.append("Multiple patients found. Please specify which patient by:\n");
            ctx.append("- Using patientCode (e.g., SDC-00000009)\n");
            ctx.append("- Providing email or phone\n");
            ctx.append("- Being more specific with the name\n");
        }

        return ctx.toString();
    }




    /* ===================== INTENT DETECTION ===================== */

    private enum QueryIntent {
        APPOINTMENT_SERVICES,      // Query về dịch vụ trong appointment
        SERVICE_SEARCH,            // Tìm kiếm dịch vụ
        MEDICAL_RECORD_SEARCH,     // Tìm kiếm hồ sơ bệnh án
        PATIENT_SEARCH,            // Tìm kiếm bệnh nhân
        GENERAL                    // Truy vấn chung
    }

    private QueryIntent detectQueryIntent(String question) {
        if (question == null || question.isBlank()) {
            return QueryIntent.GENERAL;
        }

        String q = question.toLowerCase();

        // Kiểm tra query về appointment services
        if (q.contains("services for appointment") ||
                q.contains("dịch vụ cho lịch hẹn") ||
                q.contains("appointment services") ||
                (q.contains("appointment") && q.contains("services")) ||
                q.contains("show services") && q.contains("appointment")) {
            return QueryIntent.APPOINTMENT_SERVICES;
        }

        // Kiểm tra query về service search
        if (q.contains("find services") ||
                q.contains("search services") ||
                q.contains("tìm dịch vụ") ||
                q.contains("services with") ||
                q.contains("quantity") || q.contains("unit price") ||
                q.contains("discount") || q.contains("note") ||
                q.contains("search for services")) {
            return QueryIntent.SERVICE_SEARCH;
        }

        // Kiểm tra query về medical record search
        if (q.contains("medical records") ||
                q.contains("hồ sơ bệnh án") ||
                q.contains("search records") ||
                q.contains("tìm hồ sơ") ||
                q.contains("records from") ||
                q.contains("records between") ||
                q.contains("records by") ||
                q.contains("find records")) {
            return QueryIntent.MEDICAL_RECORD_SEARCH;
        }

        // Kiểm tra query về patient search
        if (q.contains("find patients") ||
                q.contains("search patients") ||
                q.contains("tìm bệnh nhân") ||
                q.contains("patients with") ||
                q.contains("patients named") ||
                q.contains("patients by") ||
                q.contains("lookup patient")) {
            return QueryIntent.PATIENT_SEARCH;
        }

        return QueryIntent.GENERAL;
    }

    /* ===================== SPECIALIZED QUERY PROCESSING ===================== */

    private String processSpecializedQuery(QueryIntent intent, String question, ChatbotRequest request) {
        try {
            switch (intent) {
                case APPOINTMENT_SERVICES:
                    return processAppointmentServicesQuery(question);
                case SERVICE_SEARCH:
                    return processServiceSearchQuery(question);
                case MEDICAL_RECORD_SEARCH:
                    return processMedicalRecordSearchQuery(question);
                case PATIENT_SEARCH:
                    return processPatientSearchQuery(question);
                default:
                    return null;
            }
        } catch (Exception e) {
            log.error("Error processing specialized query", e);
            return null;
        }
    }

    private String processAppointmentServicesQuery(String question) {
        Integer appointmentId = extractAppointmentIdFromQuestion(question);
        if (appointmentId == null) {
            return "Please specify an appointment ID. Example: 'Show services for appointment 123'";
        }

        List<AppointmentService> services =
                insightRepository.findAllServicesByAppointmentId(appointmentId);

        if (services.isEmpty()) {
            return "No services found for appointment ID: " + appointmentId;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 **Services for Appointment ID: ").append(appointmentId).append("**\n\n");

        BigDecimal totalCost = BigDecimal.ZERO;
        int serviceNumber = 1;
        for (AppointmentService service : services) {
            sb.append("**Service #").append(serviceNumber++).append(":**\n");
            sb.append("• **Service:** ").append(service.getService().getServiceName()).append("\n");
            if (service.getServiceVariant() != null) {
                sb.append("• **Variant:** ").append(service.getServiceVariant().getVariantName()).append("\n");
            }
            sb.append("• **Quantity:** ").append(service.getQuantity()).append("\n");
            sb.append("• **Unit Price:** ").append(formatPrice(service.getUnitPrice())).append("\n");
            if (service.getDiscountPct() != null && service.getDiscountPct().compareTo(BigDecimal.ZERO) > 0) {
                sb.append("• **Discount:** ").append(service.getDiscountPct()).append("%\n");
            }
            if (service.getNote() != null && !service.getNote().isBlank()) {
                sb.append("• **Note:** ").append(service.getNote()).append("\n");
            }

            // Calculate cost for this service
            BigDecimal discountMultiplier = BigDecimal.ONE;
            if (service.getDiscountPct() != null) {
                discountMultiplier = BigDecimal.ONE
                        .subtract(service.getDiscountPct()
                                .divide(BigDecimal.valueOf(100)));
            }

            BigDecimal serviceCost = service.getUnitPrice()
                    .multiply(BigDecimal.valueOf(service.getQuantity()))
                    .multiply(discountMultiplier);
            totalCost = totalCost.add(serviceCost);

            sb.append("• **Service Cost:** ").append(formatPrice(serviceCost)).append("\n");
            sb.append("---\n");
        }

        sb.append("\n💰 **Total Cost for Appointment: ").append(formatPrice(totalCost)).append("**");
        return sb.toString();
    }

    private String processServiceSearchQuery(String question) {
        // Extract parameters from question
        Integer quantity = extractNumberAfterKeyword(question, "quantity");
        BigDecimal minPrice = extractPriceAfterKeyword(question, "min price", "price from", "minimum price");
        BigDecimal maxPrice = extractPriceAfterKeyword(question, "max price", "price to", "maximum price");
        BigDecimal discountPct = extractDecimalAfterKeyword(question, "discount", "discount percentage");
        String noteKeyword = extractTextAfterKeyword(question, "note", "with note", "containing");

        List<AppointmentService> services = insightRepository.findServicesWithDetails(
                quantity, minPrice, maxPrice, discountPct, noteKeyword
        );

        if (services.isEmpty()) {
            return "No services found matching your criteria.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🔍 **Found ").append(services.size()).append(" service(s)**\n\n");

        int count = 1;
        for (AppointmentService service : services) {
            sb.append("**Result #").append(count++).append(":**\n");
            sb.append("• **Appointment ID:** ").append(service.getAppointment().getId()).append("\n");
            sb.append("• **Service:** ").append(service.getService().getServiceName()).append("\n");
            if (service.getServiceVariant() != null) {
                sb.append("• **Variant:** ").append(service.getServiceVariant().getVariantName()).append("\n");
            }
            sb.append("• **Quantity:** ").append(service.getQuantity()).append("\n");
            sb.append("• **Unit Price:** ").append(formatPrice(service.getUnitPrice())).append("\n");
            if (service.getDiscountPct() != null) {
                sb.append("• **Discount:** ").append(service.getDiscountPct()).append("%\n");
            }
            if (service.getNote() != null && !service.getNote().isBlank()) {
                sb.append("• **Note:** ").append(service.getNote()).append("\n");
            }
            sb.append("---\n");
        }

        return sb.toString();
    }

    private String processMedicalRecordSearchQuery(String question) {
        // Extract parameters from question
        Integer patientId = extractNumberAfterKeyword(question, "patient id", "patient");
        Integer doctorId = extractNumberAfterKeyword(question, "doctor id", "doctor");
        Integer appointmentId = extractNumberAfterKeyword(question, "appointment id", "appointment");
        String diagnosisKeyword = extractTextAfterKeyword(question, "diagnosis", "with diagnosis");
        String treatmentKeyword = extractTextAfterKeyword(question, "treatment", "treatment plan");
        String prescriptionKeyword = extractTextAfterKeyword(question, "prescription", "prescription note");
        String noteKeyword = extractTextAfterKeyword(question, "note");
        LocalDate fromDate = extractDateFromQuestion(question, "from", "since", "starting");
        LocalDate toDate = extractDateFromQuestion(question, "to", "until", "ending");

        List<MedicalRecord> records;

        if (fromDate != null && toDate != null) {
            records = insightRepository.medicalRecordsByDateRange(fromDate, toDate);
        } else if (fromDate != null) {
            records = insightRepository.medicalRecordsByDate(fromDate);
        } else {
            // Use the comprehensive search method
            records = insightRepository.searchMedicalRecords(
                    patientId, doctorId, appointmentId,
                    null, null, null, // serviceId, variantId, appointmentServiceId
                    diagnosisKeyword, treatmentKeyword, prescriptionKeyword, noteKeyword
            );
        }

        if (records.isEmpty()) {
            return "No medical records found matching your criteria.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📁 **Found ").append(records.size()).append(" medical record(s)**\n\n");

        int count = 1;
        for (MedicalRecord record : records) {
            sb.append("**Record #").append(count++).append(":**\n");
            sb.append("• **Record ID:** ").append(record.getId()).append("\n");
            sb.append("• **Date:** ").append(record.getRecordDate().format(DATE_FORMAT)).append("\n");
            if (record.getPatient() != null) {
                sb.append("• **Patient:** ").append(record.getPatient().getFullName())
                        .append(" (ID: ").append(record.getPatient().getId()).append(")\n");
            }
            if (record.getDoctor() != null) {
                sb.append("• **Doctor:** ").append(record.getDoctor().getFullName()).append("\n");
            }
            if (record.getDiagnosis() != null && !record.getDiagnosis().isBlank()) {
                sb.append("• **Diagnosis:** ").append(truncateText(record.getDiagnosis(), 100)).append("\n");
            }
            if (record.getTreatmentPlan() != null && !record.getTreatmentPlan().isBlank()) {
                sb.append("• **Treatment Plan:** ").append(truncateText(record.getTreatmentPlan(), 100)).append("\n");
            }
            sb.append("---\n");
        }

        return sb.toString();
    }

    private String processPatientSearchQuery(String question) {
        // Extract parameters from question
        String nameKeyword = extractTextAfterKeyword(question, "named", "name", "with name");
        String gender = extractTextAfterKeyword(question, "gender");
        String phoneKeyword = extractTextAfterKeyword(question, "phone", "phone number", "telephone");
        String emailKeyword = extractTextAfterKeyword(question, "email", "email address");

        List<Patient> patients = insightRepository.searchPatients(
                nameKeyword, gender, phoneKeyword, emailKeyword
        );

        if (patients.isEmpty()) {
            return "No patients found matching your criteria.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("👥 **Found ").append(patients.size()).append(" patient(s)**\n\n");

        int count = 1;
        for (Patient patient : patients) {
            sb.append("**Patient #").append(count++).append(":**\n");
            sb.append("• **Patient ID:** ").append(patient.getId()).append("\n");
            sb.append("• **Code:** ").append(patient.getPatientCode()).append("\n");
            sb.append("• **Name:** ").append(patient.getFullName()).append("\n");
            sb.append("• **Gender:** ").append(patient.getGender()).append("\n");
            if (patient.getDateOfBirth() != null) {
                sb.append("• **Date of Birth:** ").append(patient.getDateOfBirth().format(DATE_FORMAT)).append("\n");
            }
            if (patient.getPhone() != null && !patient.getPhone().isBlank()) {
                sb.append("• **Phone:** ").append(patient.getPhone()).append("\n");
            }
            if (patient.getEmail() != null && !patient.getEmail().isBlank()) {
                sb.append("• **Email:** ").append(patient.getEmail()).append("\n");
            }
            sb.append("---\n");
        }

        return sb.toString();
    }

    /* ===================== CONTEXT BUILDERS ===================== */

    private String buildPatientContext(Patient patient,
                                       Integer appointmentId,
                                       Integer recordId,
                                       QueryIntent intent) {
        Integer patientId = patient.getId();
        StringBuilder ctx = new StringBuilder();

        ctx.append("=== PATIENT INFORMATION ===\n")
                .append(formatPatient(patient))
                .append("\n");

        // Chỉ thêm stats nếu không phải là query chuyên biệt
        if (intent == QueryIntent.GENERAL) {
            ctx.append("=== PATIENT STATS ===\n")
                    .append("Last Visit: ")
                    .append(formatDateTime(insightRepository.lastVisitByPatientId(patientId)))
                    .append("\nTotal Visits: ")
                    .append(insightRepository.totalVisitsByPatientId(patientId))
                    .append("\n\n");
        }

        // Thêm medical records nếu liên quan
        if (intent == QueryIntent.GENERAL || intent == QueryIntent.MEDICAL_RECORD_SEARCH) {
            ctx.append("=== MEDICAL RECORDS ===\n");
            List<MedicalRecord> medicalRecords = fetchMedicalRecords(patientId, appointmentId, recordId);
            if (medicalRecords.isEmpty()) {
                ctx.append("No medical records found.\n");
            } else {
                medicalRecords.forEach(r -> {
                    ctx.append(formatMedicalRecord(r)).append("\n");
                    List<MedicalRecordImage> images =
                            insightRepository.medicalImagesByRecordId(r.getId());
                    if (!images.isEmpty()) {
                        ctx.append("Images:\n");
                        images.forEach(img -> ctx.append(formatMedicalRecordImage(img)).append("\n"));
                    }
                });
            }
        }

        // Thêm appointments nếu liên quan
        if (intent == QueryIntent.GENERAL || intent == QueryIntent.APPOINTMENT_SERVICES) {
            ctx.append("\n=== COMPLETED APPOINTMENTS ===\n");
            appendAppointments(ctx, insightRepository.completedAppointmentsByPatientId(patientId));

            ctx.append("\n=== UPCOMING APPOINTMENTS ===\n");
            appendAppointments(ctx, insightRepository.upcomingAppointmentsByPatientId(patientId));

            ctx.append("\n=== OVERDUE APPOINTMENTS ===\n");
            appendAppointments(ctx, insightRepository.overdueAppointmentsByPatientId(patientId));
        }

        // Thêm services nếu liên quan
        if (intent == QueryIntent.GENERAL || intent == QueryIntent.SERVICE_SEARCH) {
            ctx.append("\n=== USED SERVICES ===\n");
            List<AppointmentService> usedServices =
                    insightRepository.usedServicesByPatientId(patientId);
            if (usedServices.isEmpty()) {
                ctx.append("No services found.\n");
            } else {
                usedServices.forEach(s -> ctx.append(formatAppointmentService(s)).append("\n"));
            }
        }

        if (appointmentId != null) {
            ctx.append("\n=== SPECIFIC APPOINTMENT REQUESTED ===\n");
            appendAppointmentWithServices(ctx, appointmentId, patientId);
        }

        return ctx.toString();
    }

    private String buildAppointmentOnlyContext(Integer appointmentId, QueryIntent intent) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("=== APPOINTMENT ===\n");

        Optional<Appointment> apptOpt = insightRepository.findById(appointmentId);
        if (apptOpt.isEmpty()) {
            ctx.append("Appointment not found with id: ").append(appointmentId).append("\n");
            return ctx.toString();
        }

        Appointment appointment = apptOpt.get();
        ctx.append(formatAppointment(appointment)).append("\n");

        if (appointment.getPatient() != null) {
            ctx.append("\n=== PATIENT INFORMATION ===\n")
                    .append(formatPatient(appointment.getPatient()))
                    .append("\n");
        }

        // Luôn bao gồm services cho appointment context
        ctx.append("\n=== SERVICES IN APPOINTMENT ===\n");
        List<AppointmentService> services =
                insightRepository.appointmentServicesByAppointmentId(appointmentId);
        if (services.isEmpty()) {
            ctx.append("No services for this appointment.\n");
        } else {
            services.forEach(s -> ctx.append(formatAppointmentService(s)).append("\n"));
        }

        ctx.append("\n=== MEDICAL RECORDS FOR THIS APPOINTMENT ===\n");
        List<MedicalRecord> records =
                insightRepository.medicalRecordByAppointmentId(appointmentId);
        if (records.isEmpty()) {
            ctx.append("No medical records for this appointment.\n");
        } else {
            records.forEach(r -> ctx.append(formatMedicalRecord(r)).append("\n"));
        }

        BigDecimal total = insightRepository.appointmentTotalCost(appointmentId);
        ctx.append("\nTotal Cost: ").append(formatPrice(total)).append("\n");

        return ctx.toString();
    }

    private String buildRecordOnlyContext(Integer recordId, QueryIntent intent) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("=== MEDICAL RECORD ===\n");

        Optional<MedicalRecord> recordOpt =
                insightRepository.medicalRecordById(recordId);
        if (recordOpt.isEmpty()) {
            ctx.append("Medical record not found with id: ").append(recordId).append("\n");
            return ctx.toString();
        }

        MedicalRecord record = recordOpt.get();
        ctx.append(formatMedicalRecord(record)).append("\n");

        List<MedicalRecordImage> images =
                insightRepository.medicalImagesByRecordId(recordId);
        if (!images.isEmpty()) {
            ctx.append("Images:\n");
            images.forEach(img -> ctx.append(formatMedicalRecordImage(img)).append("\n"));
        }

        return ctx.toString();
    }

    private String buildServiceSearchContext(String question) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("=== SERVICE SEARCH CONTEXT ===\n");

        // Thêm thông tin về available services
        ctx.append("Available search criteria for services:\n");
        ctx.append("- quantity: Number of service units\n");
        ctx.append("- unitPrice: Price per unit\n");
        ctx.append("- discountPct: Discount percentage\n");
        ctx.append("- note: Service note text\n");

        return ctx.toString();
    }

    private String buildMedicalRecordSearchContext(String question) {
        StringBuilder ctx = new StringBuilder();
        ctx.append("=== MEDICAL RECORD SEARCH CONTEXT ===\n");

        ctx.append("Available search criteria for medical records:\n");
        ctx.append("- patientId: Filter by patient\n");
        ctx.append("- doctorId: Filter by doctor\n");
        ctx.append("- appointmentId: Filter by appointment\n");
        ctx.append("- diagnosis: Search in diagnosis field\n");
        ctx.append("- treatmentPlan: Search in treatment plan\n");
        ctx.append("- date: Filter by specific date or date range\n");

        return ctx.toString();
    }

    private String buildGeneralContext() {
        return """
        You are assisting a dentist.
        No patient-specific data is required for this question.
        """;
    }

    /* ===================== PROMPT ===================== */

    private String buildPrompt(String question, String context, QueryIntent intent) {
        String intentSpecificInstructions = "";

        switch (intent) {
            case APPOINTMENT_SERVICES:
                intentSpecificInstructions = """
                - Focus on appointment services information
                - List all services for the specified appointment
                - Include quantity, price, discount, and notes for each service
                - Calculate and show total cost
                """;
                break;
            case SERVICE_SEARCH:
                intentSpecificInstructions = """
                - Focus on service search results
                - Present services in a clear, tabular format if possible
                - Highlight matching criteria
                - Include appointment context for each service
                """;
                break;
            case MEDICAL_RECORD_SEARCH:
                intentSpecificInstructions = """
                - Focus on medical record search results
                - Present records chronologically
                - Highlight matching search criteria
                - Include patient and doctor information
                """;
                break;
            case PATIENT_SEARCH:
                intentSpecificInstructions = """
                - Focus on patient search results
                - Present patients in a clear list
                - Include all available patient information
                - Group similar patients if applicable
                """;
                break;
        }

        return """
        You are a clinical assistant AI for dentists.

        Rules:
        - Answer in the language of the question whenever it is clearly detectable.
        - If the language of the question is unclear or mixed, answer in English by default.
        - Use ONLY the provided context.
        - Do NOT fabricate information.
        - Professional clinical tone.
        %s
        - If the context already includes patient details for an appointment or medical record, USE those details directly and DO NOT ask for additional identifiers.
        - Only if you truly cannot identify any patient from the context or question, ask the user to provide patientCode, phone number, or email (one of them is enough); do NOT guess.
        - If the user asks you to perform actions other than reading/reporting existing data (create/update/delete data, prescribe, operational instructions, or non-clinical/off-topic answers), refuse and say you cannot perform that action.
        - If the question is unrelated to dental/medical data, respond that this assistant is only for professional/clinical purposes.

        ===== CONTEXT =====
        %s

        ===== QUESTION =====
        %s
        """.formatted(intentSpecificInstructions, context, question);
    }

    /* ===================== HELPER METHODS ===================== */

    private Integer extractNumberAfterKeyword(String text, String... keywords) {
        for (String keyword : keywords) {
            Pattern pattern = Pattern.compile(keyword + "\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse number after keyword: {}", keyword);
                }
            }
        }
        return null;
    }

    private BigDecimal extractPriceAfterKeyword(String text, String... keywords) {
        for (String keyword : keywords) {
            Pattern pattern = Pattern.compile(keyword + "\\s*([\\d,.]+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    String priceStr = matcher.group(1).replace(",", "");
                    return new BigDecimal(priceStr);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse price after keyword: {}", keyword);
                }
            }
        }
        return null;
    }

    private BigDecimal extractDecimalAfterKeyword(String text, String... keywords) {
        for (String keyword : keywords) {
            Pattern pattern = Pattern.compile(keyword + "\\s*([\\d.]+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                try {
                    return new BigDecimal(matcher.group(1));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse decimal after keyword: {}", keyword);
                }
            }
        }
        return null;
    }

    private String extractTextAfterKeyword(String text, String... keywords) {
        for (String keyword : keywords) {
            Pattern pattern = Pattern.compile(keyword + "\\s+['\"]?([^'\"]+)['\"]?", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private LocalDate extractDateFromQuestion(String question, String... keywords) {
        for (String keyword : keywords) {
            Pattern pattern = Pattern.compile(keyword + "\\s+(\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4})",
                    Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(question);
            if (matcher.find()) {
                String dateStr = matcher.group(1);
                try {
                    if (dateStr.contains("-")) {
                        return LocalDate.parse(dateStr);
                    } else {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                        return LocalDate.parse(dateStr, formatter);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse date: {}", dateStr);
                }
            }
        }
        return null;
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    /* ===================== ORIGINAL METHODS ===================== */

    private boolean requiresPatient(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }

        String q = question.toLowerCase();

        boolean mentionsPatient = q.contains("bệnh nhân") || q.contains("patient");
        boolean mentionsName = extractPatientNameFromQuestion(question) != null;

        boolean mentionsHistoryOrMedical =
                q.contains("tiền sử")
                        || q.contains("medical history")
                        || q.contains("điều trị")
                        || q.contains("treatment")
                        || q.contains("history")
                        || q.contains("record")
                        || q.contains("hồ sơ")
                        || q.contains("diagnosis")
                        || q.contains("chẩn đoán")
                        || q.contains("appointments")
                        || q.contains("lịch hẹn");

        // Nếu chỉ hỏi tên bệnh nhân (tìm kiếm), không cần patientCode chính xác
        if (mentionsPatient && mentionsName && !mentionsHistoryOrMedical) {
            return false; // Có thể tìm kiếm theo tên
        }

        // Nếu hỏi về thông tin lịch sử/điều trị cụ thể, cần xác định chính xác
        if (mentionsHistoryOrMedical) {
            return true; // Cần patientCode/email/phone
        }

        // Mặc định - nếu hỏi "bệnh nhân" chung chung
        return mentionsPatient && !mentionsName;
    }
    private String extractEmailFromQuestion(String question) {
        if (question == null) return null;
        var matcher = java.util.regex.Pattern
                .compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(question);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private String extractPhoneFromQuestion(String question) {
        if (question == null) return null;
        var matcher = java.util.regex.Pattern
                .compile("\\b\\d{9,11}\\b")
                .matcher(question);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private String extractPatientCodeFromQuestion(String question) {
        if (question == null) return null;
        // Regex: SDC- followed by 5-12 digits (adjustable)
        var matcher = java.util.regex.Pattern
                .compile("sdc-\\d{5,12}", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(question);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private Integer extractAppointmentIdFromQuestion(String question) {
        if (question == null) return null;
        var matcher = java.util.regex.Pattern
                .compile("(appointment|appt|lịch hẹn|buổi hẹn)?\\D*(\\d{1,10})",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(question);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer extractRecordIdFromQuestion(String question) {
        if (question == null) return null;
        var matcher = java.util.regex.Pattern
                .compile("(record|hồ sơ|ba|bệnh án)\\D*(\\d{1,10})",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(question);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String normalizePatientCode(String fromRequest, String fromQuestion) {
        String candidate = fromRequest != null && !fromRequest.isBlank()
                ? fromRequest
                : fromQuestion;
        if (candidate == null) return null;
        candidate = candidate.trim();
        // Ensure upper-case and consistent prefix if user typed lowercase
        if (!candidate.isEmpty()) {
            return candidate.toUpperCase();
        }
        return null;
    }

    private Patient resolvePatient(String patientCode,
                                   String email,
                                   String phone) {
        // Priority order: patientCode -> email -> phone
        if (patientCode != null && !patientCode.isBlank()) {
            return patientRepo.findByPatientCode(patientCode.trim()).orElse(null);
        }

        if (email != null && !email.isBlank()) {
            String normalizedEmail = email.trim().toLowerCase();
            return patientRepo.findFirstByEmail(normalizedEmail).orElse(null);
        }

        if (phone != null && !phone.isBlank()) {
            for (String variant : buildPhoneVariants(phone)) {
                Optional<Patient> p = patientRepo.findFirstByPhone(variant);
                if (p.isPresent()) return p.get();
            }
        }

        return null;
    }

    private List<String> buildPhoneVariants(String phoneRaw) {
        String trimmed = phoneRaw.trim();
        String digitsOnly = trimmed.replaceAll("[^0-9]", "");
        List<String> variants = new java.util.ArrayList<>();

        if (!trimmed.isEmpty()) variants.add(trimmed);
        if (!digitsOnly.isEmpty()) variants.add(digitsOnly);

        if (digitsOnly.startsWith("0") && digitsOnly.length() > 1) {
            String withoutLeading0 = digitsOnly.substring(1);
            variants.add("+84" + withoutLeading0);
            variants.add(withoutLeading0);
        } else if (!digitsOnly.startsWith("+84") && digitsOnly.length() > 0) {
            variants.add("+84" + digitsOnly);
        }

        // Remove duplicates while preserving order
        return variants.stream().distinct().toList();
    }

    private List<MedicalRecord> fetchMedicalRecords(Integer patientId,
                                                    Integer appointmentId,
                                                    Integer recordId) {
        if (appointmentId != null) {
            return insightRepository.medicalRecordByAppointmentId(appointmentId);
        }

        List<MedicalRecord> records =
                insightRepository.medicalHistoryByPatientId(patientId);

        if (recordId != null) {
            return records.stream()
                    .filter(r -> r.getId().equals(recordId))
                    .collect(Collectors.toList());
        }

        return records;
    }

    private void appendAppointments(StringBuilder ctx, List<Appointment> appointments) {
        if (appointments == null || appointments.isEmpty()) {
            ctx.append("No data.\n");
            return;
        }
        appointments.forEach(a -> ctx.append(formatAppointment(a)).append("\n"));
    }

    private void appendAppointmentWithServices(StringBuilder ctx,
                                               Integer appointmentId,
                                               Integer patientId) {
        Optional<Appointment> appointmentOpt = insightRepository.findById(appointmentId);
        if (appointmentOpt.isEmpty()) {
            ctx.append("Appointment not found with id: ").append(appointmentId).append("\n");
            return;
        }
        Appointment appointment = appointmentOpt.get();
        if (!Objects.equals(appointment.getPatient().getId(), patientId)) {
            ctx.append("Appointment ").append(appointmentId)
                    .append(" does not belong to the requested patient.\n");
            return;
        }

        ctx.append(formatAppointment(appointment)).append("\n");

        List<AppointmentService> services =
                insightRepository.appointmentServicesByAppointmentId(appointmentId);
        if (services.isEmpty()) {
            ctx.append("No services for this appointment.\n");
        } else {
            ctx.append("Services in this appointment:\n");
            services.forEach(s -> ctx.append(formatAppointmentService(s)).append("\n"));
        }

        BigDecimal total = insightRepository.appointmentTotalCost(appointmentId);
        ctx.append("Total Cost: ").append(formatPrice(total)).append("\n");
    }

    private Integer coalesce(Integer a, Integer b) {
        return a != null ? a : b;
    }

    /* ===================== FORMATTERS ===================== */

    private String formatPatient(Patient p) {
        return """
        Patient Code: %s
        Patient ID: %s
        Full Name: %s
        Gender: %s
        Date of Birth: %s
        Phone: %s
        Email: %s
        """.formatted(
                p.getPatientCode(),
                p.getId(),
                p.getFullName(),
                p.getGender(),
                p.getDateOfBirth() != null
                        ? p.getDateOfBirth().format(DATE_FORMAT)
                        : "N/A",
                p.getPhone(),
                p.getEmail()
        );
    }

    private String formatAppointment(Appointment a) {
        return """
        Appointment ID: %d
        Start Time: %s
        End Time: %s
        Status: %s
        Patient: %s
        Doctor: %s
        Note: %s
        Booking Fee: %s
        """.formatted(
                a.getId(),
                formatDateTime(a.getStartDateTime()),
                formatDateTime(a.getEndDateTime()),
                a.getStatus(),
                a.getPatient() != null
                        ? a.getPatient().getFullName()
                        : "N/A",
                a.getDoctor() != null
                        ? a.getDoctor().getFullName()
                        : "N/A",
                a.getNote() != null ? a.getNote() : "",
                formatPrice(a.getBookingFee())
        );
    }

    private String formatMedicalRecord(MedicalRecord r) {
        return """
        Record ID: %d
        Record Date: %s
        Diagnosis: %s
        Treatment Plan: %s
        Prescription: %s
        Note: %s
        """.formatted(
                r.getId(),
                r.getRecordDate().format(DATE_FORMAT),
                r.getDiagnosis(),
                r.getTreatmentPlan(),
                r.getPrescriptionNote(),
                r.getNote()
        );
    }

    private String formatMedicalRecordImage(MedicalRecordImage image) {
        return """
        - Image ID: %d
          Url: %s
          Description: %s
          AI Tag: %s
        """.formatted(
                image.getId(),
                image.getImageUrl(),
                Objects.toString(image.getDescription(), ""),
                Objects.toString(image.getAiTag(), "")
        );
    }

    private String formatAppointmentService(AppointmentService s) {
        return """
        Service: %s
        Variant: %s
        Quantity: %d
        Unit Price: %s
        Discount: %s%%
        Note: %s
        """.formatted(
                s.getService().getServiceName(),
                s.getServiceVariant() != null
                        ? s.getServiceVariant().getVariantName()
                        : "N/A",
                s.getQuantity(),
                formatPrice(s.getUnitPrice()),
                s.getDiscountPct() != null ? s.getDiscountPct() : 0,
                s.getNote() != null ? s.getNote() : ""
        );
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "N/A" : String.format("%,.0f VND", price);
    }

    private String formatDateTime(Instant instant) {
        return instant == null ? "N/A" : DATETIME_FORMAT.format(instant);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "N/A" : DATETIME_FORMAT.format(dateTime.toInstant(ZoneOffset.UTC));
    }
}