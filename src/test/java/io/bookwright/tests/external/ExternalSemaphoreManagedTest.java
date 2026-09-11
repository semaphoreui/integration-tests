package io.bookwright.tests.external;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.ExternalManaged;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreExternalManagedFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@ExternalManaged
@OwnerDanil
@Feature("External Semaphore managed tasks")
class ExternalSemaphoreManagedTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("External stand executes two preconfigured task templates")
  void preconfiguredTemplatesExecute(ApiSteps api, SemaphoreExternalManagedFixtures fixtures) {
    var taskA =
        api.semaphore()
            .tasks()
            .startTaskAndKeepHistory(fixtures.projectId(), fixtures.templateA().id());
    var taskB =
        api.semaphore()
            .tasks()
            .startTaskAndKeepHistory(fixtures.projectId(), fixtures.templateB().id());

    api.semaphore().tasks().waitUntilTaskSucceeds(fixtures.projectId(), taskA.id());
    api.semaphore().tasks().waitUntilTaskSucceeds(fixtures.projectId(), taskB.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(
            fixtures.projectId(), taskA.id(), fixtures.templateA().marker());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(
            fixtures.projectId(), taskB.id(), fixtures.templateB().marker());
  }
}
