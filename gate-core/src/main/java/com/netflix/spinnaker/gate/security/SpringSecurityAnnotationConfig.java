package com.netflix.spinnaker.gate.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@ConditionalOnBean(annotation = SpinnakerAuthConfig.class)
public class SpringSecurityAnnotationConfig {}
