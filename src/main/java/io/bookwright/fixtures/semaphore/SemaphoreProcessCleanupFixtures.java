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
            new Scenario(
                new Template(
                    "bookwright-process-cleanup-normal-main-" + suffix,
                    "test-environment/fixtures/bash/process-cleanup/normal-completion/main.sh",
                    false),
                new Template(
                    "bookwright-process-cleanup-normal-verifier-" + suffix,
                    "test-environment/fixtures/bash/process-cleanup/normal-completion/verify.sh",
                    true)),
            new Scenario(
                new Template(
                    "bookwright-process-cleanup-graceful-main-" + suffix,
                    "test-environment/fixtures/bash/process-cleanup/graceful-stop/main.sh",
                    false),
                new Template(
                    "bookwright-process-cleanup-graceful-verifier-" + suffix,
                    "test-environment/fixtures/bash/process-cleanup/graceful-stop/verify.sh",
                    true)),
            new Scenario(
                new Template(
                    "bookwright-process-cleanup-resistant-main-" + suffix,
                    "test-environment/fixtures/bash/process-cleanup/resistant-stop/main.sh",
                    false),
                new Template(
                    "bookwright-process-cleanup-resistant-verifier-" + suffix,
                    "test-environment/fixtures/bash/process-cleanup/resistant-stop/verify.sh",
                    true)),
            new Scenario(
                new Template(
                    "bookwright-process-cleanup-escaped-main-" + suffix,
                    "test-environment/fixtures/bash/process-cleanup/escaped-process-group/main.sh",
                    false),
                new Template(
                    "bookwright-process-cleanup-escaped-verifier-" + suffix,
                    "test-environment/fixtures/bash/process-cleanup/escaped-process-group/verify.sh",
                    true))),
        new Expectations(
            "success",
            "stopped",
            "semaphore-process-cleanup-stdout-marker",
            "semaphore-process-cleanup-stderr-marker",
            "semaphore-resistant-process-gone",
            "semaphore-graceful-stop-descendants-gone",
            "semaphore-process-cleanup-term-ready",
            "semaphore-process-cleanup-main-term",
            "semaphore-process-cleanup-child-term",
            "semaphore-resistant-stop-ready",
            "semaphore-resistant-stop-main-term",
            "semaphore-resistant-stop-child-term",
            "semaphore-resistant-stop-processes-gone",
            "semaphore-escaped-process-ready",
            "semaphore-escaped-process-alive",
            Duration.ofSeconds(30),
            Duration.ofSeconds(12),
            Duration.ofSeconds(12),
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

  public record Templates(
      Scenario normalCompletion,
      Scenario gracefulStop,
      Scenario resistantStop,
      Scenario escapedProcessGroup) {}

  public record Scenario(Template main, Template verifier) {}

  public record Expectations(
      String successfulTaskStatus,
      String stoppedTaskStatus,
      String stdoutMarker,
      String stderrMarker,
      String resistantProcessGoneMarker,
      String gracefulDescendantsGoneMarker,
      String termReadyMarker,
      String mainTermMarker,
      String childTermMarker,
      String resistantStopReadyMarker,
      String resistantStopMainTermMarker,
      String resistantStopChildTermMarker,
      String resistantStopProcessesGoneMarker,
      String escapedProcessReadyMarker,
      String escapedProcessAliveMarker,
      Duration maximumCompletionTime,
      Duration maximumGracefulStopTime,
      Duration minimumEscalationTime,
      Duration maximumEscalationTime) {}
}
