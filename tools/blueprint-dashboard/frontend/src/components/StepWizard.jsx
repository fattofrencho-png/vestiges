import React, { useState } from 'react';
import {
  UploadCloud,
  BrainCircuit,
  Sparkles,
  CheckCircle2,
  Sliders,
  Check,
  Edit3,
  Undo2,
  Trash2,
  Layers,
  Trees,
  Castle,
  Box,
  Eye,
  Download,
  ArrowRight
} from 'lucide-react';
import Dropzone from './Dropzone';

export default function StepWizard({
  activeStep,
  setActiveStep,
  workflowMode,
  setWorkflowMode,
  targetPreset,
  setTargetPreset,
  fileName,
  aiExplanation,
  canUndo,
  onIngestFile,
  onIngestUrl,
  onRefineAction,
  onConfirmIsolation,
  onConfirmLighting,
  sliceY,
  setSliceY,
  maxLayers,
  paletteCounts,
  proposals,
  selectedProposalId,
  onSelectProposal,
  onApplyProposal,
  onOpenExportModal,
  isLoading
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }} id="step-wizard-container">
      {/* ==================== STEP 1: INGEST & TARGET ==================== */}
      <div className="glass-panel" id="wizard-step-1">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 26,
                height: 26,
                borderRadius: '50%',
                background: activeStep === 1 ? 'var(--accent-emerald)' : activeStep > 1 ? '#059669' : 'rgba(255,255,255,0.1)',
                color: activeStep <= 1 ? '#000' : '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 700,
                fontSize: '0.8rem'
              }}
            >
              {activeStep > 1 ? <Check size={16} /> : '1'}
            </div>
            <div>
              <h3 style={{ fontSize: '0.98rem', fontWeight: 600, color: '#fff' }}>
                Step 1: Input File & Isolation Target
              </h3>
              <span style={{ fontSize: '0.75rem', color: activeStep === 1 ? 'var(--accent-emerald)' : 'var(--text-dim)' }}>
                {activeStep === 1 ? '● ACTIVE — Drop raw PNG, schematic, or URL' : `Confirmed ✓ [${fileName || 'Source Loaded'}]`}
              </span>
            </div>
          </div>

          {activeStep > 1 && (
            <button
              id="edit-step-1-btn"
              className="btn-secondary"
              style={{ fontSize: '0.75rem', padding: '4px 10px' }}
              onClick={() => setActiveStep(1)}
            >
              <Edit3 size={13} />
              Edit
            </button>
          )}
        </div>

        {/* Expanded when Active */}
        {activeStep === 1 && (
          <div style={{ marginTop: 18 }}>
            <Dropzone
              onIngestFile={onIngestFile}
              onIngestUrl={onIngestUrl}
              workflowMode={workflowMode}
              setWorkflowMode={setWorkflowMode}
              targetPreset={targetPreset}
              setTargetPreset={setTargetPreset}
              isLoading={isLoading}
            />

            <div style={{ marginTop: 18 }}>
              <button
                id="confirm-step-1-btn"
                className="btn-primary"
                style={{ width: '100%', justifyContent: 'center' }}
                onClick={() => setActiveStep(2)}
                disabled={isLoading}
              >
                Confirm Input & Proceed to AI Interpretation
                <ArrowRight size={16} />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ==================== STEP 2: AI GUESS & DRAFT REFINE ==================== */}
      <div className="glass-panel" id="wizard-step-2">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 26,
                height: 26,
                borderRadius: '50%',
                background: activeStep === 2 ? 'var(--accent-cyan)' : activeStep > 2 ? '#0284c7' : 'rgba(255,255,255,0.1)',
                color: activeStep === 2 ? '#000' : '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 700,
                fontSize: '0.8rem'
              }}
            >
              {activeStep > 2 ? <Check size={16} /> : '2'}
            </div>
            <div>
              <h3 style={{ fontSize: '0.98rem', fontWeight: 600, color: '#fff' }}>
                Step 2: AI Interpretation & Subject Isolation
              </h3>
              <span style={{ fontSize: '0.75rem', color: activeStep === 2 ? 'var(--accent-cyan)' : 'var(--text-dim)' }}>
                {activeStep === 2 ? '● ACTIVE — Review AI Guess in Draft Mode' : activeStep > 2 ? 'Confirmed ✓ [Subject Isolated]' : 'Waiting for Step 1'}
              </span>
            </div>
          </div>

          {activeStep > 2 && (
            <button
              id="edit-step-2-btn"
              className="btn-secondary"
              style={{ fontSize: '0.75rem', padding: '4px 10px' }}
              onClick={() => setActiveStep(2)}
            >
              <Edit3 size={13} />
              Edit
            </button>
          )}
        </div>

        {/* Expanded when Active */}
        {activeStep === 2 && (
          <div style={{ marginTop: 18 }}>
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
                {aiExplanation || 'AI analyzed the input and isolated the primary subject.'}
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--accent-cyan)' }}>
                Did I guess right? Adjust with actions below or confirm to enable Proper 3D Lighting.
              </div>
            </div>

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
                Strip Base Ground Plane / Roads
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

              {canUndo && (
                <button
                  id="wizard-undo-btn"
                  className="btn-secondary"
                  style={{ fontSize: '0.8rem', padding: '6px 10px', justifyContent: 'center' }}
                  onClick={() => onRefineAction({ action: 'undo' })}
                  disabled={isLoading}
                >
                  <Undo2 size={14} />
                  Undo Last Modification
                </button>
              )}
            </div>

            <button
              id="confirm-step-2-btn"
              className="btn-primary"
              style={{ width: '100%', justifyContent: 'center', padding: '12px' }}
              onClick={onConfirmIsolation}
              disabled={isLoading}
            >
              <Sparkles size={16} />
              Looks Right! Confirm & Enable Proper 3D Lighting
              <ArrowRight size={16} />
            </button>
          </div>
        )}
      </div>

      {/* ==================== STEP 3: PROPER 3D LIGHTING & SLICING ==================== */}
      <div className="glass-panel" id="wizard-step-3">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 26,
                height: 26,
                borderRadius: '50%',
                background: activeStep === 3 ? 'var(--accent-amber)' : activeStep > 3 ? '#d97706' : 'rgba(255,255,255,0.1)',
                color: activeStep === 3 ? '#000' : '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 700,
                fontSize: '0.8rem'
              }}
            >
              {activeStep > 3 ? <Check size={16} /> : '3'}
            </div>
            <div>
              <h3 style={{ fontSize: '0.98rem', fontWeight: 600, color: '#fff' }}>
                Step 3: Proper 3D Lighting & Cross-Section Slicing
              </h3>
              <span style={{ fontSize: '0.75rem', color: activeStep === 3 ? 'var(--accent-amber)' : 'var(--text-dim)' }}>
                {activeStep === 3 ? '● ACTIVE — Inspect Lighting & Layers' : activeStep > 3 ? 'Confirmed ✓ [Lighting Inspected]' : 'Waiting for Step 2'}
              </span>
            </div>
          </div>

          {activeStep > 3 && (
            <button
              id="edit-step-3-btn"
              className="btn-secondary"
              style={{ fontSize: '0.75rem', padding: '4px 10px' }}
              onClick={() => setActiveStep(3)}
            >
              <Edit3 size={13} />
              Edit
            </button>
          )}
        </div>

        {/* Expanded when Active */}
        {activeStep === 3 && (
          <div style={{ marginTop: 18 }}>
            <div style={{ marginBottom: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                <span className="slider-label">Layer Cross-Section (Y: 0 to {sliceY})</span>
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.82rem', color: 'var(--accent-emerald)' }}>
                  {sliceY} / {maxLayers}
                </span>
              </div>
              <input
                id="layer-slice-slider"
                type="range"
                min="0"
                max={maxLayers}
                value={sliceY}
                onChange={(e) => setSliceY(Number(e.target.value))}
                className="range-slider"
                style={{ width: '100%' }}
              />
            </div>

            {/* Palette Distribution */}
            <div style={{ borderTop: '1px solid var(--border-subtle)', paddingTop: 12, marginBottom: 18 }}>
              <span style={{ fontSize: '0.78rem', color: 'var(--text-dim)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                Palette Distribution
              </span>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 8, maxHeight: 110, overflowY: 'auto' }}>
                {Object.entries(paletteCounts || {})
                  .sort((a, b) => b[1] - a[1])
                  .slice(0, 5)
                  .map(([bId, count]) => (
                    <div key={bId} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.82rem' }}>
                      <span style={{ color: 'var(--text-muted)' }}>{bId.replace('minecraft:', '')}</span>
                      <span style={{ fontFamily: 'var(--font-mono)', color: '#fff' }}>{count}</span>
                    </div>
                  ))}
              </div>
            </div>

            <button
              id="confirm-step-3-btn"
              className="btn-primary"
              style={{ width: '100%', justifyContent: 'center', padding: '12px' }}
              onClick={onConfirmLighting}
              disabled={isLoading}
            >
              <CheckCircle2 size={16} />
              Confirm Final Model & View Finished Proposals
              <ArrowRight size={16} />
            </button>
          </div>
        )}
      </div>

      {/* ==================== STEP 4: PROPOSALS & EXPORT ==================== */}
      <div className="glass-panel" id="wizard-step-4">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div
              style={{
                width: 26,
                height: 26,
                borderRadius: '50%',
                background: activeStep === 4 ? 'var(--accent-emerald)' : 'rgba(255,255,255,0.1)',
                color: activeStep === 4 ? '#000' : '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 700,
                fontSize: '0.8rem'
              }}
            >
              4
            </div>
            <div>
              <h3 style={{ fontSize: '0.98rem', fontWeight: 600, color: '#fff' }}>
                Step 4: Finished Product Proposals & Vault Export
              </h3>
              <span style={{ fontSize: '0.75rem', color: activeStep === 4 ? 'var(--accent-emerald)' : 'var(--text-dim)' }}>
                {activeStep === 4 ? '● ACTIVE — Choose Variation or Export' : 'Waiting for Step 3'}
              </span>
            </div>
          </div>
        </div>

        {/* Expanded when Active */}
        {activeStep === 4 && (
          <div style={{ marginTop: 18 }}>
            <button
              id="open-export-modal-btn"
              className="btn-primary"
              style={{ width: '100%', justifyContent: 'center', marginBottom: 16 }}
              onClick={onOpenExportModal}
            >
              <Download size={17} />
              Export Current Model to Schematic Vault (.schem, .nbt, etc.)
            </button>

            <span style={{ fontSize: '0.8rem', color: 'var(--text-dim)', textTransform: 'uppercase', letterSpacing: '0.05em', display: 'block', marginBottom: 10 }}>
              Finished Product Variations ({proposals.length})
            </span>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, maxHeight: 380, overflowY: 'auto' }}>
              {proposals.map((item) => {
                const isSelected = selectedProposalId === item.id;
                return (
                  <div
                    key={item.id}
                    id={`proposal-mini-${item.id}`}
                    style={{
                      background: isSelected ? 'rgba(16, 185, 129, 0.12)' : 'rgba(0,0,0,0.25)',
                      border: isSelected ? '1px solid var(--accent-emerald)' : '1px solid var(--border-subtle)',
                      borderRadius: 'var(--radius-sm)',
                      padding: 12,
                      cursor: 'pointer'
                    }}
                    onClick={() => onSelectProposal(item)}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                      <span style={{ fontWeight: 600, fontSize: '0.9rem', color: '#fff' }}>{item.title}</span>
                      <span className="brand-badge" style={{ fontSize: '0.65rem' }}>{item.category}</span>
                    </div>
                    <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: 8 }}>{item.description}</p>
                    <div style={{ display: 'flex', gap: 8 }}>
                      <button
                        className="btn-secondary"
                        style={{ flex: 1, padding: '4px 8px', fontSize: '0.75rem', justifyContent: 'center' }}
                        onClick={(e) => {
                          e.stopPropagation();
                          onSelectProposal(item);
                        }}
                      >
                        <Eye size={12} />
                        {isSelected ? 'Viewing' : 'Preview'}
                      </button>
                      <button
                        className="btn-primary"
                        style={{ flex: 1, padding: '4px 8px', fontSize: '0.75rem', justifyContent: 'center' }}
                        onClick={(e) => {
                          e.stopPropagation();
                          onApplyProposal(item.id);
                        }}
                      >
                        <Check size={12} />
                        Accept
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
