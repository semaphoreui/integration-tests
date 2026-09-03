package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ScheduleRequest(
    Long id,
    String name,
    @JsonProperty("project_id") long projectId,
    @JsonProperty("template_id") long templateId,
    @JsonProperty("cron_format") String cronFormat,
    boolean active,
    String type,
    @JsonProperty("run_at") Instant runAt,
    @JsonProperty("delete_after_run") boolean deleteAfterRun,
    @JsonProperty("task_params") ScheduleTaskParameters taskParams) {

  public ScheduleRequest(
      String name,
      long projectId,
      long templateId,
      String cronFormat,
      boolean active,
      String type) {
    this(null, name, projectId, templateId, cronFormat, active, type, null, false, null);
  }
}
