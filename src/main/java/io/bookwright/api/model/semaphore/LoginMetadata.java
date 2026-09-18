package io.bookwright.api.model.semaphore;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record LoginMetadata(
    @JsonProperty("oidc_providers") List<Provider> oidcProviders,
    @JsonProperty("ldap_providers") List<Provider> ldapProviders,
    @JsonProperty("login_with_password") boolean loginWithPassword,
    @JsonProperty("login_with_ldap") boolean loginWithLdap) {

  public LoginMetadata {
    oidcProviders = oidcProviders == null ? List.of() : List.copyOf(oidcProviders);
    ldapProviders = ldapProviders == null ? List.of() : List.copyOf(ldapProviders);
  }

  public record Provider(String id, String name, String color, String icon) {}
}
