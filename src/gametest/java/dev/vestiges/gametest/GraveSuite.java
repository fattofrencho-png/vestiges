package dev.vestiges.gametest;

import dev.vestiges.client.map.ClientWaypoints;
import dev.vestiges.grave.GraveList;
import dev.vestiges.grave.GraveService;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;

/**
 * S11: the Grave MVP. A death puts the inventory in a gravestone (switchable); the timer is a switch of its own (off: the gravestone has no expiry);
 * with the Gravestones switch off, the inventory drops like vanilla. The player really dies and respawns through the death screen.
 */
final class GraveSuite {
	private GraveSuite() {
	}

	private static int graves(TestServerContext server) {
		return server.computeOnServer(s -> {
			GraveList l = s.overworld().getAttached(GraveService.GRAVES);
			return l == null ? 0 : l.graves().size();
		});
	}

	private static void die(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("clear @p");
		server.runCommand("give @p diamond 3");
		ctx.waitTicks(5);
		server.runCommand("kill @p");
		ctx.waitForScreen(DeathScreen.class);
		ctx.waitTicks(10);
	}

	private static void respawn(ClientGameTestContext ctx) {
		ctx.clickScreenButton("deathScreen.respawn");
		ctx.waitFor(mc -> mc.player != null && mc.player.isAlive() && mc.gui.screen() == null, 600);
		ctx.waitTicks(20);
	}

	private static int diamondsNearby(TestServerContext server) {
		return server.computeOnServer(s -> {
			int n = 0;
			for (var e : s.overworld().getAllEntities()) {
				if (e instanceof ItemEntity item && item.getItem().is(Items.DIAMOND)) {
					n += item.getItem().getCount();
				}
			}
			return n;
		});
	}

	private static int diamondsCarried(TestServerContext server) {
		return server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			int n = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (p.getInventory().getItem(i).is(Items.DIAMOND)) {
					n += p.getInventory().getItem(i).getCount();
				}
			}
			return n;
		});
	}

	private static long graveExpiry(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			long expires = -1;
			for (ClientWaypoints.Waypoint w : ClientWaypoints.all()) {
				if (w.graveId != 0) {
					expires = w.expires;
				}
			}
			return expires;
		});
	}

	static void run(ClientGameTestContext ctx, TestServerContext server, Checks c) {
		server.runCommand("gamemode survival @p");
		server.runCommand("vestiges level set @p 5");

		c.section("A death makes a gravestone");
		int before = graves(server);
		die(ctx, server);
		c.check("one gravestone was made", graves(server) == before + 1, before + " -> " + graves(server));
		respawn(ctx);
		c.check("the player respawns with empty pockets (the diamonds are in the grave)", diamondsCarried(server) == 0 && diamondsNearby(server) == 0,
			diamondsCarried(server) + " carried, " + diamondsNearby(server) + " on the ground");
		ctx.waitTicks(20);
		c.check("the timer is on: the grave shows an expiry", graveExpiry(ctx) > 0, String.valueOf(graveExpiry(ctx)));

		c.section("Timer off: the gravestone stays");
		server.runCommand("vestiges config system grave_timer false");
		ctx.waitTicks(10);
		server.runCommand("gamemode survival @p");
		before = graves(server);
		die(ctx, server);
		c.check("a second gravestone was made", graves(server) == before + 1);
		respawn(ctx);
		ctx.waitTicks(20);
		c.check("with the timer off the newest gravestone has no expiry", graveExpiry(ctx) == 0, String.valueOf(graveExpiry(ctx)));
		server.runCommand("vestiges config system grave_timer true");

		c.section("Gravestones off: vanilla drops");
		server.runCommand("vestiges config system graves false");
		ctx.waitTicks(10);
		server.runCommand("gamemode survival @p");
		before = graves(server);
		die(ctx, server);
		c.check("no gravestone is made", graves(server) == before, before + " -> " + graves(server));
		c.check("the diamonds drop where the player died", diamondsNearby(server) >= 3, String.valueOf(diamondsNearby(server)));
		respawn(ctx);
		server.runCommand("vestiges config system graves true");
		server.runCommand("gamemode creative @p");
	}
}
