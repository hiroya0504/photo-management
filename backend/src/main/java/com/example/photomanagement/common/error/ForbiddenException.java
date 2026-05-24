package com.example.photomanagement.common.error;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends DomainException {

  public ForbiddenException(String message) {
    super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
  }

  public ForbiddenException(String errorCode, String message) {
    super(HttpStatus.FORBIDDEN, errorCode, message);
  }
}
