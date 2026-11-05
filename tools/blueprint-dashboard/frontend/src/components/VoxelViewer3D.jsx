import React, { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { Zap, Sparkles, RefreshCw, Eye } from 'lucide-react';

export default function VoxelViewer3D({
  voxelData,
  sliceY,
  renderQuality = 'draft',
  onToggleQuality,
  liveTitle = 'Live Interpretation'
}) {
  const mountRef = useRef(null);
  const controlsRef = useRef(null);
  const [blockCount, setBlockCount] = useState(0);

  useEffect(() => {
    if (!mountRef.current) return;

    const width = mountRef.current.clientWidth || 600;
    const height = mountRef.current.clientHeight || 700;

    // 1. Scene
    const scene = new THREE.Scene();
    const isProper = renderQuality === 'proper';

    scene.background = new THREE.Color(isProper ? 0x090d16 : 0x07090e);
    scene.fog = new THREE.FogExp2(isProper ? 0x090d16 : 0x07090e, isProper ? 0.012 : 0.008);

    // 2. Camera
    const camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 1000);

    // 3. Renderer
    const renderer = new THREE.WebGLRenderer({
      antialias: isProper,
      powerPreference: 'high-performance'
    });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.shadowMap.enabled = isProper;
    if (isProper) {
      renderer.shadowMap.type = THREE.PCFSoftShadowMap;
      renderer.toneMapping = THREE.ACESFilmicToneMapping;
      renderer.toneMappingExposure = 1.15;
    }
    mountRef.current.appendChild(renderer.domElement);

    // 4. OrbitControls
    const controls = new OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.08;
    controls.maxPolarAngle = Math.PI / 2 - 0.02; // Keep camera above ground
    controls.minDistance = 5;
    controls.maxDistance = 200;
    controlsRef.current = controls;

    // 5. Lighting
    if (isProper) {
      // Cinematic proper lighting
      const ambient = new THREE.AmbientLight(0xdbeafe, 0.55);
      scene.add(ambient);

      const sun = new THREE.DirectionalLight(0xfffaed, 1.4);
      sun.position.set(50, 80, 45);
      sun.castShadow = true;
      sun.shadow.mapSize.width = 2048;
      sun.shadow.mapSize.height = 2048;
      sun.shadow.bias = -0.0005;
      scene.add(sun);

      const skyBounce = new THREE.DirectionalLight(0x38bdf8, 0.4);
      skyBounce.position.set(-40, 30, -30);
      scene.add(skyBounce);

      const lantern = new THREE.PointLight(0xf59e0b, 1.5, 40);
      lantern.position.set(0, 10, 0);
      scene.add(lantern);
    } else {
      // Fast draft lighting
      const ambient = new THREE.AmbientLight(0xffffff, 0.9);
      scene.add(ambient);

      const dir = new THREE.DirectionalLight(0xffffff, 0.5);
      dir.position.set(30, 60, 30);
      scene.add(dir);
    }

    // 6. Grid Ground
    const gridColor1 = isProper ? 0x10b981 : 0x06b6d4;
    const gridColor2 = isProper ? 0x1e293b : 0x131d2b;
    const gridHelper = new THREE.GridHelper(64, 64, gridColor1, gridColor2);
    gridHelper.position.y = -0.01;
    scene.add(gridHelper);

    // 7. Instanced Voxel Meshes
    const voxelsGroup = new THREE.Group();
    scene.add(voxelsGroup);

    const dim = voxelData?.dimensions || { width: 16, height: 16, length: 16 };
    const centerX = dim.width / 2;
    const centerZ = dim.length / 2;
    const targetY = dim.height / 2;

    if (voxelData && voxelData.blocks && voxelData.blocks.length > 0) {
      const colorGroups = {};
      let visible = 0;

      voxelData.blocks.forEach((b) => {
        if (sliceY !== undefined && b.y > sliceY) return;
        const color = b.color_hex || '#888888';
        if (!colorGroups[color]) colorGroups[color] = [];
        colorGroups[color].push(b);
        visible++;
      });

      setBlockCount(visible);

      const boxSize = isProper ? 0.985 : 0.96;
      const boxGeo = new THREE.BoxGeometry(boxSize, boxSize, boxSize);
      const dummy = new THREE.Object3D();

      Object.entries(colorGroups).forEach(([colorHex, blocks]) => {
        const material = isProper
          ? new THREE.MeshStandardMaterial({
              color: new THREE.Color(colorHex),
              roughness: 0.75,
              metalness: 0.08
            })
          : new THREE.MeshLambertMaterial({
              color: new THREE.Color(colorHex)
            });

        const instanced = new THREE.InstancedMesh(boxGeo, material, blocks.length);
        if (isProper) {
          instanced.castShadow = true;
          instanced.receiveShadow = true;
        }

        blocks.forEach((b, idx) => {
          dummy.position.set(b.x - centerX + 0.5, b.y + 0.5, b.z - centerZ + 0.5);
          dummy.updateMatrix();
          instanced.setMatrixAt(idx, dummy.matrix);
        });

        instanced.instanceMatrix.needsUpdate = true;
        voxelsGroup.add(instanced);
      });
    }

    // Set camera position & controls target
    controls.target.set(0, targetY * 0.6, 0);
    const maxDim = Math.max(dim.width, dim.height, dim.length, 12);
    camera.position.set(maxDim * 1.4, maxDim * 1.1, maxDim * 1.4);
    controls.update();

    // 8. Animation loop
    let animId;
    const animate = () => {
      animId = requestAnimationFrame(animate);
      controls.update();
      renderer.render(scene, camera);
    };
    animate();

    // 9. Resize Handler
    const handleResize = () => {
      if (!mountRef.current) return;
      const w = mountRef.current.clientWidth;
      const h = mountRef.current.clientHeight;
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
      renderer.setSize(w, h);
    };
    window.addEventListener('resize', handleResize);

    return () => {
      cancelAnimationFrame(animId);
      controls.dispose();
      window.removeEventListener('resize', handleResize);
      if (mountRef.current && renderer.domElement) {
        mountRef.current.removeChild(renderer.domElement);
      }
      renderer.dispose();
    };
  }, [voxelData, sliceY, renderQuality]);

  const resetView = () => {
    if (!controlsRef.current || !voxelData?.dimensions) return;
    const dim = voxelData.dimensions;
    const maxDim = Math.max(dim.width, dim.height, dim.length, 12);
    controlsRef.current.target.set(0, dim.height * 0.3, 0);
    controlsRef.current.object.position.set(maxDim * 1.4, maxDim * 1.1, maxDim * 1.4);
    controlsRef.current.update();
  };

  const isProper = renderQuality === 'proper';

  return (
    <div
      className="viewport-card"
      id="voxel-viewport-card"
      style={{ height: '760px', position: 'sticky', top: '80px' }}
    >
      <div
        ref={mountRef}
        className="three-canvas-container"
        id="three-canvas-container"
        style={{ cursor: 'grab' }}
      />

      {/* Top Left Live Interpretation HUD */}
      <div className="viewport-overlay-hud">
        <div className="hud-pill" style={{ background: 'rgba(6, 182, 212, 0.25)', borderColor: 'var(--accent-cyan)' }}>
          <Eye size={14} color="var(--accent-cyan)" />
          <strong style={{ color: '#fff' }}>{liveTitle}</strong>
        </div>

        <div className="hud-pill">
          <span>Dimensions:</span>
          <strong>
            {voxelData?.dimensions
              ? `${voxelData.dimensions.width}x${voxelData.dimensions.height}x${voxelData.dimensions.length}`
              : '0x0x0'}
          </strong>
        </div>

        <div className="hud-pill">
          <span>Blocks:</span>
          <strong>{blockCount}</strong>
        </div>

        <div className="hud-pill">
          <span style={{ color: 'var(--text-muted)' }}>Controls:</span>
          <span style={{ color: 'var(--accent-cyan)' }}>Left-drag to Orbit / Right-drag to Pan / Scroll to Zoom</span>
        </div>
      </div>

      {/* Top Right Controls HUD */}
      <div style={{ position: 'absolute', top: 16, right: 16, zIndex: 10, display: 'flex', gap: 8 }}>
        <button
          className="btn-secondary"
          style={{ padding: '6px 10px', fontSize: '0.8rem' }}
          onClick={resetView}
          title="Reset Camera View"
        >
          <RefreshCw size={14} />
          Reset View
        </button>

        <button
          id="toggle-render-quality-btn"
          className="btn-secondary"
          style={{
            background: isProper ? 'rgba(16, 185, 129, 0.25)' : 'rgba(6, 182, 212, 0.2)',
            borderColor: isProper ? 'var(--accent-emerald)' : 'var(--accent-cyan)',
            backdropFilter: 'blur(8px)',
            boxShadow: isProper ? 'var(--shadow-glow)' : 'var(--shadow-cyan-glow)',
            fontSize: '0.8rem',
            padding: '6px 12px'
          }}
          onClick={onToggleQuality}
        >
          {isProper ? (
            <>
              <Sparkles size={15} color="var(--accent-emerald)" />
              Proper 3D Active
            </>
          ) : (
            <>
              <Zap size={15} color="var(--accent-cyan)" />
              Draft Mode Active
            </>
          )}
        </button>
      </div>
    </div>
  );
}
