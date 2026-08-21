package io.bookwright.util;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Generates the six-digit, SHA-1 TOTP codes used by Semaphore authenticator enrollment. */
public final class TotpCodes {

  private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  private TotpCodes() {}

  public static String current(String enrollmentUrl) {
    return at(enrollmentUrl, Instant.now());
  }

  static String at(String enrollmentUrl, Instant instant) {
    byte[] secret = decodeBase32(requiredQueryParameter(enrollmentUrl, "secret"));
    byte[] counter = ByteBuffer.allocate(Long.BYTES).putLong(instant.getEpochSecond() / 30).array();

    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(secret, "HmacSHA1"));
      byte[] hash = mac.doFinal(counter);
      int offset = hash[hash.length - 1] & 0x0f;
      int binary =
          ((hash[offset] & 0x7f) << 24)
              | ((hash[offset + 1] & 0xff) << 16)
              | ((hash[offset + 2] & 0xff) << 8)
              | (hash[offset + 3] & 0xff);
      return "%06d".formatted(binary % 1_000_000);
    } catch (GeneralSecurityException error) {
      throw new IllegalStateException("JVM does not provide HmacSHA1 for TOTP", error);
    }
  }

  public static String differentFrom(String passcode) {
    if (passcode == null || !passcode.matches("\\d{6}")) {
      throw new IllegalArgumentException("TOTP passcode must contain exactly six digits");
    }
    char replacement = passcode.charAt(5) == '0' ? '1' : '0';
    return passcode.substring(0, 5) + replacement;
  }

  private static String requiredQueryParameter(String enrollmentUrl, String name) {
    URI uri;
    try {
      uri = URI.create(enrollmentUrl);
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException("TOTP enrollment URL is invalid", error);
    }
    if (!"otpauth".equals(uri.getScheme()) || uri.getRawQuery() == null) {
      throw new IllegalArgumentException("Expected an otpauth enrollment URL with query data");
    }
    return Arrays.stream(uri.getRawQuery().split("&"))
        .map(entry -> entry.split("=", 2))
        .filter(entry -> entry.length == 2 && name.equals(entry[0]))
        .map(entry -> URLDecoder.decode(entry[1], StandardCharsets.UTF_8))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("TOTP enrollment URL has no '" + name + "'"));
  }

  private static byte[] decodeBase32(String encoded) {
    String normalized = encoded.replace("=", "").replace(" ", "").toUpperCase();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("TOTP secret is empty");
    }

    byte[] decoded = new byte[normalized.length() * 5 / 8];
    int buffer = 0;
    int bits = 0;
    int index = 0;
    for (char character : normalized.toCharArray()) {
      int value = BASE32.indexOf(character);
      if (value < 0) {
        throw new IllegalArgumentException("TOTP secret is not valid Base32");
      }
      buffer = (buffer << 5) | value;
      bits += 5;
      if (bits >= 8) {
        decoded[index++] = (byte) (buffer >> (bits - 8));
        bits -= 8;
        buffer &= (1 << bits) - 1;
      }
    }
    return decoded;
  }
}
