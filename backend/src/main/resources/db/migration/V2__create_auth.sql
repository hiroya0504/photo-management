-- M2: Authentication and authorization core tables.
-- See docs/adr/0005-auth-design.md and plan section 7.8 for the rationale.

CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  email         VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ NULL
);

-- Soft-deleted rows keep their email row; uniqueness is only enforced for active accounts.
CREATE UNIQUE INDEX idx_users_email_active
  ON users (email)
  WHERE deleted_at IS NULL;

CREATE TABLE roles (
  id   SMALLSERIAL PRIMARY KEY,
  name VARCHAR(32) NOT NULL UNIQUE
);

INSERT INTO roles (name) VALUES ('ADMIN'), ('USER');

CREATE TABLE user_roles (
  user_id BIGINT   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  role_id SMALLINT NOT NULL REFERENCES roles (id),
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  family_id  UUID         NOT NULL,
  -- SHA-256 hex digest of the plaintext token. The plaintext is never stored.
  token_hash VARCHAR(64)  NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ  NOT NULL,
  -- Set when the token is consumed by a refresh call (rotation).
  used_at    TIMESTAMPTZ  NULL,
  -- Set when the token (or its family) is revoked.
  revoked_at TIMESTAMPTZ  NULL,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Family revocation walks all rows in a family; partial index keeps it cheap.
CREATE INDEX idx_refresh_tokens_family_active
  ON refresh_tokens (family_id)
  WHERE revoked_at IS NULL;

CREATE INDEX idx_refresh_tokens_user_active
  ON refresh_tokens (user_id)
  WHERE revoked_at IS NULL;
