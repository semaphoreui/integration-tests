package io.bookwright.steps.semaphore.repositories;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Repository;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.RepositoryUpdateRequest;
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

  @Step("Delete Semaphore repository {repositoryId} in project {projectId}")
  public void delete(long projectId, long repositoryId) {
    Calls.expectStatus(api.deleteRepository(projectId, repositoryId), 204);
  }

  @Step("Verify Semaphore repository {repositoryId} is absent")
  public void verifyAbsent(long projectId, long repositoryId) {
    Calls.expectStatus(api.getRepository(projectId, repositoryId), 404);
    if (Calls.body(api.getRepositories(projectId), 200, "repository collection").stream()
        .anyMatch(item -> item.id() == repositoryId)) {
      throw new IllegalStateException(
          "Deleted Semaphore repository %d is still listed in project %d"
              .formatted(repositoryId, projectId));
    }
  }

  @Step("Create public Git repository in Semaphore project {projectId}")
  public Repository create(long projectId, RepositoryRequest request) {
    Repository repository =
        Calls.body(api.createRepository(projectId, request), 201, "created repository");
    teardown.push(
        "Delete Semaphore repository " + repository.id(),
        () ->
            Calls.expectStatus(
                Calls.response(api.deleteRepository(projectId, repository.id())), 204, 404));
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

  @Step("Get Semaphore repository {repositoryId} in project {projectId}")
  public Repository get(long projectId, long repositoryId) {
    return Calls.body(api.getRepository(projectId, repositoryId), 200, "repository");
  }

  @Step("Update Semaphore repository {repositoryId} in project {projectId}")
  public Repository update(long projectId, long repositoryId, RepositoryUpdateRequest request) {
    Calls.expectStatus(api.updateRepository(projectId, repositoryId, request), 204);
    return get(projectId, repositoryId);
  }
}
