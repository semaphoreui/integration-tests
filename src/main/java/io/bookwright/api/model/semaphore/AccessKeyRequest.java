package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccessKeyRequest(
    String name, String type, @JsonProperty("project_id") long projectId) {}
