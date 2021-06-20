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

import org.apache.logging.log4j.core.config.di.Bean;
import org.apache.logging.log4j.core.config.di.BeanManager;
import org.apache.logging.log4j.core.config.di.DefinitionException;
import org.apache.logging.log4j.core.config.di.InjectionPoint;
import org.apache.logging.log4j.core.config.di.InjectionTarget;
import org.apache.logging.log4j.core.config.di.InjectionTargetFactory;
import org.apache.logging.log4j.core.config.di.Injector;
import org.apache.logging.log4j.plugins.di.Inject;
import org.apache.logging.log4j.plugins.di.PostConstruct;
import org.apache.logging.log4j.plugins.di.PreDestroy;
import org.apache.logging.log4j.plugins.util.AnnotationUtil;
import org.apache.logging.log4j.plugins.util.TypeUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

class DefaultInjectionTargetFactory<T> implements InjectionTargetFactory<T> {
    private final BeanManager beanManager;
    private final Injector injector;
    private final Class<T> type;

    DefaultInjectionTargetFactory(final BeanManager beanManager, final Injector injector, final Class<T> type) {
        this.beanManager = beanManager;
        this.injector = injector;
        this.type = type;
    }

    @Override
    public InjectionTarget<T> createInjectionTarget(final Bean<T> bean) {
        final Constructor<T> constructor = getInjectableConstructor();
        final Collection<InjectionPoint> injectionPoints =
                new HashSet<>(beanManager.createExecutableInjectionPoints(constructor, bean));
        for (final Field field : type.getDeclaredFields()) {
            if (beanManager.isInjectable(field)) {
                // TODO: if field is static, validate it's using an appropriate scope (singleton?)
                field.setAccessible(true);
                injectionPoints.add(beanManager.createFieldInjectionPoint(field, bean));
            }
        }
        final List<Method> methods = new ArrayList<>();
        for (final Method method : type.getDeclaredMethods()) {
            methods.add(0, method);
            if (!Modifier.isStatic(method.getModifiers()) && beanManager.isInjectable(method)) {
                method.setAccessible(true);
                injectionPoints.addAll(beanManager.createExecutableInjectionPoints(method, bean));
            }
        }
        // FIXME: verify these methods are ordered properly
        final List<Method> postConstructMethods = methods.stream()
                .filter(method -> AnnotationUtil.isAnnotationPresent(method, PostConstruct.class))
                .peek(method -> method.setAccessible(true))
                .collect(Collectors.toList());
        final List<Method> preDestroyMethods = methods.stream()
                .filter(method -> AnnotationUtil.isAnnotationPresent(method, PreDestroy.class))
                .peek(method -> method.setAccessible(true))
                .collect(Collectors.toList());
        return new DefaultInjectionTarget<>(injector, type, injectionPoints, constructor,
                postConstructMethods, preDestroyMethods);
    }

    private Constructor<T> getInjectableConstructor() {
        final Constructor<?>[] allConstructors = type.getDeclaredConstructors();
        final List<Constructor<?>> injectConstructors = Arrays.stream(allConstructors)
                .filter(constructor -> AnnotationUtil.isAnnotationPresent(constructor, Inject.class))
                .collect(Collectors.toList());
        if (injectConstructors.size() > 1) {
            throw new DefinitionException("Found more than one constructor with @Inject for " + type);
        }
        if (injectConstructors.size() == 1) {
            final Constructor<?> constructor = injectConstructors.get(0);
            constructor.setAccessible(true);
            return TypeUtil.cast(constructor);
        }
        final List<Constructor<?>> injectParameterConstructors = Arrays.stream(allConstructors)
                .filter(constructor -> Arrays.stream(constructor.getParameters()).anyMatch(beanManager::isInjectable))
                .collect(Collectors.toList());
        if (injectParameterConstructors.size() > 1) {
            throw new DefinitionException("No @Inject constructors found and remaining constructors ambiguous for " + type);
        }
        if (injectParameterConstructors.size() == 1) {
            final Constructor<?> constructor = injectParameterConstructors.get(0);
            constructor.setAccessible(true);
            return TypeUtil.cast(constructor);
        }
        if (allConstructors.length == 1) {
            final Constructor<?> constructor = allConstructors[0];
            if (constructor.getParameterCount() == 0) {
                constructor.setAccessible(true);
                return TypeUtil.cast(constructor);
            }
        }
        try {
            return type.getDeclaredConstructor();
        } catch (final NoSuchMethodException ignored) {
            throw new DefinitionException("No candidate constructors found for " + type);
        }
    }
}
