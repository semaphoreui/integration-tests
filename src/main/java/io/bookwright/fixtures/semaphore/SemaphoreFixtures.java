package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.LoginRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.ScheduleRequest;
import io.bookwright.api.model.semaphore.SemaphoreTestUser;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.api.model.semaphore.User;
import io.bookwright.api.model.semaphore.UserRequest;
import io.bookwright.config.MainConfig;
import io.bookwright.util.TestData;

/** Typed, immutable data and expectations for the Semaphore smoke scenario. */
public record SemaphoreFixtures(
    LoginRequest invalidLogin,
    Projects projects,
    AccessKey accessKey,
    Repository repository,
    Inventory inventory,
    Template template,
    Schedule schedule,
    Rbac rbac,
    Expectations expectations) {

  public static SemaphoreFixtures from(MainConfig config, TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreFixtures(
        new LoginRequest(config.apiUsername(), config.apiPassword() + "-invalid"),
        new Projects(
            new ProjectRequest("bookwright-api-smoke-" + suffix, false, 0),
            new ProjectRequest("bookwright-hidden-" + suffix, false, 0)),
        new AccessKey("bookwright-none-key-" + suffix, "none"),
        new Repository("bookwright-demo-repository-" + suffix, "file:///fixtures/ansible", "main"),
        new Inventory(
            "bookwright-localhost-inventory-" + suffix,
            "[local]\nlocalhost ansible_connection=local",
            "static"),
        new Template("bookwright-build-template-" + suffix, "smoke.yml", "ansible", ""),
        new Schedule("bookwright-nightly-schedule-" + suffix, "0 0 * * *", false, ""),
        Rbac.standard(),
        new Expectations("owner", "success", "semaphore-bookwright-smoke-ok"));
  }

  public record Projects(ProjectRequest primary, ProjectRequest hidden) {}

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

  public record Schedule(String name, String cronFormat, boolean active, String type) {
    public ScheduleRequest request(long projectId, long templateId) {
      return new ScheduleRequest(name, projectId, templateId, cronFormat, active, type);
    }
  }

  public record Rbac(
      UserRequest userRequest, String password, String projectRole, AccessKey forbiddenAccessKey) {

    private static Rbac standard() {
      String username = "bookwright-rbac-guest";
      String password = "Bookwright-test-password-42!";
      return new Rbac(
          new UserRequest(
              "Bookwright Guest", username, username + "@localhost", password, false, false, false),
          password,
          "guest",
          new AccessKey("forbidden-guest-key", "none"));
    }

    public String username() {
      return userRequest.username();
    }

    public SemaphoreTestUser account(User user) {
      return new SemaphoreTestUser(user, password);
    }

    @Override
    public String toString() {
      return "Rbac[user=%s, password=[REDACTED], projectRole=%s]"
          .formatted(userRequest.username(), projectRole);
    }
  }

  public record Expectations(String ownerRole, String successfulTaskStatus, String outputMarker) {}
}
