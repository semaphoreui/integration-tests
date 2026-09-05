package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures.SecretAccessKey;
import io.bookwright.util.TestData;

/** Typed fixtures for the private HTTPS Git profile. */
public record SemaphoreHttpsGitFixtures(
    ProjectRequest authenticatedProject,
    ProjectRequest unauthenticatedProject,
    SecretAccessKey credentials,
    AccessKey publicAccessKey,
    Repository repository,
    Inventory inventory,
    Template template,
    String successfulTaskStatus,
    String failedTaskStatus,
    String outputMarker,
    String cloneFailureMarker) {

  public static SemaphoreHttpsGitFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreHttpsGitFixtures(
        new ProjectRequest("bookwright-https-git-auth-" + suffix, false, 0),
        new ProjectRequest("bookwright-https-git-no-auth-" + suffix, false, 0),
        new SecretAccessKey(
            "bookwright-https-git-key-" + suffix,
            "login_password",
            "bookwright-git",
            "Bookwright-https-token-42!"),
        new AccessKey("bookwright-https-git-none-" + suffix, "none"),
        new Repository(
            "bookwright-private-https-git-" + suffix,
            "https://git-https-fixture/ansible.git",
            "main"),
        new Inventory(
            "bookwright-https-git-inventory-" + suffix,
            "[local]\nlocalhost ansible_connection=local",
            "static"),
        new Template(
            "bookwright-https-git-template-" + suffix,
            "test-environment/fixtures/ansible/smoke.yml",
            "ansible",
            ""),
        "success",
        "error",
        "semaphore-bookwright-smoke-ok",
        "Failed updating repository");
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
