package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.TaskRequest;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.util.TestData;
import java.util.List;
import io.bookwright.config.MainConfig;

/** Typed data for a manually selected Build to Deploy artifact-version chain. */
public record SemaphoreBuildDeployFixtures(
    ProjectRequest project,
    AccessKey accessKey,
    Repository repository,
    Inventory inventory,
    BuildTemplate build,
    DeployTemplate deploy) {

  public static SemaphoreBuildDeployFixtures from(MainConfig config, TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreBuildDeployFixtures(
        new ProjectRequest("bookwright-build-deploy-" + suffix, false, 0),
        new AccessKey("bookwright-build-deploy-key-" + suffix, "none"),
        new Repository(
            "bookwright-build-deploy-repository-" + suffix, config.fixturesRepository(), config.fixturesDefaultBranch()),
        new Inventory(
            "bookwright-build-deploy-inventory-" + suffix,
            "[local]\nlocalhost ansible_connection=local",
            "static"),
        new BuildTemplate(
            "bookwright-build-template-" + suffix,
            "/test-environment/fixtures/ansible/build-version.yml",
            "ansible",
            "build",
            "1.2.3",
            "semaphore-bookwright-build-version"),
        new DeployTemplate(
            "bookwright-deploy-template-" + suffix,
            "deploy-version.yml",
            "ansible",
            "deploy",
            "semaphore-bookwright-deploy-version"));
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

  public record BuildTemplate(
      String name,
      String playbook,
      String app,
      String type,
      String startVersion,
      String outputMarker) {

    public TemplateRequest request(long projectId, long repositoryId, long inventoryId) {
      return templateRequest(
          name, projectId, repositoryId, inventoryId, playbook, app, type, startVersion, null);
    }
  }

  public record DeployTemplate(
      String name, String playbook, String app, String type, String outputMarker) {

    public TemplateRequest request(
        long projectId, long repositoryId, long inventoryId, long buildTemplateId) {
      return templateRequest(
          name, projectId, repositoryId, inventoryId, playbook, app, type, null, buildTemplateId);
    }

    public TaskRequest taskRequest(long templateId, long buildTaskId) {
      return new TaskRequest(templateId, buildTaskId, null, null, null, null, null);
    }
  }

  private static TemplateRequest templateRequest(
      String name,
      long projectId,
      long repositoryId,
      long inventoryId,
      String playbook,
      String app,
      String type,
      String startVersion,
      Long buildTemplateId) {
    return new TemplateRequest(
        name,
        projectId,
        inventoryId,
        repositoryId,
        0,
        playbook,
        app,
        type,
        null,
        false,
        List.of(),
        null,
        null,
        false,
        startVersion,
        buildTemplateId,
        false);
  }
}
