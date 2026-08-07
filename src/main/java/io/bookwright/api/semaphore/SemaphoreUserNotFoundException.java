package io.bookwright.api.semaphore;

public class SemaphoreUserNotFoundException extends RuntimeException {

  public SemaphoreUserNotFoundException(String username, int returnedUserCount) {
    super(
        "Semaphore user '%s' was not found among %d user(s) returned by GET /api/users"
            .formatted(username, returnedUserCount));
  }
}
