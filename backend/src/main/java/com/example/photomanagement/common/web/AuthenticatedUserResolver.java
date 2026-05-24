package com.example.photomanagement.common.web;

import com.example.photomanagement.common.error.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Reads the current user's id from the Bearer JWT in the {@code SecurityContext}. Throws when no
 * JWT is present, so callers can rely on the result being non-null.
 */
@Component
public class AuthenticatedUserResolver {

  public Long currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
      throw new UnauthorizedException("NOT_AUTHENTICATED", "No authenticated user in context");
    }
    String subject = jwt.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new UnauthorizedException("INVALID_TOKEN", "JWT is missing sub claim");
    }
    try {
      return Long.valueOf(subject);
    } catch (NumberFormatException e) {
      throw new UnauthorizedException("INVALID_TOKEN", "JWT sub is not numeric");
    }
  }
}
