package com.example.photomanagement.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around {@link BCryptPasswordEncoder} that also exposes a {@link #dummyHash()} for
 * timing-attack-resistant authentication paths (so that "user not found" and "wrong password" take
 * the same amount of CPU time).
 */
@Component
public class PasswordHasher {

  private final BCryptPasswordEncoder encoder;
  private final String dummyHash;

  public PasswordHasher(AuthProperties props) {
    this.encoder = new BCryptPasswordEncoder(props.password().bcryptStrength());
    // Computed once at construction; never matches any real password.
    this.dummyHash = encoder.encode("dummy-password-never-matches");
  }

  public String hash(String plain) {
    return encoder.encode(plain);
  }

  public boolean matches(String plain, String hash) {
    return encoder.matches(plain, hash);
  }

  /**
   * Hash used to make a password check take roughly the same time whether or not a user with the
   * given email exists. Comparing against this always returns false.
   */
  public String dummyHash() {
    return dummyHash;
  }
}
