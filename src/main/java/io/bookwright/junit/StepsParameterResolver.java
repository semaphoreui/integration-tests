package io.bookwright.junit;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import io.bookwright.di.ApiModule;
import io.bookwright.di.DbModule;
import io.bookwright.di.UiModule;
import io.bookwright.fixtures.database.HotelDatabaseFixtures;
import io.bookwright.fixtures.local.LocalUserFixtures;
import io.bookwright.fixtures.saucedemo.SauceDemoFixtures;
import io.bookwright.fixtures.semaphore.SemaphoreBackupFixtures;
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
import io.bookwright.steps.ApiSteps;
import io.bookwright.steps.DbSteps;
import io.bookwright.steps.UiSteps;
import io.bookwright.teardown.TeardownStorage;
import io.bookwright.ui.BrowserManager;
import java.util.Set;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Hands tests ready-made Steps facades. One Guice injector per steps facade and test method, cached
 * in the method-scoped store so all injected objects share the same per-test teardown storage.
 */
public class StepsParameterResolver implements ParameterResolver {

  private static final Set<Class<?>> SUPPORTED =
      Set.of(ApiSteps.class, UiSteps.class, DbSteps.class);

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext) {
    Class<?> type = parameterContext.getParameter().getType();
    return type == TestStore.class
        || type == TeardownStorage.class
        || type == TestUser.class
        || type == SauceDemoFixtures.class
        || type == LocalUserFixtures.class
        || type == HotelDatabaseFixtures.class
        || type == SemaphoreEncryptionRotationFixtures.class
        || type == SemaphoreBackupFixtures.class
        || type == SemaphoreBuildDeployFixtures.class
        || type == SemaphoreConcurrencyFixtures.class
        || type == SemaphoreFixtures.class
        || type == SemaphoreFileInventoryFixtures.class
        || type == SemaphoreHttpsGitFixtures.class
        || type == SemaphoreIntegrationFixtures.class
        || type == SemaphoreLdapFixtures.class
        || type == SemaphoreLoginSecurityFixtures.class
        || type == SemaphoreOidcFixtures.class
        || type == SemaphoreProjectDeletionFixtures.class
        || type == SemaphoreRunnerRoutingFixtures.class
        || type == SemaphoreScheduleFixtures.class
        || type == SemaphoreShellOutputFixtures.class
        || type == SemaphoreSshFixtures.class
        || type == SemaphoreStaticInventoryFixtures.class
        || type == SemaphoreSurveyFixtures.class
        || type == SemaphoreTerraformFixtures.class
        || type == SemaphoreTotpFixtures.class
        || type == SemaphoreTokenFixtures.class
        || type == SemaphoreUpgradeFixtures.class
        || type == SemaphoreUserLifecycleFixtures.class
        || type == SemaphoreVariableGroupFixtures.class
        || SUPPORTED.contains(type);
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext) {
    Class<?> type = parameterContext.getParameter().getType();
    if (type == TestStore.class) {
      return new TestStore(extensionContext);
    }
    if (type == TeardownStorage.class) {
      return TeardownStorage.getOrCreate(extensionContext);
    }
    if (type == SauceDemoFixtures.class) {
      return SauceDemoFixtures.from(io.bookwright.config.Configs.main());
    }
    if (type == LocalUserFixtures.class) {
      return LocalUserFixtures.from(io.bookwright.config.Configs.main());
    }
    if (type == HotelDatabaseFixtures.class) {
      return HotelDatabaseFixtures.seeded();
    }
    if (type == SemaphoreEncryptionRotationFixtures.class) {
      return SemaphoreEncryptionRotationFixtures.standard();
    }
    if (type == SemaphoreBackupFixtures.class) {
      return SemaphoreBackupFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreBuildDeployFixtures.class) {
      return SemaphoreBuildDeployFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreConcurrencyFixtures.class) {
      return SemaphoreConcurrencyFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreFixtures.class) {
      return SemaphoreFixtures.from(
          io.bookwright.config.Configs.main(), TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreFileInventoryFixtures.class) {
      return SemaphoreFileInventoryFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreHttpsGitFixtures.class) {
      return SemaphoreHttpsGitFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreIntegrationFixtures.class) {
      return SemaphoreIntegrationFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreLdapFixtures.class) {
      return SemaphoreLdapFixtures.standard();
    }
    if (type == SemaphoreLoginSecurityFixtures.class) {
      return SemaphoreLoginSecurityFixtures.from(
          io.bookwright.config.Configs.main(), TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreOidcFixtures.class) {
      return SemaphoreOidcFixtures.standard();
    }
    if (type == SemaphoreProjectDeletionFixtures.class) {
      return SemaphoreProjectDeletionFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreRunnerRoutingFixtures.class) {
      return SemaphoreRunnerRoutingFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreScheduleFixtures.class) {
      return SemaphoreScheduleFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreShellOutputFixtures.class) {
      return SemaphoreShellOutputFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreSshFixtures.class) {
      return SemaphoreSshFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreStaticInventoryFixtures.class) {
      return SemaphoreStaticInventoryFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreSurveyFixtures.class) {
      return SemaphoreSurveyFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreTerraformFixtures.class) {
      return SemaphoreTerraformFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreTotpFixtures.class) {
      return SemaphoreTotpFixtures.standard();
    }
    if (type == SemaphoreTokenFixtures.class) {
      return SemaphoreTokenFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreUpgradeFixtures.class) {
      return SemaphoreUpgradeFixtures.standard();
    }
    if (type == SemaphoreUserLifecycleFixtures.class) {
      return SemaphoreUserLifecycleFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == SemaphoreVariableGroupFixtures.class) {
      return SemaphoreVariableGroupFixtures.from(TestDataExtension.getOrCreate(extensionContext));
    }
    if (type == TestUser.class) {
      return UserFixtureExtension.require(extensionContext);
    }
    if (type == UiSteps.class) {
      // Fresh browser context per UI test; closed when the method store closes
      NamespaceRegistry.methodStore(extensionContext)
          .getOrComputeIfAbsent(
              "browser-context-cleanup", key -> (AutoCloseable) BrowserManager::closeContext);
      // Browser and Playwright are reused within a class and closed after it.
      NamespaceRegistry.classStore(extensionContext)
          .getOrComputeIfAbsent(
              "browser-session-cleanup-" + Thread.currentThread().threadId(),
              key -> BrowserManager.sessionResource(),
              AutoCloseable.class);
    }
    return injectorFor(type, extensionContext).getInstance(type);
  }

  static Injector injectorFor(Class<?> stepsType, ExtensionContext context) {
    ExtensionContext.Store store = NamespaceRegistry.methodStore(context);
    return store.getOrComputeIfAbsent(
        "guice-injector-" + stepsType.getSimpleName(),
        key -> Guice.createInjector(moduleFor(stepsType, context)),
        Injector.class);
  }

  private static Module moduleFor(Class<?> stepsType, ExtensionContext context) {
    if (stepsType == ApiSteps.class) {
      return new ApiModule(TeardownStorage.getOrCreate(context));
    }
    if (stepsType == DbSteps.class) {
      return new DbModule(TeardownStorage.getOrCreate(context));
    }
    if (stepsType == UiSteps.class) {
      return new UiModule(context);
    }
    throw new IllegalArgumentException("Unsupported steps facade: " + stepsType.getName());
  }
}
