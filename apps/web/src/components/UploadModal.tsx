import React, { useState } from 'react';
import { X, UploadCloud, FileText, CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';
import { api } from '../api/client';
import { PipelineProgressEvent } from '../types';

interface UploadModalProps {
  isOpen: boolean;
  onClose: () => void;
  projectId: string;
  onVersionReady: (versionId: string) => void;
}

export const UploadModal: React.FC<UploadModalProps> = ({
  isOpen,
  onClose,
  projectId,
  onVersionReady,
}) => {
  const [file, setFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [progressEvent, setProgressEvent] = useState<PipelineProgressEvent | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const droppedFile = e.dataTransfer.files[0];
      if (droppedFile.name.endsWith('.pdf') || droppedFile.name.endsWith('.docx')) {
        setFile(droppedFile);
        setError(null);
      } else {
        setError('Only .pdf and .docx specification files are supported.');
      }
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0]);
      setError(null);
    }
  };

  const handleStartAnalysis = async () => {
    if (!file || !projectId) return;

    try {
      setIsUploading(true);
      setError(null);

      // 1. Upload Document & initiate Version
      const version = await api.uploadDocument(projectId, file);

      // 2. Subscribe to real-time Server-Sent Events (SSE)
      api.subscribePipelineStream(
        version.id,
        (progress) => {
          setProgressEvent(progress);
        },
        (complete) => {
          setProgressEvent(complete);
          setTimeout(() => {
            setIsUploading(false);
            onVersionReady(version.id);
            onClose();
          }, 1200);
        },
        (err) => {
          console.error('Pipeline SSE stream error:', err);
          setError('Analysis encountered an interruption. Verifying backend state...');
        }
      );
    } catch (err: any) {
      console.error('Upload failure:', err);
      setError(err.message || 'Failed to initiate document ingestion');
      setIsUploading(false);
    }
  };

  const getStageLabel = (stage?: string) => {
    switch (stage) {
      case 'PARSING':
        return 'Parsing Document Layout & Bounding Boxes...';
      case 'EXTRACTING':
        return 'Decomposing Atomic Requirements & INCOSE Audit...';
      case 'DETECTING_CONFLICTS':
        return 'Vector Deduplication & Contradiction Verification...';
      case 'BUILDING_RTM':
        return 'Constructing Traceability DAG & Executive Scorecard...';
      case 'COMPLETED':
        return 'Analysis Complete!';
      default:
        return 'Initializing Pipeline...';
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-lg shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <UploadCloud className="w-5 h-5 text-sky-400" />
            <h3 className="text-base font-semibold text-white">Upload Specification Document</h3>
          </div>
          {!isUploading && (
            <button onClick={onClose} className="text-slate-400 hover:text-white transition">
              <X className="w-5 h-5" />
            </button>
          )}
        </div>

        {/* Content */}
        <div className="p-6 space-y-6">
          {!isUploading ? (
            <>
              {/* Dropzone */}
              <div
                onDragOver={(e) => e.preventDefault()}
                onDrop={handleDrop}
                className="border-2 border-dashed border-slate-700 hover:border-sky-500/60 rounded-xl p-8 text-center transition-colors bg-slate-950/40 cursor-pointer"
                onClick={() => document.getElementById('file-upload-input')?.click()}
              >
                <input
                  id="file-upload-input"
                  type="file"
                  accept=".pdf,.docx"
                  onChange={handleFileChange}
                  className="hidden"
                />
                <FileText className="w-12 h-12 text-slate-500 mx-auto mb-3" />
                <p className="text-sm font-medium text-slate-200">
                  {file ? file.name : 'Drag & drop your SRS document here'}
                </p>
                <p className="text-xs text-slate-400 mt-1">
                  Supports Adobe PDF (.pdf) and Microsoft Word (.docx) specifications
                </p>
                {file && (
                  <div className="mt-3 inline-flex items-center space-x-1.5 text-xs text-sky-400 font-mono bg-sky-950/60 px-3 py-1 rounded-full border border-sky-800">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    <span>{(file.size / 1024 / 1024).toFixed(2)} MB ready</span>
                  </div>
                )}
              </div>

              {error && (
                <div className="flex items-center space-x-2 text-xs text-rose-400 bg-rose-950/30 border border-rose-900/50 p-3 rounded-lg">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  <span>{error}</span>
                </div>
              )}

              {/* Action Buttons */}
              <div className="flex items-center justify-end space-x-3 pt-2">
                <button
                  type="button"
                  onClick={onClose}
                  className="px-4 py-2 rounded-xl text-xs font-medium text-slate-400 hover:text-white transition"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  disabled={!file}
                  onClick={handleStartAnalysis}
                  className="px-5 py-2.5 rounded-xl text-xs font-semibold bg-gradient-to-r from-sky-500 to-blue-600 hover:from-sky-400 hover:to-blue-500 text-white disabled:opacity-50 disabled:cursor-not-allowed shadow-lg shadow-sky-500/20 transition-all"
                >
                  Start Intelligent Audit
                </button>
              </div>
            </>
          ) : (
            /* Live SSE Stream Progress */
            <div className="py-6 space-y-6 text-center">
              <div className="w-16 h-16 rounded-2xl bg-sky-950 border border-sky-800/60 flex items-center justify-center mx-auto shadow-inner shadow-sky-900/40">
                <Loader2 className="w-8 h-8 text-sky-400 animate-spin" />
              </div>

              <div className="space-y-2">
                <h4 className="text-sm font-semibold text-white">
                  {getStageLabel(progressEvent?.stage)}
                </h4>
                <p className="text-xs text-slate-400 max-w-sm mx-auto">
                  {progressEvent?.message || 'Processing document sections and bounding boxes...'}
                </p>
              </div>

              {/* Progress Bar */}
              <div className="space-y-1.5 text-left">
                <div className="flex justify-between text-xs text-slate-400 font-mono">
                  <span>Stage: {progressEvent?.stage || 'IN_PROGRESS'}</span>
                  <span>{progressEvent ? `${Math.round(progressEvent.percentage)}%` : '15%'}</span>
                </div>
                <div className="w-full h-2.5 bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-gradient-to-r from-sky-500 to-indigo-500 rounded-full transition-all duration-300"
                    style={{ width: `${progressEvent ? progressEvent.percentage : 15}%` }}
                  />
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
