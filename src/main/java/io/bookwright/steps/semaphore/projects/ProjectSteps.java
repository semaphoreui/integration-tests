package io.bookwright.steps.semaphore.projects;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.ProjectRequest;
import io.bookwright.api.model.semaphore.ProjectRole;
import io.bookwright.api.model.semaphore.ProjectUpdateRequest;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.api.semaphore.projects.SemaphoreProjectsApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.List;

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
    deleteAfterTest(project);
    return project;
  }

  @Step("Create isolated Semaphore project through an isolated session")
  public Project createProject(SemaphoreSessionApis session, ProjectRequest request) {
    Project project =
        Calls.body(session.projects().createProject(request), 201, "session-created project");
    deleteAfterTest(project);
    return project;
  }

  @Step("Delete Semaphore project {project.id} after the test")
  public void deleteAfterTest(Project project) {
    teardown.push(
        "Delete Semaphore project " + project.id(),
        () -> Calls.expectStatus(api.deleteProject(project.id()), 204));
  }

  @Step("Explicitly delete Semaphore project {project.id} and all its dependents")
  public void deleteCreatedProject(Project project) {
    Calls.expectStatus(api.deleteProject(project.id()), 204);
    teardown.discardAfterExplicitCleanup();
  }

  @Step("Verify Semaphore project {project.id} is absent")
  public void verifyDeleted(Project project) {
    Calls.expectStatus(api.getProject(project.id()), 404);
    if (Calls.body(api.getProjects(), 200, "projects after deletion").stream()
        .anyMatch(
            current -> current.id() == project.id() || current.name().equals(project.name()))) {
      throw new IllegalStateException(
          "Deleted Semaphore project %d ('%s') is still present in the project list"
              .formatted(project.id(), project.name()));
    }
  }

  @Step("Find required Semaphore project {name}")
  public Project requireByName(String name) {
    List<Project> projects = Calls.body(api.getProjects(), 200, "projects");
    return projects.stream()
        .filter(project -> name.equals(project.name()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Required Semaphore project '%s' was not found. Available projects: %s"
                        .formatted(name, projects.stream().map(Project::name).toList())));
  }

  @Step("Get Semaphore project {projectId}")
  public Project getProject(long projectId) {
    return Calls.body(api.getProject(projectId), 200, "project");
  }

  @Step("Update Semaphore project {projectId}")
  public Project updateProject(long projectId, ProjectUpdateRequest request) {
    Calls.expectStatus(api.updateProject(projectId, request), 204);
    return getProject(projectId);
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
