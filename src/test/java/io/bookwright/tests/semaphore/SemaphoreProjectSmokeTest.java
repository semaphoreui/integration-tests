package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.Smoke;
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
  void ownerCanCreateAndReadProject(ApiSteps api, TestStore store) {
    api.semaphoreSystem().health();
    api.semaphoreAuth().invalidLoginIsRejected();

    var created = api.semaphoreProjects().createProject();
    var saved = api.semaphoreProjects().getProject(created.id());
    var role = api.semaphoreProjects().getProjectRole(created.id());
    var key = api.semaphoreAccessKeys().createNoneAccessKey(created.id());
    var repository = api.semaphoreRepositories().createPublicRepository(created.id(), key.id());
    var inventory = api.semaphoreInventories().createStaticInventory(created.id(), key.id());
    var template =
        api.semaphoreTemplates()
            .createAnsibleTemplate(created.id(), repository.id(), inventory.id());
    var startedTask = api.semaphoreTasks().startTask(created.id(), template.id());
    var completedTask = api.semaphoreTasks().waitUntilTaskSucceeds(created.id(), startedTask.id());
    var output = api.semaphoreTasks().getTaskOutput(created.id(), completedTask.id());
    var schedule = api.semaphoreSchedules().createInactiveSchedule(created.id(), template.id());
    var savedSchedule = api.semaphoreSchedules().getSchedule(created.id(), schedule.id());
    var schedules = api.semaphoreSchedules().getSchedules(created.id());
    var hiddenProject = api.semaphoreProjects().createProject();
    var guest = store.semaphoreRbacUser();
    api.semaphoreUsers().addGuestToProject(created.id(), guest.user().id());
    var guestSession = api.semaphoreAuth().loginAs(guest);

    assertThat(created.id()).isPositive();
    assertThat(saved.id()).isEqualTo(created.id());
    assertThat(saved.name()).isEqualTo(created.name());
    assertThat(role.role()).isEqualTo("owner");
    assertThat(key.projectId()).isEqualTo(created.id());
    assertThat(repository.sshKeyId()).isEqualTo(key.id());
    assertThat(inventory.sshKeyId()).isEqualTo(key.id());
    assertThat(template.repositoryId()).isEqualTo(repository.id());
    assertThat(template.inventoryId()).isEqualTo(inventory.id());
    assertThat(completedTask.status()).isEqualTo("success");
    assertThat(completedTask.templateId()).isEqualTo(template.id());
    assertThat(completedTask.commitHash()).isNotBlank();
    assertThat(output).isNotEmpty();
    assertThat(output).allMatch(line -> line.taskId() == completedTask.id());
    assertThat(output)
        .extracting(line -> line.output())
        .anyMatch(line -> line.contains("semaphore-bookwright-smoke-ok"));
    assertThat(savedSchedule.templateId()).isEqualTo(template.id());
    assertThat(savedSchedule.cronFormat()).isEqualTo("0 0 * * *");
    assertThat(savedSchedule.active()).isFalse();
    assertThat(schedules).extracting(item -> item.id()).contains(schedule.id());
    api.semaphoreUsers().verifyProjectReadable(guestSession, created.id());
    api.semaphoreUsers().verifyGuestCannotCreateAccessKey(guestSession, created.id());
    api.semaphoreUsers().verifyProjectHidden(guestSession, hiddenProject.id());
  }
}
