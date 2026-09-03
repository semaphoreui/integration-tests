package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CronValidationRequest(
    @JsonProperty("project_id") long projectId, @JsonProperty("cron_format") String cronFormat) {}
