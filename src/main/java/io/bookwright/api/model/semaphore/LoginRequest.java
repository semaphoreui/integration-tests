package io.bookwright.api.model.semaphore;

public record LoginRequest(String auth, String password) {

  @Override
  public String toString() {
    return "LoginRequest[auth=%s, password=[REDACTED]]".formatted(auth);
  }
}
