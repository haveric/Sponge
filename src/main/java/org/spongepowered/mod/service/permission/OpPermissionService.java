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
package org.spongepowered.mod.service.permission;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.api.service.permission.*;
import org.spongepowered.api.service.permission.context.Context;
import org.spongepowered.api.service.permission.context.ContextCalculator;
import org.spongepowered.api.util.Tristate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Permission service representing the vanilla operator permission structure
 */
public class OpPermissionService implements PermissionService {
    private final List<ContextCalculator> calcs = new CopyOnWriteArrayList<ContextCalculator>();
    private final OpsListUserCollection users = new OpsListUserCollection(this);
    private final MemorySubjectData defaultData = new MemorySubjectData(this);

    public OpPermissionService() {
        for (Object obj : MinecraftServer.getServer().getCommandManager().getCommands().values()) {
            if (!(obj instanceof ICommand)) {
                throw new IllegalStateException("All minecraft builtin commands must be ICommands");
            }
            ICommand command = (ICommand) obj;
            int opLevel = command instanceof CommandBase ? ((CommandBase) command).getRequiredPermissionLevel() : 4;
            getGroupForOpLevel(opLevel).getTransientData().setPermission(Collections.<Context>emptySet(), permissionForMCCommand(command), Tristate.TRUE);
        }
        getDefaultData().addParent(Collections.<Context>emptySet(), getGroupForOpLevel(0));
    }

    public static String permissionForMCCommand(ICommand command) {
        return "minecraft.command." + command.getCommandName();
    }

    Subject getGroupForOpLevel(int level) {
        return getGroupSubjects().get("op_" + level);
    }

    @Override
    public OpsListUserCollection getUserSubjects() {
        return users;
    }

    @Override
    public SubjectCollection getGroupSubjects() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public SubjectData getDefaultData() {
        return defaultData;
    }

    @Override
    public void registerContextCalculator(ContextCalculator calculator) {
        calcs.add(calculator);
    }

    List<ContextCalculator> getContextCalculators() {
        return calcs;
    }

    @Override
    public Optional<SubjectCollection> getSubjects(String identifier) {
        if (SUBJECTS_USER.equals(identifier)) {
            return Optional.<SubjectCollection>of(getUserSubjects());
        } else if (SUBJECTS_GROUP.equals(identifier)) {
            return Optional.of(getGroupSubjects());
        }
        return Optional.absent();
    }

    @Override
    public Map<String, SubjectCollection> getKnownSubjects() {
        return ImmutableMap.of(PermissionService.SUBJECTS_USER, getUserSubjects(), PermissionService.SUBJECTS_GROUP, getGroupSubjects());
    }
}
