package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScheduleTaskParameters(
    String environment,
    String arguments,
    @JsonProperty("git_branch") String gitBranch,
    String message,
    String version,
    @JsonProperty("inventory_id") Long inventoryId,
    Map<String, Object> params) {}
