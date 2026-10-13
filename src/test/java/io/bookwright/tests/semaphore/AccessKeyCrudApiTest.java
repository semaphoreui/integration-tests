package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreAccessKeyCrudFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore AccessKey CRUD")
@Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_PROJECT_EXISTS})
class AccessKeyCrudApiTest {

  @Test
  @DisplayName("Login/password key can be read, rotated and deleted")
  void accessKeyLifecycle(ApiSteps api, TestStore store, SemaphoreAccessKeyCrudFixtures fixture) {
    long projectId = store.semaphoreProject().id();
    var key = api.semaphore().accessKeys().createAndVerifyMasked(projectId, fixture.original());

    assertThat(
            api.semaphore()
                .accessKeys()
                .updateAndVerifyMasked(
                    projectId,
                    key.id(),
                    fixture.updateRequest(projectId, key.id()),
                    fixture.rotated()))
        .satisfies(
            saved -> {
              assertThat(saved.id()).isEqualTo(key.id());
              assertThat(saved.name()).isEqualTo(fixture.rotated().name());
              assertThat(saved.type()).isEqualTo(fixture.rotated().type());
              assertThat(saved.projectId()).isEqualTo(projectId);
            });
    api.semaphore().accessKeys().verifyMasked(projectId, key.id(), fixture.original());

    api.semaphore().accessKeys().delete(projectId, key.id());
    api.semaphore().accessKeys().verifyAbsent(projectId, key.id());
  }
}
