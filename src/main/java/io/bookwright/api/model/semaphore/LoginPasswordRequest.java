package io.bookwright.api.model.semaphore;

public record LoginPasswordRequest(String login, String password) {

  @Override
  public String toString() {
    return "LoginPasswordRequest[login=%s, password=[REDACTED]]".formatted(login);
  }
}
