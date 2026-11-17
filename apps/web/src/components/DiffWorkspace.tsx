import React, { useState, useEffect } from 'react';
import { VersionDiffResponse, DiffItem, DocumentVersion } from '../types';
import { api } from '../api/client';
import {
  GitCompare,
  AlertTriangle,
  ArrowRight,
  PlusCircle,
  MinusCircle,
  RefreshCw,
  Layers,
  Sparkles,
} from 'lucide-react';

interface DiffWorkspaceProps {
  currentVersionId: string;
  documentVersions: DocumentVersion[];
}

export const DiffWorkspace: React.FC<DiffWorkspaceProps> = ({
  currentVersionId,
  documentVersions,
}) => {
  const [baseVersionId, setBaseVersionId] = useState<string>('');
  const [targetVersionId, setTargetVersionId] = useState<string>(currentVersionId);
  const [diffData, setDiffData] = useState<VersionDiffResponse | null>(null);
  const [filterType, setFilterType] = useState<string>('ALL');
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (documentVersions.length >= 2) {
      setBaseVersionId(documentVersions[0].id);
      setTargetVersionId(documentVersions[documentVersions.length - 1].id);
    } else if (documentVersions.length === 1) {
      setBaseVersionId(documentVersions[0].id);
      setTargetVersionId(documentVersions[0].id);
    }
  }, [documentVersions]);

  useEffect(() => {
    if (baseVersionId && targetVersionId) {
      loadDiff();
    }
  }, [baseVersionId, targetVersionId]);

  const loadDiff = async () => {
    try {
      setIsLoading(true);
      const data = await api.getVersionDiff(baseVersionId, targetVersionId);
      setDiffData(data);
    } catch (err) {
      console.error('Failed to load version diff:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const filteredDiffs = diffData
    ? diffData.diffs.filter((d) => {
        if (filterType === 'BREAKING') return d.changeType === 'MODIFIED_BREAKING';
        if (filterType === 'EDITORIAL') return d.changeType === 'MODIFIED_EDITORIAL';
        if (filterType === 'ADDED') return d.changeType === 'ADDED';
        if (filterType === 'DELETED') return d.changeType === 'DELETED';
        return true;
      })
    : [];

  return (
    <div className="h-[calc(100vh-4rem)] flex flex-col bg-slate-950 overflow-hidden">
      {/* Top Controls Header */}
      <div className="h-16 border-b border-slate-800 px-6 flex items-center justify-between bg-slate-900/60">
        <div className="flex items-center space-x-4">
          <div className="flex items-center space-x-2">
            <GitCompare className="w-5 h-5 text-sky-400" />
            <h2 className="text-sm font-semibold text-white">Hungarian Bipartite Revision Tracking</h2>
          </div>

          {/* Version Selectors */}
          <div className="flex items-center space-x-2 text-xs">
            <span className="text-slate-400">Base (Vn):</span>
            <select
              value={baseVersionId}
              onChange={(e) => setBaseVersionId(e.target.value)}
              className="bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2.5 py-1 text-xs focus:border-sky-500"
            >
              {documentVersions.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.versionLabel} (v{v.versionNumber})
                </option>
              ))}
            </select>

            <ArrowRight className="w-4 h-4 text-slate-600" />

            <span className="text-slate-400">Target (Vn+1):</span>
            <select
              value={targetVersionId}
              onChange={(e) => setTargetVersionId(e.target.value)}
              className="bg-slate-900 border border-slate-800 text-slate-200 rounded-lg px-2.5 py-1 text-xs focus:border-sky-500"
            >
              {documentVersions.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.versionLabel} (v{v.versionNumber})
                </option>
              ))}
            </select>

            <button
              onClick={loadDiff}
              className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition"
              title="Re-run Diff"
            >
              <RefreshCw className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Change Stats Summary */}
        {diffData && (
          <div className="flex items-center space-x-2 text-xs font-mono">
            <span className="bg-emerald-950/60 text-emerald-300 border border-emerald-800/60 px-2 py-0.5 rounded">
              Unchanged: {diffData.totalUnchanged}
            </span>
            <span className="bg-sky-950/60 text-sky-300 border border-sky-800/60 px-2 py-0.5 rounded">
              Editorial: {diffData.totalEditorial}
            </span>
            <span className="bg-rose-950/60 text-rose-300 border border-rose-800/60 px-2 py-0.5 rounded">
              Breaking: {diffData.totalBreaking}
            </span>
            <span className="bg-indigo-950/60 text-indigo-300 border border-indigo-800/60 px-2 py-0.5 rounded">
              Added: {diffData.totalAdded}
            </span>
            <span className="bg-slate-800 text-slate-400 border border-slate-700 px-2 py-0.5 rounded">
              Deleted: {diffData.totalDeleted}
            </span>
          </div>
        )}
      </div>

      {/* Main Diff Stream Area */}
      <div className="flex-1 overflow-y-auto p-6 space-y-4">
        {/* Filter Bar */}
        <div className="flex items-center space-x-2 text-xs mb-2">
          {['ALL', 'BREAKING', 'EDITORIAL', 'ADDED', 'DELETED'].map((tab) => (
            <button
              key={tab}
              onClick={() => setFilterType(tab)}
              className={`px-3 py-1 rounded-lg text-xs font-medium transition ${
                filterType === tab
                  ? 'bg-sky-600 text-white'
                  : 'bg-slate-900 text-slate-400 hover:text-slate-200'
              }`}
            >
              {tab}
            </button>
          ))}
        </div>

        {isLoading ? (
          <div className="py-20 text-center text-slate-400 font-mono text-sm">
            <RefreshCw className="w-5 h-5 animate-spin mx-auto mb-2 text-sky-400" />
            <span>Computing Hungarian optimal bipartite alignment over dense vectors...</span>
          </div>
        ) : filteredDiffs.length === 0 ? (
          <div className="py-20 text-center text-slate-500 text-sm">
            No revision differences matching the filter.
          </div>
        ) : (
          filteredDiffs.map((diff) => {
            let badgeStyle = 'bg-slate-800 text-slate-300';
            if (diff.changeType === 'MODIFIED_BREAKING') {
              badgeStyle = 'bg-rose-950 text-rose-300 border border-rose-800';
            } else if (diff.changeType === 'MODIFIED_EDITORIAL') {
              badgeStyle = 'bg-sky-950 text-sky-300 border border-sky-800';
            } else if (diff.changeType === 'ADDED') {
              badgeStyle = 'bg-emerald-950 text-emerald-300 border border-emerald-800';
            } else if (diff.changeType === 'DELETED') {
              badgeStyle = 'bg-amber-950 text-amber-300 border border-amber-800';
            }

            return (
              <div
                key={diff.id}
                className="p-5 rounded-xl border border-slate-800 bg-slate-900/60 space-y-3 shadow-md hover:border-slate-700 transition"
              >
                {/* Diff Header */}
                <div className="flex items-center justify-between text-xs">
                  <div className="flex items-center space-x-3">
                    <span className={`px-2 py-0.5 rounded font-mono font-bold text-[10px] ${badgeStyle}`}>
                      {diff.changeType}
                    </span>
                    <span className="text-slate-400 font-mono">
                      {diff.baseReqCode || '-'} &rarr; {diff.targetReqCode || '-'}
                    </span>
                  </div>

                  <span className="font-mono text-slate-400 text-[11px]">
                    Cosine Similarity: {(diff.similarityScore * 100).toFixed(1)}%
                  </span>
                </div>

                {/* Side-by-side statement comparison */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs font-sans">
                  {/* Base Version Text */}
                  <div className="p-3 rounded-lg bg-slate-950/60 border border-slate-800/80 space-y-1">
                    <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">
                      Base Specification ({diff.baseReqCode || 'N/A'})
                    </span>
                    <p className="text-slate-300 leading-relaxed">
                      {diff.baseText || <span className="text-slate-600 italic">None (Added in target)</span>}
                    </p>
                  </div>

                  {/* Target Version Text */}
                  <div className="p-3 rounded-lg bg-slate-950/60 border border-slate-800/80 space-y-1">
                    <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider block">
                      Target Specification ({diff.targetReqCode || 'N/A'})
                    </span>
                    <p className="text-slate-200 leading-relaxed font-medium">
                      {diff.targetText || <span className="text-slate-600 italic">None (Deleted in target)</span>}
                    </p>
                  </div>
                </div>

                {/* Downstream Breaking Impact Alert */}
                {diff.impactSummary && (
                  <div className="p-3 rounded-lg bg-rose-950/40 border border-rose-900/60 flex items-start space-x-3 text-xs text-rose-300">
                    <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                    <div>
                      <strong className="font-semibold block mb-0.5">RTM Downstream Impact Warning:</strong>
                      <span>{diff.impactSummary}</span>
                    </div>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};
