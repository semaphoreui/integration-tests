package io.bookwright.steps.semaphore.cache;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.bookwright.api.RetrofitFactory;
import io.bookwright.api.model.semaphore.LoginRequest;
import io.bookwright.api.model.semaphore.User;
import io.bookwright.api.semaphore.cache.CacheProbeResponse;
import io.bookwright.api.semaphore.cache.SemaphoreWebCacheApi;
import io.bookwright.api.semaphore.cache.WebCacheSession;
import io.bookwright.config.MainConfig;
import io.bookwright.util.Calls;
import io.qameta.allure.Step;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import okhttp3.ResponseBody;
import retrofit2.Response;

/** Assertions at the application/shared-cache boundary. */
public class WebCacheSteps {

  private static final String CACHE_STATUS_HEADER = "X-Bookwright-Cache-Status";
  private static final String PROBE_HEADER = "X-Bookwright-Probe";
  private static final Pattern STATIC_ASSET =
      Pattern.compile("(?:src|href)=\\\"([^\\\"]+\\.(?:js|css)(?:\\?[^\\\"]*)?)\\\"");

  private final MainConfig config;
  private final ObjectMapper mapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  @Inject
  public WebCacheSteps(MainConfig config) {
    this.config = config;
  }

  @Step("Verify authenticated Semaphore responses declare a private no-store policy")
  public void verifyAuthenticatedResponsePolicy(LoginRequest account, String probe) {
    CacheProbeResponse response = get(login(config.cacheOriginBaseUrl(), account), "user", probe);
    requireStatus(response, 200, "authenticated user response");
    requirePrivateNoStore(response.cacheControl());
  }

  @Step("Verify a shared cache cannot serve one Semaphore user to another")
  public void verifyUserBoundary(LoginRequest first, LoginRequest second, String probe) {
    WebCacheSession firstSession = login(config.apiBaseUrl(), first);
    WebCacheSession secondSession = login(config.apiBaseUrl(), second);

    requireUser(get(firstSession, "user", probe), first.auth(), "first cache request");
    CacheProbeResponse secondResponse = get(secondSession, "user", probe);
    requireUser(secondResponse, second.auth(), "second cache request");
    requireNotHit(secondResponse, "authenticated response for a different user");
  }

  @Step("Verify unkeyed forwarding headers and query parameters cannot poison a user response")
  public void verifyUnkeyedInputsCannotPoison(
      LoginRequest attacker,
      LoginRequest victim,
      String probe,
      Map<String, String> unkeyedHeaders) {
    WebCacheSession attackerSession = login(config.apiBaseUrl(), attacker);
    WebCacheSession victimSession = login(config.apiBaseUrl(), victim);

    requireUser(
        get(attackerSession, "user?bookwright_variant=attacker", probe, unkeyedHeaders),
        attacker.auth(),
        "unkeyed-header priming request");
    CacheProbeResponse victimResponse = get(victimSession, "user?bookwright_variant=victim", probe);
    requireUser(victimResponse, victim.auth(), "request after unkeyed-header priming");
    requireNotHit(victimResponse, "response primed through unkeyed request inputs");
  }

  @Step("Verify Host is isolated in the shared-cache key")
  public void verifyHostIsKeyed(LoginRequest account, String probe, String poisonedHost) {
    WebCacheSession session = login(config.apiBaseUrl(), account);
    requireUser(
        get(session, "user", probe, Map.of("Host", poisonedHost)),
        account.auth(),
        "poisoned Host request");
    CacheProbeResponse regularHost = get(session, "user", probe);
    requireUser(regularHost, account.auth(), "regular Host request");
    requireNotHit(regularHost, "response primed under a different Host");
  }

  @Step("Verify a versioned public asset remains shared-cacheable")
  public void verifyPublicStaticAssetCacheable(String probe) {
    WebCacheSession anonymous = anonymous();
    CacheProbeResponse shell = get(anonymous, config.uiBaseUrl() + "/", probe);
    requireStatus(shell, 200, "Semaphore SPA shell");
    String asset = findStaticAsset(shell.body());
    String assetUrl = asset.startsWith("http") ? asset : config.uiBaseUrl() + absolutePath(asset);

    CacheProbeResponse first = get(anonymous, assetUrl, probe);
    CacheProbeResponse second = get(anonymous, assetUrl, probe);
    requireStatus(first, 200, "first static asset response");
    requireStatus(second, 200, "second static asset response");
    requirePublicCachePolicy(first.cacheControl(), "versioned static asset");
    if (!"HIT".equals(second.cacheStatus())) {
      throw new IllegalStateException(
          "Expected the repeated public asset request to be a shared-cache HIT but received %s"
              .formatted(display(second.cacheStatus())));
    }
  }

  @Step("Verify the public API specification remains shared-cacheable")
  public void verifyPublicDocumentationCacheable(String path, String probe) {
    WebCacheSession anonymous = anonymous();
    CacheProbeResponse first = get(anonymous, config.uiBaseUrl() + path, probe);
    CacheProbeResponse second = get(anonymous, config.uiBaseUrl() + path, probe);
    requireStatus(first, 200, "first API specification response");
    requireStatus(second, 200, "second API specification response");
    requirePublicCachePolicy(first.cacheControl(), "API specification");
    if (!"HIT".equals(second.cacheStatus())) {
      throw new IllegalStateException(
          "Expected the repeated API specification request to be a shared-cache HIT but received %s"
              .formatted(display(second.cacheStatus())));
    }
  }

  private WebCacheSession anonymous() {
    return new WebCacheSession(
        RetrofitFactory.create(config.apiBaseUrl()).create(SemaphoreWebCacheApi.class));
  }

  private WebCacheSession login(String baseUrl, LoginRequest account) {
    SemaphoreWebCacheApi api = RetrofitFactory.create(baseUrl).create(SemaphoreWebCacheApi.class);
    Calls.expectStatus(api.login(account), 204);
    return new WebCacheSession(api);
  }

  private CacheProbeResponse get(WebCacheSession session, String url, String probe) {
    return get(session, url, probe, Map.of());
  }

  private CacheProbeResponse get(
      WebCacheSession session, String url, String probe, Map<String, String> extraHeaders) {
    Map<String, String> headers = new LinkedHashMap<>(extraHeaders);
    headers.put(PROBE_HEADER, probe);
    Response<ResponseBody> response = Calls.response(session.api().get(url, headers));
    return new CacheProbeResponse(
        response.code(),
        readBody(response),
        response.headers().get(CACHE_STATUS_HEADER),
        response.headers().get("Cache-Control"));
  }

  private void requireUser(CacheProbeResponse response, String expectedUsername, String operation) {
    requireStatus(response, 200, operation);
    User user;
    try {
      user = mapper.readValue(response.body(), User.class);
    } catch (IOException error) {
      throw new IllegalStateException(
          "Could not parse " + operation + " as a Semaphore user", error);
    }
    if (!expectedUsername.equals(user.username())) {
      throw new IllegalStateException(
          "%s crossed the shared-cache user boundary: expected %s but received %s (cache=%s)"
              .formatted(
                  operation, expectedUsername, user.username(), display(response.cacheStatus())));
    }
  }

  private void requirePrivateNoStore(String cacheControl) {
    String normalized = cacheControl == null ? "" : cacheControl.toLowerCase(Locale.ROOT);
    if (!normalized.contains("private") || !normalized.contains("no-store")) {
      throw new IllegalStateException(
          "Authenticated response must declare Cache-Control: private, no-store but received %s"
              .formatted(display(cacheControl)));
    }
  }

  private void requirePublicCachePolicy(String cacheControl, String description) {
    String normalized = cacheControl == null ? "" : cacheControl.toLowerCase(Locale.ROOT);
    if (!normalized.contains("public") || !normalized.contains("max-age")) {
      throw new IllegalStateException(
          "%s must retain an explicit public max-age policy but received %s"
              .formatted(description, display(cacheControl)));
    }
  }

  private void requireNotHit(CacheProbeResponse response, String description) {
    if ("HIT".equals(response.cacheStatus())) {
      throw new IllegalStateException(description + " must not be served from a shared cache");
    }
  }

  private void requireStatus(CacheProbeResponse response, int expected, String description) {
    if (response.status() != expected) {
      throw new IllegalStateException(
          "%s expected HTTP %d but received HTTP %d"
              .formatted(description, expected, response.status()));
    }
  }

  private String findStaticAsset(String html) {
    var match = STATIC_ASSET.matcher(html);
    if (!match.find()) {
      throw new IllegalStateException(
          "Semaphore SPA shell did not reference a JavaScript or CSS asset");
    }
    return match.group(1);
  }

  private String absolutePath(String path) {
    return path.startsWith("/") ? path : "/" + path;
  }

  private String readBody(Response<ResponseBody> response) {
    ResponseBody body = response.body() != null ? response.body() : response.errorBody();
    if (body == null) {
      return "";
    }
    try (body) {
      return body.string();
    } catch (IOException error) {
      throw new IllegalStateException("Could not read cache probe response", error);
    }
  }

  private String display(String value) {
    return value == null || value.isBlank() ? "<absent>" : value;
  }
}
