package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.RepositoryUpdateRequest;
import io.bookwright.config.MainConfig;
import io.bookwright.util.TestData;

/** Repository read/update/delete data. */
public record SemaphoreRepositoryCrudFixtures(
    String name, String updatedName, String gitUrl, String branch, String updatedBranch) {

  public static SemaphoreRepositoryCrudFixtures from(MainConfig config, TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreRepositoryCrudFixtures(
        "bookwright-crud-repository-" + suffix,
        "bookwright-crud-repository-updated-" + suffix,
        config.fixturesRepository(),
        config.fixturesDefaultBranch(),
        "bookwright-fixture-ref");
  }

  public RepositoryRequest request(long projectId, long keyId) {
    return new RepositoryRequest(name, projectId, gitUrl, branch, keyId);
  }

  public RepositoryUpdateRequest updateRequest(long projectId, long repositoryId, long keyId) {
    return new RepositoryUpdateRequest(
        repositoryId, updatedName, projectId, gitUrl, updatedBranch, keyId);
  }
}
