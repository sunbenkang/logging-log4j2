/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */

package org.apache.logging.log4j.core.config.di.impl;

import org.apache.logging.log4j.core.config.di.BeanManager;
import org.apache.logging.log4j.core.config.di.InitializationContext;
import org.apache.logging.log4j.core.config.di.InitializationException;
import org.apache.logging.log4j.core.config.di.InjectionPoint;
import org.apache.logging.log4j.core.config.di.Injector;
import org.apache.logging.log4j.plugins.di.Disposes;
import org.apache.logging.log4j.plugins.util.AnnotationUtil;
import org.apache.logging.log4j.plugins.util.TypeUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.Optional;

public class DefaultInjector implements Injector {
    private final BeanManager beanManager;

    public DefaultInjector(final BeanManager beanManager) {
        this.beanManager = beanManager;
    }

    @Override
    public <T> T construct(final Constructor<T> constructor, final Collection<InjectionPoint> points, final InitializationContext<T> context) {
        try {
            return constructor.newInstance(createArguments(constructor.getParameters(), points, context, null));
        } catch (final IllegalAccessException | InstantiationException e) {
            throw new InitializationException("Error invoking constructor " + constructor, e);
        } catch (final InvocationTargetException e) {
            throw new InitializationException("Error invoking constructor " + constructor, e.getCause());
        }
    }

    @Override
    public <D, T> T produce(final D producerInstance, final Method producerMethod, final Collection<InjectionPoint> points, final InitializationContext<D> context) {
        try {
            return TypeUtil.cast(producerMethod.invoke(producerInstance, createArguments(producerMethod.getParameters(), points, context, null)));
        } catch (IllegalAccessException e) {
            throw new InitializationException("Error producing instance via " + producerMethod.getName(), e);
        } catch (InvocationTargetException e) {
            throw new InitializationException("Error producing instance via " + producerMethod.getName(), e.getCause());
        }
    }

    @Override
    public <T> void dispose(final T disposerInstance, final Method disposerMethod, final Collection<InjectionPoint> points, final Object instance, final InitializationContext<T> context) {
        try {
            disposerMethod.invoke(disposerInstance, createArguments(disposerMethod.getParameters(), points, context, instance));
        } catch (IllegalAccessException e) {
            throw new InitializationException("Error disposing instance via " + disposerMethod.getName(), e);
        } catch (InvocationTargetException e) {
            throw new InitializationException("Error disposing instance via " + disposerMethod.getName(), e.getCause());
        }
    }

    @Override
    public <T> void invoke(final T instance, final Method method, final Collection<InjectionPoint> points, final InitializationContext<T> context) {
        try {
            method.invoke(instance, createArguments(method.getParameters(), points, context, null));
        } catch (IllegalAccessException e) {
            throw new InitializationException("Error invoking injection method " + method.getName(), e);
        } catch (InvocationTargetException e) {
            throw new InitializationException("Error invoking injection method " + method.getName(), e.getCause());
        }
    }

    @Override
    public <D, T> void set(final D instance, final Field field, final InjectionPoint point, final InitializationContext<D> context) {
        final Optional<T> optionalValue = beanManager.getInjectableValue(point, context);
        optionalValue.ifPresent(value -> {
            try {
                field.set(instance, value);
            } catch (IllegalAccessException e) {
                throw new InitializationException("Error injecting value to field " + field.getName(), e);
            }
        });
    }

    private Object[] createArguments(final Parameter[] parameters, final Collection<InjectionPoint> injectionPoints,
                                     final InitializationContext<?> context, final Object producedInstance) {
        final Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            final Parameter parameter = parameters[i];
            if (AnnotationUtil.isAnnotationPresent(parameter, Disposes.class)) {
                arguments[i] = producedInstance;
            } else {
                final InjectionPoint injectionPoint = injectionPoints.stream()
                        .filter(point -> parameter.equals(point.getElement()))
                        .findAny()
                        .orElseThrow();
                arguments[i] = beanManager.getInjectableValue(injectionPoint, context)
                        .orElseThrow(() -> new UnsupportedOperationException("TODO: primitives and defaults"));
            }
        }
        return arguments;
    }
}
