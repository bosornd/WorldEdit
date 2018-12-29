/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.forge;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.platform.PlatformReadyEvent;
import com.sk89q.worldedit.extension.platform.Platform;
import com.sk89q.worldedit.forge.net.LeftClickAirEventMessage;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.item.ItemType;
import com.sk89q.worldedit.world.item.ItemTypes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * The Forge implementation of WorldEdit.
 */
@Mod(ForgeWorldEdit.MOD_ID)
public class ForgeWorldEdit {

    private static final Logger LOGGER = LogManager.getLogger();
    public static final String MOD_ID = "worldedit";
    public static final String CUI_PLUGIN_CHANNEL = "worldedit:cui";

    private ForgePermissionsProvider provider;

    public static ForgeWorldEdit inst;

    public static CommonProxy proxy = DistExecutor.runForDist(() -> ClientProxy::new, () -> CommonProxy::new);

    private ForgePlatform platform;
    private ForgeConfiguration config;
    private File workingDir;

    public ForgeWorldEdit() {
        inst = this;

        FMLModLoadingContext.get().getModEventBus().addListener(this::preInit);
        FMLModLoadingContext.get().getModEventBus().addListener(this::init);
        FMLModLoadingContext.get().getModEventBus().addListener(this::postInit);
        FMLModLoadingContext.get().getModEventBus().addListener(this::serverAboutToStart);
        FMLModLoadingContext.get().getModEventBus().addListener(this::serverStopping);
        FMLModLoadingContext.get().getModEventBus().addListener(this::serverStarted);

        MinecraftForge.EVENT_BUS.register(ThreadSafeCache.getInstance());
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void preInit(FMLPreInitializationEvent event) {
        // Setup working directory
        workingDir = new File(event.getModConfigurationDirectory() + File.separator + "worldedit");
        workingDir.mkdir();

        config = new ForgeConfiguration(this);
        config.load();
    }

    public void init(FMLInitializationEvent event) {
        WECUIPacketHandler.init();
        InternalPacketHandler.init();
        proxy.registerHandlers();
    }

    public void postInit(FMLPostInitializationEvent event) {
        LOGGER.info("WorldEdit for Forge (version " + getInternalVersion() + ") is loaded");
    }

    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        if (this.platform != null) {
            LOGGER.warn("FMLServerStartingEvent occurred when FMLServerStoppingEvent hasn't");
            WorldEdit.getInstance().getPlatformManager().unregister(platform);
        }

        this.platform = new ForgePlatform(this);

        WorldEdit.getInstance().getPlatformManager().register(platform);

        if (ModList.get().isLoaded("sponge")) {
            this.provider = new ForgePermissionsProvider.SpongePermissionsProvider();
        } else {
            this.provider = new ForgePermissionsProvider.VanillaPermissionsProvider(platform);
        }

        // TODO Setup states
        for (ResourceLocation name : ForgeRegistries.BLOCKS.getKeys()) {
            String nameStr = name.toString();
            if (!BlockType.REGISTRY.keySet().contains(nameStr)) {
                BlockTypes.register(new BlockType(nameStr));
            }
        }

        for (ResourceLocation name : ForgeRegistries.ITEMS.getKeys()) {
            String nameStr = name.toString();
            if (!ItemType.REGISTRY.keySet().contains(nameStr)) {
                ItemTypes.register(new ItemType(nameStr));
            }
        }
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        WorldEdit worldEdit = WorldEdit.getInstance();
        worldEdit.getSessionManager().unload();
        worldEdit.getPlatformManager().unregister(platform);
    }

    public void serverStarted(FMLServerStartedEvent event) {
        WorldEdit.getInstance().getEventBus().post(new PlatformReadyEvent());
    }

    @SubscribeEvent
    public void onCommandEvent(CommandEvent event) {
        if ((event.getSender() instanceof EntityPlayerMP)) {
            if (((EntityPlayerMP) event.getSender()).world.isRemote) return;
            String[] split = new String[event.getParameters().length + 1];
            System.arraycopy(event.getParameters(), 0, split, 1, event.getParameters().length);
            split[0] = event.getCommand().getName();
            com.sk89q.worldedit.event.platform.CommandEvent weEvent =
                    new com.sk89q.worldedit.event.platform.CommandEvent(wrap((EntityPlayerMP) event.getSender()), Joiner.on(" ").join(split));
            WorldEdit.getInstance().getEventBus().post(weEvent);
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (platform == null) {
            return;
        }

        if (!platform.isHookingEvents())
            return; // We have to be told to catch these events

        if (event.getWorld().isRemote && event instanceof LeftClickEmpty) {
            // catch LCE, pass it to server
            InternalPacketHandler.CHANNEL.sendToServer(new LeftClickAirEventMessage());
            return;
        }
        
        boolean isLeftDeny = event instanceof PlayerInteractEvent.LeftClickBlock
                && ((PlayerInteractEvent.LeftClickBlock) event)
                        .getUseItem() == Event.Result.DENY;
        boolean isRightDeny =
                event instanceof PlayerInteractEvent.RightClickBlock
                        && ((PlayerInteractEvent.RightClickBlock) event)
                                .getUseItem() == Event.Result.DENY;
        if (isLeftDeny || isRightDeny || event.getEntity().world.isRemote) {
            return;
        }

        WorldEdit we = WorldEdit.getInstance();
        ForgePlayer player = wrap((EntityPlayerMP) event.getEntityPlayer());
        ForgeWorld world = getWorld(event.getEntityPlayer().world);

        if (event instanceof PlayerInteractEvent.LeftClickEmpty) {
            if (we.handleArmSwing(player)) {
                // this event cannot be canceled
                // event.setCanceled(true);
            }
        } else if (event instanceof PlayerInteractEvent.LeftClickBlock) {
            Location pos = new Location(world, event.getPos().getX(), event.getPos().getY(), event.getPos().getZ());

            if (we.handleBlockLeftClick(player, pos)) {
                event.setCanceled(true);
            }

            if (we.handleArmSwing(player)) {
                event.setCanceled(true);
            }
        } else if (event instanceof PlayerInteractEvent.RightClickBlock) {
            Location pos = new Location(world, event.getPos().getX(), event.getPos().getY(), event.getPos().getZ());

            if (we.handleBlockRightClick(player, pos)) {
                event.setCanceled(true);
            }

            if (we.handleRightClick(player)) {
                event.setCanceled(true);
            }
        } else if (event instanceof PlayerInteractEvent.RightClickItem) {
            if (we.handleRightClick(player)) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Get the configuration.
     *
     * @return the Forge configuration
     */
    ForgeConfiguration getConfig() {
        return this.config;
    }

    /**
     * Get the WorldEdit proxy for the given player.
     *
     * @param player the player
     * @return the WorldEdit player
     */
    public ForgePlayer wrap(EntityPlayerMP player) {
        checkNotNull(player);
        return new ForgePlayer(player);
    }

    /**
     * Get the session for a player.
     *
     * @param player the player
     * @return the session
     */
    public LocalSession getSession(EntityPlayerMP player) {
        checkNotNull(player);
        return WorldEdit.getInstance().getSessionManager().get(wrap(player));
    }

    /**
     * Get the WorldEdit proxy for the given world.
     *
     * @param world the world
     * @return the WorldEdit world
     */
    public ForgeWorld getWorld(World world) {
        checkNotNull(world);
        return new ForgeWorld(world);
    }

    /**
     * Get the WorldEdit proxy for the platform.
     *
     * @return the WorldEdit platform
     */
    public Platform getPlatform() {
        return this.platform;
    }

    /**
     * Get the working directory where WorldEdit's files are stored.
     *
     * @return the working directory
     */
    public File getWorkingDir() {
        return this.workingDir;
    }

    /**
     * Get the version of the WorldEdit-for-Forge implementation.
     *
     * @return a version string
     */
    String getInternalVersion() {
        return ForgeWorldEdit.class.getAnnotation(Mod.class).version();
    }

    public void setPermissionsProvider(ForgePermissionsProvider provider) {
        this.provider = provider;
    }

    public ForgePermissionsProvider getPermissionsProvider() {
        return provider;
    }

}
