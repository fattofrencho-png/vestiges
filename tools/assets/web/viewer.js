// The 3D view: draws block lists with real game textures, and can photograph one from four sides for the AI review.
import * as THREE from "./vendor/three.module.min.js";

const BIOMES = {
  forest: { foliage: "#59ae30", grass: "#79c05a" },
  dark_forest: { foliage: "#59ae30", grass: "#507a32" },
  plains: { foliage: "#77ab2f", grass: "#91bd59" },
  taiga: { foliage: "#68a464", grass: "#86b783" },
  jungle: { foliage: "#30bb0b", grass: "#59c93c" },
  swamp: { foliage: "#6a7039", grass: "#6a7039" },
  savanna: { foliage: "#aea42a", grass: "#bfb755" },
};
const TIMES = {
  day: { sky: "#87b7f0", sun: ["#fff6e0", 2.3], fill: ["#e4eeff", 2.1], pos: [40, 90, 30] },
  sunset: { sky: "#e8976a", sun: ["#ffb070", 2.4], fill: ["#b9a0d0", 1.5], pos: [90, 22, 20] },
  night: { sky: "#0d1326", sun: ["#8ea6e0", 0.8], fill: ["#46558a", 1.1], pos: [-30, 80, -40] },
};
const SIDE = { north: [0, 0, -1], south: [0, 0, 1], west: [-1, 0, 0], east: [1, 0, 0], up: [0, 1, 0], down: [0, -1, 0] };
const SIX = Object.values(SIDE);
const CELL = 16, SHEET = 512;

const $ = (id) => document.getElementById(id);
const canvas = $("canvas");
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.shadowMap.enabled = true;
renderer.shadowMap.autoUpdate = false;
let photoRenderer = null;
const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(60, 1, 0.05, 1500);
const sun = new THREE.DirectionalLight();
sun.castShadow = true;
sun.shadow.mapSize.set(2048, 2048);
sun.shadow.bias = -0.0006;
sun.shadow.normalBias = 0.05;
const fill = new THREE.HemisphereLight();
const world = new THREE.Group();
scene.add(sun, sun.target, fill, world);

const sheetCanvas = document.createElement("canvas");
sheetCanvas.width = sheetCanvas.height = SHEET;
const sheetDraw = sheetCanvas.getContext("2d", { willReadFrequently: true });
const sheet = new THREE.CanvasTexture(sheetCanvas);
sheet.magFilter = sheet.minFilter = THREE.NearestFilter;
sheet.generateMipmaps = false;
sheet.colorSpace = THREE.SRGBColorSpace;
const cells = new Map();
const loading = new Map();
const blockMaterial = new THREE.MeshLambertMaterial({ map: sheet, vertexColors: true, alphaTest: 0.5, side: THREE.DoubleSide });
const MARKERS = { barrier: "#ff3b30", code: "#ffb020", unknown: "#ff40ff" }; // red: invisible · amber: drawn by the game's code · pink: not vanilla
const outlineMaterials = Object.fromEntries(Object.entries(MARKERS).map(([k, color]) => [k, new THREE.LineBasicMaterial({ color })]));

function loadImage(url) {
  return new Promise((done, fail) => {
    const image = new Image();
    image.onload = () => done(image);
    image.onerror = () => fail(new Error("Missing texture " + url));
    image.src = url;
  });
}
function cellFor(tex) {
  if (!loading.has(tex)) {
    const index = cells.size;
    const spot = { x: (index % (SHEET / CELL)) * CELL, y: Math.floor(index / (SHEET / CELL)) * CELL };
    cells.set(tex, spot);
    loading.set(tex, loadImage("/mc/" + tex).then((image) => {
      sheetDraw.drawImage(image, 0, 0, image.width, image.width, spot.x, spot.y, CELL, CELL); // animated textures: first frame
      sheet.needsUpdate = true;
    }));
  }
  return loading.get(tex);
}

const posKey = (x, y, z) => `${x},${y},${z}`;

async function buildMesh(data, foliage) {
  await Promise.all(Object.values(data.states).flatMap((s) => s.quads.map((q) => cellFor(q.tex))));
  const kinds = new Map();
  for (const [x, y, z, state] of data.blocks) kinds.set(posKey(x, y, z), data.states[state].kind);
  const position = [], normal = [], uv = [], color = [], index = [], lines = { barrier: [], code: [], unknown: [] };
  const white = new THREE.Color(1, 1, 1);
  const tintOf = (tint) => (tint === "foliage" ? foliage : tint ? new THREE.Color(tint) : white);

  for (const [x, y, z, state] of data.blocks) {
    if (y < 0) continue; // under the ground in the preview
    const info = data.states[state];
    if (info.kind === "invisible") {
      const a = 0.02, b = 0.98;
      const c = [[a, a, a], [b, a, a], [b, a, b], [a, a, b], [a, b, a], [b, b, a], [b, b, b], [a, b, b]];
      for (const [p, q] of [[0, 1], [1, 2], [2, 3], [3, 0], [4, 5], [5, 6], [6, 7], [7, 4], [0, 4], [1, 5], [2, 6], [3, 7]]) {
        lines[info.marker || "code"].push(x + c[p][0], y + c[p][1], z + c[p][2], x + c[q][0], y + c[q][1], z + c[q][2]);
      }
      continue;
    }
    if (info.kind === "leaves" && SIX.every(([a, b, c]) => {
      const k = kinds.get(posKey(x + a, y + b, z + c));
      return k === "solid" || k === "leaves";
    })) continue; // fully buried leaves can't be seen
    const tint = tintOf(info.tint);
    for (const quad of info.quads) {
      if (quad.cull) {
        const [a, b, c] = SIDE[quad.cull];
        const beside = kinds.get(posKey(x + a, y + b, z + c));
        if (beside === "solid" || (info.kind === "fluid" && beside === "fluid")) continue;
      }
      const cell = cells.get(quad.tex);
      const first = position.length / 3;
      const paint = quad.tinted ? tint : white;
      for (let i = 0; i < 4; i++) {
        position.push(x + quad.v[i * 3], y + quad.v[i * 3 + 1], z + quad.v[i * 3 + 2]);
        normal.push(...quad.n);
        const u = Math.min(Math.max(quad.uv[i * 2], 0.005), 0.995); // a tiny inset stops neighbouring textures bleeding in
        const v = Math.min(Math.max(quad.uv[i * 2 + 1], 0.005), 0.995);
        uv.push((cell.x + u * CELL) / SHEET, 1 - (cell.y + v * CELL) / SHEET);
        color.push(paint.r, paint.g, paint.b);
      }
      index.push(first, first + 3, first + 2, first, first + 2, first + 1);
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", new THREE.Float32BufferAttribute(position, 3));
  geometry.setAttribute("normal", new THREE.Float32BufferAttribute(normal, 3));
  geometry.setAttribute("uv", new THREE.Float32BufferAttribute(uv, 2));
  geometry.setAttribute("color", new THREE.Float32BufferAttribute(color, 3));
  geometry.setIndex(index);
  const group = new THREE.Group();
  const mesh = new THREE.Mesh(geometry, blockMaterial);
  mesh.castShadow = mesh.receiveShadow = true;
  group.add(mesh);
  for (const [marker, points] of Object.entries(lines)) {
    if (!points.length) continue;
    const lineGeometry = new THREE.BufferGeometry();
    lineGeometry.setAttribute("position", new THREE.Float32BufferAttribute(points, 3));
    group.add(new THREE.LineSegments(lineGeometry, outlineMaterials[marker]));
  }
  return group;
}

function makePlayer() {
  const figure = new THREE.Group();
  const part = (w, h, d, x, y, colour) => {
    const box = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), new THREE.MeshLambertMaterial({ color: colour }));
    box.position.set(x, y, 0);
    box.castShadow = true;
    figure.add(box);
  };
  part(0.25, 0.75, 0.25, -0.125, 0.375, "#3b3f8f");
  part(0.25, 0.75, 0.25, 0.125, 0.375, "#3b3f8f");
  part(0.5, 0.75, 0.25, 0, 1.125, "#2fa3a3");
  part(0.25, 0.75, 0.25, -0.375, 1.125, "#c99a78");
  part(0.25, 0.75, 0.25, 0.375, 1.125, "#c99a78");
  part(0.5, 0.5, 0.5, 0, 1.75, "#c99a78");
  return figure;
}

let ground = null, oak = null, referenceData = null;
const player = makePlayer();
let extent = 20;
const centre = new THREE.Vector3();
let wanted = 0;

async function ready() {
  if (ground) return;
  const image = await loadImage("/mc/block/grass_block_top");
  const small = document.createElement("canvas");
  small.width = small.height = 16;
  small.getContext("2d").drawImage(image, 0, 0, 16, 16);
  const texture = new THREE.CanvasTexture(small);
  texture.magFilter = THREE.NearestFilter;
  texture.minFilter = THREE.NearestMipmapLinearFilter;
  texture.wrapS = texture.wrapT = THREE.RepeatWrapping;
  texture.repeat.set(1400, 1400);
  texture.colorSpace = THREE.SRGBColorSpace;
  ground = new THREE.Mesh(new THREE.PlaneGeometry(1400, 1400), new THREE.MeshLambertMaterial({ map: texture }));
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;
  scene.add(ground);
}

function applyLook() {
  if (!ground) return;
  const time = TIMES[$("time").value];
  ground.material.color.set(BIOMES[$("biome").value].grass);
  ground.position.set(centre.x, 0, centre.z);
  scene.background = new THREE.Color(time.sky);
  scene.fog = new THREE.Fog(time.sky, 250, 650); // hides the edge of the ground
  sun.color.set(time.sun[0]);
  sun.intensity = time.sun[1];
  fill.color.set(time.fill[0]);
  fill.groundColor.set("#4a4a3a");
  fill.intensity = time.fill[1];
  sun.target.position.copy(centre);
  sun.position.copy(centre).add(new THREE.Vector3(...time.pos).multiplyScalar(Math.max(extent, 30) / 40));
  const reach = extent * 1.6;
  Object.assign(sun.shadow.camera, { left: -reach, right: reach, top: reach, bottom: -reach, near: 1, far: reach * 6 });
  sun.shadow.camera.updateProjectionMatrix();
  renderer.shadowMap.needsUpdate = true;
}

let lastItems = null;
async function place(items) {
  const number = ++wanted;
  await ready();
  if (!referenceData) referenceData = await (await fetch("/api/reference")).json();
  const foliage = new THREE.Color(BIOMES[$("biome").value].foliage);
  const made = [];
  let cursor = 0, top = 8, deep = 4;
  for (const data of items) {
    const group = await buildMesh(data, foliage);
    let low = Infinity, high = -Infinity, near = Infinity, far = -Infinity;
    for (const [x, y, z] of data.blocks) {
      if (y < 0) continue;
      low = Math.min(low, x); high = Math.max(high, x); near = Math.min(near, z); far = Math.max(far, z); top = Math.max(top, y);
    }
    group.position.x = cursor - low;
    made.push(group);
    cursor += high - low + 1 + 6;
    deep = Math.max(deep, far - near + 1);
  }
  const reference = await buildMesh(referenceData, foliage);
  if (number !== wanted) return false; // something newer was asked for
  world.children.slice().forEach((child) => {
    world.remove(child);
    child.traverse?.((part) => { if (part.geometry && part !== player && !player.children.includes(part)) part.geometry.dispose(); });
  });
  made.forEach((m) => world.add(m));
  oak = reference;
  oak.position.set(-5, 0, deep / 2 + 3);
  oak.visible = $("showOak").checked;
  player.position.set(-3, 0, deep / 2 + 6);
  player.visible = $("showPlayer").checked;
  world.add(oak, player);
  const across = Math.max(cursor - 6, 4);
  centre.set(across / 2, Math.max(top / 2, 3), 0);
  extent = Math.max(across * 0.6, top * 0.75, deep * 0.6, 8);
  applyLook();
  lastItems = items;
  return true;
}

// ---- moving around ----
const orbit = { yaw: 0.7, pitch: 0.22, distance: 40, target: new THREE.Vector3() };
let walking = false, visible = false;
const walker = { position: new THREE.Vector3(), yaw: 0, pitch: 0 };
const keys = new Set();

function resetView() {
  orbit.yaw = 0.7;
  orbit.pitch = 0.22;
  orbit.distance = extent * 2.1;
  orbit.target.copy(centre);
}
function placeCamera() {
  if (walking) {
    camera.position.copy(walker.position);
    camera.rotation.set(walker.pitch, walker.yaw, 0, "YXZ");
    return;
  }
  const c = Math.cos(orbit.pitch);
  camera.position.set(
    orbit.target.x + orbit.distance * c * Math.sin(orbit.yaw),
    Math.max(orbit.target.y + orbit.distance * Math.sin(orbit.pitch), 0.3),
    orbit.target.z + orbit.distance * c * Math.cos(orbit.yaw));
  camera.lookAt(orbit.target);
}
const ORBIT_HELP = "Drag to spin · right-drag to move · wheel to zoom";
$("help").textContent = ORBIT_HELP;
canvas.addEventListener("contextmenu", (e) => e.preventDefault());
canvas.addEventListener("pointermove", (e) => {
  if (walking || !e.buttons) return;
  if (e.buttons & 1) {
    orbit.yaw -= e.movementX * 0.006;
    orbit.pitch = Math.min(Math.max(orbit.pitch + e.movementY * 0.006, -0.1), 1.5);
  } else {
    const speed = orbit.distance * 0.0016;
    const right = new THREE.Vector3().setFromMatrixColumn(camera.matrixWorld, 0);
    const up = new THREE.Vector3().setFromMatrixColumn(camera.matrixWorld, 1);
    orbit.target.addScaledVector(right, -e.movementX * speed).addScaledVector(up, e.movementY * speed);
  }
});
canvas.addEventListener("wheel", (e) => {
  e.preventDefault();
  orbit.distance = Math.min(Math.max(orbit.distance * Math.exp(e.deltaY * 0.001), 3), 500);
}, { passive: false });
$("walk").onclick = () => {
  walker.position.set(centre.x + extent * 0.7, 1.62, centre.z + extent * 0.7);
  walker.yaw = Math.PI / 4;
  walker.pitch = 0.1;
  canvas.requestPointerLock();
};
$("reset").onclick = resetView;
document.addEventListener("pointerlockchange", () => {
  walking = document.pointerLockElement === canvas;
  $("help").textContent = walking ? "WASD to walk · Space up · Shift down · Ctrl faster · Esc to stop (you pass through blocks)" : ORBIT_HELP;
});
document.addEventListener("mousemove", (e) => {
  if (!walking) return;
  walker.yaw -= e.movementX * 0.0025;
  walker.pitch = Math.min(Math.max(walker.pitch - e.movementY * 0.0025, -1.5), 1.5);
});
addEventListener("keydown", (e) => { if (walking) { keys.add(e.code); e.preventDefault(); } });
addEventListener("keyup", (e) => keys.delete(e.code));
addEventListener("blur", () => keys.clear());
$("biome").onchange = () => { if (lastItems) place(lastItems); };
$("time").onchange = applyLook;
$("showPlayer").onchange = (e) => { player.visible = e.target.checked; renderer.shadowMap.needsUpdate = true; };
$("showOak").onchange = (e) => { if (oak) oak.visible = e.target.checked; renderer.shadowMap.needsUpdate = true; };

function resize() {
  const box = $("view").getBoundingClientRect();
  if (!box.width || !box.height) return;
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  renderer.setSize(box.width, box.height, false);
  camera.aspect = box.width / box.height;
  camera.updateProjectionMatrix();
}
addEventListener("resize", resize);

let last = performance.now();
function frame(now) {
  const delta = Math.min((now - last) / 1000, 0.1);
  last = now;
  if (walking) {
    const speed = (keys.has("ControlLeft") ? 14 : 5) * delta;
    const forward = (keys.has("KeyW") ? 1 : 0) - (keys.has("KeyS") ? 1 : 0);
    const strafe = (keys.has("KeyD") ? 1 : 0) - (keys.has("KeyA") ? 1 : 0);
    walker.position.x += (-Math.sin(walker.yaw) * forward + Math.cos(walker.yaw) * strafe) * speed;
    walker.position.z += (-Math.cos(walker.yaw) * forward - Math.sin(walker.yaw) * strafe) * speed;
    walker.position.y = Math.max(walker.position.y + ((keys.has("Space") ? 1 : 0) - (keys.has("ShiftLeft") ? 1 : 0)) * speed, 1.62);
  }
  if (visible) {
    placeCamera();
    renderer.render(scene, camera);
  }
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);

/** Shows these block lists side by side. */
export async function show(items, keepView) {
  visible = true;
  resize();
  if (await place(items)) {
    resize();
    if (!keepView) resetView();
  }
}
export function hide() {
  visible = false;
}
/** Photographs one block list from four sides, as PNG data URLs. Works with the view hidden. */
export async function photograph(data) {
  await place([data]);
  photoRenderer = photoRenderer || new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
  photoRenderer.shadowMap.enabled = true;
  photoRenderer.setSize(768, 768, false);
  const lens = new THREE.PerspectiveCamera(50, 1, 0.05, 1500);
  const images = [];
  for (const yaw of [0.5, 2.07, 3.64, 5.21]) {
    const d = extent * 2.4, c = Math.cos(0.2);
    lens.position.set(centre.x + d * c * Math.sin(yaw), centre.y + d * Math.sin(0.2), centre.z + d * c * Math.cos(yaw));
    lens.lookAt(centre);
    photoRenderer.render(scene, lens);
    images.push(photoRenderer.domElement.toDataURL("image/png"));
  }
  return images;
}
