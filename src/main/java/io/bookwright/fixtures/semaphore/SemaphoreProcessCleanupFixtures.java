package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.TaskRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.config.MainConfig;
import io.bookwright.util.TestData;
import java.time.Duration;
import java.util.List;

/** Typed data and expectations for local process-group cleanup. */
public record SemaphoreProcessCleanupFixtures(
    ProjectRequest project,
    AccessKey accessKey,
    Repository repository,
    Inventory inventory,
    Templates templates,
    Expectations expectations) {

  public static SemaphoreProcessCleanupFixtures from(MainConfig config, TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreProcessCleanupFixtures(
        new ProjectRequest("bookwright-process-cleanup-" + suffix, false, 0),
        new AccessKey("bookwright-process-cleanup-key-" + suffix, "none"),
        new Repository(
            "bookwright-process-cleanup-repository-" + suffix,
            config.fixturesRepository(),
            config.fixturesDefaultBranch()),
        new Inventory(
            "bookwright-process-cleanup-inventory-" + suffix,
            "[local]\nlocalhost ansible_connection=local",
            "static"),
        new Templates(
            new Template(
                "bookwright-process-cleanup-main-" + suffix,
                "test-environment/fixtures/bash/process-cleanup/main.sh",
                false),
            new Template(
                "bookwright-process-cleanup-verifier-" + suffix,
                "test-environment/fixtures/bash/process-cleanup/verify.sh",
                true)),
        new Expectations(
            "success",
            "semaphore-process-cleanup-stdout-marker",
            "semaphore-process-cleanup-stderr-marker",
            "semaphore-process-cleanup-child-gone",
            Duration.ofSeconds(30)));
  }

  public TaskRequest verificationRequest(long verifierTemplateId, long completedTaskId) {
    return new TaskRequest(
        verifierTemplateId, null, null, "[\"%d\"]".formatted(completedTaskId), null, null);
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

  public record Template(String name, String playbook, boolean allowTaskArguments) {
    public TemplateRequest request(long projectId, long repositoryId, long inventoryId) {
      return new TemplateRequest(
          name,
          projectId,
          inventoryId,
          repositoryId,
          0,
          playbook,
          "bash",
          "",
          null,
          allowTaskArguments,
          List.of(),
          null,
          null,
          false);
    }
  }

  public record Templates(Template main, Template verifier) {}

  public record Expectations(
      String successfulTaskStatus,
      String stdoutMarker,
      String stderrMarker,
      String childGoneMarker,
      Duration maximumCompletionTime) {}
}
