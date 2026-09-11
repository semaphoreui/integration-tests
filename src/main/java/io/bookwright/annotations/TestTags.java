package io.bookwright.annotations;

/**
 * Tag values in one place so gradle {@code -DincludeTags=...} and annotations never drift apart.
 */
public final class TestTags {

  public static final String SMOKE = "smoke";
  public static final String REGRESSION = "regression";
  public static final String API = "api";
  public static final String UI = "ui";
  public static final String DB = "db";
  public static final String EXTERNAL = "external";
  public static final String EXTERNAL_MANAGED = "external-managed";
  public static final String EXTERNAL_MANAGED_SETUP = "external-managed-setup";

  private TestTags() {}
}
