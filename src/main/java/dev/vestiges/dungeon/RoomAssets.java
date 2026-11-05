package dev.vestiges.dungeon;

import dev.vestiges.Vestiges;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Rooms the author builds and hands in as vanilla structure files (made with structure blocks, or a Litematica schematic converted to a structure
 * file). A room's data file names one in {@code structure}; it is read from {@code data/<namespace>/structure/<path>.nbt}.
 * <p>
 * The convention: the file is the whole room, walls included, so it is {@code size + 2} blocks wide and deep. Its lowest corner lands on the
 * room's north-west wall corner, one block under the floor, so the floor surface is its second layer from the bottom. The doors are cut by the
 * game (5 wide and 4 tall in the middle of the west and east walls) whatever the file has there. Marker blocks are vanilla blocks that are
 * replaced with air when the room is pasted: gold block = a monster spawn point, chest = a cache spot, lodestone = the boss spot, iron door = a
 * door slot (removed, the game's own doors replace it).
 */
public final class RoomAssets {
	private RoomAssets() {
	}

	/** The structure a room names, or null when there is no such file. */
	public static StructureTemplate load(MinecraftServer server, String structure) {
		Identifier id = Identifier.tryParse(structure.contains(":") ? structure : Vestiges.MOD_ID + ":" + structure);
		return id == null ? null : server.getStructureTemplateManager().get(id).orElse(null);
	}

	/**
	 * Pastes the structure as the room and reads its markers. Returns the room with its markers, or null when there is no template. A fighting room
	 * without a gold block gets the usual spawn points of an open floor.
	 */
	public static DungeonRuns.Room place(ServerLevel level, DungeonRuns.Room room, StructureTemplate template, Random random) {
		if (template == null) {
			return null;
		}
		BlockPos origin = new BlockPos(room.x() - 1, DungeonPlots.FLOOR_Y - 1, room.z() - 1);
		StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true);
		template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
		List<BlockPos> spawns = positions(template, origin, settings, Blocks.GOLD_BLOCK);
		List<BlockPos> caches = positions(template, origin, settings, Blocks.CHEST);
		List<BlockPos> boss = positions(template, origin, settings, Blocks.LODESTONE);
		List<BlockPos> doors = positions(template, origin, settings, Blocks.IRON_DOOR);
		for (List<BlockPos> markers : List.of(spawns, caches, boss, doors)) {
			for (BlockPos p : markers) {
				DungeonBuilder.set(level, p.getX(), p.getY(), p.getZ(), Blocks.AIR.defaultBlockState());
			}
		}
		if (room.fighting() && spawns.isEmpty()) {
			spawns = RoomBuilder.spawnPoints(room, RoomBuilder.floorMap("hall", room.size()), random);
		}
		Collections.shuffle(spawns, random);
		if (caches.size() > 1 && !room.role().equals("reward")) {
			caches = List.of(caches.get(0));
		}
		return room.withMarkers(spawns, caches, boss.size() > 1 ? List.of(boss.get(0)) : boss);
	}

	private static List<BlockPos> positions(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, Block block) {
		List<BlockPos> out = new ArrayList<>();
		for (var info : template.filterBlocks(origin, settings, block)) {
			out.add(info.pos());
		}
		return out;
	}
}
