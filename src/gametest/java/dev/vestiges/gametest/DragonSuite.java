package dev.vestiges.gametest;

import dev.vestiges.data.VestigesData;
import dev.vestiges.dragon.DragonDef;
import dev.vestiges.dragon.DragonService;
import dev.vestiges.dragon.PlayerDragons;
import dev.vestiges.inventory.InventoryService;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * S12: the first dragons (Act I). Slice 1: the six data files load, the kill count is per world and set only by an operator command, and the
 * ender chest page count follows the dragons a player was rewarded for. Later slices add summoning, the gate, the fights and the rewards.
 */
final class DragonSuite {
	private DragonSuite() {
	}

	private static int kills(TestServerContext server) {
		return server.computeOnServer(s -> DragonService.world(s).kills());
	}

	private static int pages(TestServerContext server) {
		return server.computeOnServer(s -> InventoryService.unlockedPages(s.getPlayerList().getPlayers().get(0)));
	}

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		c.section("The data");
		c.check("six dragons are loaded", VestigesData.DRAGONS.all().size() == 6, String.valueOf(VestigesData.DRAGONS.all().size()));
		DragonDef two = server.computeOnServer(s -> DragonService.def(2));
		c.check("dragon 2 is Belaz, the Ember, gate level 8", two != null && two.name().equals("Belaz") && two.gate() == 8 && two.title().equals("the Ember"),
			String.valueOf(two));
		c.check("there is no dragon 7 yet (summoning would give a plain respawn)", server.computeOnServer(s -> DragonService.def(7)) == null);
		c.check("the Dragons switch is on in a new world", DragonService.enabled());

		c.section("The kill count");
		c.check("a new world starts at 0 kills", kills(server) == 0, String.valueOf(kills(server)));
		String info = c.expectOk(server, "vestiges dragon info").text();
		c.check("info names the next dragon", info != null, info);
		c.expectOk(server, "vestiges dragon count 3");
		c.check("the count is set by the operator command", kills(server) == 3, String.valueOf(kills(server)));
		c.expectOk(server, "vestiges dragon count 0");
		c.check("and back to 0", kills(server) == 0);

		c.section("Ender chest pages");
		c.check("a new player has 1 page", pages(server) == 1, String.valueOf(pages(server)));
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			DragonService.setPlayer(p, PlayerDragons.EMPTY.with(1).with(2).with(3));
		});
		c.check("three rewarded dragons make 4 pages", pages(server) == 4, String.valueOf(pages(server)));
		server.runCommand("vestiges config system dragons false");
		ctx.waitTicks(5);
		c.check("with the Dragons switch off the player has only the first page", pages(server) == 1, String.valueOf(pages(server)));
		server.runCommand("vestiges config system dragons true");
		server.runOnServer(s -> DragonService.setPlayer(s.getPlayerList().getPlayers().get(0), PlayerDragons.EMPTY));
		c.check("clearing the record goes back to 1 page", pages(server) == 1, String.valueOf(pages(server)));
	}
}
