import React from 'react';
import {
  BrainCircuit,
  Check,
  Undo2,
  Trash2,
  Trees,
  Castle,
  Layers,
  Sparkles,
  ArrowRight
} from 'lucide-react';

export default function RefinementPanel({
  aiExplanation,
  canUndo,
  onRefineAction,
  onAgreeAndSwitchProper,
  isLoading
}) {
  return (
    <div className="glass-panel" id="refinement-panel" style={{ marginTop: 20 }}>
      <div className="panel-title-row" style={{ marginBottom: 12 }}>
        <h3 className="panel-title" style={{ fontSize: '1rem' }}>
          <BrainCircuit size={18} color="var(--accent-cyan)" />
          AI Segmentation & Subject Isolation
        </h3>
        {canUndo && (
          <button
            id="undo-refine-btn"
            className="btn-secondary"
            style={{ fontSize: '0.75rem', padding: '4px 10px' }}
            onClick={() => onRefineAction({ action: 'undo' })}
            disabled={isLoading}
            title="Undo last modification"
          >
            <Undo2 size={13} />
            Undo
          </button>
        )}
      </div>

      {/* AI Guess Card */}
      <div
        style={{
          background: 'rgba(6, 182, 212, 0.08)',
          border: '1px solid rgba(6, 182, 212, 0.3)',
          borderRadius: 'var(--radius-sm)',
          padding: '12px 14px',
          marginBottom: 16
        }}
      >
        <div style={{ fontSize: '0.88rem', color: '#fff', fontWeight: 500, marginBottom: 4 }}>
          {aiExplanation || 'AI detected and segmented the primary structure.'}
        </div>
        <div style={{ fontSize: '0.8rem', color: 'var(--accent-cyan)' }}>
          Did I guess right? Choose an action below or confirm to switch to Proper 3D Rendering.
        </div>
      </div>

      {/* Quick Refinement Action Buttons */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
        <button
          id="action-keep-largest-btn"
          className="btn-secondary"
          style={{ justifyContent: 'flex-start', fontSize: '0.82rem', padding: '8px 12px' }}
          onClick={() => onRefineAction({ action: 'keep_largest_cluster' })}
          disabled={isLoading}
        >
          <Trash2 size={15} color="var(--accent-amber)" />
          Discard Background Rocks & Floating Noise
        </button>

        <button
          id="action-remove-ground-btn"
          className="btn-secondary"
          style={{ justifyContent: 'flex-start', fontSize: '0.82rem', padding: '8px 12px' }}
          onClick={() => onRefineAction({ action: 'remove_ground' })}
          disabled={isLoading}
        >
          <Layers size={15} color="var(--accent-emerald)" />
          Strip Base Ground / Road Plane (Keep Subject)
        </button>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          <button
            id="action-isolate-tree-btn"
            className="btn-secondary"
            style={{ fontSize: '0.8rem', padding: '8px 10px', justifyContent: 'center' }}
            onClick={() => onRefineAction({ action: 'isolate_preset', preset: 'tree' })}
            disabled={isLoading}
          >
            <Trees size={15} color="var(--accent-emerald)" />
            Isolate as Tree
          </button>

          <button
            id="action-isolate-building-btn"
            className="btn-secondary"
            style={{ fontSize: '0.8rem', padding: '8px 10px', justifyContent: 'center' }}
            onClick={() => onRefineAction({ action: 'isolate_preset', preset: 'building' })}
            disabled={isLoading}
          >
            <Castle size={15} color="var(--accent-cyan)" />
            Isolate as Building
          </button>
        </div>
      </div>

      {/* Agree / Proceed to Proper 3D Lighting */}
      <button
        id="agree-and-proper-btn"
        className="btn-primary"
        style={{ width: '100%', justifyContent: 'center', padding: '12px' }}
        onClick={onAgreeAndSwitchProper}
        disabled={isLoading}
      >
        <Sparkles size={16} />
        Looks Right! Switch to Proper 3D Lighting
      </button>
    </div>
  );
}
