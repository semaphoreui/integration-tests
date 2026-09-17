package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.LoginRequest;
import io.bookwright.api.model.semaphore.UserRequest;
import io.bookwright.util.TestData;
import java.util.Map;

/** Two isolated users and deterministic request markers for shared-cache boundary checks. */
public record SemaphoreWebCacheFixtures(
    Account first,
    Account second,
    String policyProbe,
    String userBoundaryProbe,
    String unkeyedInputProbe,
    String hostProbe,
    String staticAssetProbe,
    String documentationProbe,
    String documentationPath,
    Map<String, String> unkeyedHeaders,
    String poisonedHost) {

  public static SemaphoreWebCacheFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreWebCacheFixtures(
        account("first", suffix),
        account("second", suffix),
        "policy-" + suffix,
        "users-" + suffix,
        "unkeyed-" + suffix,
        "host-" + suffix,
        "asset-" + suffix,
        "docs-" + suffix,
        "/swagger/api-docs.yml",
        Map.of(
            "X-Forwarded-Host", "poison.invalid",
            "X-Original-URL", "/poisoned-response",
            "X-Rewrite-URL", "/poisoned-response"),
        "poison.invalid");
  }

  private static Account account(String position, String suffix) {
    String username = "bookwright-cache-" + position + "-" + suffix;
    return new Account(
        new UserRequest(
            "Bookwright Cache " + position,
            username,
            username + "@bookwright.test",
            "Bookwright-cache-" + position + "-" + suffix + "-42!",
            false,
            false,
            false));
  }

  public record Account(UserRequest request) {
    public LoginRequest login() {
      return new LoginRequest(request.username(), request.password());
    }
  }
}
