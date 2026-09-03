package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreUserLifecycleFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore user lifecycle")
class UserLifecycleApiTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Administrator can create, update, delete and recreate a local user")
  void administratorManagesSupportedUserLifecycle(
      ApiSteps api, SemaphoreUserLifecycleFixtures fixture) {
    var created = api.semaphore().users().createDisposable(fixture.initial());
    var updated = api.semaphore().users().update(created.id(), fixture.updated());

    assertThat(updated.id()).isEqualTo(created.id());
    assertThat(updated.name()).isEqualTo(fixture.updated().name());
    assertThat(updated.username()).isEqualTo(fixture.updated().username());
    assertThat(updated.email()).isEqualTo(fixture.updated().email());
    assertThat(updated.alert()).isTrue();

    api.semaphore().users().delete(created.id());
    api.semaphore().users().verifyMissing(created.id());

    assertThat(api.semaphore().users().createDisposable(fixture.initial()).username())
        .isEqualTo(fixture.initial().username());
  }
}
