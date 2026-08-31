package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.LoginRequest;
import io.bookwright.config.MainConfig;
import io.bookwright.util.TestData;

/** Typed credentials for password-login security boundaries. */
public record SemaphoreLoginSecurityFixtures(
    LoginRequest correct,
    LoginRequest existingUserWrongPassword,
    LoginRequest unknownUser,
    LoginRequest emptyPassword,
    int repeatedFailureCount) {

  public static SemaphoreLoginSecurityFixtures from(MainConfig config, TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreLoginSecurityFixtures(
        new LoginRequest(config.apiUsername(), config.apiPassword()),
        new LoginRequest(config.apiUsername(), config.apiPassword() + "-wrong-" + suffix),
        new LoginRequest("bookwright-no-user-" + suffix, "Bw-unknown-" + suffix + "-42!"),
        new LoginRequest(config.apiUsername(), ""),
        5);
  }
}
