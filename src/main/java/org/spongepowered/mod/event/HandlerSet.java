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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.util.event.Order;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

class HandlerSet {

    private final Set<RegisteredEvent> handlers = Sets.newHashSet();
    private EnumMap<Order, List<Handler>> orderGrouped = bakeOrderGrouping();

    private EnumMap<Order, List<Handler>> bakeOrderGrouping() {
        EnumMap<Order, List<Handler>> orderGrouped = Maps.newEnumMap(Order.class);
        for (Order order : Order.values()) {
            orderGrouped.put(order, new ArrayList<Handler>());
        }
        for (OrderedHandler tuple : handlers) {
            orderGrouped.get(tuple.getOrder()).add(tuple.getHandler());
        }
        return orderGrouped;
    }

    public boolean register(Handler handler, Order order, PluginContainer container) {
        checkNotNull(handler, "handler");
        checkNotNull(order, "order");
        checkNotNull(container, "container");

        synchronized (handlers) {
            if (handlers.add(new RegisteredEvent(handler, order, container))) {
                orderGrouped = bakeOrderGrouping();
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean remove(Handler handler) {
        checkNotNull(handler, "handler");

        synchronized (handlers) {
            if (handlers.remove(new RegisteredEvent(handler))) {
                orderGrouped = bakeOrderGrouping();
                return true;
            } else {
                return false;
            }
        }
    }

    public List<Handler> getImmutable(Order order) {
        return orderGrouped.get(order);
    }

    private static class RegisteredEvent extends OrderedHandler {
        private final PluginContainer container;

        private RegisteredEvent(Handler handler) {
            this(handler, Order.DEFAULT, null);
        }

        private RegisteredEvent(Handler handler, Order order, PluginContainer container) {
            super(handler, order);
            this.container = container;
        }

        public PluginContainer getContainer() {
            return container;
        }
    }

}
