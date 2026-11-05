package dev.vestiges.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * Plans where a run's 13 rooms stand: a start room, 6 fighting rooms, the Lieutenant, 3 fighting rooms, the Boss and a reward room in a line along
 * +X, joined by corridors of 8 to 16 blocks. Fighting rooms are picked at random from the theme's pool, never the same one twice in a row. Pure
 * logic (no world), so it is unit-tested.
 */
public final class RoomLayout {
	public static final List<String> ORDER = List.of("start", "combat", "combat", "combat", "combat", "combat", "combat", "lieutenant", "combat", "combat",
		"combat", "boss", "reward");
	public static final int MIN_CORRIDOR = 8;
	public static final int MAX_CORRIDOR = 16;
	/** The doors are this wide and this tall, on the middle line of the wall. */
	public static final int DOOR_WIDTH = 5;
	public static final int DOOR_HEIGHT = 4;

	/** A room template to choose from: its id, role, shape and floor size. */
	public record Template(String id, String role, String shape, int size) {
	}

	private RoomLayout() {
	}

	/** The room's height: the roof of a fighting room is higher the more important it is. */
	public static int heightOf(String role) {
		return switch (role) {
			case "boss" -> 14;
			case "lieutenant" -> 12;
			case "combat" -> 10;
			default -> 8;
		};
	}

	/**
	 * The rooms of a run on a plot, or null when the pool has no template for a role. Rooms come out untouched ({@link DungeonRuns.Room#IDLE}) with no
	 * markers yet; the builder fills those in.
	 */
	public static List<DungeonRuns.Room> plan(int plot, Function<String, List<Template>> pool, Random random) {
		int originX = DungeonPlots.originX(plot);
		int originZ = DungeonPlots.originZ(plot);
		List<DungeonRuns.Room> rooms = new ArrayList<>();
		int x = originX + 1;
		String lastCombat = "";
		for (int i = 0; i < ORDER.size(); i++) {
			String role = ORDER.get(i);
			List<Template> templates = pool.apply(role);
			if (templates == null || templates.isEmpty()) {
				return null;
			}
			Template pick = templates.get(random.nextInt(templates.size()));
			if (role.equals("combat") && templates.size() > 1) {
				while (pick.id().equals(lastCombat)) {
					pick = templates.get(random.nextInt(templates.size()));
				}
			}
			if (role.equals("combat")) {
				lastCombat = pick.id();
			}
			int corridor = i == ORDER.size() - 1 ? 0 : MIN_CORRIDOR + random.nextInt(MAX_CORRIDOR - MIN_CORRIDOR + 1);
			rooms.add(new DungeonRuns.Room(i, role, pick.id(), pick.shape(), x, originZ - pick.size() / 2, pick.size(), heightOf(role), corridor, DungeonRuns.Room.IDLE,
				false, List.of(), List.of(), List.of()));
			// Room, its east wall, the corridor, the next room's west wall.
			x += pick.size() + corridor + 2;
		}
		return List.copyOf(rooms);
	}

	/** The east end of the last room. */
	public static int endX(List<DungeonRuns.Room> rooms) {
		DungeonRuns.Room last = rooms.get(rooms.size() - 1);
		return last.x() + last.size();
	}
}
