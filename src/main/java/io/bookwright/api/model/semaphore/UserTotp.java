package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserTotp(
    long id,
    @JsonProperty("user_id") long userId,
    String url,
    @JsonProperty("recovery_code") String recoveryCode) {

  @Override
  public String toString() {
    return "UserTotp[id=%d, userId=%d, url=[REDACTED], recoveryCode=[REDACTED]]"
        .formatted(id, userId);
  }
}
