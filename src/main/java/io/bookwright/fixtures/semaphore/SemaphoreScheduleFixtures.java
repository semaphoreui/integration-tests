package io.bookwright.fixtures.semaphore;

import io.bookwright.api.model.semaphore.CronValidationRequest;
import io.bookwright.api.model.semaphore.ScheduleRequest;
import io.bookwright.api.model.semaphore.ScheduleTaskParameters;
import io.bookwright.util.TestData;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Typed schedule scenarios and expectations. */
public record SemaphoreScheduleFixtures(
    CronSchedule cron,
    RunAtSchedule runAt,
    String invalidCronFormat,
    String invalidType,
    String expectedCronError,
    String expectedMissingRunAtError,
    String expectedPastRunAtError,
    String expectedInvalidTypeError,
    String expectedTimezone) {

  public static SemaphoreScheduleFixtures from(TestData data) {
    String suffix = Long.toUnsignedString(data.testSeed(), 36);
    return new SemaphoreScheduleFixtures(
        new CronSchedule(
            "bookwright-nightly-schedule-" + suffix,
            "0 0 * * *",
            "bookwright-weekly-schedule-" + suffix,
            "15 4 * * 1",
            "bookwright-cron-message-" + suffix),
        new RunAtSchedule(
            "bookwright-one-shot-schedule-" + suffix,
            "bookwright-run-at-message-" + suffix,
            "run_at"),
        "*/foo",
        "unknown",
        "Cron:",
        "run_at must be provided",
        "run_at must be in the future",
        "invalid schedule type",
        "UTC");
  }

  public CronValidationRequest invalidCron(long projectId) {
    return new CronValidationRequest(projectId, invalidCronFormat);
  }

  public record CronSchedule(
      String name,
      String cronFormat,
      String updatedName,
      String updatedCronFormat,
      String taskMessage) {

    public ScheduleRequest request(long projectId, long templateId) {
      return new ScheduleRequest(
          null, name, projectId, templateId, cronFormat, false, "", null, false, taskParameters());
    }

    public CronValidationRequest validation(long projectId) {
      return new CronValidationRequest(projectId, cronFormat);
    }

    public ScheduleRequest update(io.bookwright.api.model.semaphore.Schedule schedule) {
      return new ScheduleRequest(
          schedule.id(),
          updatedName,
          schedule.projectId(),
          schedule.templateId(),
          updatedCronFormat,
          schedule.active(),
          "",
          null,
          false,
          taskParameters());
    }

    public ScheduleTaskParameters taskParameters() {
      return SemaphoreScheduleFixtures.taskParameters(taskMessage);
    }
  }

  public record RunAtSchedule(String name, String taskMessage, String type) {

    public ScheduleRequest futureRequest(long projectId, long templateId) {
      return request(
          projectId,
          templateId,
          Instant.now().plus(Duration.ofHours(2)).truncatedTo(ChronoUnit.SECONDS),
          type);
    }

    public ScheduleRequest pastRequest(long projectId, long templateId) {
      return request(
          projectId,
          templateId,
          Instant.now().minus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.SECONDS),
          type);
    }

    public ScheduleRequest missingRunAtRequest(long projectId, long templateId) {
      return request(projectId, templateId, null, type);
    }

    public ScheduleRequest invalidTypeRequest(long projectId, long templateId, String invalidType) {
      return request(projectId, templateId, null, invalidType);
    }

    private ScheduleRequest request(long projectId, long templateId, Instant runAt, String type) {
      return new ScheduleRequest(
          null, name, projectId, templateId, "", false, type, runAt, true, taskParameters());
    }

    public ScheduleTaskParameters taskParameters() {
      return SemaphoreScheduleFixtures.taskParameters(taskMessage);
    }
  }

  private static ScheduleTaskParameters taskParameters(String message) {
    return new ScheduleTaskParameters(
        "", null, null, message, null, null, Map.of("limit", "localhost"));
  }
}
