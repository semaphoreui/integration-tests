package io.bookwright.steps.semaphore.schedules;

import com.google.inject.Inject;
import io.bookwright.api.model.semaphore.CronValidationRequest;
import io.bookwright.api.model.semaphore.Schedule;
import io.bookwright.api.model.semaphore.ScheduleActiveRequest;
import io.bookwright.api.model.semaphore.ScheduleRequest;
import io.bookwright.api.semaphore.SemaphoreSessionApis;
import io.bookwright.api.semaphore.schedules.SemaphoreSchedulesApi;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.io.IOException;
import java.util.List;
import retrofit2.Call;
import retrofit2.Response;

public class ScheduleSteps {

  private final SemaphoreSchedulesApi api;
  private final TeardownStorage teardown;

  @Inject
  public ScheduleSteps(SemaphoreSchedulesApi api, TeardownStorage teardown) {
    this.api = api;
    this.teardown = teardown;
  }

  @Step("Create Semaphore schedule for template {templateId}")
  public Schedule create(long projectId, ScheduleRequest request) {
    Schedule schedule = Calls.body(api.createSchedule(projectId, request), 201, "created schedule");
    teardown.push(
        "Delete Semaphore schedule " + schedule.id(),
        () -> Calls.expectStatus(api.deleteSchedule(projectId, schedule.id()), 204));
    return schedule;
  }

  @Step("Validate Semaphore cron expression {request.cronFormat}")
  public void validateCron(long projectId, CronValidationRequest request) {
    Calls.expectStatus(api.validateCron(projectId, request), 200);
  }

  @Step("Check that Semaphore rejects cron expression {request.cronFormat}")
  public String rejectedCron(long projectId, CronValidationRequest request) {
    return requiredError(api.validateCron(projectId, request), 400, "cron validation");
  }

  @Step("Check that Semaphore rejects schedule creation")
  public String rejectedCreate(long projectId, ScheduleRequest request) {
    return requiredError(api.createSchedule(projectId, request), 400, "schedule creation");
  }

  @Step("Verify isolated user cannot create a Semaphore schedule in project {projectId}")
  public void verifyCannotCreate(
      SemaphoreSessionApis session, long projectId, ScheduleRequest request) {
    Calls.expectStatus(session.schedules().createSchedule(projectId, request), 403);
  }

  @Step("Get Semaphore schedule {scheduleId}")
  public Schedule getSchedule(long projectId, long scheduleId) {
    return Calls.body(api.getSchedule(projectId, scheduleId), 200, "schedule");
  }

  @Step("List schedules in Semaphore project {projectId}")
  public List<Schedule> getSchedules(long projectId) {
    return Calls.body(api.getSchedules(projectId), 200, "schedules");
  }

  @Step("Update Semaphore schedule {scheduleId}")
  public Schedule update(long projectId, long scheduleId, ScheduleRequest request) {
    Calls.expectStatus(api.updateSchedule(projectId, scheduleId, request), 204);
    return getSchedule(projectId, scheduleId);
  }

  @Step("Set Semaphore schedule {scheduleId} active state to {active}")
  public Schedule setActive(long projectId, long scheduleId, boolean active) {
    Calls.expectStatus(
        api.setScheduleActive(projectId, scheduleId, new ScheduleActiveRequest(active)), 204);
    return getSchedule(projectId, scheduleId);
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

  private String requiredError(Call<?> call, int expectedStatus, String operation) {
    Response<?> response = Calls.response(call);
    Calls.expectStatus(response, expectedStatus);
    try (var body = response.errorBody()) {
      if (body == null) {
        throw new IllegalStateException(
            "Semaphore returned %d for %s without an error body"
                .formatted(expectedStatus, operation));
      }
      String diagnostic = body.string();
      if (diagnostic.isBlank()) {
        throw new IllegalStateException(
            "Semaphore returned %d for %s with an empty error body"
                .formatted(expectedStatus, operation));
      }
      return diagnostic;
    } catch (IOException error) {
      throw new IllegalStateException("Could not read Semaphore " + operation + " error", error);
    }
  }
}
