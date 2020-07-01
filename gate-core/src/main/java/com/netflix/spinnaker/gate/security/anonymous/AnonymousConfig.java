package com.netflix.spinnaker.gate.security.anonymous;

import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig;
import com.netflix.spinnaker.gate.services.CredentialsService;
import com.netflix.spinnaker.security.User;
import java.util.*;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

/**
 * Requires auth.anonymous.enabled to be true in Fiat configs to work properly. This is because
 * anonymous users are a special permissions case, because the "user" doesn't actually exist in the
 * backing UserRolesProvider.
 */
@ConditionalOnMissingBean(annotation = SpinnakerAuthConfig.class)
@Configuration
@EnableWebSecurity
@Order(Ordered.LOWEST_PRECEDENCE)
public class AnonymousConfig extends WebSecurityConfigurerAdapter {

  private static final Logger log = LoggerFactory.getLogger(AnonymousConfig.class);

  private static String KEY = "spinnaker-anonymous";
  private static String DEFAULT_EMAIL = "anonymous";

  private CredentialsService credentialsService;
  private FiatStatus fiatStatus;

  private List<String> anonymousAllowedAccounts = new ArrayList<>();

  public AnonymousConfig(CredentialsService credentialsService, FiatStatus fiatStatus) {
    this.credentialsService = credentialsService;
    this.fiatStatus = fiatStatus;
  }

  @SneakyThrows
  public void configure(HttpSecurity http) {
    updateAnonymousAccounts();
    // Not using the ImmutableUser version in order to update allowedAccounts.
    User principal = new User();
    principal.setEmail(DEFAULT_EMAIL);
    principal.setAllowedAccounts(anonymousAllowedAccounts);

    http.anonymous().key(KEY).principal(principal).and().csrf().disable();
  }

  @Scheduled(fixedDelay = 60_000L)
  public void updateAnonymousAccounts() {
    if (fiatStatus.isEnabled()) {
      return;
    }

    try {
      List<String> newAnonAccounts =
          (List<String>)
              Optional.ofNullable(credentialsService.getAccountNames(Collections.emptyList()))
                  .orElseGet(ArrayList::new);

      List<String> toAdd = new ArrayList<>(newAnonAccounts);
      toAdd.removeAll(anonymousAllowedAccounts);

      List<String> toRemove = new ArrayList<>(anonymousAllowedAccounts);
      toRemove.removeAll(newAnonAccounts);

      anonymousAllowedAccounts.removeAll(toRemove);
      anonymousAllowedAccounts.addAll(toAdd);
    } catch (Exception e) {
      log.warn("Error while updating anonymous accounts", e);
    }
  }
}
