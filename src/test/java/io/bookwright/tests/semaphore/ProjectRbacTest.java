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
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_RBAC_USER_EXISTS})
  @DisplayName("Manager can manage resources and tasks but not project members")
  void managerPermissionsMatchRoleContract(
      ApiSteps api, TestStore store, SemaphoreFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.projects().primary());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(
                project.id(), fixtures.repositories().primary().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.inventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures
                    .templates()
                    .primary()
                    .request(project.id(), repository.id(), inventory.id()));
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
    var completedTask = api.semaphore().tasks().startAndWait(session, project.id(), template.id());

    assertThat(role.role()).isEqualTo(fixtures.rbac().managerRole());
    assertThat(role.permissions()).isEqualTo(fixtures.rbac().managerPermissions());
    assertThat(managerKey.projectId()).isEqualTo(project.id());
    assertThat(completedTask.status()).isEqualTo(fixtures.expectations().successfulTaskStatus());
    api.semaphore().projects().verifyCannotDelete(session, project.id());
    api.semaphore()
        .users()
        .verifyCannotRemoveFromProject(session, project.id(), account.user().id());
  }

  @Test
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_RBAC_USER_EXISTS})
  @DisplayName("Task runner can start tasks but cannot manage project resources")
  void taskRunnerPermissionsMatchRoleContract(
      ApiSteps api, TestStore store, SemaphoreFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.projects().primary());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(
                project.id(), fixtures.repositories().primary().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.inventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures
                    .templates()
                    .primary()
                    .request(project.id(), repository.id(), inventory.id()));
    var account = store.semaphoreRbacUser();
    api.semaphore()
        .users()
        .addToProject(project.id(), account.user().id(), fixtures.rbac().taskRunnerRole());
    var session = api.semaphore().auth().loginAs(account);
    var role = api.semaphore().projects().getProjectRole(session, project.id());
    var completedTask = api.semaphore().tasks().startAndWait(session, project.id(), template.id());

    assertThat(role.role()).isEqualTo(fixtures.rbac().taskRunnerRole());
    assertThat(role.permissions()).isEqualTo(fixtures.rbac().taskRunnerPermissions());
    assertThat(completedTask.status()).isEqualTo(fixtures.expectations().successfulTaskStatus());
    api.semaphore()
        .accessKeys()
        .verifyCannotCreate(
            session, project.id(), fixtures.rbac().forbiddenAccessKey().request(project.id()));
    api.semaphore().projects().verifyCannotDelete(session, project.id());
    api.semaphore()
        .users()
        .verifyCannotRemoveFromProject(session, project.id(), account.user().id());
  }
}
