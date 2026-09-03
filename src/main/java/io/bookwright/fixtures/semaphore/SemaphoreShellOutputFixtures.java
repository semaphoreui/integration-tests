package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.util.TestData;
import java.time.Duration;

/** Typed data and expectations for Bash output capture regressions. */
public record SemaphoreShellOutputFixtures(
    ProjectRequest project,
    AccessKey accessKey,
    Repository repository,
    Inventory inventory,
    Templates templates,
    Expectations expectations) {

  public static SemaphoreShellOutputFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreShellOutputFixtures(
        new ProjectRequest("bookwright-shell-output-" + suffix, false, 0),
        new AccessKey("bookwright-shell-output-key-" + suffix, "none"),
        new Repository(
            "bookwright-shell-output-repository-" + suffix, "file:///fixtures/ansible", "main"),
        new Inventory(
            "bookwright-shell-output-inventory-" + suffix,
            "[local]\nlocalhost ansible_connection=local",
            "static"),
        new Templates(
            new Template(
                "bookwright-shell-output-template-" + suffix,
                "bash/capture-output/normal.sh",
                "bash",
                ""),
            new Template(
                "bookwright-background-shell-output-template-" + suffix,
                "bash/capture-output/background.sh",
                "bash",
                "")),
        new Expectations(
            "semaphore-shell-stdout-marker",
            "semaphore-shell-stderr-marker",
            Duration.ofSeconds(30)));
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

  public record Templates(Template normal, Template backgroundChild) {}

  public record Expectations(
      String stdoutMarker, String stderrMarker, Duration maximumBackgroundCompletionTime) {}
}
