import React, { useState } from 'react';
import { X, Download, Save, Check } from 'lucide-react';

export default function ExportModal({ isOpen, onClose, onSave, activeDesignName }) {
  const [title, setTitle] = useState(activeDesignName || 'Custom Blueprint');
  const [author, setAuthor] = useState('Architect');
  const [description, setDescription] = useState('Crafted and finalized in Blueprint Studio');
  const [tagsInput, setTagsInput] = useState('medieval, fortress, structure');
  const [selectedFormats, setSelectedFormats] = useState({
    schem: true,
    litematic: true,
    nbt: true,
    schematic: true
  });
  const [isSaving, setIsSaving] = useState(false);

  if (!isOpen) return null;

  const toggleFormat = (key) => {
    setSelectedFormats((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  const handleSave = async (e) => {
    e.preventDefault();
    setIsSaving(true);
    const formats = Object.keys(selectedFormats).filter((k) => selectedFormats[k]);
    const tags = tagsInput.split(',').map((t) => t.trim()).filter(Boolean);

    await onSave({
      title,
      author,
      description,
      tags,
      formats
    });
    setIsSaving(false);
    onClose();
  };

  return (
    <div className="modal-backdrop" id="export-modal-backdrop" onClick={onClose}>
      <div
        className="modal-dialog"
        id="export-modal-dialog"
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>
            Export to Schematic Library
          </h2>
          <button
            onClick={onClose}
            style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
          >
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSave}>
          <div style={{ marginBottom: 14 }}>
            <label style={{ fontSize: '0.82rem', color: 'var(--text-muted)', display: 'block', marginBottom: 6 }}>
              Structure Title
            </label>
            <input
              id="export-title-input"
              type="text"
              required
              className="input-field"
              style={{ width: '100%' }}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 14 }}>
            <div>
              <label style={{ fontSize: '0.82rem', color: 'var(--text-muted)', display: 'block', marginBottom: 6 }}>
                Author / Creator
              </label>
              <input
                id="export-author-input"
                type="text"
                className="input-field"
                style={{ width: '100%' }}
                value={author}
                onChange={(e) => setAuthor(e.target.value)}
              />
            </div>
            <div>
              <label style={{ fontSize: '0.82rem', color: 'var(--text-muted)', display: 'block', marginBottom: 6 }}>
                Tags (comma-separated)
              </label>
              <input
                id="export-tags-input"
                type="text"
                className="input-field"
                style={{ width: '100%' }}
                value={tagsInput}
                onChange={(e) => setTagsInput(e.target.value)}
              />
            </div>
          </div>

          <div style={{ marginBottom: 14 }}>
            <label style={{ fontSize: '0.82rem', color: 'var(--text-muted)', display: 'block', marginBottom: 6 }}>
              Description
            </label>
            <textarea
              id="export-desc-input"
              className="input-field"
              style={{ width: '100%', height: 70, resize: 'none' }}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>

          <label style={{ fontSize: '0.82rem', color: 'var(--text-muted)', display: 'block', marginBottom: 8 }}>
            Export Formats
          </label>
          <div className="format-checkboxes">
            <label className="format-label">
              <input
                type="checkbox"
                checked={selectedFormats.schem}
                onChange={() => toggleFormat('schem')}
                style={{ accentColor: 'var(--accent-emerald)' }}
              />
              <span>Sponge .schem (1.13+)</span>
            </label>

            <label className="format-label">
              <input
                type="checkbox"
                checked={selectedFormats.litematic}
                onChange={() => toggleFormat('litematic')}
                style={{ accentColor: 'var(--accent-emerald)' }}
              />
              <span>Litematica .litematic</span>
            </label>

            <label className="format-label">
              <input
                type="checkbox"
                checked={selectedFormats.nbt}
                onChange={() => toggleFormat('nbt')}
                style={{ accentColor: 'var(--accent-emerald)' }}
              />
              <span>Structure .nbt (Mojang)</span>
            </label>

            <label className="format-label">
              <input
                type="checkbox"
                checked={selectedFormats.schematic}
                onChange={() => toggleFormat('schematic')}
                style={{ accentColor: 'var(--accent-emerald)' }}
              />
              <span>Legacy .schematic (1.12-)</span>
            </label>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12, marginTop: 24 }}>
            <button type="button" className="btn-secondary" onClick={onClose} disabled={isSaving}>
              Cancel
            </button>
            <button
              id="confirm-export-btn"
              type="submit"
              className="btn-primary"
              disabled={isSaving}
            >
              <Save size={16} />
              {isSaving ? 'Exporting...' : 'Save & Export to Library'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
