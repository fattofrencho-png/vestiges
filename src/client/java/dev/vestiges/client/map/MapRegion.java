package dev.vestiges.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.vestiges.Vestiges;
import dev.vestiges.map.MapMath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** One 512×512 block area of one layer: the pixels (one per block), its GPU texture, and its file on disk. */
final class MapRegion {
	private static int nextTextureId;

	final Path file;
	private NativeImage image;
	private DynamicTexture texture;
	private Identifier textureId;
	private boolean dirtyOnDisk;
	private boolean dirtyOnGpu;
	private long lastUpload;

	private MapRegion(Path file, NativeImage image) {
		this.file = file;
		this.image = image;
	}

	static MapRegion load(Path file, boolean create) {
		if (Files.exists(file)) {
			try (var in = Files.newInputStream(file)) {
				NativeImage img = NativeImage.read(in);
				if (img.getWidth() == MapMath.REGION_SIZE && img.getHeight() == MapMath.REGION_SIZE) {
					return new MapRegion(file, img);
				}
				img.close();
			} catch (IOException e) {
				Vestiges.LOGGER.warn("Vestiges map: could not read {}, starting it fresh", file);
			}
		}
		if (!create) {
			return null;
		}
		NativeImage img = new NativeImage(MapMath.REGION_SIZE, MapMath.REGION_SIZE, true);
		img.fillRect(0, 0, MapMath.REGION_SIZE, MapMath.REGION_SIZE, 0);
		return new MapRegion(file, img);
	}

	void set(int localX, int localZ, int argb) {
		if (image.getPixel(localX, localZ) != argb) {
			image.setPixel(localX, localZ, argb);
			dirtyOnDisk = true;
			dirtyOnGpu = true;
		}
	}

	int get(int localX, int localZ) {
		return image.getPixel(localX, localZ);
	}

	/** The texture to draw, created on first use and re-uploaded at most twice a second while the area changes. */
	Identifier texture() {
		if (texture == null) {
			textureId = Vestiges.id("map/region_" + (nextTextureId++));
			texture = new DynamicTexture(() -> "Vestiges map region", image);
			Minecraft.getInstance().getTextureManager().register(textureId, texture);
			dirtyOnGpu = false;
			lastUpload = System.currentTimeMillis();
		} else if (dirtyOnGpu && System.currentTimeMillis() - lastUpload > 500) {
			texture.upload();
			dirtyOnGpu = false;
			lastUpload = System.currentTimeMillis();
		}
		return textureId;
	}

	void save() {
		if (!dirtyOnDisk) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			image.writeToFile(file);
			dirtyOnDisk = false;
		} catch (IOException e) {
			Vestiges.LOGGER.warn("Vestiges map: could not save {}", file, e);
		}
	}

	/** Saves and frees the pixels and the GPU texture. */
	void close() {
		save();
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(textureId);
			texture = null;
			image = null;
		} else if (image != null) {
			image.close();
			image = null;
		}
	}
}
