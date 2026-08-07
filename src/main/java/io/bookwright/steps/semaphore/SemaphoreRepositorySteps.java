package io.bookwright.steps.semaphore;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Repository;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.semaphore.SemaphoreRepositoriesApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.UUID;

public class SemaphoreRepositorySteps {

  private final SemaphoreRepositoriesApi api;
  private final TeardownStorage teardown;

  @Inject
  public SemaphoreRepositorySteps(SemaphoreRepositoriesApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create public Git repository in Semaphore project {projectId}")
  public Repository createPublicRepository(long projectId, long keyId) {
    Repository repository =
        Calls.body(
            api.createRepository(
                projectId,
                new RepositoryRequest(
                    "bookwright-demo-repository-" + UUID.randomUUID(),
                    projectId,
                    "file:///fixtures/ansible",
                    "main",
                    keyId)),
            201,
            "created repository");
    teardown.push(
        "Delete Semaphore repository " + repository.id(),
        () -> Calls.expectStatus(api.deleteRepository(projectId, repository.id()), 204));
    return repository;
  }
}
