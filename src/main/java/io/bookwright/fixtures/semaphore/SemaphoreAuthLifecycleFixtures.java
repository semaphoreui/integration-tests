package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.LoginRequest;
import io.bookwright.api.model.semaphore.SemaphoreTestUser;
import io.bookwright.api.model.semaphore.User;
import io.bookwright.api.model.semaphore.UserPasswordRequest;
import io.bookwright.api.model.semaphore.UserRequest;

/** Stable local accounts and credentials for session and password lifecycle checks. */
public record SemaphoreAuthLifecycleFixtures(Account actor, Account target) {

  public static SemaphoreAuthLifecycleFixtures standard() {
    return new SemaphoreAuthLifecycleFixtures(
        new Account(
            "Bookwright Auth Actor",
            "bookwright-auth-actor",
            "bookwright-auth-actor@localhost",
            "Bookwright-auth-actor-42!",
            "Bookwright-auth-actor-next-42!",
            "Bookwright-auth-actor-wrong-42!"),
        new Account(
            "Bookwright Auth Target",
            "bookwright-auth-target",
            "bookwright-auth-target@localhost",
            "Bookwright-auth-target-42!",
            "Bookwright-auth-target-next-42!",
            "Bookwright-auth-target-wrong-42!"));
  }

  public record Account(
      String name,
      String username,
      String email,
      String password,
      String changedPassword,
      String wrongPassword) {

    public UserRequest userRequest() {
      return new UserRequest(name, username, email, password, false, false, false);
    }

    public LoginRequest login() {
      return new LoginRequest(username, password);
    }

    public LoginRequest changedLogin() {
      return new LoginRequest(username, changedPassword);
    }

    public UserPasswordRequest baselinePassword() {
      return new UserPasswordRequest(password, "");
    }

    public UserPasswordRequest validChange() {
      return new UserPasswordRequest(changedPassword, password);
    }

    public UserPasswordRequest invalidCurrentPasswordChange() {
      return new UserPasswordRequest(changedPassword, wrongPassword);
    }

    public UserPasswordRequest administratorChange() {
      return new UserPasswordRequest(changedPassword, "");
    }

    public SemaphoreTestUser account(User user) {
      return new SemaphoreTestUser(user, password);
    }

    @Override
    public String toString() {
      return "Account[name=%s, username=%s, email=%s, password=[REDACTED], changedPassword=[REDACTED], wrongPassword=[REDACTED]]"
          .formatted(name, username, email);
    }
  }
}
