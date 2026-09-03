package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IntegrationRequest(
    String name,
    @JsonProperty("project_id") long projectId,
    @JsonProperty("template_id") long templateId,
    @JsonProperty("auth_method") String authMethod,
    @JsonProperty("auth_secret_id") Long authSecretId,
    @JsonProperty("auth_header") String authHeader,
    boolean searchable) {}
