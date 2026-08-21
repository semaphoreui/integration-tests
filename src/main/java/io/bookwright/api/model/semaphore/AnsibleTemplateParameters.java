package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AnsibleTemplateParameters(
    @JsonProperty("allow_debug") boolean allowDebug,
    @JsonProperty("allow_override_inventory") boolean allowOverrideInventory,
    @JsonProperty("allow_override_limit") boolean allowOverrideLimit,
    @JsonProperty("allow_override_tags") boolean allowOverrideTags,
    @JsonProperty("allow_override_skip_tags") boolean allowOverrideSkipTags,
    @JsonProperty("allow_override_skip_galaxy_install") boolean allowOverrideSkipGalaxyInstall,
    @JsonProperty("skip_galaxy_install") boolean skipGalaxyInstall,
    List<String> limit,
    List<String> tags,
    @JsonProperty("skip_tags") List<String> skipTags) {}
