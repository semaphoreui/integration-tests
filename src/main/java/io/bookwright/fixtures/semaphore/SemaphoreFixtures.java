package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.LoginPasswordRequest;
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
    SecretAccessKey secretAccessKey,
    Repositories repositories,
    Inventory inventory,
    Templates templates,
    Schedule schedule,
    Rbac rbac,
    Expectations expectations) {

  public static SemaphoreFixtures from(MainConfig config, TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreFixtures(
        new LoginRequest(config.apiUsername(), config.apiPassword() + "-invalid"),
        new Projects(
            new ProjectRequest("bookwright-api-smoke-" + suffix, false, 0),
            new ProjectRequest("bookwright-hidden-" + suffix, false, 0),
            new ProjectRequest("bookwright-secrets-" + suffix, false, 0),
            new ProjectRequest("bookwright-git-" + suffix, false, 0)),
        new AccessKey("bookwright-none-key-" + suffix, "none"),
        new SecretAccessKey(
            "bookwright-login-key-" + suffix,
            "login_password",
            "bookwright-local-user",
            "Bw-secret-" + suffix + "-42!"),
        new Repositories(
            new Repository(
                "bookwright-demo-repository-" + suffix, config.fixturesRepository(), config.fixturesDefaultBranch()),
            new Repository(
                "bookwright-ref-repository-" + suffix,
                config.fixturesRepository(),
                "bookwright-fixture-ref"),
            new Repository(
                "bookwright-missing-ref-repository-" + suffix,
                config.fixturesRepository(),
                "bookwright-missing-ref"),
            new Repository(
                "bookwright-unavailable-repository-" + suffix,
                "https://127.0.0.1:1/bookwright-unavailable.git",
                "main")),
        new Inventory(
            "bookwright-localhost-inventory-" + suffix,
            "[local]\nlocalhost ansible_connection=local",
            "static"),
        new Templates(
            new Template("bookwright-build-template-" + suffix, "smoke.yml", "ansible", ""),
            new Template(
                "bookwright-stoppable-template-" + suffix, "long-running.yml", "ansible", "")),
        new Schedule("bookwright-nightly-schedule-" + suffix, "0 0 * * *", false, ""),
        Rbac.standard(),
        new Expectations(
            "owner",
            "success",
            "error",
            "stopped",
            "semaphore-bookwright-smoke-ok",
            "Failed updating repository",
            "semaphore-bookwright-stop-ready",
            "semaphore-bookwright-stop-completed"));
  }

  public record Projects(
      ProjectRequest primary, ProjectRequest hidden, ProjectRequest secrets, ProjectRequest git) {}

  public record AccessKey(String name, String type) {
    public AccessKeyRequest request(long projectId) {
      return new AccessKeyRequest(name, type, projectId);
    }
  }

  public record SecretAccessKey(String name, String type, String login, String password) {

    public AccessKeyRequest request(long projectId) {
      return new AccessKeyRequest(name, type, projectId, new LoginPasswordRequest(login, password));
    }

    @Override
    public String toString() {
      return "SecretAccessKey[name=%s, type=%s, login=%s, password=[REDACTED]]"
          .formatted(name, type, login);
    }
  }

  public record Repository(String name, String gitUrl, String gitBranch) {
    public RepositoryRequest request(long projectId, long keyId) {
      return new RepositoryRequest(name, projectId, gitUrl, gitBranch, keyId);
    }
  }

  public record Repositories(
      Repository primary,
      Repository alternateBranch,
      Repository missingBranch,
      Repository unavailable) {}

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

  public record Templates(Template primary, Template longRunning) {}

  public record Schedule(String name, String cronFormat, boolean active, String type) {

    public ScheduleRequest request(long projectId, long templateId) {
      return new ScheduleRequest(name, projectId, templateId, cronFormat, active, type);
    }
  }

  public record Rbac(
      UserRequest userRequest,
      String password,
      String guestRole,
      String managerRole,
      long managerPermissions,
      String taskRunnerRole,
      long taskRunnerPermissions,
      AccessKey forbiddenAccessKey) {

    private static Rbac standard() {
      String username = "bookwright-rbac-guest";
      String password = "Bookwright-test-password-42!";
      return new Rbac(
          new UserRequest(
              "Bookwright Guest", username, username + "@localhost", password, false, false, false),
          password,
          "guest",
          "manager",
          5,
          "task_runner",
          1,
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
      return "Rbac[user=%s, password=[REDACTED], roles=%s/%s/%s]"
          .formatted(userRequest.username(), guestRole, managerRole, taskRunnerRole);
    }
  }

  public record Expectations(
      String ownerRole,
      String successfulTaskStatus,
      String failedTaskStatus,
      String stoppedTaskStatus,
      String outputMarker,
      String cloneFailureMarker,
      String stopReadyMarker,
      String stopCompletedMarker) {}
}
