package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@OwnerDanil
@Feature("Semaphore task lifecycle")
@Preconditions({
  Precondition.SEMAPHORE_ADMIN_SESSION,
  Precondition.SEMAPHORE_PROJECT_EXISTS,
  Precondition.SEMAPHORE_EXECUTABLE_TEMPLATE_EXISTS
})
class RecentTasksApiTest {

  @Test
  @DisplayName("Recent tasks list includes completed executions in newest-first order")
  void recentTasksContainCompletedExecutions(
      ApiSteps api, TestStore store, SemaphoreFixtures core) {
    var template = store.semaphoreTemplate();
    var first = api.semaphore().tasks().startAndWait(template.projectId(), template.id());
    var second = api.semaphore().tasks().startAndWait(template.projectId(), template.id());

    assertThat(api.semaphore().tasks().getLastTasks(template.projectId()))
        .allSatisfy(
            task -> {
              assertThat(task.projectId()).isEqualTo(template.projectId());
              assertThat(task.templateId()).isEqualTo(template.id());
              assertThat(task.status()).isEqualTo(core.expectations().successfulTaskStatus());
            })
        .extracting(task -> task.id())
        .containsExactly(second.id(), first.id());
  }
}
