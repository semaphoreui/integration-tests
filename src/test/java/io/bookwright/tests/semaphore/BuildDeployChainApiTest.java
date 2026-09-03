package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreBuildDeployFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore Build and Deploy templates")
class BuildDeployChainApiTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Deploy task receives the selected successful Build version")
  void deployReceivesSelectedBuildVersion(ApiSteps api, SemaphoreBuildDeployFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(project.id(), fixtures.repository().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.inventory().request(project.id(), key.id()));
    var buildTemplate =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures.build().request(project.id(), repository.id(), inventory.id()));
    var deployTemplate =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures
                    .deploy()
                    .request(project.id(), repository.id(), inventory.id(), buildTemplate.id()));
    var buildTask = api.semaphore().tasks().startAndWait(project.id(), buildTemplate.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), buildTask.id(), fixtures.build().outputMarker());
    var deployTask =
        api.semaphore()
            .tasks()
            .startAndWait(
                project.id(), fixtures.deploy().taskRequest(deployTemplate.id(), buildTask.id()));
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(
            project.id(), deployTask.id(), fixtures.deploy().outputMarker());

    var persistedBuild = api.semaphore().tasks().getTask(project.id(), buildTask.id());
    var persistedDeploy = api.semaphore().tasks().getTask(project.id(), deployTask.id());
    var deployHistory = api.semaphore().tasks().requirePersistedTask(project.id(), deployTask.id());
    var eligibleBuilds =
        api.semaphore().tasks().getTasksForTemplate(project.id(), buildTemplate.id());

    assertThat(buildTemplate.type()).isEqualTo(fixtures.build().type());
    assertThat(buildTemplate.startVersion()).isEqualTo(fixtures.build().startVersion());
    assertThat(deployTemplate.type()).isEqualTo(fixtures.deploy().type());
    assertThat(deployTemplate.buildTemplateId()).isEqualTo(buildTemplate.id());
    assertThat(deployTemplate.autorun()).isFalse();
    assertThat(persistedBuild.version()).isEqualTo(fixtures.build().startVersion());
    assertThat(eligibleBuilds)
        .filteredOn(task -> "success".equals(task.status()))
        .extracting(task -> task.id())
        .containsExactly(buildTask.id());
    assertThat(persistedDeploy.buildTaskId()).isEqualTo(buildTask.id());
    assertThat(persistedDeploy.version()).isNull();
    assertThat(deployHistory.buildTask()).isNotNull();
    assertThat(deployHistory.buildTask().version()).isEqualTo(persistedBuild.version());
    assertThat(api.semaphore().tasks().getTaskOutputText(project.id(), buildTask.id()))
        .contains(fixtures.build().outputMarker(), persistedBuild.version());
    assertThat(api.semaphore().tasks().getTaskOutputText(project.id(), deployTask.id()))
        .contains(fixtures.deploy().outputMarker(), persistedBuild.version());
  }
}
