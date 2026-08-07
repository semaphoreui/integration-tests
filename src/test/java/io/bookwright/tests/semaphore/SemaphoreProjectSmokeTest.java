package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.Smoke;
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
  @DisplayName("Owner can execute and clean up the core project workflow")
  void ownerCanCreateAndReadProject(ApiSteps api) {
    api.semaphore().health();
    api.semaphore().invalidLoginIsRejected();
    api.semaphore().login();

    var created = api.semaphore().createProject();
    var saved = api.semaphore().getProject(created.id());
    var role = api.semaphore().getProjectRole(created.id());
    var key = api.semaphore().createNoneAccessKey(created.id());
    var repository = api.semaphore().createPublicRepository(created.id(), key.id());
    var inventory = api.semaphore().createStaticInventory(created.id(), key.id());
    var template =
        api.semaphore().createAnsibleTemplate(created.id(), repository.id(), inventory.id());
    var startedTask = api.semaphore().startTask(created.id(), template.id());
    var completedTask = api.semaphore().waitUntilTaskSucceeds(created.id(), startedTask.id());
    var output = api.semaphore().getTaskOutput(created.id(), completedTask.id());
    var schedule = api.semaphore().createInactiveSchedule(created.id(), template.id());
    var savedSchedule = api.semaphore().getSchedule(created.id(), schedule.id());
    var schedules = api.semaphore().getSchedules(created.id());

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
  }
}
