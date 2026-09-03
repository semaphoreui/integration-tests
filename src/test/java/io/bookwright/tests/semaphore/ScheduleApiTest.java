package io.bookwright.tests.semaphore;

import static org.assertj.core.api.Assertions.assertThat;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.annotations.Regression;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreScheduleFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.junit.TestStore;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Api
@Regression
@OwnerDanil
@Feature("Semaphore schedules")
class ScheduleApiTest {

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Cron schedule can be validated, updated and activated")
  void cronScheduleLifecycle(
      ApiSteps api, SemaphoreFixtures fixtures, SemaphoreScheduleFixtures schedules) {
    var context = createRunnableTemplate(api, fixtures);
    var data = schedules.cron();

    api.semaphore()
        .schedules()
        .validateCron(context.projectId(), data.validation(context.projectId()));
    var created =
        api.semaphore()
            .schedules()
            .create(context.projectId(), data.request(context.projectId(), context.templateId()));
    var updated =
        api.semaphore().schedules().update(context.projectId(), created.id(), data.update(created));
    var activated = api.semaphore().schedules().setActive(context.projectId(), created.id(), true);

    assertThat(created.active()).isFalse();
    assertThat(created.taskParams().message()).isEqualTo(data.taskMessage());
    assertThat(updated.name()).isEqualTo(data.updatedName());
    assertThat(updated.cronFormat()).isEqualTo(data.updatedCronFormat());
    assertThat(updated.taskParams().params()).isEqualTo(data.taskParameters().params());
    assertThat(activated.active()).isTrue();
    assertThat(
            api.semaphore()
                .schedules()
                .setActive(context.projectId(), created.id(), false)
                .active())
        .isFalse();
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("Invalid cron, run time and schedule type are rejected with diagnostics")
  void invalidSchedulePayloadsAreRejected(
      ApiSteps api, SemaphoreFixtures fixtures, SemaphoreScheduleFixtures schedules) {
    var context = createRunnableTemplate(api, fixtures);

    assertThat(
            api.semaphore()
                .schedules()
                .rejectedCron(context.projectId(), schedules.invalidCron(context.projectId())))
        .contains(schedules.expectedCronError());
    assertThat(
            api.semaphore()
                .schedules()
                .rejectedCreate(
                    context.projectId(),
                    schedules
                        .runAt()
                        .missingRunAtRequest(context.projectId(), context.templateId())))
        .contains(schedules.expectedMissingRunAtError());
    assertThat(
            api.semaphore()
                .schedules()
                .rejectedCreate(
                    context.projectId(),
                    schedules.runAt().pastRequest(context.projectId(), context.templateId())))
        .contains(schedules.expectedPastRunAtError());
    assertThat(
            api.semaphore()
                .schedules()
                .rejectedCreate(
                    context.projectId(),
                    schedules
                        .runAt()
                        .invalidTypeRequest(
                            context.projectId(), context.templateId(), schedules.invalidType())))
        .contains(schedules.expectedInvalidTypeError());
  }

  @Test
  @Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
  @DisplayName("One-shot schedule persists run time and task parameters")
  void runAtSchedulePersistsExecutionParameters(
      ApiSteps api, SemaphoreFixtures fixtures, SemaphoreScheduleFixtures schedules) {
    var context = createRunnableTemplate(api, fixtures);
    var data = schedules.runAt();
    var created =
        api.semaphore()
            .schedules()
            .create(
                context.projectId(), data.futureRequest(context.projectId(), context.templateId()));

    assertThat(created.type()).isEqualTo(data.type());
    assertThat(created.runAt()).isAfter(java.time.Instant.now());
    assertThat(created.cronFormat()).isEmpty();
    assertThat(created.deleteAfterRun()).isTrue();
    assertThat(created.taskParams().message()).isEqualTo(data.taskMessage());
    assertThat(api.semaphore().system().info().scheduleTimezone())
        .isEqualTo(schedules.expectedTimezone());
  }

  @Test
  @Preconditions({Precondition.SEMAPHORE_ADMIN_SESSION, Precondition.SEMAPHORE_RBAC_USER_EXISTS})
  @DisplayName("Task runner cannot create schedules")
  void taskRunnerCannotCreateSchedule(
      ApiSteps api,
      TestStore store,
      SemaphoreFixtures fixtures,
      SemaphoreScheduleFixtures schedules) {
    var context = createRunnableTemplate(api, fixtures);
    var account = store.semaphoreRbacUser();
    api.semaphore()
        .users()
        .addToProject(context.projectId(), account.user().id(), fixtures.rbac().taskRunnerRole());

    api.semaphore()
        .schedules()
        .verifyCannotCreate(
            api.semaphore().auth().loginAs(account),
            context.projectId(),
            schedules.cron().request(context.projectId(), context.templateId()));
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
    return new RunnableTemplate(
        project.id(),
        api.semaphore()
            .templates()
            .create(
                project.id(),
                fixtures
                    .templates()
                    .primary()
                    .request(project.id(), repository.id(), inventory.id()))
            .id());
  }

  private record RunnableTemplate(long projectId, long templateId) {}
}
