package org.spongepowered.mod.service.permission;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerProfileCache;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.SubjectCollection;
import org.spongepowered.api.service.permission.context.Context;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * User collection keeping track of opped users
 */
public class OpsListUserCollection implements SubjectCollection {
    private final OpPermissionService service;

    public OpsListUserCollection(OpPermissionService service) {
        this.service = service;
    }

    @Override
    public String getIdentifier() {
        return PermissionService.SUBJECTS_USER;
    }

    @Override
    public Subject get(String identifier) {
        UUID uid = identToUUID(identifier);
        return new MCPlayerSubject(uuidToGameProfile(uid), this); // TODO cache subject objects while users are online
    }

    private GameProfile uuidToGameProfile(UUID uid) {
        PlayerProfileCache cache = MinecraftServer.getServer().getPlayerProfileCache();
        GameProfile profile = cache.func_152652_a(uid); // Get already cached profile by uuid
        if (profile == null) {
            profile = MinecraftServer.getServer().getMinecraftSessionService().fillProfileProperties(new GameProfile(uid, null), false);
            cache.func_152649_a(profile); // Cache newly looked up profile
            cache.func_152658_c(); // Save
        }
        return profile;
    }

    @Override
    public boolean hasRegistered(String identifier) {
        UUID uid = identToUUID(identifier);
        if (uid == null) {
            return false;
        }
        GameProfile profile = uuidToGameProfile(uid);
        return MinecraftServer.getServer().getConfigurationManager().getOppedPlayers().getEntry(profile) != null;
    }

    private UUID identToUUID(String identifier) {
        try {
            return UUID.fromString(identifier);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public Iterable<Subject> getAllSubjects() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public Map<Subject, Boolean> getAllWithPermission(String permission) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public Map<Subject, Boolean> getAllWithPermission(Set<Context> contexts, String permission) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public OpPermissionService getService() {
        return service;
    }
}
