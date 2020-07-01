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

package com.netflix.spinnaker.gate.filters;

import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;

public class FiatSessionFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(FiatSessionFilter.class);

  private boolean enabled;
  private FiatStatus fiatStatus;
  private FiatPermissionEvaluator permissionEvaluator;

  public FiatSessionFilter(
      boolean enabled, FiatStatus fiatStatus, FiatPermissionEvaluator permissionEvaluator) {
    this.enabled = enabled;
    this.fiatStatus = fiatStatus;
    this.permissionEvaluator = permissionEvaluator;
  }

  /**
   * This filter checks if the user has an entry in Fiat, and if not, forces them to re-login. This
   * is handy for (re)populating the Fiat user repo for a deployment with existing users & sessions.
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    UserPermission.View fiatPermission = null;

    if (fiatStatus.isEnabled() && this.enabled) {
      final String user = AuthenticatedRequest.getSpinnakerUser().orElse(null);
      log.debug("Fiat session filter - found user: " + user);

      if (user != null) {
        fiatPermission = permissionEvaluator.getPermission(user);
        if (fiatPermission == null) {
          HttpServletRequest httpReq = (HttpServletRequest) request;
          HttpSession session = httpReq.getSession(false);
          if (session != null) {
            log.info(
                "Invalidating user '{}' session '{}' because Fiat permission was not found.",
                StructuredArguments.value("user", user),
                StructuredArguments.value("session", session));
            session.invalidate();
            SecurityContextHolder.clearContext();
          }
        }

      } else {
        log.warn(
            "Authenticated user was not present in authenticated request. Check authentication settings.");
      }

    } else {
      if (log.isDebugEnabled()) {
        log.debug(
            "Skipping Fiat session filter: Both `services.fiat.enabled` ({}) and the FiatSessionFilter ({}) need to be enabled.",
            fiatStatus.isEnabled(),
            enabled);
      }
    }

    try {
      chain.doFilter(request, response);
    } finally {
      if (fiatPermission != null && fiatPermission.isLegacyFallback()) {
        log.info("Invalidating fallback permissions for " + fiatPermission.getName());
        permissionEvaluator.invalidatePermission(fiatPermission.getName());
      }
    }
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void destroy() {}
}
