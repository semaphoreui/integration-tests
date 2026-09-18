package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserPasswordRequest(
    String password, @JsonProperty("current_password") String currentPassword) {

  @Override
  public String toString() {
    return "UserPasswordRequest[password=[REDACTED], currentPassword=[REDACTED]]";
  }
}
