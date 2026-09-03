package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreProjectDeletionFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore project deletion")
class ProjectDeletionApiTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Stopped task allows project deletion with all dependent resources")
  void stoppedTaskAllowsProjectDeletion(ApiSteps api, SemaphoreProjectDeletionFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var taskId = createAndStartTask(api, fixtures, project.id());

    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), taskId, fixtures.readyMarker());
    assertThat(api.semaphore().tasks().stopAndWait(project.id(), taskId, false).status())
        .isEqualTo(fixtures.stoppedTaskStatus());

    api.semaphore().projects().deleteCreatedProject(project);
    api.semaphore().projects().verifyDeleted(project);
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Known defect: project deletion is accepted while a task is running")
  void runningTaskDoesNotBlockProjectDeletion(
      ApiSteps api, SemaphoreProjectDeletionFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.project());
    var taskId = createAndStartTask(api, fixtures, project.id());

    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), taskId, fixtures.readyMarker());
    api.semaphore().projects().deleteCreatedProject(project);

    api.semaphore().projects().verifyDeleted(project);
  }

  private long createAndStartTask(
      ApiSteps api, SemaphoreProjectDeletionFixtures fixtures, long projectId) {
    var key =
        api.semaphore().accessKeys().create(projectId, fixtures.accessKey().request(projectId));
    var repository =
        api.semaphore()
            .repositories()
            .create(projectId, fixtures.repository().request(projectId, key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(projectId, fixtures.inventory().request(projectId, key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                projectId, fixtures.template().request(projectId, repository.id(), inventory.id()));
    return api.semaphore().tasks().startTask(projectId, template.id()).id();
  }
}
