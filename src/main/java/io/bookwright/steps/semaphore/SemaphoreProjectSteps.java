package io.bookwright.steps.semaphore;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.ProjectRole;
import io.bookwright.api.semaphore.SemaphoreProjectsApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.UUID;

public class SemaphoreProjectSteps {

  private final SemaphoreProjectsApi api;
  private final TeardownStorage teardown;

  @Inject
  public SemaphoreProjectSteps(SemaphoreProjectsApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
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
