package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IntegrationMatcherRequest(
    @JsonProperty("integration_id") long integrationId,
    String name,
    @JsonProperty("match_type") String matchType,
    String method,
    @JsonProperty("body_data_type") String bodyDataType,
    String key,
    String value) {}
