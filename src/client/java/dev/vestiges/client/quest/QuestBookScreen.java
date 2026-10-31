package dev.vestiges.client.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/** A quest shown as a book page: an offer, or a story choice whose options are ticked with a check mark. First version; the UI chat polishes it. */
public final class QuestBookScreen extends Screen {
	private static final Identifier BOOK = Identifier.withDefaultNamespace("textures/gui/book.png");
	private static final int BOOK_W = 192;
	private static final int TEXT_X = 36;
	private static final int TEXT_W = 114;
	private static final int TEXT_TOP = 16;
	private static final int INK = 0xFF000000;
	private final String id;
	private final List<Component> page = new ArrayList<>();
	private final JsonArray choices;
	private int picked = -1;

	private QuestBookScreen(String id, JsonArray choices) {
		super(Component.literal("Quest"));
		this.id = id;
		this.choices = choices;
	}

	/** The page a villager or a note opens. */
	public static QuestBookScreen offer(JsonObject o) {
		QuestBookScreen s = new QuestBookScreen(o.get("id").getAsString(), null);
		s.head(o.get("name").getAsString(), o.get("giver").getAsString());
		String intro = o.get("intro").getAsString();
		if (!intro.isEmpty()) {
			s.page.add(Component.literal(intro));
			s.page.add(Component.empty());
		}
		for (JsonElement e : o.getAsJsonArray("steps")) {
			s.page.add(Component.literal("- " + e.getAsString()));
		}
		s.rewardLine(o.get("reward").getAsString());
		return s;
	}

	/** The page of a story choice: the current step with its options. */
	public static QuestBookScreen choice(String id, String name, String giver, String text, JsonArray choices) {
		QuestBookScreen s = new QuestBookScreen(id, choices);
		s.head(name, giver);
		s.page.add(Component.literal(text));
		return s;
	}

	private void head(String name, String giver) {
		page.add(Component.literal(name).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED));
		if (!giver.isEmpty()) {
			page.add(Component.literal(giver).withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GRAY));
		}
		page.add(Component.empty());
	}

	private void rewardLine(String reward) {
		if (!reward.isEmpty()) {
			page.add(Component.empty());
			page.add(Component.literal("Reward: ").withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_GREEN).append(Component.literal(reward).withStyle(s -> s.withBold(false))));
		}
	}

	private int bookX() {
		return (width - BOOK_W) / 2;
	}

	private int lines(List<Component> text) {
		int n = 0;
		for (Component c : text) {
			n += Math.max(1, font.split(c, TEXT_W).size());
		}
		return n;
	}

	@Override
	protected void init() {
		int y = 2 + BOOK_W + 4;
		if (choices == null) {
			addRenderableWidget(Button.builder(Component.literal("Accept"), b -> {
				ClientQuests.send("accept", id, "");
				onClose();
			}).bounds(width / 2 - 92, y, 90, 20).build());
			addRenderableWidget(Button.builder(Component.literal("Not now"), b -> onClose()).bounds(width / 2 + 2, y, 90, 20).build());
			return;
		}
		int optionY = 2 + TEXT_TOP + lines(page) * 9 + 4;
		for (int i = 0; i < choices.size(); i++) {
			Option option = new Option(bookX() + TEXT_X, optionY, i, choices.get(i).getAsJsonObject());
			addRenderableWidget(option);
			optionY += option.getHeight() + 3;
		}
		Button confirm = Button.builder(Component.literal("Choose"), b -> {
			ClientQuests.send("choose", id, String.valueOf(picked));
			onClose();
		}).bounds(width / 2 - 92, y, 90, 20).build();
		confirm.active = picked >= 0;
		addRenderableWidget(confirm);
		addRenderableWidget(Button.builder(Component.literal("Not now"), b -> onClose()).bounds(width / 2 + 2, y, 90, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.blit(RenderPipelines.GUI_TEXTURED, BOOK, bookX(), 2, 0, 0, BOOK_W, BOOK_W, BOOK_W, BOOK_W, 256, 256, 0xFFFFFFFF);
		int y = 2 + TEXT_TOP;
		for (Component c : page) {
			List<FormattedCharSequence> split = font.split(c, TEXT_W);
			if (split.isEmpty()) {
				y += 5;
			}
			for (FormattedCharSequence line : split) {
				g.text(font, line, bookX() + TEXT_X, y, INK, false);
				y += 9;
			}
		}
		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** One option on the page: a box that shows a check mark when ticked, its label, and what it does to your standing. */
	private final class Option extends AbstractWidget {
		private final int index;
		private final List<FormattedCharSequence> label;
		private final List<FormattedCharSequence> effects;

		Option(int x, int y, int index, JsonObject o) {
			super(x, y, TEXT_W, 0, Component.literal(o.get("label").getAsString()));
			this.index = index;
			label = font.split(getMessage(), TEXT_W - 12);
			String text = o.get("effects").getAsString();
			effects = text.isEmpty() ? List.of() : font.split(Component.literal(text), TEXT_W - 12);
			setHeight(Math.max(10, (label.size() + effects.size()) * 9));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			int x = getX();
			int y = getY();
			g.fill(x, y, x + 9, y + 9, 0xFF3B2A14);
			g.fill(x + 1, y + 1, x + 8, y + 8, isHovered() ? 0xFFF4E9C8 : 0xFFE8DAB2);
			if (picked == index) {
				g.text(font, "✔", x + 1, y + 1, 0xFF1E7A3C, false);
			}
			int ly = y;
			for (FormattedCharSequence line : label) {
				g.text(font, line, x + 12, ly, INK, false);
				ly += 9;
			}
			for (FormattedCharSequence line : effects) {
				g.text(font, line, x + 12, ly, 0xFF6B5A3A, false);
				ly += 9;
			}
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			picked = index;
			rebuildWidgets();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
