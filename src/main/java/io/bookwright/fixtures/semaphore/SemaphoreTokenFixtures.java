package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.ApiTokenRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.util.TestData;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Data and time boundaries for Semaphore API-token authentication. */
public record SemaphoreTokenFixtures(String tokenName, String projectName) {

  public static SemaphoreTokenFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreTokenFixtures(
        "bookwright-api-token-" + suffix, "bookwright-token-project-" + suffix);
  }

  public ApiTokenRequest validRequest() {
    return new ApiTokenRequest(
        tokenName, Instant.now().plus(Duration.ofMinutes(15)).truncatedTo(ChronoUnit.SECONDS));
  }

  public ApiTokenRequest expiredRequest() {
    return new ApiTokenRequest(
        tokenName + "-expired",
        Instant.now().minus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.SECONDS));
  }

  public ProjectRequest projectRequest() {
    return new ProjectRequest(projectName, false, 0);
  }
}
