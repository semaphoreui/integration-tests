package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScheduleRequest(
    String name,
    @JsonProperty("project_id") long projectId,
    @JsonProperty("template_id") long templateId,
    @JsonProperty("cron_format") String cronFormat,
    boolean active,
    String type) {}
