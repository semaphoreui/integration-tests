package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RunnerTag(String tag, @JsonProperty("number_of_runners") int numberOfRunners) {}
