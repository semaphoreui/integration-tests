package io.bookwright.steps.semaphore.projects;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.ProjectRole;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.api.semaphore.projects.SemaphoreProjectsApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;

public class ProjectSteps {

  private final SemaphoreProjectsApi api;
  private final TeardownStorage teardown;

  @Inject
  public ProjectSteps(SemaphoreProjectsApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create isolated Semaphore project")
  public Project createProject(ProjectRequest request) {
    Project project = Calls.body(api.createProject(request), 201, "created project");
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

  @Step("Get isolated user's role in Semaphore project {projectId}")
  public ProjectRole getProjectRole(SemaphoreSessionApis session, long projectId) {
    return Calls.body(session.projects().getProjectRole(projectId), 200, "project role");
  }

  @Step("Verify isolated user cannot delete Semaphore project {projectId}")
  public void verifyCannotDelete(SemaphoreSessionApis session, long projectId) {
    Calls.expectStatus(session.projects().deleteProject(projectId), 403);
  }
}
