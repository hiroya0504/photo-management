package com.example.photomanagement.common.error;

import org.springframework.http.HttpStatus;

public class ValidationException extends DomainException {

  public ValidationException(String message) {
    super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }

  public ValidationException(String errorCode, String message) {
    super(HttpStatus.BAD_REQUEST, errorCode, message);
  }
}
