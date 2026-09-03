package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record Schedule(
    long id,
    String name,
    @JsonProperty("project_id") long projectId,
    @JsonProperty("template_id") long templateId,
    @JsonProperty("cron_format") String cronFormat,
    boolean active,
    String type,
    @JsonProperty("run_at") Instant runAt,
    @JsonProperty("delete_after_run") boolean deleteAfterRun,
    @JsonProperty("task_params") ScheduleTaskParameters taskParams,
    @JsonProperty("tpl_name") String templateName) {}
