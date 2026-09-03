package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IntegrationExtractValue(
    long id,
    @JsonProperty("integration_id") long integrationId,
    String name,
    @JsonProperty("value_source") String valueSource,
    @JsonProperty("body_data_type") String bodyDataType,
    String key,
    String variable,
    @JsonProperty("variable_type") String variableType) {}
