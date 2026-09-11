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
                    config("EXTERNAL_MUTATIONS_ALLOWED", "false")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("EXTERNAL_MUTATIONS_ALLOWED=true");
  }

  @Test
  void providesStableNamesAndACompleteDefaultBackup() {
    var fixture =
        SemaphoreExternalManagedFixtures.from(config("EXTERNAL_MUTATIONS_ALLOWED", "true"));
    var backup = fixture.projectBackup();

    assertThat(fixture.projectName()).isEqualTo("bookwright-external-managed");
    assertThat(fixture.templateA().name()).isNotEqualTo(fixture.templateB().name());
    assertThat(backup.at("/meta/name").asText()).isEqualTo(fixture.projectName());
    assertThat(backup.at("/repositories/0/git_url").asText())
        .isEqualTo("https://github.com/semaphoreui/integration-tests.git");
    assertThat(backup.at("/repositories/0/git_branch").asText()).isEqualTo("main");
    assertThat(backup.at("/templates").size()).isEqualTo(2);
  }

  @Test
  void appliesExplicitFixtureRepositoryAndBranchToTheBackup() {
    var fixture =
        SemaphoreExternalManagedFixtures.from(
            config(
                "EXTERNAL_MUTATIONS_ALLOWED",
                "true",
                "EXTERNAL_MANAGED_FIXTURE_REPOSITORY",
                "https://git.example.test/fixtures.git",
                "EXTERNAL_MANAGED_FIXTURE_BRANCH",
                "release-fixtures"));

    assertThat(fixture.projectBackup().at("/repositories/0/git_url").asText())
        .isEqualTo("https://git.example.test/fixtures.git");
    assertThat(fixture.projectBackup().at("/repositories/0/git_branch").asText())
        .isEqualTo("release-fixtures");
  }

  private ExternalManagedConfig config(String... entries) {
    var values = new java.util.HashMap<String, String>();
    for (int index = 0; index < entries.length; index += 2) {
      values.put(entries[index], entries[index + 1]);
    }
    return ConfigFactory.create(ExternalManagedConfig.class, Map.copyOf(values));
  }
}
