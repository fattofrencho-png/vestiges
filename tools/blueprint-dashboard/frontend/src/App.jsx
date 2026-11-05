import React, { useState, useEffect } from 'react';
import {
  Box,
  Compass,
  FolderArchive,
  Download
} from 'lucide-react';
import VoxelViewer3D from './components/VoxelViewer3D';
import LibraryVault from './components/LibraryVault';
import ExportModal from './components/ExportModal';
import StepWizard from './components/StepWizard';
import { createDefaultSampleModel } from './utils/sampleModel';

export default function App() {
  const [activeTab, setActiveTab] = useState('studio'); // 'studio' | 'library'
  const [activeStep, setActiveStep] = useState(1); // 1: Ingest -> 2: Draft Refine -> 3: Proper 3D -> 4: Proposals/Export
  const [workflowMode, setWorkflowMode] = useState('analysis'); // 'analysis' | 'creation'
  const [targetPreset, setTargetPreset] = useState('tree'); // default to 'tree' as user mentioned tree & roots

  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState('Engine Ready');
  const [sessionId, setSessionId] = useState(null);

  // Active Voxel State - initialized with starter sample so right side always has live 3D render
  const [activeVoxelData, setActiveVoxelData] = useState(() => createDefaultSampleModel());
  const [activeTitle, setActiveTitle] = useState('Starter Sample (Tree & Roots)');
  const [sliceY, setSliceY] = useState(16);
  const [renderQuality, setRenderQuality] = useState('draft'); // 'draft' | 'proper'

  // Refinement / AI Guess State
  const [aiExplanation, setAiExplanation] = useState(
    'Initial Sample: Stylized Oak Tree & Roots. Isolated trunk and canopy, stripped surrounding terrain.'
  );
  const [canUndo, setCanUndo] = useState(false);

  // Variations proposals
  const [proposals, setProposals] = useState([]);
  const [selectedProposalId, setSelectedProposalId] = useState(null);

  // Library State
  const [libraryItems, setLibraryItems] = useState([]);
  const [isExportModalOpen, setIsExportModalOpen] = useState(false);

  useEffect(() => {
    fetchLibrary();
  }, []);

  const fetchLibrary = async () => {
    try {
      const res = await fetch('/api/library');
      if (res.ok) {
        const data = await res.json();
        setLibraryItems(data);
      }
    } catch (err) {
      console.error('Failed to load library:', err);
    }
  };

  // Ingest File (PNG or Schematic)
  const handleIngestFile = async (file, mode, wfMode, tgtPreset) => {
    setIsLoading(true);
    setStatusMessage('Analyzing and isolating subject...');
    setRenderQuality('draft');
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('mode', mode);
      formData.append('workflow_mode', wfMode);
      formData.append('target_preset', tgtPreset);

      const res = await fetch('/api/ingest/file', {
        method: 'POST',
        body: formData
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.detail || 'Ingest failed');
      }

      const data = await res.json();
      setSessionId(data.session_id);
      setActiveVoxelData(data.preview);
      setActiveTitle(data.filename || 'Custom Structure');
      setSliceY(data.preview.dimensions.height);
      setAiExplanation(data.ai_guess_explanation || 'Subject isolated.');
      setCanUndo(false);

      if (wfMode === 'analysis') {
        setActiveStep(2); // Step 1 disappears, Step 2 becomes active
        setStatusMessage(`Draft isolated: ${data.preview.stats.solid_blocks} blocks`);
      } else {
        setActiveStep(4);
        await loadVariations(data.session_id);
      }
    } catch (err) {
      alert(`Error processing file: ${err.message}`);
      setStatusMessage('Ingest failed');
    } finally {
      setIsLoading(false);
    }
  };

  // Ingest URL
  const handleIngestUrl = async (url, mode, wfMode, tgtPreset) => {
    setIsLoading(true);
    setStatusMessage('Fetching resource and isolating subject...');
    setRenderQuality('draft');
    try {
      const res = await fetch('/api/ingest/url', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          url,
          mode,
          workflow_mode: wfMode,
          target_preset: tgtPreset
        })
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.detail || 'URL Ingest failed');
      }

      const data = await res.json();
      setSessionId(data.session_id);
      setActiveVoxelData(data.preview);
      setActiveTitle(data.filename || 'Web Resource');
      setSliceY(data.preview.dimensions.height);
      setAiExplanation(data.ai_guess_explanation || 'Subject isolated.');
      setCanUndo(false);

      if (wfMode === 'analysis') {
        setActiveStep(2);
      } else {
        setActiveStep(4);
        await loadVariations(data.session_id);
      }
    } catch (err) {
      alert(`Error fetching URL: ${err.message}`);
      setStatusMessage('URL Ingest failed');
    } finally {
      setIsLoading(false);
    }
  };

  // Refine action from human feedback
  const handleRefineAction = async (refinePayload) => {
    if (!sessionId) {
      // Local fallback for starter sample
      alert("Please upload a file or schematic to apply server-side segmentation actions.");
      return;
    }
    setIsLoading(true);
    if (renderQuality === 'proper') {
      setRenderQuality('draft');
    }

    try {
      const res = await fetch(`/api/refine/${sessionId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(refinePayload)
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.detail || 'Refinement failed');
      }

      const data = await res.json();
      setActiveVoxelData(data.preview);
      setSliceY(data.preview.dimensions.height);
      setAiExplanation(data.ai_guess_explanation);
      setCanUndo(data.can_undo);
      setStatusMessage(data.ai_guess_explanation);
    } catch (err) {
      alert(`Refinement error: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  // Confirm Step 2 -> Step 3
  const handleConfirmIsolation = async () => {
    setRenderQuality('proper');
    setActiveStep(3); // Step 2 collapses, Step 3 opens
    setStatusMessage('Proper 3D lighting enabled. Inspecting final render.');
    if (sessionId) {
      await loadVariations(sessionId);
    }
  };

  // Confirm Step 3 -> Step 4
  const handleConfirmLighting = async () => {
    setActiveStep(4); // Step 3 collapses, Step 4 opens
    if (sessionId && proposals.length === 0) {
      await loadVariations(sessionId);
    }
  };

  // Load Finished Product Proposals
  const loadVariations = async (sessId) => {
    try {
      setStatusMessage('Generating finished product variations...');
      const res = await fetch(`/api/variations/${sessId}`);
      if (res.ok) {
        const data = await res.json();
        setProposals(data.proposals || []);
        setStatusMessage(`Generated ${data.proposals.length} finished product proposals`);
      }
    } catch (err) {
      console.error('Failed to load variations:', err);
    }
  };

  // Preview proposal
  const handleSelectProposal = (proposal) => {
    setSelectedProposalId(proposal.id);
    setActiveVoxelData(proposal.voxel_preview);
    setSliceY(proposal.voxel_preview.dimensions.height);
  };

  // Apply proposal
  const handleApplyProposal = async (proposalId) => {
    if (!sessionId) return;
    setIsLoading(true);
    try {
      const res = await fetch(`/api/variations/${sessionId}/apply/${proposalId}`, {
        method: 'POST'
      });
      if (res.ok) {
        const data = await res.json();
        setActiveVoxelData(data.preview);
        setSelectedProposalId(proposalId);
        const chosen = proposals.find((p) => p.id === proposalId);
        if (chosen) setActiveTitle(chosen.title);
        setStatusMessage(`Applied proposal: ${chosen?.title || proposalId}`);
      }
    } catch (err) {
      alert(`Error applying proposal: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  // Save to Library
  const handleSaveToLibrary = async (saveData) => {
    setIsLoading(true);
    try {
      const res = await fetch('/api/library/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          session_id: sessionId,
          title: saveData.title,
          author: saveData.author,
          description: saveData.description,
          tags: saveData.tags,
          formats: saveData.formats,
          voxel_data: activeVoxelData
        })
      });

      if (!res.ok) {
        throw new Error('Save to library failed');
      }

      await fetchLibrary();
      setStatusMessage(`Saved ${saveData.title} to Schematic Library`);
      setActiveTab('library');
    } catch (err) {
      alert(`Save error: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  // Load from library into studio
  const handleLoadItemIntoStudio = async (itemId, deriveNew = false) => {
    setIsLoading(true);
    try {
      const res = await fetch(`/api/library/${itemId}`);
      if (res.ok) {
        const voxelJson = await res.json();
        setActiveVoxelData(voxelJson);
        const itemMeta = libraryItems.find((i) => i.id === itemId);
        const baseTitle = itemMeta?.title || 'Library Structure';
        setActiveTitle(deriveNew ? `${baseTitle} (Variation)` : baseTitle);
        setSliceY(voxelJson.dimensions.height);
        setActiveTab('studio');

        if (deriveNew) {
          setWorkflowMode('creation');
          setRenderQuality('proper');
          setActiveStep(4);
          setStatusMessage(`Derived from ${baseTitle}. Generating new variations...`);
          const saveRes = await fetch('/api/library/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              session_id: '',
              title: `${baseTitle} Variant`,
              voxel_data: voxelJson
            })
          });
          if (saveRes.ok) {
            const savedData = await saveRes.json();
            setSessionId(savedData.item_id);
            await loadVariations(savedData.item_id);
          }
        } else {
          setRenderQuality('proper');
          setActiveStep(3);
          setStatusMessage(`Loaded ${baseTitle} into 3D Inspector`);
        }
      }
    } catch (err) {
      alert(`Failed to load item: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  const toggleRenderQuality = () => {
    setRenderQuality((prev) => (prev === 'draft' ? 'proper' : 'draft'));
  };

  const maxLayers = activeVoxelData?.dimensions?.height || 32;

  const getLiveRenderTitle = () => {
    if (activeStep === 1) return `Step 1 Preview: ${activeTitle}`;
    if (activeStep === 2) return `Step 2 Draft Isolation: ${activeTitle}`;
    if (activeStep === 3) return `Step 3 Proper 3D Render: ${activeTitle}`;
    return `Step 4 Finished Variation: ${activeTitle}`;
  };

  return (
    <div>
      {/* Top Header Bar */}
      <header className="header-bar">
        <div className="brand-wrapper">
          <div className="brand-icon">
            <Box size={22} />
          </div>
          <div>
            <h1 className="brand-title">Blueprint Studio</h1>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 2 }}>
              <span className="brand-badge">Minecraft 3D Engine</span>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                {statusMessage}
              </span>
            </div>
          </div>
        </div>

        <nav className="nav-tabs">
          <button
            id="tab-studio-btn"
            className={`nav-tab-btn ${activeTab === 'studio' ? 'active' : ''}`}
            onClick={() => setActiveTab('studio')}
          >
            <Compass size={17} />
            Studio & 3D Inspector
          </button>
          <button
            id="tab-library-btn"
            className={`nav-tab-btn ${activeTab === 'library' ? 'active' : ''}`}
            onClick={() => {
              fetchLibrary();
              setActiveTab('library');
            }}
          >
            <FolderArchive size={17} />
            Schematic Vault ({libraryItems.length})
          </button>
        </nav>
      </header>

      {/* Main Content Area */}
      <main className="dashboard-container">
        {activeTab === 'studio' ? (
          /* Two Column Split: Left is Step-by-Step, Right is Live 3D Render */
          <div className="grid-workspace" style={{ gridTemplateColumns: '460px 1fr', alignItems: 'start' }}>
            {/* LEFT COLUMN: Step-by-Step Wizard (Only Active Step is Expanded) */}
            <div>
              <StepWizard
                activeStep={activeStep}
                setActiveStep={setActiveStep}
                workflowMode={workflowMode}
                setWorkflowMode={setWorkflowMode}
                targetPreset={targetPreset}
                setTargetPreset={setTargetPreset}
                fileName={activeTitle}
                aiExplanation={aiExplanation}
                canUndo={canUndo}
                onIngestFile={handleIngestFile}
                onIngestUrl={handleIngestUrl}
                onRefineAction={handleRefineAction}
                onConfirmIsolation={handleConfirmIsolation}
                onConfirmLighting={handleConfirmLighting}
                sliceY={sliceY}
                setSliceY={setSliceY}
                maxLayers={maxLayers}
                paletteCounts={activeVoxelData?.stats?.palette_counts}
                proposals={proposals}
                selectedProposalId={selectedProposalId}
                onSelectProposal={handleSelectProposal}
                onApplyProposal={handleApplyProposal}
                onOpenExportModal={() => setIsExportModalOpen(true)}
                isLoading={isLoading}
              />
            </div>

            {/* RIGHT COLUMN: Live 3D Render of Current Interpretation */}
            <div>
              <VoxelViewer3D
                voxelData={activeVoxelData}
                sliceY={sliceY}
                renderQuality={renderQuality}
                onToggleQuality={toggleRenderQuality}
                liveTitle={getLiveRenderTitle()}
              />
            </div>
          </div>
        ) : (
          /* Schematic Vault / Library View */
          <LibraryVault
            libraryItems={libraryItems}
            onLoadItemIntoStudio={handleLoadItemIntoStudio}
            onRefresh={fetchLibrary}
          />
        )}
      </main>

      {/* Export to Library Modal */}
      <ExportModal
        isOpen={isExportModalOpen}
        onClose={() => setIsExportModalOpen(false)}
        onSave={handleSaveToLibrary}
        activeDesignName={activeTitle}
      />
    </div>
  );
}
