package io.bookwright.steps;

import com.google.inject.Inject;
import io.bookwright.api.SemaphoreApi;
import io.bookwright.api.model.semaphore.AccessKey;
import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.Inventory;
import io.bookwright.api.model.semaphore.InventoryRequest;
import io.bookwright.api.model.semaphore.LoginRequest;
import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.ProjectRole;
import io.bookwright.api.model.semaphore.Repository;
import io.bookwright.api.model.semaphore.RepositoryRequest;
import io.bookwright.api.model.semaphore.Template;
import io.bookwright.api.model.semaphore.TemplateRequest;
import io.bookwright.config.MainConfig;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.io.IOException;
import java.util.UUID;

public class SemaphoreApiSteps {

  private final SemaphoreApi api;
  private final MainConfig config;
  private final TeardownStorage teardown;

  @Inject
  public SemaphoreApiSteps(SemaphoreApi api, MainConfig config, TeardownStorage teardown) {
    this.api = api;
    this.config = config;
    this.teardown = teardown;
  }

  @Step("Check Semaphore API health")
  public void health() {
    var response = Calls.expectStatus(api.ping(), 200);
    try (var body = response.body()) {
      if (body == null || !"pong".equals(body.string())) {
        throw new IllegalStateException("Expected health response body 'pong'");
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not read health response", e);
    }
  }

  @Step("Check that invalid Semaphore credentials are rejected")
  public void invalidLoginIsRejected() {
    var response =
        Calls.response(
            api.login(new LoginRequest(config.apiUsername(), config.apiPassword() + "-invalid")));
    if (response.code() < 400 || response.code() >= 500) {
      throw new IllegalStateException(
          "Expected invalid login to return 4xx but received " + response.code());
    }
  }

  @Step("Login to Semaphore API")
  public void login() {
    Calls.expectStatus(
        api.login(new LoginRequest(config.apiUsername(), config.apiPassword())), 204);
  }

  @Step("Create isolated Semaphore project")
  public Project createProject() {
    String name = "bookwright-api-smoke-" + UUID.randomUUID();
    Project project =
        Calls.body(api.createProject(new ProjectRequest(name, false, 0)), 201, "created project");
    teardown.push(
        "Delete Semaphore project " + project.id(),
        () -> Calls.expectStatus(api.deleteProject(project.id()), 204));
    return project;
  }

  @Step("Get Semaphore project {projectId}")
  public Project getProject(long projectId) {
    return Calls.body(api.getProject(projectId), 200, "project");
  }

  @Step("Get current role in Semaphore project {projectId}")
  public ProjectRole getProjectRole(long projectId) {
    return Calls.body(api.getProjectRole(projectId), 200, "project role");
  }

  @Step("Create no-auth access key in Semaphore project {projectId}")
  public AccessKey createNoneAccessKey(long projectId) {
    AccessKey key =
        Calls.body(
            api.createAccessKey(
                projectId,
                new AccessKeyRequest(
                    "bookwright-none-key-" + UUID.randomUUID(), "none", projectId)),
            201,
            "created access key");
    teardown.push(
        "Delete Semaphore access key " + key.id(),
        () -> Calls.expectStatus(api.deleteAccessKey(projectId, key.id()), 204));
    return key;
  }

  @Step("Create public Git repository in Semaphore project {projectId}")
  public Repository createPublicRepository(long projectId, long keyId) {
    Repository repository =
        Calls.body(
            api.createRepository(
                projectId,
                new RepositoryRequest(
                    "bookwright-demo-repository-" + UUID.randomUUID(),
                    projectId,
                    "https://github.com/semaphoreui/semaphore-demo.git",
                    "main",
                    keyId)),
            201,
            "created repository");
    teardown.push(
        "Delete Semaphore repository " + repository.id(),
        () -> Calls.expectStatus(api.deleteRepository(projectId, repository.id()), 204));
    return repository;
  }

  @Step("Create static inventory in Semaphore project {projectId}")
  public Inventory createStaticInventory(long projectId, long keyId) {
    Inventory inventory =
        Calls.body(
            api.createInventory(
                projectId,
                new InventoryRequest(
                    "bookwright-localhost-inventory-" + UUID.randomUUID(),
                    projectId,
                    "[local]\nlocalhost ansible_connection=local",
                    keyId,
                    "static")),
            201,
            "created inventory");
    teardown.push(
        "Delete Semaphore inventory " + inventory.id(),
        () -> Calls.expectStatus(api.deleteInventory(projectId, inventory.id()), 204));
    return inventory;
  }

  @Step("Create Ansible task template in Semaphore project {projectId}")
  public Template createAnsibleTemplate(long projectId, long repositoryId, long inventoryId) {
    Template template =
        Calls.body(
            api.createTemplate(
                projectId,
                new TemplateRequest(
                    "bookwright-build-template-" + UUID.randomUUID(),
                    projectId,
                    inventoryId,
                    repositoryId,
                    0,
                    "build.yml",
                    "ansible",
                    "")),
            201,
            "created task template");
    teardown.push(
        "Delete Semaphore task template " + template.id(),
        () -> Calls.expectStatus(api.deleteTemplate(projectId, template.id()), 204));
    return template;
  }
}
