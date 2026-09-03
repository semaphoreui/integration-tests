package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.Smoke;
import io.bookwright.api.model.testenvironment.DynamicRunnerEvent;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@Smoke
@OwnerDanil
@Feature("Semaphore dynamic runner")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "feature-dynamic-runner")
class DynamicRunnerTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Webhook launches one runner process for one task and the process exits")
  void webhookLaunchesOneOffRunner(ApiSteps api, SemaphoreFixtures fixtures) {
    var project = api.semaphore().projects().createProject(fixtures.projects().primary());
    var key =
        api.semaphore()
            .accessKeys()
            .create(project.id(), fixtures.accessKey().request(project.id()));
    var repository =
        api.semaphore()
            .repositories()
            .create(
                project.id(), fixtures.repositories().primary().request(project.id(), key.id()));
    var inventory =
        api.semaphore()
            .inventories()
            .create(project.id(), fixtures.inventory().request(project.id(), key.id()));
    var template =
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures
                    .templates()
                    .primary()
                    .request(project.id(), repository.id(), inventory.id()));
    var task = api.semaphore().tasks().startAndWait(project.id(), template.id());

    assertThat(api.semaphore().runners().waitUntilDynamicTaskLifecycle(task.id()).events())
        .filteredOn(event -> event.taskId() == task.id())
        .extracting(DynamicRunnerEvent::type, DynamicRunnerEvent::exitCode)
        .containsExactly(
            tuple("webhook_start", null),
            tuple("runner_started", null),
            tuple("webhook_finish", null),
            tuple("runner_exited", 0));
  }
}
