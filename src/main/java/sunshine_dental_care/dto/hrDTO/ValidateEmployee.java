package sunshine_dental_care.dto.hrDTO;

import java.util.regex.Pattern;

import sunshine_dental_care.exceptions.hr.EmployeeExceptions.EmployeeValidationException;
import sunshine_dental_care.repositories.auth.UserRepo;

public final class ValidateEmployee {

    private static final Pattern EMAIL_REGEX = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PHONE_REGEX = Pattern.compile("^[0-9]{10,11}$");

    private ValidateEmployee() {
    }

    public static void validateCreate(EmployeeRequest request, UserRepo userRepo) {
        requireNonBlank(request.getFullName(), "Full name is required");
        requireNonBlank(request.getEmail(), "Email is required");
        requireNonBlank(request.getUsername(), "Username is required");
        requireNonBlank(request.getPassword(), "Password is required for new employee");

        requireNonNull(request.getRoleId(), "Role ID is required for new employee");
        requireNonNull(request.getClinicId(), "Clinic ID is required for new employee");

        if (!EMAIL_REGEX.matcher(request.getEmail()).matches()) {
            throw new EmployeeValidationException("Invalid email format");
        }
        if (request.getPhone() != null && !PHONE_REGEX.matcher(request.getPhone()).matches()) {
            throw new EmployeeValidationException("Phone must be 10-11 digits");
        }
        if (request.getPassword().length() < 6) {
            throw new EmployeeValidationException("Password must be at least 6 characters");
        }

        if (userRepo.findByEmailIgnoreCase(request.getEmail()).isPresent()) {
            throw new EmployeeValidationException("Email already exists: " + request.getEmail());
        }
        if (userRepo.findByUsernameIgnoreCase(request.getUsername()).isPresent()) {
            throw new EmployeeValidationException("Username already exists: " + request.getUsername());
        }

        // Validate role: allow only DOCTOR(3), RECEPTIONIST(4), ACCOUNTANT(5)
        if (request.getRoleId() != null) {
            int roleId = request.getRoleId();
            boolean allowed = roleId == 3 || roleId == 4 || roleId == 5;
            if (!allowed) {
                throw new EmployeeValidationException("HR can only create DOCTOR, RECEPTIONIST or ACCOUNTANT users");
            }
        }
    }

    public static void validateUpdate(Integer userId, EmployeeRequest request, UserRepo userRepo) {
        if (request.getEmail() != null) {
            requireNonBlank(request.getEmail(), "Email cannot be empty");
            if (!EMAIL_REGEX.matcher(request.getEmail()).matches()) {
                throw new EmployeeValidationException("Invalid email format");
            }
            userRepo.findByEmailIgnoreCase(request.getEmail()).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new EmployeeValidationException("Email already exists: " + request.getEmail());
                }
            });
        }
        if (request.getUsername() != null) {
            requireNonBlank(request.getUsername(), "Username cannot be empty");
            userRepo.findByUsernameIgnoreCase(request.getUsername()).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) {
                    throw new EmployeeValidationException("Username already exists: " + request.getUsername());
                }
            });
        }
        if (request.getPhone() != null && !PHONE_REGEX.matcher(request.getPhone()).matches()) {
            throw new EmployeeValidationException("Phone must be 10-11 digits");
        }
        if (request.getPassword() != null && request.getPassword().length() < 6) {
            throw new EmployeeValidationException("Password must be at least 6 characters");
        }
    }

    public static void validateToggle(Boolean isActive, String reason, Boolean currentStatus) {
        requireNonNull(isActive, "Status is required");
        requireNonBlank(reason, "Reason is required for status change");
        if (currentStatus != null && currentStatus.equals(isActive)) {
            throw new EmployeeValidationException("Employee is already " + (isActive ? "active" : "inactive"));
        }
    }

    private static void requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new EmployeeValidationException(message);
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new EmployeeValidationException(message);
        }
    }
}

