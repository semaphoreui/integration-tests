package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreScheduleFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@OwnerDanil
@Feature("Semaphore schedules")
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "feature-schedule-timezone")
class ScheduledTaskExecutionTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Active one-shot schedule creates and completes a task")
  void activeRunAtScheduleExecutes(
      ApiSteps api, SemaphoreFixtures fixtures, SemaphoreScheduleFixtures schedules) {
    var context = createRunnableTemplate(api, fixtures);
    var execution = schedules.execution().nextRunAt(context.projectId(), context.templateId());
    var schedule = api.semaphore().schedules().create(context.projectId(), execution.request());
    var completedTask =
        api.semaphore()
            .tasks()
            .waitForScheduledTaskToSucceed(
                context.projectId(), schedule.id(), context.templateId());

    assertThat(Instant.now()).isAfterOrEqualTo(execution.expectedAt());
    assertThat(completedTask.scheduleId()).isEqualTo(schedule.id());
    assertThat(completedTask.message()).isEqualTo(schedules.execution().taskMessage());
    assertThat(completedTask.status()).isEqualTo(fixtures.expectations().successfulTaskStatus());
    assertThat(api.semaphore().tasks().getTaskOutputText(context.projectId(), completedTask.id()))
        .contains(fixtures.expectations().outputMarker());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Active cron schedule creates and completes a task in configured timezone")
  void activeCronScheduleExecutes(
      ApiSteps api, SemaphoreFixtures fixtures, SemaphoreScheduleFixtures schedules) {
    var context = createRunnableTemplate(api, fixtures);
    var execution =
        schedules
            .execution()
            .next(context.projectId(), context.templateId(), schedules.expectedTimezone());
    var schedule = api.semaphore().schedules().create(context.projectId(), execution.request());
    var completedTask =
        api.semaphore()
            .tasks()
            .waitForScheduledTaskToSucceed(
                context.projectId(), schedule.id(), context.templateId());

    assertThat(api.semaphore().system().info().scheduleTimezone())
        .isEqualTo(schedules.expectedTimezone());
    assertThat(schedule.active()).isTrue();
    assertThat(schedule.cronFormat()).isEqualTo(execution.request().cronFormat());
    assertThat(Instant.now()).isAfterOrEqualTo(execution.expectedAt());
    assertThat(completedTask.scheduleId()).isEqualTo(schedule.id());
    assertThat(completedTask.message()).isEqualTo(schedules.execution().taskMessage());
    assertThat(completedTask.status()).isEqualTo(fixtures.expectations().successfulTaskStatus());
    assertThat(api.semaphore().tasks().getTaskOutputText(context.projectId(), completedTask.id()))
        .contains(fixtures.expectations().outputMarker());
  }

  private RunnableTemplate createRunnableTemplate(ApiSteps api, SemaphoreFixtures fixtures) {
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
    return new RunnableTemplate(project.id(), template.id());
  }

  private record RunnableTemplate(long projectId, long templateId) {}
}
