package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record VariableGroup(
    long id,
    String name,
    @JsonProperty("project_id") long projectId,
    String json,
    String env,
    List<VariableGroupSecret> secrets) {}
