package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.Smoke;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
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
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Owner can execute and clean up the core project workflow")
  void ownerCanExecuteCoreProjectWorkflow(ApiSteps api, SemaphoreFixtures fixtures) {
    var created = api.semaphore().projects().createProject(fixtures.projects().primary());
    var saved = api.semaphore().projects().getProject(created.id());
    var role = api.semaphore().projects().getProjectRole(created.id());
    assertThat(created.id()).isPositive();
    assertThat(saved.id()).isEqualTo(created.id());
    assertThat(saved.name()).isEqualTo(created.name());
    assertThat(role.role()).isEqualTo(fixtures.rbac().ownerRole());

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
    assertThat(key.projectId()).isEqualTo(created.id());
    assertThat(repository.sshKeyId()).isEqualTo(key.id());
    assertThat(inventory.sshKeyId()).isEqualTo(key.id());
    assertThat(template.repositoryId()).isEqualTo(repository.id());
    assertThat(template.inventoryId()).isEqualTo(inventory.id());

    var completedTask = api.semaphore().tasks().startAndWait(created.id(), template.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(
            created.id(), completedTask.id(), fixtures.expectations().outputMarker());
    var output = api.semaphore().tasks().getTaskOutput(created.id(), completedTask.id());
    assertThat(completedTask.status()).isEqualTo(fixtures.expectations().successfulTaskStatus());
    assertThat(completedTask.templateId()).isEqualTo(template.id());
    assertThat(completedTask.commitHash()).isNotBlank();
    assertThat(output).isNotEmpty();
    assertThat(output).allMatch(line -> line.taskId() == completedTask.id());
    assertThat(output)
        .extracting(line -> line.output())
        .anyMatch(line -> line.contains(fixtures.expectations().outputMarker()));
  }
}
