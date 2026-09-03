package io.bookwright.tests.external;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.External;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@External
@OwnerDanil
@Feature("External Semaphore API")
class ExternalSemaphoreApiTest {

  @Test
  @DisplayName("External Semaphore health endpoint is available")
  void healthEndpointIsAvailable(ApiSteps api) {
    api.semaphore().system().health();
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("External credentials can read system information and visible projects")
  void authenticatedReadOnlyEndpointsAreAvailable(ApiSteps api) {
    assertThat(api.semaphore().system().info().version()).isNotBlank();
    assertThat(api.semaphore().projects().getProjects()).doesNotContainNull();
  }
}
