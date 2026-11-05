export function createDefaultSampleModel() {
  const blocks = [];
  const counts = {};

  const addBlock = (x, y, z, blockId, colorHex) => {
    blocks.push({ x, y, z, block_id: blockId, color_hex: colorHex });
    counts[blockId] = (counts[blockId] || 0) + 1;
  };

  // Base roots and trunk
  addBlock(7, 0, 7, 'minecraft:dirt', '#866043');
  addBlock(8, 0, 8, 'minecraft:dirt', '#866043');
  addBlock(9, 0, 8, 'minecraft:dirt', '#866043');

  // Trunk
  for (let y = 1; y <= 7; y++) {
    addBlock(8, y, 8, 'minecraft:oak_log', '#6c5634');
    if (y <= 3) {
      addBlock(9, y, 8, 'minecraft:oak_log', '#6c5634');
    }
  }

  // Branches
  addBlock(7, 5, 8, 'minecraft:oak_log', '#6c5634');
  addBlock(6, 6, 8, 'minecraft:oak_log', '#6c5634');
  addBlock(9, 5, 9, 'minecraft:oak_log', '#6c5634');
  addBlock(10, 6, 10, 'minecraft:oak_log', '#6c5634');

  // Canopy (Leaves)
  for (let x = 5; x <= 11; x++) {
    for (let z = 5; z <= 11; z++) {
      for (let y = 6; y <= 9; y++) {
        // Spherical hollow canopy
        const dx = x - 8;
        const dy = y - 7.5;
        const dz = z - 8;
        const dist = Math.sqrt(dx * dx + dy * dy * 1.5 + dz * dz);
        if (dist <= 3.2 && dist >= 1.2) {
          const isMossy = (x + y + z) % 5 === 0;
          addBlock(
            x,
            y,
            z,
            isMossy ? 'minecraft:moss_block' : 'minecraft:oak_leaves',
            isMossy ? '#5c8c32' : '#3c7828'
          );
        }
      }
    }
  }

  // Lantern hanging from branch
  addBlock(6, 5, 8, 'minecraft:lantern', '#f0b450');

  return {
    dimensions: { width: 16, height: 11, length: 16 },
    blocks: blocks,
    stats: {
      total_blocks: 16 * 11 * 16,
      solid_blocks: blocks.length,
      palette_counts: counts,
      dimensions: { width: 16, height: 11, length: 16 }
    }
  };
}
