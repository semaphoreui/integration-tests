package io.bookwright.steps.semaphore.repositories;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Repository;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.semaphore.repositories.SemaphoreRepositoriesApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.List;

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

  @Step("Find required repository {name} in Semaphore project {projectId}")
  public Repository requireByName(long projectId, String name) {
    List<Repository> repositories = getRepositories(projectId);
    return repositories.stream()
        .filter(repository -> name.equals(repository.name()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Required repository '%s' was not found in project %d. Available repositories: %s"
                        .formatted(
                            name,
                            projectId,
                            repositories.stream().map(Repository::name).toList())));
  }

  @Step("Get repositories in Semaphore project {projectId}")
  public List<Repository> getRepositories(long projectId) {
    return Calls.body(api.getRepositories(projectId), 200, "repositories");
  }
}
