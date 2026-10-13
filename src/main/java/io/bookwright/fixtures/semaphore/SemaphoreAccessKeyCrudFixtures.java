package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyUpdateRequest;
import io.bookwright.api.model.semaphore.LoginPasswordRequest;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.SecretAccessKey;
import io.bookwright.util.TestData;

/** Original and rotated credentials for access-key lifecycle checks. */
public record SemaphoreAccessKeyCrudFixtures(SecretAccessKey original, SecretAccessKey rotated) {

  public static SemaphoreAccessKeyCrudFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreAccessKeyCrudFixtures(
        new SecretAccessKey(
            "bookwright-crud-key-" + suffix,
            "login_password",
            "original-user-" + suffix,
            "Bw-crud-original-" + suffix + "-42!"),
        new SecretAccessKey(
            "bookwright-crud-key-updated-" + suffix,
            "login_password",
            "rotated-user-" + suffix,
            "Bw-crud-updated-" + suffix + "-42!"));
  }

  public AccessKeyUpdateRequest updateRequest(long projectId, long keyId) {
    return new AccessKeyUpdateRequest(
        keyId,
        rotated.name(),
        rotated.type(),
        projectId,
        true,
        new LoginPasswordRequest(rotated.login(), rotated.password()),
        null);
  }
}
