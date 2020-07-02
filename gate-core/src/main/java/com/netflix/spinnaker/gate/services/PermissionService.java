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

import com.google.common.base.Strings;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.gate.retrofit.UpstreamBadRequest;
import com.netflix.spinnaker.gate.security.SpinnakerUser;
import com.netflix.spinnaker.gate.services.internal.ExtendedFiatService;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.User;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;
import retrofit.client.Response;

@Component
public class PermissionService {

  private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

  private final FiatService fiatService;
  private final ExtendedFiatService extendedFiatService;
  private final ServiceAccountFilterConfigProps serviceAccountFilterConfigProps;
  private final Optional<FiatService> fiatLoginService;
  private final FiatPermissionEvaluator permissionEvaluator;
  private final FiatStatus fiatStatus;

  public PermissionService(
      FiatService fiatService,
      ExtendedFiatService extendedFiatService,
      ServiceAccountFilterConfigProps serviceAccountFilterConfigProps,
      Optional<FiatService> fiatLoginService,
      FiatPermissionEvaluator permissionEvaluator,
      FiatStatus fiatStatus) {
    this.fiatService = fiatService;
    this.extendedFiatService = extendedFiatService;
    this.serviceAccountFilterConfigProps = serviceAccountFilterConfigProps;
    this.fiatLoginService = fiatLoginService;
    this.permissionEvaluator = permissionEvaluator;
    this.fiatStatus = fiatStatus;
  }

  public boolean isEnabled() {
    return fiatStatus.isEnabled();
  }

  private FiatService getFiatServiceForLogin() {
    return fiatLoginService.orElse(fiatService);
  }

  @SneakyThrows
  public void login(final String userId) {
    if (fiatStatus.isEnabled()) {
      try {
        AuthenticatedRequest.allowAnonymous(
            () -> {
              getFiatServiceForLogin().loginUser(userId, "");
              permissionEvaluator.invalidatePermission(userId);
              return null;
            });
      } catch (RetrofitError e) {
        throw UpstreamBadRequest.classifyError(e);
      }
    }
  }

  @SneakyThrows
  public void loginWithRoles(final String userId, final Collection<String> roles) {
    if (fiatStatus.isEnabled()) {
      try {
        AuthenticatedRequest.allowAnonymous(
            () -> {
              getFiatServiceForLogin().loginWithRoles(userId, roles);
              permissionEvaluator.invalidatePermission(userId);
              return null;
            });
      } catch (RetrofitError e) {
        throw UpstreamBadRequest.classifyError(e);
      }
    }
  }

  @SneakyThrows
  public void logout(String userId) {
    if (fiatStatus.isEnabled()) {
      try {
        getFiatServiceForLogin().logoutUser(userId);
        permissionEvaluator.invalidatePermission(userId);
      } catch (RetrofitError e) {
        throw UpstreamBadRequest.classifyError(e);
      }
    }
  }

  @SneakyThrows
  public void sync() {
    if (fiatStatus.isEnabled()) {
      try {
        getFiatServiceForLogin().sync(Collections.emptyList());
      } catch (RetrofitError e) {
        throw UpstreamBadRequest.classifyError(e);
      }
    }
  }

  @SneakyThrows
  public Set<Role.View> getRoles(String userId) {
    if (!fiatStatus.isEnabled()) {
      return new HashSet<>();
    }

    try {
      return Optional.ofNullable(permissionEvaluator.getPermission(userId))
          .map(UserPermission.View::getRoles)
          .orElseGet(HashSet::new);
    } catch (RetrofitError e) {
      throw UpstreamBadRequest.classifyError(e);
    }
  }

  List<UserPermission.View> lookupServiceAccounts(String userId) {
    try {
      return extendedFiatService.getUserServiceAccounts(userId);
    } catch (RetrofitError re) {
      final Response response = re.getResponse();
      boolean notFound = (response == null) || response.getStatus() == HttpStatus.NOT_FOUND.value();
      if (notFound) {
        return new ArrayList<>();
      }

      boolean shouldRetry =
          re.getResponse() == null
              || HttpStatus.valueOf(re.getResponse().getStatus()).is5xxServerError();
      throw new SystemException("getUserServiceAccounts failed", re).setRetryable(shouldRetry);
    }
  }

  public List<String> getServiceAccountsForApplication(
      @SpinnakerUser final User user, @Nonnull final String application) {
    boolean hasRequiredDataForMethod =
        serviceAccountFilterConfigProps.isEnabled()
            && user != null
            && !Strings.isNullOrEmpty(application)
            && fiatStatus.isEnabled()
            && !serviceAccountFilterConfigProps.getMatchAuthorizations().isEmpty();
    if (!hasRequiredDataForMethod) {
      return getServiceAccounts(user);
    }

    List<String> filteredServiceAccounts;
    RetrySupport retry = new RetrySupport();
    try {
      List<UserPermission.View> serviceAccounts =
          retry.retry(
              () -> lookupServiceAccounts(user.getUsername()), 3, Duration.ofMillis(50), false);

      filteredServiceAccounts =
          serviceAccounts.stream()
              .filter(
                  serviceAccount ->
                      serviceAccount.getApplications().stream()
                          .anyMatch(
                              app ->
                                  app.getName().equalsIgnoreCase(application)
                                      && app.getAuthorizations().stream()
                                          .anyMatch(
                                              it ->
                                                  serviceAccountFilterConfigProps
                                                      .getMatchAuthorizations()
                                                      .contains(it))))
              .map(UserPermission.View::getName)
              .collect(Collectors.toList());
    } catch (SpinnakerException se) {
      log.error(
          "failed to lookup user {} service accounts for application {}, falling back to all user service accounts",
          user,
          application,
          se);
      return getServiceAccounts(user);
    }

    // if there are no service accounts for the requested application, fall back to the full list of
    // service accounts for the user
    //  to avoid a chicken and egg problem of trying to enable security for the first time on an
    // application
    return filteredServiceAccounts.isEmpty() ? getServiceAccounts(user) : filteredServiceAccounts;
  }

  @SneakyThrows
  public List<String> getServiceAccounts(@SpinnakerUser User user) {
    if (user == null) {
      log.debug("getServiceAccounts: Spinnaker user is null.");
      return new ArrayList<>();
    }

    if (!fiatStatus.isEnabled()) {
      log.debug("getServiceAccounts: Fiat disabled.");
      return new ArrayList<>();
    }

    try {
      UserPermission.View view = permissionEvaluator.getPermission(user.getUsername());
      return view.getServiceAccounts().stream()
          .map(ServiceAccount.View::getName)
          .collect(Collectors.toList());
    } catch (RetrofitError re) {
      throw UpstreamBadRequest.classifyError(re);
    }
  }

  public boolean isAdmin(String userId) {
    return permissionEvaluator.getPermission(userId).isAdmin();
  }
}
