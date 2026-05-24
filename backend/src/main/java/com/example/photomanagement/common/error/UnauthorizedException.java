package com.example.photomanagement.common.error;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends DomainException {

  public UnauthorizedException(String message) {
    super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
  }

  public UnauthorizedException(String errorCode, String message) {
    super(HttpStatus.UNAUTHORIZED, errorCode, message);
  }
}
