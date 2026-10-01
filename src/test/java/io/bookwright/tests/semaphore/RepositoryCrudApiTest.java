package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreRepositoryCrudFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore Repository CRUD")
@Preconditions({
  Precondition.SEMAPHORE_ADMIN_SESSION,
  Precondition.SEMAPHORE_PROJECT_EXISTS,
  Precondition.SEMAPHORE_ACCESS_KEY_EXISTS
})
class RepositoryCrudApiTest {

  @Test
  @DisplayName("Repository can be read, updated and deleted")
  void repositoryLifecycle(ApiSteps api, TestStore store, SemaphoreRepositoryCrudFixtures fixture) {
    long projectId = store.semaphoreProject().id();
    long keyId = store.semaphoreAccessKey().id();
    var repository =
        api.semaphore().repositories().create(projectId, fixture.request(projectId, keyId));
    assertThat(api.semaphore().repositories().get(projectId, repository.id()))
        .isEqualTo(repository);

    assertThat(
            api.semaphore()
                .repositories()
                .update(
                    projectId,
                    repository.id(),
                    fixture.updateRequest(projectId, repository.id(), keyId)))
        .satisfies(
            saved -> {
              assertThat(saved.id()).isEqualTo(repository.id());
              assertThat(saved.projectId()).isEqualTo(projectId);
              assertThat(saved.name()).isEqualTo(fixture.updatedName());
              assertThat(saved.gitUrl()).isEqualTo(fixture.gitUrl());
              assertThat(saved.gitBranch()).isEqualTo(fixture.updatedBranch());
              assertThat(saved.sshKeyId()).isEqualTo(keyId);
            });

    api.semaphore().repositories().delete(projectId, repository.id());
    api.semaphore().repositories().verifyAbsent(projectId, repository.id());
  }
}
