package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.Smoke;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@Smoke
@OwnerDanil
@Feature("Semaphore project API")
class SemaphoreProjectSmokeTest {

  @Test
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_RBAC_USER_EXISTS})
  @DisplayName("Owner can execute and clean up the core project workflow")
  void ownerCanCreateAndReadProject(ApiSteps api, TestStore store, SemaphoreFixtures fixtures) {
    api.semaphore().system().health();
    api.semaphore().auth().invalidLoginIsRejected(fixtures.invalidLogin());

    var created = api.semaphore().projects().createProject(fixtures.projects().primary());
    var saved = api.semaphore().projects().getProject(created.id());
    var role = api.semaphore().projects().getProjectRole(created.id());
    var key =
        api.semaphore()
            .accessKeys()
            .create(created.id(), fixtures.accessKey().request(created.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(
                created.id(), fixtures.repositories().primary().request(created.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(created.id(), fixtures.inventory().request(created.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                created.id(),
                fixtures
                    .templates()
                    .primary()
                    .request(created.id(), repository.id(), inventory.id()));
    var startedTask = api.semaphore().tasks().startTask(created.id(), template.id());
    var completedTask =
        api.semaphore().tasks().waitUntilTaskSucceeds(created.id(), startedTask.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(
            created.id(), completedTask.id(), fixtures.expectations().outputMarker());
    var output = api.semaphore().tasks().getTaskOutput(created.id(), completedTask.id());
    var schedule =
        api.semaphore()
            .schedules()
            .create(created.id(), fixtures.schedule().request(created.id(), template.id()));
    var savedSchedule = api.semaphore().schedules().getSchedule(created.id(), schedule.id());
    var schedules = api.semaphore().schedules().getSchedules(created.id());
    var hiddenProject = api.semaphore().projects().createProject(fixtures.projects().hidden());
    var guest = store.semaphoreRbacUser();
    api.semaphore()
        .users()
        .addToProject(created.id(), guest.user().id(), fixtures.rbac().guestRole());
    var guestSession = api.semaphore().auth().loginAs(guest);

    assertThat(created.id()).isPositive();
    assertThat(saved.id()).isEqualTo(created.id());
    assertThat(saved.name()).isEqualTo(created.name());
    assertThat(role.role()).isEqualTo(fixtures.expectations().ownerRole());
    assertThat(key.projectId()).isEqualTo(created.id());
    assertThat(repository.sshKeyId()).isEqualTo(key.id());
    assertThat(inventory.sshKeyId()).isEqualTo(key.id());
    assertThat(template.repositoryId()).isEqualTo(repository.id());
    assertThat(template.inventoryId()).isEqualTo(inventory.id());
    assertThat(completedTask.status()).isEqualTo(fixtures.expectations().successfulTaskStatus());
    assertThat(completedTask.templateId()).isEqualTo(template.id());
    assertThat(completedTask.commitHash()).isNotBlank();
    assertThat(output).isNotEmpty();
    assertThat(output).allMatch(line -> line.taskId() == completedTask.id());
    assertThat(output)
        .extracting(line -> line.output())
        .anyMatch(line -> line.contains(fixtures.expectations().outputMarker()));
    assertThat(savedSchedule.templateId()).isEqualTo(template.id());
    assertThat(savedSchedule.cronFormat()).isEqualTo(fixtures.schedule().cronFormat());
    assertThat(savedSchedule.active()).isEqualTo(fixtures.schedule().active());
    assertThat(schedules).extracting(item -> item.id()).contains(schedule.id());
    api.semaphore().users().verifyProjectReadable(guestSession, created.id());
    api.semaphore()
        .accessKeys()
        .verifyCannotCreate(
            guestSession, created.id(), fixtures.rbac().forbiddenAccessKey().request(created.id()));
    api.semaphore().users().verifyProjectHidden(guestSession, hiddenProject.id());
  }
}
