package io.bookwright.steps.semaphore.repositories;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Repository;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.semaphore.repositories.SemaphoreRepositoriesApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;

public class RepositorySteps {

  private final SemaphoreRepositoriesApi api;
  private final TeardownStorage teardown;

  @Inject
  public RepositorySteps(SemaphoreRepositoriesApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create public Git repository in Semaphore project {projectId}")
  public Repository create(long projectId, RepositoryRequest request) {
    Repository repository =
        Calls.body(api.createRepository(projectId, request), 201, "created repository");
    teardown.push(
        "Delete Semaphore repository " + repository.id(),
        () -> Calls.expectStatus(api.deleteRepository(projectId, repository.id()), 204));
    return repository;
  }
}
