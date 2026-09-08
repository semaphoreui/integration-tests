package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.TaskRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.config.MainConfig;
import io.bookwright.util.TestData;
import java.util.List;

/** Data for two tasks that coordinate after distinct branch checkouts. */
public record SemaphoreBranchIsolationFixtures(
    ProjectRequest project,
    AccessKey accessKey,
    Repository repository,
    Inventory inventory,
    Template template,
    String branchA,
    String branchB,
    String branchAReadyMarker,
    String branchAMarker,
    String branchBMarker,
    String successfulTaskStatus) {

  public static SemaphoreBranchIsolationFixtures from(MainConfig config, TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    String token = "bookwright-branch-isolation-" + suffix;
    return new SemaphoreBranchIsolationFixtures(
        new ProjectRequest("bookwright-branch-isolation-" + suffix, false, 2),
        new AccessKey("bookwright-branch-isolation-key-" + suffix, "none"),
        new Repository(
            "bookwright-branch-isolation-repository-" + suffix,
            config.fixturesRepository(),
            config.fixturesDefaultBranch()),
        new Inventory(
            "bookwright-branch-isolation-inventory-" + suffix,
            "[local]\nlocalhost ansible_connection=local",
            "static"),
        new Template(
            "bookwright-branch-isolation-template-" + suffix,
            "test-environment/fixtures/ansible/branch-isolation.yml",
            "[\"--extra-vars\",\"bookwright_isolation_token=" + token + "\"]"),
        "bookwright-branch-isolation-a",
        "bookwright-branch-isolation-b",
        "semaphore-bookwright-branch-a-ready",
        "semaphore-bookwright-branch-a-marker-branch-a",
        "semaphore-bookwright-branch-b-marker-branch-b",
        "success");
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

  public record Template(String name, String playbook, String arguments) {
    public TemplateRequest request(long projectId, long repositoryId, long inventoryId) {
      return new TemplateRequest(
          name,
          projectId,
          inventoryId,
          repositoryId,
          0,
          playbook,
          "ansible",
          "",
          arguments,
          false,
          List.of(),
          null,
          null,
          true,
          null,
          null,
          false,
          true);
    }
  }

  public TaskRequest branchATask(long templateId) {
    return new TaskRequest(templateId, branchA);
  }

  public TaskRequest branchBTask(long templateId) {
    return new TaskRequest(templateId, branchB);
  }
}
