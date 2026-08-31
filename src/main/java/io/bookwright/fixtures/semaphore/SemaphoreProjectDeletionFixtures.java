package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.util.TestData;

/** Typed data for project deletion with a running or stopped task. */
public record SemaphoreProjectDeletionFixtures(
    ProjectRequest project,
    AccessKey accessKey,
    Repository repository,
    Inventory inventory,
    Template template,
    String readyMarker,
    String stoppedTaskStatus) {

  public static SemaphoreProjectDeletionFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreProjectDeletionFixtures(
        new ProjectRequest("bookwright-project-delete-" + suffix, false, 0),
        new AccessKey("bookwright-project-delete-key-" + suffix, "none"),
        new Repository(
            "bookwright-project-delete-repository-" + suffix, "file:///fixtures/ansible", "main"),
        new Inventory(
            "bookwright-project-delete-inventory-" + suffix,
            "[local]\nlocalhost ansible_connection=local",
            "static"),
        new Template(
            "bookwright-project-delete-template-" + suffix, "project-deletion.yml", "ansible", ""),
        "semaphore-bookwright-project-delete-ready",
        "stopped");
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

  public record Inventory(String name, String content, String type) {
    public InventoryRequest request(long projectId, long keyId) {
      return new InventoryRequest(name, projectId, content, keyId, type);
    }
  }

  public record Template(String name, String playbook, String app, String type) {
    public TemplateRequest request(long projectId, long repositoryId, long inventoryId) {
      return new TemplateRequest(
          name, projectId, inventoryId, repositoryId, 0, playbook, app, type);
    }
  }
}
