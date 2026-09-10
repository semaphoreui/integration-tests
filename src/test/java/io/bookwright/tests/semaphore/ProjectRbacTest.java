package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore project RBAC")
class ProjectRbacTest {

  @Test
  @Preconditions({
    Precondition.SEMAPHORE_ADMIN_SESSION,
    Precondition.SEMAPHORE_PROJECT_EXISTS,
    Precondition.SEMAPHORE_RBAC_USER_EXISTS
  })
  @DisplayName("Owner can update the project and manage project members")
  void ownerPermissionsMatchRoleContract(
      ApiSteps api, TestStore store, SemaphoreFixtures fixtures) {
    var project = store.semaphoreProject();
    var account = store.semaphoreRbacUser();
    var member = api.semaphore().users().createDisposable(fixtures.rbac().memberRequest());
    var updateRequest = fixtures.projects().updated(project);
    api.semaphore()
        .users()
        .addToProject(project.id(), account.user().id(), fixtures.rbac().ownerRole());
    var session = api.semaphore().auth().loginAs(account);
    var role = api.semaphore().projects().getProjectRole(session, project.id());
    var updated = api.semaphore().projects().updateProject(session, project.id(), updateRequest);
    api.semaphore()
        .users()
        .addToProject(session, project.id(), member.id(), fixtures.rbac().guestRole());

    assertThat(role.role()).isEqualTo(fixtures.rbac().ownerRole());
    assertThat(role.permissions()).isEqualTo(fixtures.rbac().ownerPermissions());
    assertThat(updated.name()).isEqualTo(updateRequest.name());
    assertThat(updated.alert()).isEqualTo(updateRequest.alert());
    assertThat(updated.maxParallelTasks()).isEqualTo(updateRequest.maxParallelTasks());
  }

  @Test
  @Preconditions({
    Precondition.SEMAPHORE_ADMIN_SESSION,
    Precondition.SEMAPHORE_PROJECT_EXISTS,
    Precondition.SEMAPHORE_EXECUTABLE_TEMPLATE_EXISTS,
    Precondition.SEMAPHORE_RBAC_USER_EXISTS
  })
  @DisplayName("Manager can manage resources and tasks but cannot update the project or members")
  void managerPermissionsMatchRoleContract(
      ApiSteps api, TestStore store, SemaphoreFixtures fixtures) {
    var project = store.semaphoreProject();
    var account = store.semaphoreRbacUser();
    api.semaphore()
        .users()
        .addToProject(project.id(), account.user().id(), fixtures.rbac().managerRole());
    var session = api.semaphore().auth().loginAs(account);
    var role = api.semaphore().projects().getProjectRole(session, project.id());
    var managerKey =
        api.semaphore()
            .accessKeys()
            .create(
                session, project.id(), fixtures.rbac().forbiddenAccessKey().request(project.id()));
    var completedTask =
        api.semaphore().tasks().startAndWait(session, project.id(), store.semaphoreTemplate().id());

    assertThat(role.role()).isEqualTo(fixtures.rbac().managerRole());
    assertThat(role.permissions()).isEqualTo(fixtures.rbac().managerPermissions());
    assertThat(managerKey.projectId()).isEqualTo(project.id());
    assertThat(completedTask.status()).isEqualTo(fixtures.expectations().successfulTaskStatus());
    api.semaphore()
        .projects()
        .verifyCannotUpdate(session, project.id(), fixtures.projects().updated(project));
    api.semaphore().projects().verifyCannotDelete(session, project.id());
    api.semaphore()
        .users()
        .verifyCannotRemoveFromProject(session, project.id(), account.user().id());
  }

  @Test
  @Preconditions({
    Precondition.SEMAPHORE_ADMIN_SESSION,
    Precondition.SEMAPHORE_PROJECT_EXISTS,
    Precondition.SEMAPHORE_EXECUTABLE_TEMPLATE_EXISTS,
    Precondition.SEMAPHORE_RBAC_USER_EXISTS
  })
  @DisplayName("Task runner can start tasks but cannot manage project resources")
  void taskRunnerPermissionsMatchRoleContract(
      ApiSteps api, TestStore store, SemaphoreFixtures fixtures) {
    var project = store.semaphoreProject();
    var account = store.semaphoreRbacUser();
    api.semaphore()
        .users()
        .addToProject(project.id(), account.user().id(), fixtures.rbac().taskRunnerRole());
    var session = api.semaphore().auth().loginAs(account);
    var role = api.semaphore().projects().getProjectRole(session, project.id());
    var completedTask =
        api.semaphore().tasks().startAndWait(session, project.id(), store.semaphoreTemplate().id());

    assertThat(role.role()).isEqualTo(fixtures.rbac().taskRunnerRole());
    assertThat(role.permissions()).isEqualTo(fixtures.rbac().taskRunnerPermissions());
    assertThat(completedTask.status()).isEqualTo(fixtures.expectations().successfulTaskStatus());
    api.semaphore()
        .accessKeys()
        .verifyCannotCreate(
            session, project.id(), fixtures.rbac().forbiddenAccessKey().request(project.id()));
    api.semaphore()
        .projects()
        .verifyCannotUpdate(session, project.id(), fixtures.projects().updated(project));
    api.semaphore().projects().verifyCannotDelete(session, project.id());
    api.semaphore()
        .users()
        .verifyCannotRemoveFromProject(session, project.id(), account.user().id());
  }

  @Test
  @Preconditions({
    Precondition.SEMAPHORE_ADMIN_SESSION,
    Precondition.SEMAPHORE_PROJECT_EXISTS,
    Precondition.SEMAPHORE_EXECUTABLE_TEMPLATE_EXISTS,
    Precondition.SEMAPHORE_RBAC_USER_EXISTS
  })
  @DisplayName("Guest can read the project but cannot change it or start tasks")
  void guestPermissionsMatchRoleContract(
      ApiSteps api, TestStore store, SemaphoreFixtures fixtures) {
    var project = store.semaphoreProject();
    var account = store.semaphoreRbacUser();
    api.semaphore()
        .users()
        .addToProject(project.id(), account.user().id(), fixtures.rbac().guestRole());
    var session = api.semaphore().auth().loginAs(account);
    var role = api.semaphore().projects().getProjectRole(session, project.id());

    assertThat(role.role()).isEqualTo(fixtures.rbac().guestRole());
    assertThat(role.permissions()).isEqualTo(fixtures.rbac().guestPermissions());
    api.semaphore().users().verifyProjectReadable(session, project.id());
    api.semaphore()
        .tasks()
        .verifyCannotStart(session, project.id(), store.semaphoreTemplate().id());
    api.semaphore()
        .accessKeys()
        .verifyCannotCreate(
            session, project.id(), fixtures.rbac().forbiddenAccessKey().request(project.id()));
    api.semaphore()
        .projects()
        .verifyCannotUpdate(session, project.id(), fixtures.projects().updated(project));
    api.semaphore().projects().verifyCannotDelete(session, project.id());
    api.semaphore()
        .users()
        .verifyCannotRemoveFromProject(session, project.id(), account.user().id());
  }
}
