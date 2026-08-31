package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ApiToken(
    String id,
    Instant created,
    boolean expired,
    @JsonProperty("expires_at") Instant expiresAt,
    @JsonProperty("user_id") long userId,
    String name) {

  public String prefix() {
    if (id == null || id.length() < 8) {
      throw new IllegalStateException("Semaphore API token ID is shorter than its public prefix");
    }
    return id.substring(0, 8);
  }

  @Override
  public String toString() {
    return "ApiToken[id=[REDACTED], created=%s, expired=%s, expiresAt=%s, userId=%d, name=%s]"
        .formatted(created, expired, expiresAt, userId, name);
  }
}
