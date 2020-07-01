package com.netflix.spinnaker.gate.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Any non-anonymous Spinnaker authentication mechanism should have this annotation included on
 * its @Configuration bean.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SpinnakerAuthConfig {}
