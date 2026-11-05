import React, { useState } from 'react';
import { Search, Download, Eye, Calendar, Box, Tag } from 'lucide-react';

export default function LibraryVault({ libraryItems, onLoadItemIntoStudio, onRefresh }) {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedTag, setSelectedTag] = useState('');

  const filteredItems = (libraryItems || []).filter((item) => {
    const matchSearch =
      !searchTerm ||
      item.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      item.description.toLowerCase().includes(searchTerm.toLowerCase());

    const matchTag =
      !selectedTag || (item.tags && item.tags.some((t) => t.toLowerCase() === selectedTag.toLowerCase()));

    return matchSearch && matchTag;
  });

  return (
    <div id="library-vault-container">
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 28,
          flexWrap: 'wrap',
          gap: 16
        }}
      >
        <div>
          <h2 style={{ fontSize: '1.4rem', fontWeight: 700, color: '#fff', marginBottom: 4 }}>
            Schematic Vault & Library
          </h2>
          <p style={{ fontSize: '0.88rem', color: 'var(--text-muted)' }}>
            Exported Minecraft structures ready for in-game use or further iteration
          </p>
        </div>

        <div style={{ display: 'flex', gap: 12 }}>
          <div style={{ position: 'relative', width: 280 }}>
            <input
              id="library-search-input"
              type="text"
              placeholder="Search library..."
              className="input-field"
              style={{ width: '100%', paddingLeft: 34 }}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
            <Search
              size={16}
              color="var(--text-dim)"
              style={{ position: 'absolute', left: 12, top: 12 }}
            />
          </div>
        </div>
      </div>

      {filteredItems.length === 0 ? (
        <div
          className="glass-panel"
          style={{ textAlign: 'center', padding: '60px 20px', color: 'var(--text-muted)' }}
        >
          <Box size={48} style={{ margin: '0 auto 16px', opacity: 0.4 }} />
          <h3 style={{ fontSize: '1.1rem', color: '#fff', marginBottom: 8 }}>No Schematics Found</h3>
          <p style={{ fontSize: '0.88rem' }}>
            Inspect a raw image or schematic in the Studio, choose a finished variation, and export it here.
          </p>
        </div>
      ) : (
        <div className="library-grid" id="library-grid">
          {filteredItems.map((item) => (
            <div key={item.id} className="library-card" id={`library-card-${item.id}`}>
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                  <h3 style={{ fontSize: '1.1rem', fontWeight: 600, color: '#fff' }}>{item.title}</h3>
                  <span className="brand-badge" style={{ fontSize: '0.68rem' }}>
                    {item.total_blocks} Blocks
                  </span>
                </div>

                <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: 16, lineHeight: 1.4 }}>
                  {item.description || 'No description provided.'}
                </p>

                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 16 }}>
                  {(item.tags || []).map((t, idx) => (
                    <span
                      key={idx}
                      className="chip"
                      style={{ cursor: 'pointer' }}
                      onClick={() => setSelectedTag(selectedTag === t ? '' : t)}
                    >
                      #{t}
                    </span>
                  ))}
                </div>
              </div>

              <div>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    fontSize: '0.78rem',
                    color: 'var(--text-dim)',
                    borderTop: '1px solid var(--border-subtle)',
                    paddingTop: 12,
                    marginBottom: 14
                  }}
                >
                  <span>
                    Dim: {item.dimensions.width}x{item.dimensions.height}x{item.dimensions.length}
                  </span>
                  <span>{item.created_at}</span>
                </div>

                <div style={{ display: 'flex', gap: 8, alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap' }}>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <button
                      className="btn-secondary"
                      style={{ fontSize: '0.8rem', padding: '6px 10px' }}
                      onClick={() => onLoadItemIntoStudio(item.id, false)}
                    >
                      <Eye size={14} />
                      Inspect in 3D
                    </button>

                    <button
                      className="btn-primary"
                      style={{ fontSize: '0.8rem', padding: '6px 10px' }}
                      onClick={() => onLoadItemIntoStudio(item.id, true)}
                      title="Use this saved entry to generate new creations and variations"
                    >
                      <Box size={14} />
                      Derive & Create New
                    </button>
                  </div>

                  <div className="download-badges" style={{ margin: 0 }}>
                    {(item.formats_available || ['schem', 'litematic', 'nbt', 'schematic']).map((fmt) => (
                      <a
                        key={fmt}
                        href={`/api/library/${item.id}/export/${fmt}`}
                        download={`${item.title}.${fmt}`}
                        className="download-btn"
                        title={`Download .${fmt}`}
                      >
                        <Download size={11} />
                        .{fmt}
                      </a>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
