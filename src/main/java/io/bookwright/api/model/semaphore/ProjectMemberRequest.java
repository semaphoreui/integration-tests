package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProjectMemberRequest(@JsonProperty("user_id") long userId, String role) {}
