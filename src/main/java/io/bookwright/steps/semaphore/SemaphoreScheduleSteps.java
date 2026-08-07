package io.bookwright.steps.semaphore;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Schedule;
import io.bookwright.api.model.semaphore.ScheduleRequest;
import io.bookwright.api.semaphore.SemaphoreSchedulesApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.List;
import java.util.UUID;

public class SemaphoreScheduleSteps {

  private final SemaphoreSchedulesApi api;
  private final TeardownStorage teardown;

  @Inject
  public SemaphoreScheduleSteps(SemaphoreSchedulesApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create inactive cron schedule for Semaphore template {templateId}")
  public Schedule createInactiveSchedule(long projectId, long templateId) {
    Schedule schedule =
        Calls.body(
            api.createSchedule(
                projectId,
                new ScheduleRequest(
                    "bookwright-nightly-schedule-" + UUID.randomUUID(),
                    projectId,
                    templateId,
                    "0 0 * * *",
                    false,
                    "")),
            201,
            "created schedule");
    teardown.push(
        "Delete Semaphore schedule " + schedule.id(),
        () -> Calls.expectStatus(api.deleteSchedule(projectId, schedule.id()), 204));
    return schedule;
  }

  @Step("Get Semaphore schedule {scheduleId}")
  public Schedule getSchedule(long projectId, long scheduleId) {
    return Calls.body(api.getSchedule(projectId, scheduleId), 200, "schedule");
  }

  @Step("List schedules in Semaphore project {projectId}")
  public List<Schedule> getSchedules(long projectId) {
    return Calls.body(api.getSchedules(projectId), 200, "schedules");
  }
}
