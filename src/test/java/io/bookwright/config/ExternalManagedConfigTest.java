package io.bookwright.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.bookwright.fixtures.semaphore.SemaphoreExternalManagedFixtures;
import java.util.Map;
import org.aeonbits.owner.ConfigFactory;
import org.junit.jupiter.api.Test;

class ExternalManagedConfigTest {

  @Test
  void mutationsAreDisabledByDefault() {
    assertThat(ConfigFactory.create(ExternalManagedConfig.class).mutationsAllowed()).isFalse();
  }

  @Test
  void rejectsManagedFixtureWithoutExplicitMutationOptIn() {
    assertThatThrownBy(
            () ->
                SemaphoreExternalManagedFixtures.from(
                    config(
                        "EXTERNAL_MUTATIONS_ALLOWED",
                        "false",
                        "EXTERNAL_MANAGED_PROJECT_ID",
                        "1",
                        "EXTERNAL_MANAGED_TEMPLATE_A_ID",
                        "2",
                        "EXTERNAL_MANAGED_TEMPLATE_B_ID",
                        "3",
                        "EXTERNAL_MANAGED_MARKER_A",
                        "marker-a",
                        "EXTERNAL_MANAGED_MARKER_B",
                        "marker-b")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("EXTERNAL_MUTATIONS_ALLOWED=true");
  }

  @Test
  void acceptsExplicitAndDistinctManagedFixtureReferences() {
    var fixture =
        SemaphoreExternalManagedFixtures.from(
            config(
                "EXTERNAL_MUTATIONS_ALLOWED",
                "true",
                "EXTERNAL_MANAGED_PROJECT_ID",
                "11",
                "EXTERNAL_MANAGED_TEMPLATE_A_ID",
                "21",
                "EXTERNAL_MANAGED_TEMPLATE_B_ID",
                "22",
                "EXTERNAL_MANAGED_MARKER_A",
                "marker-a",
                "EXTERNAL_MANAGED_MARKER_B",
                "marker-b"));

    assertThat(fixture.projectId()).isEqualTo(11);
    assertThat(fixture.templateA().id()).isEqualTo(21);
    assertThat(fixture.templateB().id()).isEqualTo(22);
  }

  private ExternalManagedConfig config(String... entries) {
    var values = new java.util.HashMap<String, String>();
    for (int index = 0; index < entries.length; index += 2) {
      values.put(entries[index], entries[index + 1]);
    }
    return ConfigFactory.create(ExternalManagedConfig.class, Map.copyOf(values));
  }
}
