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

package com.sk89q.worldedit.sponge;

import com.sk89q.worldedit.entity.BaseEntity;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.world.biome.BiomeType;

final class IDHelper {

    private IDHelper() { }

    public static int resolve(ItemType type) {
        return 0;
    }

    public static int resolve(BlockType type) {
        return 0;
    }

    public static int resolve(BiomeType type) {
        return 0;
    }


    public static ItemType resolveItem(int ID) {
        return null;
    }

    public static BlockType resolveBlock(int ID) {
        return null;
    }

    public static BiomeType resolveBiome(int ID) {
        return null;
    }


}