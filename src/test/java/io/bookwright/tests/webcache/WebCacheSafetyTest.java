package io.bookwright.tests.webcache;

import io.bookwright.annotations.Api;
import io.bookwright.annotations.OwnerDanil;
import io.bookwright.fixtures.semaphore.SemaphoreWebCacheFixtures;
import io.bookwright.junit.Precondition;
import io.bookwright.junit.Preconditions;
import io.bookwright.steps.ApiSteps;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Api
@OwnerDanil
@Feature("Semaphore web-cache safety")
@Preconditions(Precondition.SEMAPHORE_ADMIN_SESSION)
@EnabledIfSystemProperty(named = "SEMAPHORE_PROFILE", matches = "feature-web-cache-safety")
class WebCacheSafetyTest {

  @Test
  @DisplayName("Authenticated responses declare an explicit private no-store policy")
  void authenticatedResponsesDeclarePrivateNoStore(
      ApiSteps api, SemaphoreWebCacheFixtures fixtures) {
    api.semaphore().users().createDisposable(fixtures.first().request());
    api.semaphore()
        .webCache()
        .verifyAuthenticatedResponsePolicy(fixtures.first().login(), fixtures.policyProbe());
  }

  @Test
  @DisplayName("Shared cache cannot serve one authenticated user to another")
  void sharedCacheDoesNotCrossUserBoundary(ApiSteps api, SemaphoreWebCacheFixtures fixtures) {
    api.semaphore().users().createDisposable(fixtures.first().request());
    api.semaphore().users().createDisposable(fixtures.second().request());
    api.semaphore()
        .webCache()
        .verifyUserBoundary(
            fixtures.first().login(), fixtures.second().login(), fixtures.userBoundaryProbe());
  }

  @Test
  @DisplayName("Unkeyed forwarding headers and query parameters cannot poison a user response")
  void unkeyedInputsCannotPoisonAuthenticatedResponse(
      ApiSteps api, SemaphoreWebCacheFixtures fixtures) {
    api.semaphore().users().createDisposable(fixtures.first().request());
    api.semaphore().users().createDisposable(fixtures.second().request());
    api.semaphore()
        .webCache()
        .verifyUnkeyedInputsCannotPoison(
            fixtures.first().login(),
            fixtures.second().login(),
            fixtures.unkeyedInputProbe(),
            fixtures.unkeyedHeaders());
  }

  @Test
  @DisplayName("Host is isolated in the shared-cache key")
  void hostIsPartOfSharedCacheKey(ApiSteps api, SemaphoreWebCacheFixtures fixtures) {
    api.semaphore().users().createDisposable(fixtures.first().request());
    api.semaphore()
        .webCache()
        .verifyHostIsKeyed(fixtures.first().login(), fixtures.hostProbe(), fixtures.poisonedHost());
  }

  @Test
  @DisplayName("Versioned assets and the API specification remain shared-cacheable")
  void publicResourcesRemainCacheable(ApiSteps api, SemaphoreWebCacheFixtures fixtures) {
    api.semaphore().webCache().verifyPublicStaticAssetCacheable(fixtures.staticAssetProbe());
    api.semaphore()
        .webCache()
        .verifyPublicDocumentationCacheable(
            fixtures.documentationPath(), fixtures.documentationProbe());
  }
}
