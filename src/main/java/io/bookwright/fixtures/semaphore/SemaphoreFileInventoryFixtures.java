package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.InventoryUpdateRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.config.MainConfig;
import io.bookwright.util.TestData;

/** Typed data for Ansible inventories stored in a Git repository. */
public record SemaphoreFileInventoryFixtures(
    ProjectRequest project,
    AccessKey accessKey,
    Repository repository,
    FileInventory inventory,
    FileInventory unsafeInventory,
    Template template,
    String successfulTaskStatus,
    String outputMarker) {

  public static SemaphoreFileInventoryFixtures from(MainConfig config, TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreFileInventoryFixtures(
        new ProjectRequest("bookwright-file-inventory-" + suffix, false, 0),
        new AccessKey("bookwright-file-inventory-key-" + suffix, "none"),
        new Repository(
            "bookwright-file-inventory-repository-" + suffix,
            config.fixturesRepository(),
            config.fixturesDefaultBranch()),
        new FileInventory(
            "bookwright-file-inventory-" + suffix,
            "test-environment/fixtures/ansible/inventories/localhost.ini",
            "file"),
        new FileInventory(
            "bookwright-unsafe-file-inventory-" + suffix,
            "../bookwright-outside-repository.ini",
            "file"),
        new Template(
            "bookwright-file-inventory-template-" + suffix,
            "test-environment/fixtures/ansible/file-inventory.yml",
            "ansible",
            ""),
        "success",
        "semaphore-bookwright-file-inventory-ok");
  }

  public record AccessKey(String name, String type) {
    public AccessKeyRequest request(long projectId) {
      return new AccessKeyRequest(name, type, projectId);
    }
  }

  public record Repository(String name, String gitUrl, String gitBranch) {
    public RepositoryRequest request(long projectId, long keyId) {
      return new RepositoryRequest(name, projectId, gitUrl, gitBranch, keyId);
    }
  }

  public record FileInventory(String name, String path, String type) {
    public InventoryRequest request(long projectId, long keyId, long repositoryId) {
      return new InventoryRequest(name, projectId, path, keyId, null, repositoryId, type);
    }

    public InventoryUpdateRequest updateRequest(
        long inventoryId, long projectId, long keyId, long repositoryId) {
      return new InventoryUpdateRequest(
          inventoryId, name, projectId, path, keyId, null, repositoryId, type);
    }
  }

  public record Template(String name, String playbook, String app, String type) {
    public TemplateRequest request(long projectId, long repositoryId, long inventoryId) {
      return new TemplateRequest(
          name, projectId, inventoryId, repositoryId, 0, playbook, app, type);
    }
  }
}
