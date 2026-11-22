/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.awgconfig.android.core.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be applied to a package, class or method to indicate that all
 * class fields and method parameters and return values in that element are nonnull
 * by default unless overridden.
 *
 * Портировано из wireguard-android/awg-android; зависимость javax.annotation (JSR305)
 * заменена самодостаточной аннотацией с той же семантикой.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD})
public @interface NonNullForAll {
}
