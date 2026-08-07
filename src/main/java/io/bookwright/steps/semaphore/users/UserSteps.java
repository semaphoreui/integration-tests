package io.bookwright.steps.semaphore.users;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.AccessKeyRequest;
import io.bookwright.api.model.semaphore.Project;
import io.bookwright.api.model.semaphore.ProjectMemberRequest;
import io.bookwright.api.model.semaphore.User;
import io.bookwright.api.model.semaphore.UserRequest;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.api.semaphore.SemaphoreUserNotFoundException;
import io.bookwright.api.semaphore.users.SemaphoreUsersApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.List;

public class UserSteps {

  private final SemaphoreUsersApi api;
  private final TeardownStorage teardown;

  @Inject
  public UserSteps(SemaphoreUsersApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Find Semaphore user {username}")
  public User findByUsername(String username) {
    List<User> users = getUsers();
    return users.stream()
        .filter(user -> username.equals(user.username()))
        .findFirst()
        .orElseThrow(() -> new SemaphoreUserNotFoundException(username, users.size()));
  }

  @Step("Get Semaphore users")
  public List<User> getUsers() {
    return Calls.body(api.getUsers(), 200, "users");
  }

  @Step("Create Semaphore user")
  public User create(UserRequest request) {
    try {
      return Calls.body(api.createUser(request), 201, "created user");
    } catch (RuntimeException error) {
      throw new IllegalStateException(
          "Failed to create Semaphore user '%s'".formatted(request.username()), error);
    }
  }

  @Step("Add user {userId} to Semaphore project {projectId} as guest")
  public void addToProject(long projectId, long userId, String role) {
    Calls.expectStatus(api.addProjectUser(projectId, new ProjectMemberRequest(userId, role)), 204);
    teardown.push(
        "Remove Semaphore user %d from project %d".formatted(userId, projectId),
        () -> Calls.expectStatus(api.removeProjectUser(projectId, userId), 204));
  }

  @Step("Verify guest can read assigned Semaphore project {projectId}")
  public void verifyProjectReadable(SemaphoreSessionApis session, long projectId) {
    Project project =
        Calls.body(session.projects().getProject(projectId), 200, "guest-visible project");
    if (project.id() != projectId) {
      throw new IllegalStateException("Guest received a different project");
    }
  }

  @Step("Verify guest cannot read unassigned Semaphore project {projectId}")
  public void verifyProjectHidden(SemaphoreSessionApis session, long projectId) {
    Calls.expectStatus(session.projects().getProject(projectId), 404);
  }

  @Step("Verify guest cannot create access keys in Semaphore project {projectId}")
  public void verifyCannotCreateAccessKey(
      SemaphoreSessionApis session, long projectId, AccessKeyRequest request) {
    Calls.expectStatus(session.accessKeys().createAccessKey(projectId, request), 403);
  }
}
