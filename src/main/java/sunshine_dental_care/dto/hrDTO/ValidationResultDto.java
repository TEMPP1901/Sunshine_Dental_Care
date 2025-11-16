package sunshine_dental_care.dto.hrDTO;

import java.util.ArrayList;
import java.util.List;

public class ValidationResultDto {
    private boolean isValid;
    private List<String> errors;
    private List<String> warnings;
    
    // Constructors
    public ValidationResultDto() {
        this.isValid = true;
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }
    
    public ValidationResultDto(boolean isValid, List<String> errors) {
        this.isValid = isValid;
        this.errors = errors != null ? errors : new ArrayList<>();
        this.warnings = new ArrayList<>();
    }
    
    // Getters and Setters
    public boolean isValid() {
        return isValid;
    }
    
    public void setValid(boolean valid) {
        isValid = valid;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
    
    public List<String> getWarnings() {
        return warnings;
    }
    
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
    
    // Helper methods
    public void addError(String error) {
        this.errors.add(error);
        this.isValid = false;
    }
    
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
    
    public boolean hasBlockingErrors() {
        return !errors.isEmpty();
    }
}
