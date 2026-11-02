import React from 'react';
import { Sparkles, Check, ArrowRight, Eye } from 'lucide-react';

export default function VariationsStudio({
  proposals,
  activeProposalId,
  onSelectProposal,
  onApplyProposal,
  isLoading
}) {
  if (!proposals || proposals.length === 0) {
    return null;
  }

  return (
    <div className="variations-section" id="variations-studio-section">
      <div className="section-header">
        <h2 className="section-title">
          <Sparkles size={22} color="var(--accent-cyan)" />
          Finished Product Proposals
        </h2>
        <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
          {proposals.length} Variations Generated from your Input
        </span>
      </div>

      <div className="proposals-grid" id="proposals-grid">
        {proposals.map((item) => {
          const isSelected = activeProposalId === item.id;
          const stats = item.voxel_preview?.stats;

          return (
            <div
              key={item.id}
              id={`proposal-card-${item.id}`}
              className={`proposal-card ${isSelected ? 'selected' : ''}`}
              onClick={() => onSelectProposal(item)}
            >
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
                  <div
                    style={{
                      width: 14,
                      height: 14,
                      borderRadius: 3,
                      backgroundColor: item.thumbnail_color || 'var(--accent-emerald)',
                      boxShadow: `0 0 8px ${item.thumbnail_color || 'var(--accent-emerald)'}`
                    }}
                  />
                  <span className="proposal-badge">{item.category}</span>
                </div>

                <h3 className="proposal-title">{item.title}</h3>
                <p className="proposal-desc">{item.description}</p>
              </div>

              <div>
                <div className="proposal-meta">
                  <span>
                    Size: {item.voxel_preview.dimensions.width}x{item.voxel_preview.dimensions.height}x{item.voxel_preview.dimensions.length}
                  </span>
                  <span>{stats?.solid_blocks || 0} Blocks</span>
                </div>

                <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
                  <button
                    className={`btn-secondary`}
                    style={{
                      flex: 1,
                      justifyContent: 'center',
                      background: isSelected ? 'rgba(16, 185, 129, 0.2)' : undefined,
                      borderColor: isSelected ? 'var(--accent-emerald)' : undefined
                    }}
                    onClick={(e) => {
                      e.stopPropagation();
                      onSelectProposal(item);
                    }}
                  >
                    <Eye size={15} />
                    {isSelected ? 'Viewing in 3D' : 'Preview'}
                  </button>

                  <button
                    className="btn-primary"
                    style={{ flex: 1, justifyContent: 'center' }}
                    onClick={(e) => {
                      e.stopPropagation();
                      onApplyProposal(item.id);
                    }}
                  >
                    <Check size={15} />
                    Accept
                  </button>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
