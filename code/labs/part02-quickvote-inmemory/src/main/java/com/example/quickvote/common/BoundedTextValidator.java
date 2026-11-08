package com.example.quickvote.common;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class BoundedTextValidator implements ConstraintValidator<BoundedText, String> {

    private int maxChars;
    private int maxBytes;

    @Override
    public void initialize(BoundedText annotation) {
        this.maxChars = annotation.maxChars();
        this.maxBytes = annotation.maxBytes();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.codePointCount(0, value.length()) <= maxChars
                && value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}
