package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record VariableGroupRequest(
    long id,
    String name,
    @JsonProperty("project_id") long projectId,
    String json,
    String env,
    List<VariableGroupSecretRequest> secrets) {}
