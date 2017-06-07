/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.services.SecurityGroupService
import groovy.transform.InheritConstructors
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/securityGroups")
class SecurityGroupController {

  @Autowired
  SecurityGroupService securityGroupService

  @ApiOperation(value = "Retrieve a list of security groups, grouped by account, cloud provider, and region")
  @RequestMapping(method = RequestMethod.GET)
  Map all(@RequestParam(value = "id", required = false) String id,
          @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    if (id) {
      def result = securityGroupService.getById(id, sourceApp)
      if (result) {
        result
      } else {
        throw new SecurityGroupNotFoundException(id)
      }
    } else {
      securityGroupService.getAll(sourceApp)
    }
  }

  @ApiOperation(value = "Retrieve a list of security groups for a given account, grouped by region")
  @RequestMapping(value = "/{account}", method = RequestMethod.GET)
  Map allByAccount(
      @PathVariable String account,
      @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider,
      @RequestParam(value = "region", required = false) String region,
      @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    securityGroupService.getForAccountAndProviderAndRegion(account, provider, region, sourceApp)
  }

  @ApiOperation(value = "Retrieve a security group's details")
  @RequestMapping(value = "/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  Map getSecurityGroup(
      @PathVariable String account,
      @PathVariable String region,
      @PathVariable String name,
      @RequestParam(value = "provider", defaultValue = "aws", required = false) String provider,
      @RequestParam(value = "vpcId", required = false) String vpcId,
      @RequestHeader(value = "X-RateLimit-App", required = false) String sourceApp) {
    securityGroupService.getSecurityGroup(account, provider, name, region, sourceApp, vpcId)
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @InheritConstructors
  static class SecurityGroupNotFoundException extends RuntimeException {}
}
