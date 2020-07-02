/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService.AccountDetails;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import groovy.util.logging.Slf4j;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** DefaultProviderLookupService. */
@Slf4j
@Component("providerLookupService")
class DefaultProviderLookupService implements ProviderLookupService, AccountLookupService {

  private static final Logger log = LoggerFactory.getLogger(DefaultProviderLookupService.class);

  private static final String FALLBACK = "unknown";
  private static final TypeReference<List<Map>> JSON_LIST = new TypeReference<List<Map>>() {};
  private static final TypeReference<List<AccountDetails>> ACCOUNT_DETAILS_LIST =
      new TypeReference<List<AccountDetails>>() {};

  private final ClouddriverService clouddriverService;
  private final ObjectMapper mapper = new ObjectMapper();

  private final AtomicReference<List<AccountDetails>> accountsCache =
      new AtomicReference<>(new ArrayList<>());

  @Autowired
  DefaultProviderLookupService(ClouddriverService clouddriverService) {
    this.clouddriverService = clouddriverService;
  }

  @Scheduled(fixedDelay = 30_000L)
  void refreshCache() {
    try {
      List<ClouddriverService.AccountDetails> accounts =
          AuthenticatedRequest.allowAnonymous(clouddriverService::getAccountDetails);

      // migration support, prefer permissions configuration, translate requiredGroupMembership
      // (for credentialsservice in non fiat mode) into permissions collection.
      //
      // Ignore explicitly set requiredGroupMemberships if permissions are also present.
      accounts.forEach(
          account -> {
            if (account.getPermissions() != null) {
              account.setPermissions(
                  account.getPermissions().entrySet().stream()
                      .peek(
                          permission -> {
                            Set<String> rolesLower = toLowerCase(permission.getValue().stream());
                            permission.setValue(rolesLower);
                          })
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
              if (account.getRequiredGroupMembership() != null
                  && !account.getRequiredGroupMembership().isEmpty()) {
                Set<String> rgmSet = toLowerCase(account.getRequiredGroupMembership().stream());
                if (account.getPermissions().get("WRITE") != rgmSet) {
                  log.warn(
                      "on Account {}: preferring permissions: {} over requiredGroupMemberships: {} for authz decision",
                      account.getName(),
                      account.getPermissions(),
                      rgmSet);
                }
              }
            } else {
              account.setRequiredGroupMembership(
                  toLowerCase(account.getRequiredGroupMembership().stream()));
              if (!account.getRequiredGroupMembership().isEmpty()) {
                Map<String, Collection<String>> perms = new HashMap<>();
                perms.put("READ", account.getRequiredGroupMembership());
                perms.put("WRITE", account.getRequiredGroupMembership());
                account.setPermissions(perms);
              } else {
                account.setPermissions(new HashMap<>());
              }
            }
          });
      accountsCache.set(accounts);
    } catch (Exception e) {
      log.error("Unable to refresh account details cache", e);
    }
  }

  @Override
  public String providerForAccount(String account) {
    return Optional.ofNullable(accountsCache.get())
        .map(
            cache ->
                cache.stream()
                    .filter(it -> it.getName().equals(account))
                    .findFirst()
                    .map(ClouddriverService.Account::getType)
                    .orElse(FALLBACK))
        .orElse(FALLBACK);
  }

  @Override
  public List<AccountDetails> getAccounts() {
    final List<AccountDetails> original = accountsCache.get();
    final List<Map> accountsCopy = mapper.convertValue(original, JSON_LIST);
    return mapper.convertValue(accountsCopy, ACCOUNT_DETAILS_LIST);
  }

  private static Set<String> toLowerCase(Stream<String> stream) {
    return stream.map(String::toLowerCase).collect(Collectors.toSet());
  }
}
