package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyUpdateRequest;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.InventoryUpdateRequest;
import io.bookwright.api.model.semaphore.LoginPasswordRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.RepositoryUpdateRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.api.model.semaphore.TemplateUpdateRequest;
import io.bookwright.config.MainConfig;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.SecretAccessKey;
import io.bookwright.util.TestData;
import java.util.List;

/** Data for read/update contracts of the core project resources. */
public record SemaphoreResourceCrudFixtures(
    ProjectRequest project,
    SecretAccessKey accessKey,
    SecretAccessKey updatedAccessKey,
    RepositoryData repository,
    InventoryData inventory,
    TemplateData template,
    String successMarker,
    String stopReadyMarker,
    String stopCompletedMarker) {

  public static SemaphoreResourceCrudFixtures from(MainConfig config, TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreResourceCrudFixtures(
        new ProjectRequest("bookwright-resource-crud-" + suffix, false, 0),
        new SecretAccessKey(
            "bookwright-crud-key-" + suffix,
            "login_password",
            "original-user-" + suffix,
            "Bw-crud-original-" + suffix + "-42!"),
        new SecretAccessKey(
            "bookwright-crud-key-updated-" + suffix,
            "login_password",
            "rotated-user-" + suffix,
            "Bw-crud-updated-" + suffix + "-42!"),
        new RepositoryData(
            "bookwright-crud-repository-" + suffix,
            "bookwright-crud-repository-updated-" + suffix,
            config.fixturesRepository(),
            config.fixturesDefaultBranch(),
            "bookwright-fixture-ref"),
        new InventoryData(
            "bookwright-crud-inventory-" + suffix,
            "bookwright-crud-inventory-updated-" + suffix,
            "[initial]\nlocalhost ansible_connection=local",
            "[updated]\nlocalhost ansible_connection=local",
            "static"),
        new TemplateData(
            "bookwright-crud-template-" + suffix,
            "bookwright-crud-template-updated-" + suffix,
            "ansible/smoke.yml",
            "ansible/long-running.yml",
            "ansible",
            ""),
        "semaphore-bookwright-smoke-ok",
        "semaphore-bookwright-stop-ready",
        "semaphore-bookwright-stop-completed");
  }

  public AccessKeyUpdateRequest accessKeyUpdateRequest(long projectId, long keyId) {
    return new AccessKeyUpdateRequest(
        keyId,
        updatedAccessKey.name(),
        updatedAccessKey.type(),
        projectId,
        true,
        new LoginPasswordRequest(updatedAccessKey.login(), updatedAccessKey.password()),
        null);
  }

  public record RepositoryData(
      String name, String updatedName, String gitUrl, String branch, String updatedBranch) {

    public RepositoryRequest request(long projectId, long keyId) {
      return new RepositoryRequest(name, projectId, gitUrl, branch, keyId);
    }

    public RepositoryUpdateRequest updatedRequest(long projectId, long repositoryId, long keyId) {
      return new RepositoryUpdateRequest(
          repositoryId, updatedName, projectId, gitUrl, updatedBranch, keyId);
    }
  }

  public record InventoryData(
      String name, String updatedName, String content, String updatedContent, String type) {

    public InventoryRequest request(long projectId, long keyId) {
      return new InventoryRequest(name, projectId, content, keyId, type);
    }

    public InventoryUpdateRequest updatedRequest(long projectId, long inventoryId, long keyId) {
      return new InventoryUpdateRequest(
          inventoryId, updatedName, projectId, updatedContent, keyId, null, null, type);
    }
  }

  public record TemplateData(
      String name,
      String updatedName,
      String playbook,
      String longRunningPlaybook,
      String app,
      String type) {

    public TemplateRequest request(long projectId, long repositoryId, long inventoryId) {
      return new TemplateRequest(
          name, projectId, inventoryId, repositoryId, 0, playbook, app, type);
    }

    public TemplateUpdateRequest updatedRequest(
        long projectId, long templateId, long repositoryId, long inventoryId) {
      return updateRequest(projectId, templateId, repositoryId, inventoryId, playbook);
    }

    public TemplateUpdateRequest stoppableRequest(
        long projectId, long templateId, long repositoryId, long inventoryId) {
      return updateRequest(projectId, templateId, repositoryId, inventoryId, longRunningPlaybook);
    }

    private TemplateUpdateRequest updateRequest(
        long projectId,
        long templateId,
        long repositoryId,
        long inventoryId,
        String updatedPlaybook) {
      return new TemplateUpdateRequest(
          templateId,
          updatedName,
          projectId,
          inventoryId,
          repositoryId,
          0,
          updatedPlaybook,
          app,
          type,
          "[]",
          true,
          List.of(),
          null,
          null,
          false,
          null,
          null,
          false,
          false);
    }
  }
}
