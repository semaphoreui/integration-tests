package io.bookwright.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;

/** Removes credentials from HTTP diagnostics without modifying the actual request or response. */
public final class SecretSanitizer {

  static final String REDACTED = "[REDACTED]";
  static final String OMITTED_BODY = "[BODY OMITTED]";

  private static final Set<String> SENSITIVE_MARKERS =
      Set.of(
          "authorization",
          "cookie",
          "credential",
          "password",
          "passcode",
          "passwd",
          "passphrase",
          "private_key",
          "recovery_code",
          "signature",
          "token",
          "secret");

  private static final ObjectMapper JSON = new ObjectMapper();

  // URL-only names: ordinary JSON fields such as task state or an error code are not secrets.
  private static final Set<String> AUTH_QUERY_PARAMETERS =
      Set.of(
          "code",
          "state",
          "nonce",
          "session_state",
          "code_verifier",
          "code_challenge",
          "client_assertion",
          "samlrequest",
          "samlresponse",
          "relaystate");

  private SecretSanitizer() {}

  public static String url(HttpUrl url) {
    // User info can hold Basic Auth credentials; fragments can carry OAuth response material.
    HttpUrl.Builder safe = url.newBuilder().username("").password("").fragment(null).query(null);
    // Rebuild from decoded names: replacing a literal name can leave encoded aliases such as
    // %63ode untouched. Iterate each occurrence so duplicate parameters are all sanitized.
    for (int index = 0; index < url.querySize(); index++) {
      String name = url.queryParameterName(index);
      boolean sensitive =
          isSensitive(name)
              || AUTH_QUERY_PARAMETERS.contains(name.toLowerCase(Locale.ROOT).replace('-', '_'));
      safe.addQueryParameter(name, sensitive ? REDACTED : url.queryParameterValue(index));
    }
    return safe.build().toString();
  }

  public static String url(String url) {
    if (url == null) {
      return "[URL OMITTED]";
    }
    try {
      return url(HttpUrl.get(url));
    } catch (IllegalArgumentException e) {
      return "[URL OMITTED]";
    }
  }

  public static String headers(Headers headers) {
    if (headers.size() == 0) {
      return "<none>";
    }
    return headers.names().stream()
        .map(
            name ->
                name
                    + ": "
                    + (isSensitive(name) ? REDACTED : String.join(", ", headers.values(name))))
        .collect(Collectors.joining(System.lineSeparator()));
  }

  public static String body(String body, MediaType mediaType) {
    if (body == null || body.isBlank()) {
      return "<empty>";
    }
    if (mediaType == null) {
      return OMITTED_BODY;
    }

    String subtype = mediaType.subtype().toLowerCase(Locale.ROOT);
    if (subtype.equals("json") || subtype.endsWith("+json")) {
      return sanitizeJson(body);
    }
    if (mediaType.type().equalsIgnoreCase("application")
        && subtype.equals("x-www-form-urlencoded")) {
      return sanitizeForm(body);
    }
    return OMITTED_BODY;
  }

  private static String sanitizeJson(String body) {
    try {
      JsonNode root = JSON.readTree(body);
      redact(root);
      return JSON.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      return OMITTED_BODY;
    }
  }

  private static String sanitizeForm(String body) {
    try {
      return Arrays.stream(body.split("&"))
          .map(SecretSanitizer::sanitizeFormEntry)
          .collect(Collectors.joining("&"));
    } catch (IllegalArgumentException e) {
      return OMITTED_BODY;
    }
  }

  private static String sanitizeFormEntry(String entry) {
    String[] pair = entry.split("=", 2);
    String decodedName = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
    if (!isSensitive(decodedName) || pair.length == 1) {
      return entry;
    }
    return pair[0] + "=" + URLEncoder.encode(REDACTED, StandardCharsets.UTF_8);
  }

  private static void redact(JsonNode node) {
    if (node instanceof ObjectNode object) {
      object
          .properties()
          .forEach(
              entry -> {
                if (isSensitive(entry.getKey()) || isSensitiveValue(entry.getValue())) {
                  object.put(entry.getKey(), REDACTED);
                } else {
                  redact(entry.getValue());
                }
              });
    } else if (node instanceof ArrayNode array) {
      array.forEach(SecretSanitizer::redact);
    }
  }

  private static boolean isSensitive(String name) {
    String normalized = name.toLowerCase(Locale.ROOT).replace('-', '_');
    return SENSITIVE_MARKERS.stream().anyMatch(normalized::contains)
        || normalized.contains("api_key")
        || normalized.contains("apikey");
  }

  private static boolean isSensitiveValue(JsonNode value) {
    return value.isTextual() && value.textValue().startsWith("otpauth://");
  }
}
