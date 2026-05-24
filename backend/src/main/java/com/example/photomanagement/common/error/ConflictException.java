package com.example.photomanagement.common.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends DomainException {

  public ConflictException(String message) {
    super(HttpStatus.CONFLICT, "CONFLICT", message);
  }

  public ConflictException(String errorCode, String message) {
    super(HttpStatus.CONFLICT, errorCode, message);
  }
}
