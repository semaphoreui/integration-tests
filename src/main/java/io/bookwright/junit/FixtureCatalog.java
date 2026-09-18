package io.bookwright.junit;

import io.bookwright.config.Configs;
import io.bookwright.config.MainConfig;
import io.bookwright.fixtures.database.HotelDatabaseFixtures;
import io.bookwright.fixtures.local.LocalUserFixtures;
import io.bookwright.fixtures.saucedemo.SauceDemoFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreBackupFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreBranchIsolationFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreBuildDeployFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreConcurrencyFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreEncryptionRotationFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreFileInventoryFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreHttpsGitFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreIntegrationFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreLdapFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreLoginSecurityFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreOidcFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreProjectDeletionFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreRunnerRoutingFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreScheduleFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreShellOutputFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreSshFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreStaticInventoryFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreSurveyFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreTerraformFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreTokenFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreTotpFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreUpgradeFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreUserLifecycleFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreVariableGroupFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreWebCacheFixtures;
import io.bookwright.util.TestData;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

/** Single type-safe registry for test-method fixture parameters. */
final class FixtureCatalog {

  private static final Map<Class<?>, FixtureDefinition<?>> FIXTURES =
      Map.ofEntries(
          fixture(SauceDemoFixtures.class, context -> SauceDemoFixtures.from(context.config())),
          fixture(LocalUserFixtures.class, context -> LocalUserFixtures.from(context.config())),
          fixture(HotelDatabaseFixtures.class, context -> HotelDatabaseFixtures.seeded()),
          fixture(
              SemaphoreEncryptionRotationFixtures.class,
              context -> SemaphoreEncryptionRotationFixtures.from(context.config())),
          fixture(
              SemaphoreBackupFixtures.class,
              context -> SemaphoreBackupFixtures.from(context.testData())),
          fixture(
              SemaphoreBranchIsolationFixtures.class,
              context ->
                  SemaphoreBranchIsolationFixtures.from(context.config(), context.testData())),
          fixture(
              SemaphoreBuildDeployFixtures.class,
              context -> SemaphoreBuildDeployFixtures.from(context.config(), context.testData())),
          fixture(
              SemaphoreConcurrencyFixtures.class,
              context -> SemaphoreConcurrencyFixtures.from(context.testData())),
          fixture(
              SemaphoreFixtures.class,
              context -> SemaphoreFixtures.from(context.config(), context.testData())),
          fixture(
              SemaphoreFileInventoryFixtures.class,
              context -> SemaphoreFileInventoryFixtures.from(context.config(), context.testData())),
          fixture(
              SemaphoreHttpsGitFixtures.class,
              context -> SemaphoreHttpsGitFixtures.from(context.testData())),
          fixture(
              SemaphoreIntegrationFixtures.class,
              context -> SemaphoreIntegrationFixtures.from(context.testData())),
          fixture(SemaphoreLdapFixtures.class, context -> SemaphoreLdapFixtures.standard()),
          fixture(
              SemaphoreLoginSecurityFixtures.class,
              context -> SemaphoreLoginSecurityFixtures.from(context.config(), context.testData())),
          fixture(SemaphoreOidcFixtures.class, context -> SemaphoreOidcFixtures.standard()),
          fixture(
              SemaphoreProjectDeletionFixtures.class,
              context ->
                  SemaphoreProjectDeletionFixtures.from(context.config(), context.testData())),
          fixture(
              SemaphoreRunnerRoutingFixtures.class,
              context -> SemaphoreRunnerRoutingFixtures.from(context.testData())),
          fixture(
              SemaphoreScheduleFixtures.class,
              context -> SemaphoreScheduleFixtures.from(context.testData())),
          fixture(
              SemaphoreShellOutputFixtures.class,
              context -> SemaphoreShellOutputFixtures.from(context.config(), context.testData())),
          fixture(
              SemaphoreSshFixtures.class, context -> SemaphoreSshFixtures.from(context.testData())),
          fixture(
              SemaphoreStaticInventoryFixtures.class,
              context ->
                  SemaphoreStaticInventoryFixtures.from(context.config(), context.testData())),
          fixture(
              SemaphoreSurveyFixtures.class,
              context -> SemaphoreSurveyFixtures.from(context.testData())),
          fixture(
              SemaphoreTerraformFixtures.class,
              context -> SemaphoreTerraformFixtures.from(context.config(), context.testData())),
          fixture(SemaphoreTotpFixtures.class, context -> SemaphoreTotpFixtures.standard()),
          fixture(
              SemaphoreTokenFixtures.class,
              context -> SemaphoreTokenFixtures.from(context.testData())),
          fixture(
              SemaphoreUpgradeFixtures.class,
              context -> SemaphoreUpgradeFixtures.from(context.config())),
          fixture(
              SemaphoreUserLifecycleFixtures.class,
              context -> SemaphoreUserLifecycleFixtures.from(context.testData())),
          fixture(
              SemaphoreVariableGroupFixtures.class,
              context -> SemaphoreVariableGroupFixtures.from(context.testData())),
          fixture(
              SemaphoreWebCacheFixtures.class,
              context -> SemaphoreWebCacheFixtures.from(context.testData())));

  private FixtureCatalog() {}

  static boolean supports(Class<?> type) {
    return FIXTURES.containsKey(type);
  }

  static Set<Class<?>> fixtureTypes() {
    return FIXTURES.keySet();
  }

  static <T> T resolve(Class<T> type, ExtensionContext extensionContext) {
    return resolve(
        type,
        new FixtureContext(Configs.main(), () -> TestDataExtension.getOrCreate(extensionContext)));
  }

  static <T> T resolve(Class<T> type, MainConfig config, TestData testData) {
    return resolve(type, new FixtureContext(config, () -> testData));
  }

  private static <T> T resolve(Class<T> type, FixtureContext context) {
    FixtureDefinition<?> definition = FIXTURES.get(type);
    if (definition == null) {
      throw new ParameterResolutionException("No fixture factory registered for " + type.getName());
    }
    return type.cast(definition.create(context));
  }

  private static <T> Map.Entry<Class<?>, FixtureDefinition<?>> fixture(
      Class<T> type, Function<FixtureContext, T> factory) {
    return Map.entry(type, new FixtureDefinition<>(type, factory));
  }

  private record FixtureContext(MainConfig config, Supplier<TestData> testDataSupplier) {

    TestData testData() {
      return testDataSupplier.get();
    }
  }

  private record FixtureDefinition<T>(Class<T> type, Function<FixtureContext, T> factory) {

    T create(FixtureContext context) {
      return type.cast(factory.apply(context));
    }
  }
}
