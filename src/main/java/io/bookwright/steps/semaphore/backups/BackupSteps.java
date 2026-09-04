package io.bookwright.steps.semaphore.backups;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.api.semaphore.backups.SemaphoreBackupsApi;
import io.bookwright.api.semaphore.projects.SemaphoreProjectsApi;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.SecretAccessKey;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;

public class BackupSteps {

  private final SemaphoreBackupsApi api;
  private final SemaphoreProjectsApi projectsApi;
  private final TeardownStorage teardown;

  @Inject
  public BackupSteps(
      SemaphoreBackupsApi api, SemaphoreProjectsApi projectsApi, TeardownStorage teardown) {
    this.api = api;
    this.projectsApi = projectsApi;
    this.teardown = teardown;
  }

  @Step("Export backup of Semaphore project {projectId}")
  public JsonNode exportProject(long projectId) {
    return Calls.body(api.getProjectBackup(projectId), 200, "project backup");
  }

  @Step("Export Semaphore project {projectId} and verify its access-key secret is absent")
  public JsonNode exportProjectAndVerifyMasked(long projectId, SecretAccessKey secret) {
    JsonNode backup = exportProject(projectId);
    SecretAssertions.absent("project backup", backup.toString(), secret);
    return backup;
  }

  @Step("Restore Semaphore project backup as {projectName}")
  public Project restoreProject(JsonNode backup, String projectName) {
    ObjectNode renamed = withProjectName(backup, projectName);

    Project restored = Calls.body(api.restoreProject(renamed), 200, "restored project");
    teardown.push(
        "Delete restored Semaphore project " + restored.id(),
        () -> Calls.expectStatus(projectsApi.deleteProject(restored.id()), 204));
    return restored;
  }

  @Step("Verify non-admin user cannot restore Semaphore project backup")
  public void verifyCannotRestore(
      SemaphoreSessionApis session, JsonNode backup, String projectName) {
    Calls.expectStatus(session.backups().restoreProject(withProjectName(backup, projectName)), 401);
  }

  @Step("Verify restore rejects a template referencing missing repository {repositoryName}")
  public void verifyMissingTemplateRepositoryRejected(
      JsonNode backup, String projectName, String repositoryName) {
    ObjectNode invalid = withProjectName(backup, projectName);
    requiredObject(requiredArray(invalid, "templates").get(0), "templates[0]")
        .put("repository", repositoryName);
    expectRejectedRestore(invalid);
  }

  @Step("Restore backup while duplicate repositories are still accepted")
  public Project restoreWithDuplicateRepositoriesCurrentlyAccepted(
      JsonNode backup, String projectName) {
    ObjectNode invalid = withProjectName(backup, projectName);
    ArrayNode repositories = requiredArray(invalid, "repositories");
    if (repositories.isEmpty()) {
      throw new IllegalStateException("Semaphore project backup has no repositories to duplicate");
    }
    repositories.add(repositories.get(0).deepCopy());
    Project restored =
        Calls.body(
            api.restoreProject(invalid), 200, "project restored with duplicate repositories");
    teardown.push(
        "Delete duplicate-repository Semaphore project " + restored.id(),
        () -> Calls.expectStatus(projectsApi.deleteProject(restored.id()), 204));
    return restored;
  }

  private void expectRejectedRestore(JsonNode backup) {
    Calls.expectStatus(api.restoreProject(backup), 400);
  }

  private ObjectNode withProjectName(JsonNode backup, String projectName) {
    ObjectNode copy = requiredObjectCopy(backup);
    requiredObject(copy.get("meta"), "meta").put("name", projectName);
    return copy;
  }

  private ArrayNode requiredArray(ObjectNode backup, String field) {
    JsonNode value = backup.get(field);
    if (!(value instanceof ArrayNode array)) {
      throw new IllegalStateException("Semaphore project backup has no array '" + field + "'");
    }
    return array;
  }

  private ObjectNode requiredObject(JsonNode value, String field) {
    if (!(value instanceof ObjectNode object)) {
      throw new IllegalStateException("Semaphore project backup has no object '" + field + "'");
    }
    return object;
  }

  private ObjectNode requiredObjectCopy(JsonNode backup) {
    if (!(backup instanceof ObjectNode object)) {
      throw new IllegalStateException("Semaphore project backup root is not a JSON object");
    }
    return object.deepCopy();
  }
}
