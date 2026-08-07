package io.bookwright.api.model.semaphore;

public record UserRequest(
    String name,
    String username,
    String email,
    String password,
    boolean alert,
    boolean admin,
    boolean external) {

  @Override
  public String toString() {
    return "UserRequest[name=%s, username=%s, email=%s, password=[REDACTED], alert=%s, admin=%s, external=%s]"
        .formatted(name, username, email, alert, admin, external);
  }
}
