package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreUpgradeFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.bookwright.teardown.TeardownStorage;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@OwnerDanil
@Feature("Semaphore release upgrade")
class UpgradeCompatibilityTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @EnabledIfSystemProperty(named = "SEMAPHORE_UPGRADE_PHASE", matches = "seed")
  @DisplayName("N-1 release creates the persisted upgrade fixture")
  void seedPreviousRelease(
      ApiSteps api, SemaphoreUpgradeFixtures fixture, TeardownStorage teardown) {
    var project = api.semaphore().projects().createProject(fixture.project());
    var key = api.semaphore().accessKeys().createAndVerifyMasked(project.id(), fixture.accessKey());
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixture.repository().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixture.inventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixture.template().request(project.id(), repository.id(), inventory.id()));
    var completedTask = api.semaphore().tasks().startAndWait(project.id(), template.id());
    var schedule =
        api.semaphore()
            .schedules()
            .create(project.id(), fixture.schedule().request(project.id(), template.id()));

    assertThat(completedTask.status()).isEqualTo("success");
    assertThat(
            api.semaphore()
                .tasks()
                .waitUntilTaskOutputContains(
                    project.id(), completedTask.id(), fixture.outputMarker()))
        .contains(fixture.outputMarker());
    assertThat(schedule.templateId()).isEqualTo(template.id());

    teardown.retainCreatedData();
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @EnabledIfSystemProperty(named = "SEMAPHORE_UPGRADE_PHASE", matches = "verify")
  @DisplayName("Current release preserves N-1 data and can execute its template")
  void verifyCurrentRelease(ApiSteps api, SemaphoreUpgradeFixtures fixture) {
    var project = api.semaphore().projects().requireByName(fixture.project().name());
    var repository =
        api.semaphore().repositories().requireByName(project.id(), fixture.repository().name());
    var inventory =
        api.semaphore().inventories().requireByName(project.id(), fixture.inventory().name());
    var template =
        api.semaphore().templates().requireByName(project.id(), fixture.template().name());
    var schedule =
        api.semaphore().schedules().requireByName(project.id(), fixture.schedule().name());
    var persistedTask =
        api.semaphore().tasks().requireSuccessfulForTemplate(project.id(), template.id());

    assertThat(repository.projectId()).isEqualTo(project.id());
    assertThat(inventory.projectId()).isEqualTo(project.id());
    assertThat(template.repositoryId()).isEqualTo(repository.id());
    assertThat(template.inventoryId()).isEqualTo(inventory.id());
    assertThat(schedule.templateId()).isEqualTo(template.id());
    assertThat(
            api.semaphore()
                .tasks()
                .waitUntilTaskOutputContains(
                    project.id(), persistedTask.id(), fixture.outputMarker()))
        .contains(fixture.outputMarker());

    var key = api.semaphore().accessKeys().requireByName(project.id(), fixture.accessKey().name());
    api.semaphore().accessKeys().verifyMasked(project.id(), key.id(), fixture.accessKey());
    assertThat(repository.sshKeyId()).isEqualTo(key.id());
    assertThat(inventory.sshKeyId()).isEqualTo(key.id());

    var rerunTask = api.semaphore().tasks().startAndWait(project.id(), template.id());
    assertThat(
            api.semaphore()
                .tasks()
                .waitUntilTaskOutputContains(project.id(), rerunTask.id(), fixture.outputMarker()))
        .contains(fixture.outputMarker());
  }
}
