package io.bookwright.tests.semaphore;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.Smoke;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@Smoke
@OwnerDanil
@Feature("Semaphore system health")
class SystemHealthApiTest {

  @Test
  @DisplayName("Health endpoint responds without an authenticated session")
  void healthEndpointIsAvailable(ApiSteps api) {
    api.semaphore().system().health();
  }
}
