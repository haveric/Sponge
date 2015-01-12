/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered.org <http://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.spongepowered.mod.event;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.plugin.PluginManager;
import org.spongepowered.api.service.event.EventManager;
import org.spongepowered.api.util.event.Cancellable;
import org.spongepowered.api.util.event.Event;
import org.spongepowered.api.util.event.Order;
import org.spongepowered.api.util.event.Subscribe;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkNotNull;

public class SpongeEventBus implements EventManager {

    private static final Logger log = LoggerFactory.getLogger(SpongeEventBus.class);

    private final PluginManager pluginManager;
    private final HandlerFactory handlerFactory = new InvokeHandlerFactory();
    private final ConcurrentMap<Class<?>, HandlerSet> handlersByEvent = Maps.newConcurrentMap();
    private final LoadingCache<Class<?>, List<HandlerSet>> handlerHierarchyCache =
            CacheBuilder.newBuilder().build(new CacheLoader<Class<?>, List<HandlerSet>>() {
                @SuppressWarnings("unchecked")
                @Override
                public List<HandlerSet> load(Class<?> key) throws Exception {
                    List<HandlerSet> handlerSets = Lists.newArrayList();
                    Set<Class<?>> types = (Set) TypeToken.of(key).getTypes().rawTypes();
                    synchronized (handlersByEvent) {
                        for (Class<?> type : types) {
                            if (Event.class.isAssignableFrom(type)) {
                                handlerSets.add(getHandlerSet(type));
                            }
                        }
                    }
                    return handlerSets;
                }
            });

    @Inject
    public SpongeEventBus(PluginManager pluginManager) {
        checkNotNull(pluginManager, "pluginManager");
        this.pluginManager = pluginManager;
    }

    private HandlerSet getHandlerSet(Class<?> type) {
        checkNotNull(type, "type");
        @Nullable HandlerSet handlerSets;
        synchronized (handlersByEvent) {
            handlerSets = handlersByEvent.get(type);
            if (handlerSets == null) {
                handlerSets = new HandlerSet();
                handlersByEvent.put(type, handlerSets);
            }
        }
        return handlerSets;
    }

    private List<HandlerSet> getHandlerSetHierarchy(Class<?> type) {
        return handlerHierarchyCache.getUnchecked(type);
    }

    @SuppressWarnings("unchecked")
    private Multimap<Class<?>, OrderedHandler> findAllMethodHandlers(Object object) {
        Multimap<Class<?>, OrderedHandler> handlers = HashMultimap.create();
        Class<?> type = object.getClass();

        for (Method method : type.getMethods()) {
            @Nullable Subscribe subscribe = method.getAnnotation(Subscribe.class);

            if (subscribe != null) {
                Class<?>[] paramTypes = method.getParameterTypes();

                if (isValidHandler(method)) {
                    Class<? extends Event> eventClass = (Class<? extends Event>) paramTypes[0];
                    Handler handler = createHandler(object, method, subscribe.ignoreCancelled());
                    handlers.put(eventClass, new OrderedHandler(handler, subscribe.order()));
                } else {
                    log.warn("The method {} on {} has @{} but has the wrong signature",
                            method, method.getDeclaringClass().getName(), Subscribe.class.getName());
                }
            }
        }

        return handlers;
    }

    public boolean register(Class<?> type, Handler handler, Order order, PluginContainer container) {
        getHandlerSetHierarchy(type); // Build cache early
        return getHandlerSet(type).register(handler, order, container);
    }

    public boolean unregister(Class<?> type, Handler handler) {
        return getHandlerSet(type).remove(handler);
    }

    private Handler createHandler(Object object, Method method, boolean ignoreCancelled) {
        return handlerFactory.createHandler(object, method, ignoreCancelled);
    }

    private void callListener(Handler handler, Event event) {
        try {
            handler.handle(event);
        } catch (Throwable t) {
            log.warn("A handler raised an error when handling an event", t);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void register(Object plugin, Object object) {
        checkNotNull(object, "plugin");
        checkNotNull(object, "object");

        Optional<PluginContainer> container = pluginManager.fromInstance(object);
        if (!container.isPresent()) {
            throw new IllegalArgumentException("The specified object is not a plugin object");
        }

        for (Map.Entry<Class<?>, OrderedHandler> entry : findAllMethodHandlers(object).entries()) {
            register(entry.getKey(), entry.getValue().getHandler(), entry.getValue().getOrder(), container.get());
        }
    }

    @Override
    public void unregister(Object object) {
        checkNotNull(object, "object");

        for (Map.Entry<Class<?>, OrderedHandler> entry : findAllMethodHandlers(object).entries()) {
            unregister(entry.getKey(), entry.getValue().getHandler());
        }
    }

    @Override
    public boolean post(Event event) {
        checkNotNull(event, "event");

        List<HandlerSet> handlerSets = getHandlerSetHierarchy(event.getClass());
        for (Order order : Order.values()) {
            for (HandlerSet handlerSet : handlerSets) {
                for (Handler handler : handlerSet.getImmutable(order)) {
                    callListener(handler, event);
                }
            }
        }

        return event instanceof Cancellable && ((Cancellable) event).isCancelled();
    }

    private static boolean isValidHandler(Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();
        return !Modifier.isStatic(method.getModifiers())
                && !Modifier.isAbstract(method.getModifiers())
                && !Modifier.isInterface(method.getDeclaringClass().getModifiers())
                && method.getReturnType() == void.class
                && paramTypes.length == 1
                && Event.class.isAssignableFrom(paramTypes[0]);
    }

}
