package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.LoginRequest;
import io.bookwright.api.model.semaphore.UserRequest;
import io.bookwright.util.TestData;

/** Per-test disposable accounts for independent API and browser TOTP scenarios. */
public record SemaphoreTotpFixtures(Account apiAccount, Account uiAccount) {

  public static SemaphoreTotpFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreTotpFixtures(
        new Account(
            "Bookwright TOTP User",
            "bookwright-totp-user-" + suffix,
            "bookwright-totp-user-" + suffix + "@localhost",
            "Bookwright-TOTP-password-42!",
            false),
        new Account(
            "Bookwright TOTP UI Admin",
            "bookwright-totp-ui-admin-" + suffix,
            "bookwright-totp-ui-admin-" + suffix + "@localhost",
            "Bookwright-TOTP-UI-password-42!",
            true));
  }

  public record Account(
      String name, String username, String email, String password, boolean admin) {

    public UserRequest userRequest() {
      return new UserRequest(name, username, email, password, false, admin, false);
    }

    public LoginRequest loginRequest() {
      return new LoginRequest(username, password);
    }

    @Override
    public String toString() {
      return "Account[name=%s, username=%s, email=%s, password=[REDACTED], admin=%s]"
          .formatted(name, username, email, admin);
    }
  }
}
