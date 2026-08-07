package io.bookwright.steps;

import com.google.inject.Inject;
import io.bookwright.api.SemaphoreApi;
import io.bookwright.api.model.semaphore.LoginRequest;
import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.ProjectRole;
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
}
