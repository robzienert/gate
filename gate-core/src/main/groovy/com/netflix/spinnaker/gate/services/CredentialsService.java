package com.netflix.spinnaker.gate.services;

import static com.netflix.spinnaker.fiat.model.Authorization.WRITE;

import com.google.common.collect.Sets;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CredentialsService {

  private AccountLookupService accountLookupService;
  private FiatStatus fiatStatus;

  public CredentialsService(AccountLookupService accountLookupService, FiatStatus fiatStatus) {
    this.accountLookupService = accountLookupService;
    this.fiatStatus = fiatStatus;
  }

  public Collection<String> getAccountNames(Collection<String> userRoles) {
    return getAccountNames(userRoles, false);
  }

  public Collection<String> getAccountNames(
      Collection<String> userRoles, boolean ignoreFiatStatus) {
    return getAccounts(userRoles, ignoreFiatStatus).stream()
        .map(ClouddriverService.Account::getName)
        .collect(Collectors.toList());
  }

  /** Returns all account names that a user with the specified list of userRoles has access to. */
  public List<ClouddriverService.AccountDetails> getAccounts(
      Collection<String> userRoles, final boolean ignoreFiatStatus) {
    final Set<String> userRolesLower =
        userRoles.stream().map(String::toLowerCase).collect(Collectors.toSet());

    return accountLookupService.getAccounts().stream()
        .filter(
            account -> {
              if (!ignoreFiatStatus && fiatStatus.isEnabled()) {
                return true;
              }
              if (account.getPermissions() == null || account.getPermissions().isEmpty()) {
                return true;
              }

              Set<String> permissions =
                  account.getPermissions().getOrDefault(WRITE.toString(), Collections.emptySet())
                      .stream()
                      .map(String::toLowerCase)
                      .collect(Collectors.toSet());
              return !Sets.intersection(userRolesLower, permissions).isEmpty();
            })
        .collect(Collectors.toList());
  }
}
