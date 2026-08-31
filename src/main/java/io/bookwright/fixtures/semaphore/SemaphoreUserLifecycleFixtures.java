package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.UserRequest;
import io.bookwright.util.TestData;

/** Disposable user data for the lifecycle supported by the Community API. */
public record SemaphoreUserLifecycleFixtures(UserRequest initial, UserRequest updated) {

  public static SemaphoreUserLifecycleFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    String username = "bookwright-user-" + suffix;
    return new SemaphoreUserLifecycleFixtures(
        new UserRequest(
            "Bookwright User " + suffix,
            username,
            username + "@localhost",
            "Bw-user-" + suffix + "-42!",
            false,
            false,
            false),
        new UserRequest(
            "Updated Bookwright User " + suffix,
            username,
            username + "-updated@localhost",
            "",
            true,
            false,
            false));
  }
}
