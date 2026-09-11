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
    var project = api.semaphore().projects().requireByName(fixtures.projectName());
    var repository =
        api.semaphore().repositories().requireByName(project.id(), fixtures.repositoryName());
    var inventory =
        api.semaphore().inventories().requireByName(project.id(), fixtures.inventoryName());
    var templateA =
        api.semaphore().templates().requireByName(project.id(), fixtures.templateA().name());
    var templateB =
        api.semaphore().templates().requireByName(project.id(), fixtures.templateB().name());
    fixtures.validate(project, repository, inventory, templateA, templateB);

    var taskA = api.semaphore().tasks().startTaskAndKeepHistory(project.id(), templateA.id());
    var taskB = api.semaphore().tasks().startTaskAndKeepHistory(project.id(), templateB.id());

    api.semaphore().tasks().waitUntilTaskSucceeds(project.id(), taskA.id());
    api.semaphore().tasks().waitUntilTaskSucceeds(project.id(), taskB.id());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), taskA.id(), fixtures.templateA().marker());
    api.semaphore()
        .tasks()
        .waitUntilTaskOutputContains(project.id(), taskB.id(), fixtures.templateB().marker());
  }
}
