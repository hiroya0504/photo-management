package com.example.photomanagement.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends DomainException {

  public NotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
  }

  public NotFoundException(String errorCode, String message) {
    super(HttpStatus.NOT_FOUND, errorCode, message);
  }
}
