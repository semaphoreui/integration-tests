package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

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
@Feature("Semaphore project API")
class SemaphoreProjectSmokeTest {

  @Test
  @DisplayName("Authenticated owner can create, read and clean up an isolated project")
  void ownerCanCreateAndReadProject(ApiSteps api) {
    api.semaphore().health();
    api.semaphore().invalidLoginIsRejected();
    api.semaphore().login();

    var created = api.semaphore().createProject();
    var saved = api.semaphore().getProject(created.id());
    var role = api.semaphore().getProjectRole(created.id());

    assertThat(created.id()).isPositive();
    assertThat(saved.id()).isEqualTo(created.id());
    assertThat(saved.name()).isEqualTo(created.name());
    assertThat(role.role()).isEqualTo("owner");
  }
}
