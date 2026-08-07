package io.bookwright.api.model.semaphore;

public record SemaphoreTestUser(User user, String password) {

  @Override
  public String toString() {
    return "SemaphoreTestUser[user=" + user.username() + ", password=<redacted>]";
  }
}
