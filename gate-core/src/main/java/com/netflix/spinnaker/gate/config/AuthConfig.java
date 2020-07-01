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

package com.netflix.spinnaker.gate.config;

import static com.netflix.spinnaker.gate.config.AuthConfig.PermissionRevokingLogoutSuccessHandler.LOGGED_OUT_URL;

import com.google.common.base.Strings;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.gate.filters.FiatSessionFilter;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.gate.services.ServiceAccountFilterConfigProps;
import com.netflix.spinnaker.security.User;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Component;

@Configuration
@EnableConfigurationProperties({ServiceConfiguration.class, ServiceAccountFilterConfigProps.class})
public class AuthConfig {

  @Autowired private PermissionRevokingLogoutSuccessHandler permissionRevokingLogoutSuccessHandler;

  @Autowired private FiatStatus fiatStatus;

  @Autowired private FiatPermissionEvaluator permissionEvaluator;

  @Autowired private RequestMatcherProvider requestMatcherProvider;

  @Value("${security.debug:false}")
  private boolean securityDebug;

  @Value("${fiat.session-filter.enabled:true}")
  private boolean fiatSessionFilterEnabled;

  public void configure(HttpSecurity http) throws Exception {
    http.requestMatcher(requestMatcherProvider.requestMatcher())
        .authorizeRequests()
        .antMatchers("/**/favicon.ico")
        .permitAll()
        .antMatchers(HttpMethod.OPTIONS, "/**")
        .permitAll()
        .antMatchers(LOGGED_OUT_URL)
        .permitAll()
        .antMatchers("/auth/user")
        .permitAll()
        .antMatchers("/plugins/deck/**")
        .permitAll()
        .antMatchers(HttpMethod.POST, "/webhooks/**")
        .permitAll()
        .antMatchers(HttpMethod.POST, "/notifications/callbacks/**")
        .permitAll()
        .antMatchers("/health")
        .permitAll()
        .antMatchers("/**")
        .authenticated();

    if (fiatSessionFilterEnabled) {
      Filter fiatSessionFilter = new FiatSessionFilter(true, fiatStatus, permissionEvaluator);
      http.addFilterBefore(fiatSessionFilter, AnonymousAuthenticationFilter.class);
    }

    http.logout()
        .logoutUrl("/auth/logout")
        .logoutSuccessHandler(permissionRevokingLogoutSuccessHandler)
        .permitAll()
        .and()
        .csrf()
        .disable();
  }

  public void configure(WebSecurity web) {
    web.debug(securityDebug);
  }

  @Component
  public static class PermissionRevokingLogoutSuccessHandler
      implements LogoutSuccessHandler, InitializingBean {
    static final String LOGGED_OUT_URL = "/auth/loggedOut";

    private SimpleUrlLogoutSuccessHandler delegate = new SimpleUrlLogoutSuccessHandler();

    private PermissionService permissionService;

    public PermissionRevokingLogoutSuccessHandler(PermissionService permissionService) {
      this.permissionService = permissionService;
    }

    @Override
    public void afterPropertiesSet() {
      delegate.setDefaultTargetUrl(LOGGED_OUT_URL);
    }

    @Override
    public void onLogoutSuccess(
        HttpServletRequest request, HttpServletResponse response, Authentication authentication)
        throws IOException, ServletException {
      final User user = (User) authentication.getPrincipal();
      String username = (user == null ? null : user.getUsername());
      if (!Strings.isNullOrEmpty(username)) {
        permissionService.logout(username);
      }

      delegate.onLogoutSuccess(request, response, authentication);
    }
  }
}
