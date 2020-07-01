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

import com.google.common.base.Strings;
import java.net.URI;
import java.util.regex.Pattern;
import lombok.SneakyThrows;

public class GateOriginValidator implements OriginValidator {

  private final URI deckUri;
  private final Pattern redirectHosts;
  private final Pattern allowedOrigins;
  private final boolean expectLocalhost;

  @SneakyThrows
  public GateOriginValidator(
      String deckUri,
      String redirectHostsPattern,
      String allowedOriginsPattern,
      boolean expectLocalhost) {
    this.deckUri = Strings.isNullOrEmpty(deckUri) ? null : new URI(deckUri);
    this.redirectHosts =
        Strings.isNullOrEmpty(redirectHostsPattern) ? null : Pattern.compile(redirectHostsPattern);
    this.allowedOrigins =
        Strings.isNullOrEmpty(allowedOriginsPattern)
            ? null
            : Pattern.compile(allowedOriginsPattern);
    this.expectLocalhost = expectLocalhost;
  }

  public boolean isExpectedOrigin(String origin) {
    if (Strings.isNullOrEmpty(origin) || deckUri == null) {
      return false;
    }

    try {
      URI uri = URI.create(origin);
      if (Strings.isNullOrEmpty(uri.getScheme()) && Strings.isNullOrEmpty(uri.getHost())) {
        return false;
      }

      if (expectLocalhost && uri.getHost().equalsIgnoreCase("localhost")) {
        return true;
      }

      return deckUri.getScheme().equals(uri.getScheme())
          && deckUri.getHost().equals(uri.getHost())
          && deckUri.getPort() == uri.getPort();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isValidOrigin(String origin) {
    if (Strings.isNullOrEmpty(origin)) {
      return false;
    }

    try {
      URI uri = URI.create(origin);
      if (Strings.isNullOrEmpty(uri.getScheme()) && Strings.isNullOrEmpty(uri.getHost())) {
        return false;
      }

      if (allowedOrigins != null) {
        return allowedOrigins.matcher(origin).matches();
      }

      if (redirectHosts != null) {
        return redirectHosts.matcher(uri.getHost()).matches();
      }

      if (deckUri == null) {
        return false;
      }

      return deckUri.getScheme().equals(uri.getScheme())
          && deckUri.getHost().equals(uri.getHost())
          && deckUri.getPort() == uri.getPort();
    } catch (Exception e) {
      return false;
    }
  }
}
