import React, { useState } from 'react';
import { UploadCloud, Link, Sparkles, Layers, Box, Compass, Wand2 } from 'lucide-react';

export default function Dropzone({
  onIngestFile,
  onIngestUrl,
  workflowMode,
  setWorkflowMode,
  targetPreset,
  setTargetPreset,
  isLoading
}) {
  const [isDragActive, setIsDragActive] = useState(false);
  const [urlInput, setUrlInput] = useState('');
  const [mode, setMode] = useState('auto');

  const handleDragOver = (e) => {
    e.preventDefault();
    setIsDragActive(true);
  };

  const handleDragLeave = () => {
    setIsDragActive(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      onIngestFile(e.dataTransfer.files[0], mode, workflowMode, targetPreset);
    }
  };

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      onIngestFile(e.target.files[0], mode, workflowMode, targetPreset);
    }
  };

  const handleUrlSubmit = (e) => {
    e.preventDefault();
    if (urlInput.trim()) {
      onIngestUrl(urlInput.trim(), mode, workflowMode, targetPreset);
    }
  };

  return (
    <div className="glass-panel" id="dropzone-panel">
      <div className="panel-title-row">
        <h2 className="panel-title">
          <UploadCloud size={20} color="var(--accent-emerald)" />
          Ingest & Inspect Engine
        </h2>
        <span className="brand-badge">Auto-Isolator</span>
      </div>

      {/* Preset: Mode Selection (Analysis vs Creation) */}
      <div style={{ marginBottom: 16 }}>
        <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)', display: 'block', marginBottom: 6 }}>
          Preset / Workflow Purpose
        </label>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          <button
            type="button"
            id="mode-analysis-btn"
            className="btn-secondary"
            style={{
              justifyContent: 'center',
              fontSize: '0.8rem',
              padding: '8px',
              background: workflowMode === 'analysis' ? 'rgba(16, 185, 129, 0.2)' : undefined,
              borderColor: workflowMode === 'analysis' ? 'var(--accent-emerald)' : undefined,
              color: workflowMode === 'analysis' ? '#fff' : 'var(--text-muted)'
            }}
            onClick={() => setWorkflowMode('analysis')}
          >
            <Compass size={14} />
            Analysis (Isolate Subject)
          </button>

          <button
            type="button"
            id="mode-creation-btn"
            className="btn-secondary"
            style={{
              justifyContent: 'center',
              fontSize: '0.8rem',
              padding: '8px',
              background: workflowMode === 'creation' ? 'rgba(6, 182, 212, 0.2)' : undefined,
              borderColor: workflowMode === 'creation' ? 'var(--accent-cyan)' : undefined,
              color: workflowMode === 'creation' ? '#fff' : 'var(--text-muted)'
            }}
            onClick={() => setWorkflowMode('creation')}
          >
            <Wand2 size={14} />
            Creation (Variations)
          </button>
        </div>
      </div>

      {/* Target Subject Selector */}
      <div style={{ marginBottom: 16 }}>
        <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)', display: 'block', marginBottom: 6 }}>
          Target Subject to Isolate
        </label>
        <select
          id="target-preset-select"
          value={targetPreset}
          onChange={(e) => setTargetPreset(e.target.value)}
          className="input-field"
          style={{ width: '100%', cursor: 'pointer' }}
        >
          <option value="auto">Smart Auto-Detect (Remove Noise)</option>
          <option value="tree">Tree & Roots (Strip Ground, Keep Canopy)</option>
          <option value="building">Building / Architecture (Strip Base Terrain & Roads)</option>
          <option value="decorations">Interior & Props (Strip Masonry)</option>
          <option value="landscape">Full Landscape (Keep Natural Terrain)</option>
        </select>
      </div>

      {/* Voxelization Mode */}
      <div style={{ marginBottom: 14 }}>
        <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)', display: 'block', marginBottom: 6 }}>
          Voxelization Mode
        </label>
        <select
          id="voxel-mode-select"
          value={mode}
          onChange={(e) => setMode(e.target.value)}
          className="input-field"
          style={{ width: '100%', cursor: 'pointer' }}
        >
          <option value="auto">Auto-Detect (Smart Structure / Art)</option>
          <option value="heightmap">Heightmap 3D Relief (Luminance to Y)</option>
          <option value="pixel_art">2D Pixel Art Upright (Standing Mural)</option>
          <option value="floorplan">Blueprint / Floorplan (Extruded Walls)</option>
        </select>
      </div>

      <div
        id="file-dropzone"
        className={`dropzone ${isDragActive ? 'drag-active' : ''}`}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => document.getElementById('hidden-file-input').click()}
      >
        <input
          id="hidden-file-input"
          type="file"
          accept=".png,.jpg,.jpeg,.webp,.schem,.litematic,.nbt,.schematic"
          style={{ display: 'none' }}
          onChange={handleFileChange}
        />
        <Box size={38} className="dropzone-icon" />
        <div className="dropzone-text-primary">
          {isLoading ? 'Isolating Subject & Voxelizing...' : 'Drop Raw PNG or Schematic here'}
        </div>
        <div className="dropzone-text-secondary">or click to browse from your device</div>

        <div className="supported-chips">
          <span className="chip">PNG / JPG</span>
          <span className="chip">.schem (Sponge)</span>
          <span className="chip">.litematic</span>
          <span className="chip">.nbt</span>
          <span className="chip">.schematic (Legacy)</span>
        </div>
      </div>

      <form onSubmit={handleUrlSubmit} className="url-input-group" id="url-ingest-form">
        <input
          id="url-input-field"
          type="url"
          placeholder="Paste image or schematic link (http://...)"
          className="input-field"
          value={urlInput}
          onChange={(e) => setUrlInput(e.target.value)}
          disabled={isLoading}
        />
        <button
          id="submit-url-btn"
          type="submit"
          className="btn-primary"
          disabled={isLoading || !urlInput.trim()}
        >
          <Link size={16} />
          Fetch
        </button>
      </form>
    </div>
  );
}
