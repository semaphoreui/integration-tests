package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaskRequest(@JsonProperty("template_id") long templateId) {}
