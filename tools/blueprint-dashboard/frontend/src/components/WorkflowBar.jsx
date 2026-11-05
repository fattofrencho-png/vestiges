import React from 'react';
import { UploadCloud, BrainCircuit, Sparkles, CheckCircle2, ChevronRight } from 'lucide-react';

export default function WorkflowBar({ currentStep, onStepClick }) {
  const steps = [
    { id: 1, label: '1. Ingest & Target', icon: UploadCloud },
    { id: 2, label: '2. AI Guess & Draft', icon: BrainCircuit },
    { id: 3, label: '3. Proper 3D Lighting', icon: Sparkles },
    { id: 4, label: '4. Proposals & Vault', icon: CheckCircle2 }
  ];

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        marginBottom: 24,
        padding: '12px 20px',
        background: 'rgba(13, 17, 23, 0.65)',
        backdropFilter: 'blur(10px)',
        border: '1px solid var(--border-subtle)',
        borderRadius: 'var(--radius-lg)'
      }}
      id="workflow-stepper-bar"
    >
      {steps.map((step, idx) => {
        const Icon = step.icon;
        const isActive = currentStep === step.id;
        const isCompleted = currentStep > step.id;

        return (
          <React.Fragment key={step.id}>
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                padding: '6px 14px',
                borderRadius: 'var(--radius-sm)',
                background: isActive
                  ? 'rgba(16, 185, 129, 0.18)'
                  : isCompleted
                  ? 'rgba(6, 182, 212, 0.1)'
                  : 'transparent',
                border: isActive
                  ? '1px solid rgba(16, 185, 129, 0.4)'
                  : '1px solid transparent',
                color: isActive ? '#fff' : isCompleted ? 'var(--accent-cyan)' : 'var(--text-dim)',
                cursor: 'pointer',
                transition: 'all 0.2s ease',
                fontWeight: isActive ? 600 : 400,
                fontSize: '0.85rem'
              }}
              onClick={() => onStepClick && onStepClick(step.id)}
            >
              <Icon size={16} color={isActive ? 'var(--accent-emerald)' : isCompleted ? 'var(--accent-cyan)' : 'var(--text-dim)'} />
              <span>{step.label}</span>
            </div>

            {idx < steps.length - 1 && (
              <ChevronRight size={14} color="var(--border-subtle)" />
            )}
          </React.Fragment>
        );
      })}
    </div>
  );
}
