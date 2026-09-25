package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.assertions.SecretAssertions;
import io.bookwright.fixtures.semaphore.SemaphoreResourceCrudFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore project resource CRUD")
class ProjectResourceCrudApiTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Updated project resources remain readable and executable")
  void updatedProjectResourcesRemainReadableAndExecutable(
      ApiSteps api, SemaphoreResourceCrudFixtures fixture) {
    var project = api.semaphore().projects().createProject(fixture.project());
    var key = api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixture.accessKey());
    var updatedKey =
        api.semaphore()
            .accessKeys()
            .updateAndVerifyMasked(
                project.id(),
                key.id(),
                fixture.accessKeyUpdateRequest(project.id(), key.id()),
                fixture.updatedAccessKey());
    api.semaphore().accessKeys().verifyMasked(project.id(), key.id(), fixture.accessKey());

    assertThat(updatedKey)
        .satisfies(
            saved -> {
              assertThat(saved.id()).isEqualTo(key.id());
              assertThat(saved.name()).isEqualTo(fixture.updatedAccessKey().name());
              assertThat(saved.type()).isEqualTo(fixture.updatedAccessKey().type());
              assertThat(saved.projectId()).isEqualTo(project.id());
            });

    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixture.repository().request(project.id(), key.id()));
    assertThat(api.semaphore().repositories().get(project.id(), repository.id()))
        .isEqualTo(repository);
    var updatedRepository =
        api.semaphore()
            .repositories()
            .update(
                project.id(),
                repository.id(),
                fixture.repository().updatedRequest(project.id(), repository.id(), key.id()));

    assertThat(updatedRepository)
        .satisfies(
            saved -> {
              assertThat(saved.name()).isEqualTo(fixture.repository().updatedName());
              assertThat(saved.gitUrl()).isEqualTo(fixture.repository().gitUrl());
              assertThat(saved.gitBranch()).isEqualTo(fixture.repository().updatedBranch());
              assertThat(saved.sshKeyId()).isEqualTo(key.id());
            });

    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixture.inventory().request(project.id(), key.id()));
    assertThat(api.semaphore().inventories().get(project.id(), inventory.id()))
        .isEqualTo(inventory);
    var updatedInventory =
        api.semaphore()
            .inventories()
            .update(
                project.id(),
                inventory.id(),
                fixture.inventory().updatedRequest(project.id(), inventory.id(), key.id()));

    assertThat(updatedInventory)
        .satisfies(
            saved -> {
              assertThat(saved.name()).isEqualTo(fixture.inventory().updatedName());
              assertThat(saved.inventory()).isEqualTo(fixture.inventory().updatedContent());
              assertThat(saved.type()).isEqualTo(fixture.inventory().type());
              assertThat(saved.sshKeyId()).isEqualTo(key.id());
            });

    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixture
                    .template()
                    .request(project.id(), updatedRepository.id(), updatedInventory.id()));
    assertThat(api.semaphore().templates().get(project.id(), template.id())).isEqualTo(template);
    var updatedTemplate =
        api.semaphore()
            .templates()
            .update(
                project.id(),
                template.id(),
                fixture
                    .template()
                    .updatedRequest(
                        project.id(),
                        template.id(),
                        updatedRepository.id(),
                        updatedInventory.id()));

    assertThat(updatedTemplate)
        .satisfies(
            saved -> {
              assertThat(saved.name()).isEqualTo(fixture.template().updatedName());
              assertThat(saved.playbook()).isEqualTo(fixture.template().playbook());
              assertThat(saved.repositoryId()).isEqualTo(updatedRepository.id());
              assertThat(saved.inventoryId()).isEqualTo(updatedInventory.id());
              assertThat(saved.arguments()).isEqualTo("[]");
              assertThat(saved.allowOverrideArgsInTask()).isTrue();
            });

    var completedTask = api.semaphore().tasks().startAndWait(project.id(), template.id());
    var completedOutput =
        api.semaphore()
            .tasks()
            .waitUntilTaskOutputContains(project.id(), completedTask.id(), fixture.successMarker());
    var completedRawOutput =
        api.semaphore().tasks().getTaskRawOutput(project.id(), completedTask.id());
    SecretAssertions.credentialsAbsent(
        "updated resource task output", completedOutput, fixture.accessKey());
    SecretAssertions.credentialsAbsent(
        "updated resource task output", completedOutput, fixture.updatedAccessKey());
    SecretAssertions.credentialsAbsent(
        "updated resource raw task output", completedRawOutput, fixture.accessKey());
    SecretAssertions.credentialsAbsent(
        "updated resource raw task output", completedRawOutput, fixture.updatedAccessKey());

    api.semaphore()
        .templates()
        .update(
            project.id(),
            template.id(),
            fixture
                .template()
                .stoppableRequest(
                    project.id(), template.id(), updatedRepository.id(), updatedInventory.id()));
    var activeTask = api.semaphore().tasks().startTask(project.id(), template.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), activeTask.id(), fixture.stopReadyMarker());
    api.semaphore().templates().stopAllTasks(project.id(), template.id(), false);
    var stoppedTask = api.semaphore().tasks().waitUntilTaskStops(project.id(), activeTask.id());

    assertThat(stoppedTask.status()).isEqualTo("stopped");
    assertThat(api.semaphore().tasks().getTaskOutputText(project.id(), stoppedTask.id()))
        .doesNotContain(fixture.stopCompletedMarker());
    assertThat(api.semaphore().tasks().getLastTasks(project.id()))
        .extracting(task -> task.id())
        .contains(completedTask.id(), stoppedTask.id());
  }
}
