package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AnsibleTaskParameters(
    boolean debug,
    @JsonProperty("debug_level") int debugLevel,
    @JsonProperty("dry_run") boolean dryRun,
    boolean diff,
    List<String> limit,
    List<String> tags,
    @JsonProperty("skip_tags") List<String> skipTags,
    @JsonProperty("skip_galaxy_install") boolean skipGalaxyInstall) {}
