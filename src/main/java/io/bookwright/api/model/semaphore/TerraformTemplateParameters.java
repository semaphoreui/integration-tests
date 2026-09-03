package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TerraformTemplateParameters(
    @JsonProperty("allow_destroy") boolean allowDestroy,
    @JsonProperty("allow_auto_approve") boolean allowAutoApprove,
    @JsonProperty("auto_approve") boolean autoApprove,
    @JsonProperty("override_backend") boolean overrideBackend,
    @JsonProperty("backend_filename") String backendFilename)
    implements TemplateParameters {}
