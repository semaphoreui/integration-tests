package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SemaphoreSystemInfo(
    String version, @JsonProperty("schedule_timezone") String scheduleTimezone) {}
