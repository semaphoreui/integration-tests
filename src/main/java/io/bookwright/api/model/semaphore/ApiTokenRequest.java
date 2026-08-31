package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ApiTokenRequest(String name, @JsonProperty("expires_at") Instant expiresAt) {}
