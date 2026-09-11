package io.bookwright.config;

import org.aeonbits.owner.Config;

/** Explicit opt-in and pre-created resource identifiers for managed external checks. */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({"system:properties", "system:env"})
public interface ExternalManagedConfig extends Config {

  @Key("EXTERNAL_MUTATIONS_ALLOWED")
  @DefaultValue("false")
  boolean mutationsAllowed();

  @Key("EXTERNAL_MANAGED_PROJECT_ID")
  long projectId();

  @Key("EXTERNAL_MANAGED_TEMPLATE_A_ID")
  long templateAId();

  @Key("EXTERNAL_MANAGED_TEMPLATE_B_ID")
  long templateBId();

  @Key("EXTERNAL_MANAGED_MARKER_A")
  String markerA();

  @Key("EXTERNAL_MANAGED_MARKER_B")
  String markerB();
}
