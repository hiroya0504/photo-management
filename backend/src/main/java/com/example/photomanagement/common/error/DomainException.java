package com.example.photomanagement.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for all domain-level exceptions. Carries the HTTP status and a stable,
 * machine-readable error code that flows into the {@code ProblemDetails} response body.
 */
public abstract class DomainException extends RuntimeException {

  private final HttpStatus status;
  private final String errorCode;

  protected DomainException(HttpStatus status, String errorCode, String message) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
