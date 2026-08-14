package io.bookwright.steps.semaphore.schedules;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.Schedule;
import io.bookwright.api.model.semaphore.ScheduleRequest;
import io.bookwright.api.semaphore.schedules.SemaphoreSchedulesApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.util.List;

public class ScheduleSteps {

  private final SemaphoreSchedulesApi api;
  private final TeardownStorage teardown;

  @Inject
  public ScheduleSteps(SemaphoreSchedulesApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create inactive cron schedule for Semaphore template {templateId}")
  public Schedule create(long projectId, ScheduleRequest request) {
    Schedule schedule = Calls.body(api.createSchedule(projectId, request), 201, "created schedule");
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

  @Step("Find required schedule {name} in Semaphore project {projectId}")
  public Schedule requireByName(long projectId, String name) {
    List<Schedule> schedules = getSchedules(projectId);
    return schedules.stream()
        .filter(schedule -> name.equals(schedule.name()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Required schedule '%s' was not found in project %d. Available schedules: %s"
                        .formatted(
                            name, projectId, schedules.stream().map(Schedule::name).toList())));
  }
}
