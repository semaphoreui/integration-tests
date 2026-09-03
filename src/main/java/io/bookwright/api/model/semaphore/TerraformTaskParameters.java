package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TerraformTaskParameters(
    boolean plan,
    boolean destroy,
    @JsonProperty("auto_approve") boolean autoApprove,
    boolean upgrade,
    boolean reconfigure)
    implements TaskParameters {}
