package dev.vestiges;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {
	/** Per-player Vestiges data. Persistent and copied on death; later milestones grow it (level, tree, Codex, feats). */
	public static final AttachmentType<PlayerProfile> PROFILE = AttachmentRegistry.create(Vestiges.id("profile"),
		builder -> builder.persistent(PlayerProfile.CODEC).copyOnDeath());

	private ModAttachments() {
	}

	public static void init() {
		// Touching the class registers the attachments.
	}
}
