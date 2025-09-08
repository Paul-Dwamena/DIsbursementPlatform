package com.example.common.exception;

import java.util.List;

public class ValidationErrorResponse extends ErrorResponse {
    private List<FieldErrorDetail> errors;

    public ValidationErrorResponse(String errorCode, List<FieldErrorDetail> errors) {
        super(errorCode, "Validation failed");
        this.errors = errors;
    }

    public List<FieldErrorDetail> getErrors() {
        return errors;
    }

    public void setErrors(List<FieldErrorDetail> errors) {
        this.errors = errors;
    }
}
