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
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.UserListOpsEntry;
import org.spongepowered.api.service.permission.MemorySubjectData;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.SubjectCollection;
import org.spongepowered.api.service.permission.SubjectData;
import org.spongepowered.api.service.permission.context.Context;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.util.command.CommandSource;

import java.util.List;
import java.util.Set;

/**
 * An implementation of vanilla minecraft's 4 op groups
 */
public class MCPlayerSubject implements Subject {
    private final GameProfile player;
    private final MemorySubjectData data;
    private final OpsListUserCollection collection;

    public MCPlayerSubject(GameProfile player, OpsListUserCollection users) {
        this.player = player;
        this.data = new MemorySubjectData(users.getService());
        this.collection = users;
    }

    @Override
    public String getIdentifier() {
        return player.getId().toString();
    }

    @Override
    public Optional<CommandSource> getCommandSource() {
        return Optional.fromNullable((CommandSource) MinecraftServer.getServer().getConfigurationManager().func_177451_a(player.getId()));
    }

    int getOpLevel() {
        // Query op level from server ops list based on player's game profile
        return ((UserListOpsEntry) MinecraftServer.getServer().getConfigurationManager().getOppedPlayers().getEntry(player)).func_152644_a();
    }

    @Override
    public SubjectCollection getContainingCollection() {
        return collection;
    }

    @Override
    public SubjectData getData() {
        return data;
    }

    @Override
    public SubjectData getTransientData() {
        return getData();
    }

    @Override
    public boolean hasPermission(Set<Context> contexts, String permission) {
        return getPermissionValue(contexts, permission) == Tristate.TRUE;
    }


    @Override
    public boolean hasPermission(String permission) {
        return hasPermission(getActiveContexts(), permission);
    }

    @Override
    public Tristate getPermissionValue(Set<Context> contexts, String permission) {
        Tristate res = getData().getPermissions(contexts).get(permission);
        if (res == null) {
            res = getData().getPermissions(SubjectData.GLOBAL_CONTEXT).get(permission);
            if (res == null) {
                res = Tristate.UNDEFINED;
            }
        }
        if (res == Tristate.UNDEFINED) {
            for (Subject parent : getData().getParents(contexts)) {
                Tristate tempRes = parent.getPermissionValue(contexts, permission);
                if (tempRes != Tristate.UNDEFINED) {
                    res = tempRes;
                    break;
                }
            }
        }
        return res;
    }

    @Override
    public boolean isChildOf(Subject parent) {
        return isChildOf(getActiveContexts(), parent);
    }

    @Override
    public boolean isChildOf(Set<Context> contexts, Subject parent) {
        return getData().getParents(contexts).contains(parent);
    }

    @Override
    public List<Subject> getParents() {
        return getParents(getActiveContexts());
    }

    @Override
    public List<Subject> getParents(Set<Context> contexts) {
        return getData().getParents(contexts);
    }

    @Override
    public Set<Context> getActiveContexts() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
