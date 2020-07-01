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

import static net.logstash.logback.argument.StructuredArguments.*;

import com.netflix.spinnaker.gate.config.Headers;
import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CorsFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(CorsFilter.class);

  private final OriginValidator originValidator;

  public CorsFilter(OriginValidator originValidator) {
    this.originValidator = originValidator;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    HttpServletResponse response = (HttpServletResponse) res;
    HttpServletRequest request = (HttpServletRequest) req;
    String origin = request.getHeader("Origin");
    if (!originValidator.isValidOrigin(origin)) {
      origin = "*";
    } else if (!originValidator.isExpectedOrigin(origin)) {
      log.debug(
          "CORS request with full authentication support from non-default origin header. Request Method: {}. Origin header: {}.",
          kv("requestMethod", request.getMethod()),
          kv("origin", origin));
    }

    response.setHeader("Access-Control-Allow-Credentials", "true");
    response.setHeader("Access-Control-Allow-Origin", origin);
    response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT, PATCH");
    response.setHeader("Access-Control-Max-Age", "3600");
    response.setHeader(
        "Access-Control-Allow-Headers",
        "x-requested-with, content-type, authorization, X-RateLimit-App, X-Spinnaker-Priority");
    response.setHeader(
        "Access-Control-Expose-Headers", Headers.AUTHENTICATION_REDIRECT_HEADER_NAME);
    chain.doFilter(req, res);
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void destroy() {}
}
