package io.bookwright.config;

import org.aeonbits.owner.Config;

/** Explicit opt-in and Git source for persistent managed-external fixtures. */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({"system:properties", "system:env"})
public interface ExternalManagedConfig extends Config {

  @Key("EXTERNAL_MUTATIONS_ALLOWED")
  @DefaultValue("false")
  boolean mutationsAllowed();

  @Key("EXTERNAL_MANAGED_FIXTURE_REPOSITORY")
  @DefaultValue("https://github.com/semaphoreui/integration-tests.git")
  String fixtureRepository();

  @Key("EXTERNAL_MANAGED_FIXTURE_BRANCH")
  @DefaultValue("main")
  String fixtureBranch();
}
