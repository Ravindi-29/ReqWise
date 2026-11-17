import React, { useState, useEffect } from 'react';
import { RtmGraphResponse, RtmNode, RtmEdge } from '../types';
import { api } from '../api/client';
import {
  Network,
  Table,
  Plus,
  AlertOctagon,
  CheckCircle2,
  HelpCircle,
  Trash2,
  ArrowRight,
  Filter,
} from 'lucide-react';

interface RtmWorkspaceProps {
  versionId: string;
}

export const RtmWorkspace: React.FC<RtmWorkspaceProps> = ({ versionId }) => {
  const [graphData, setGraphData] = useState<RtmGraphResponse | null>(null);
  const [viewMode, setViewMode] = useState<'table' | 'graph'>('table');
  const [filterGap, setFilterGap] = useState<'ALL' | 'ORPHAN' | 'UNVERIFIED'>('ALL');
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [sourceReqId, setSourceReqId] = useState('');
  const [targetReqId, setTargetReqId] = useState('');
  const [linkType, setLinkType] = useState('DEPENDS_ON');
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    loadGraph();
  }, [versionId]);

  const loadGraph = async () => {
    try {
      setIsLoading(true);
      const data = await api.getRtmGraph(versionId);
      setGraphData(data);
    } catch (err) {
      console.error('Failed to load RTM graph:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleCreateLink = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!sourceReqId || !targetReqId) return;

    try {
      await api.createRtmLink(versionId, { sourceReqId, targetReqId, linkType });
      setIsAddModalOpen(false);
      setSourceReqId('');
      setTargetReqId('');
      await loadGraph();
    } catch (err) {
      console.error('Failed to create traceability link:', err);
    }
  };

  const handleDeleteLink = async (linkId: string) => {
    try {
      await api.deleteRtmLink(linkId);
      await loadGraph();
    } catch (err) {
      console.error('Failed to delete link:', err);
    }
  };

  if (isLoading || !graphData) {
    return (
      <div className="h-[calc(100vh-4rem)] flex items-center justify-center text-slate-400 font-mono text-sm">
        <div className="w-5 h-5 border-2 border-sky-500 border-t-transparent rounded-full animate-spin mr-3" />
        <span>Evaluating Requirement Traceability Matrix (RTM) DAG...</span>
      </div>
    );
  }

  // Filter nodes for table
  const filteredNodes = graphData.nodes.filter((node) => {
    if (filterGap === 'ORPHAN') return node.isOrphan;
    if (filterGap === 'UNVERIFIED') return node.isUnverified;
    return true;
  });

  // Upstream / Downstream lookup
  const upstreamMap: { [key: string]: RtmEdge[] } = {};
  const downstreamMap: { [key: string]: RtmEdge[] } = {};
  graphData.edges.forEach((edge) => {
    if (!downstreamMap[edge.sourceId]) downstreamMap[edge.sourceId] = [];
    downstreamMap[edge.sourceId].push(edge);

    if (!upstreamMap[edge.targetId]) upstreamMap[edge.targetId] = [];
    upstreamMap[edge.targetId].push(edge);
  });

  return (
    <div className="h-[calc(100vh-4rem)] flex flex-col bg-slate-950 overflow-hidden">
      {/* Top Header & Stats Bar */}
      <div className="h-16 border-b border-slate-800 px-6 flex items-center justify-between bg-slate-900/60">
        <div className="flex items-center space-x-6">
          <div className="flex items-center space-x-3">
            <Network className="w-5 h-5 text-sky-400" />
            <h2 className="text-sm font-semibold text-white">Requirement Traceability Matrix</h2>
          </div>

          {/* Quick Metrics */}
          <div className="hidden md:flex items-center space-x-3 text-xs font-mono">
            <span className="bg-slate-800 text-slate-300 px-2.5 py-1 rounded-lg border border-slate-700">
              Coverage: <strong className="text-sky-400">{graphData.coveragePercentage}%</strong>
            </span>
            <span className="bg-amber-950/60 text-amber-300 px-2.5 py-1 rounded-lg border border-amber-800/60">
              Orphans: <strong>{graphData.orphanCount}</strong>
            </span>
            <span className="bg-rose-950/60 text-rose-300 px-2.5 py-1 rounded-lg border border-rose-800/60">
              Unverified: <strong>{graphData.unverifiedCount}</strong>
            </span>
          </div>
        </div>

        {/* View Toggle & Actions */}
        <div className="flex items-center space-x-3">
          <div className="flex items-center bg-slate-900 border border-slate-800 p-0.5 rounded-xl text-xs">
            <button
              onClick={() => setViewMode('table')}
              className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg font-medium transition ${
                viewMode === 'table' ? 'bg-sky-600 text-white' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Table className="w-3.5 h-3.5" />
              <span>Grid Matrix</span>
            </button>
            <button
              onClick={() => setViewMode('graph')}
              className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg font-medium transition ${
                viewMode === 'graph' ? 'bg-sky-600 text-white' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Network className="w-3.5 h-3.5" />
              <span>DAG Graph</span>
            </button>
          </div>

          <button
            onClick={() => setIsAddModalOpen(true)}
            className="flex items-center space-x-1.5 px-3.5 py-1.5 rounded-xl text-xs font-semibold bg-sky-600 hover:bg-sky-500 text-white transition shadow-sm"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add Link</span>
          </button>
        </div>
      </div>

      {/* Main Workspace Area */}
      <div className="flex-1 overflow-auto p-6">
        {viewMode === 'table' ? (
          /* Table Matrix Grid View */
          <div className="space-y-4">
            {/* Filter Bar */}
            <div className="flex items-center space-x-2 text-xs">
              <span className="text-slate-400 font-medium flex items-center space-x-1">
                <Filter className="w-3.5 h-3.5" />
                <span>Filter Gaps:</span>
              </span>
              <button
                onClick={() => setFilterGap('ALL')}
                className={`px-3 py-1 rounded-lg ${
                  filterGap === 'ALL'
                    ? 'bg-sky-600 text-white'
                    : 'bg-slate-900 text-slate-400 hover:text-slate-200'
                }`}
              >
                All Specifications ({graphData.nodes.length})
              </button>
              <button
                onClick={() => setFilterGap('ORPHAN')}
                className={`px-3 py-1 rounded-lg ${
                  filterGap === 'ORPHAN'
                    ? 'bg-amber-600 text-white'
                    : 'bg-slate-900 text-slate-400 hover:text-slate-200'
                }`}
              >
                Orphan Specs ({graphData.orphanCount})
              </button>
              <button
                onClick={() => setFilterGap('UNVERIFIED')}
                className={`px-3 py-1 rounded-lg ${
                  filterGap === 'UNVERIFIED'
                    ? 'bg-rose-600 text-white'
                    : 'bg-slate-900 text-slate-400 hover:text-slate-200'
                }`}
              >
                Unverified Specs ({graphData.unverifiedCount})
              </button>
            </div>

            {/* Matrix Table */}
            <div className="border border-slate-800 rounded-xl overflow-hidden bg-slate-900/60 shadow-xl">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="bg-slate-900 border-b border-slate-800 text-slate-400 font-semibold uppercase tracking-wider text-[11px]">
                    <th className="py-3.5 px-4">Req Code</th>
                    <th className="py-3.5 px-4">Section</th>
                    <th className="py-3.5 px-4">Type / Category</th>
                    <th className="py-3.5 px-4 min-w-[280px]">Atomic Statement</th>
                    <th className="py-3.5 px-4">Upstream Source</th>
                    <th className="py-3.5 px-4">Downstream Verification</th>
                    <th className="py-3.5 px-4">Traceability Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/80">
                  {filteredNodes.map((node) => {
                    const upLinks = upstreamMap[node.id] || [];
                    const downLinks = downstreamMap[node.id] || [];

                    const hasUp = upLinks.length > 0 || node.sectionPath.startsWith('1.');
                    const hasDown = downLinks.length > 0;
                    const isComplete = hasUp && hasDown;

                    return (
                      <tr key={node.id} className="hover:bg-slate-800/40 transition">
                        <td className="py-3 px-4 font-mono font-bold text-sky-400">{node.reqCode}</td>
                        <td className="py-3 px-4 text-slate-400 font-mono text-[11px]">{node.sectionPath}</td>
                        <td className="py-3 px-4">
                          <span className="bg-slate-800 text-slate-300 text-[10px] font-mono px-2 py-0.5 rounded">
                            {node.reqType}
                            {node.nfrCategory ? ` : ${node.nfrCategory}` : ''}
                          </span>
                        </td>
                        <td className="py-3 px-4 text-slate-200 leading-relaxed font-sans">{node.label}</td>
                        <td className="py-3 px-4">
                          {upLinks.length > 0 ? (
                            <div className="flex flex-wrap gap-1">
                              {upLinks.map((e) => (
                                <span
                                  key={e.id}
                                  className="font-mono text-[10px] bg-emerald-950 text-emerald-400 border border-emerald-800/60 px-1.5 py-0.5 rounded"
                                >
                                  {e.sourceReqCode} ({e.linkType})
                                </span>
                              ))}
                            </div>
                          ) : (
                            <span className="font-mono text-[10px] text-amber-400 bg-amber-950/60 border border-amber-800/60 px-2 py-0.5 rounded">
                              ORPHAN (No Parent)
                            </span>
                          )}
                        </td>
                        <td className="py-3 px-4">
                          {downLinks.length > 0 ? (
                            <div className="flex flex-wrap gap-1">
                              {downLinks.map((e) => (
                                <span
                                  key={e.id}
                                  className="font-mono text-[10px] bg-sky-950 text-sky-400 border border-sky-800/60 px-1.5 py-0.5 rounded"
                                >
                                  {e.targetReqCode} ({e.linkType})
                                </span>
                              ))}
                            </div>
                          ) : (
                            <span className="font-mono text-[10px] text-rose-400 bg-rose-950/60 border border-rose-800/60 px-2 py-0.5 rounded">
                              UNVERIFIED
                            </span>
                          )}
                        </td>
                        <td className="py-3 px-4">
                          {isComplete ? (
                            <span className="inline-flex items-center space-x-1 text-[11px] font-semibold text-emerald-400">
                              <CheckCircle2 className="w-3.5 h-3.5" />
                              <span>COMPLETE</span>
                            </span>
                          ) : (
                            <span className="inline-flex items-center space-x-1 text-[11px] font-semibold text-amber-400">
                              <AlertOctagon className="w-3.5 h-3.5" />
                              <span>GAP DETECTED</span>
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        ) : (
          /* Interactive DAG Graph Visualization View */
          <div className="h-full border border-slate-800 rounded-xl bg-slate-900/60 p-6 flex flex-col relative overflow-hidden">
            <div className="text-xs text-slate-400 font-mono mb-4 flex items-center justify-between">
              <span>Interactive Dependency Graph: Nodes ({graphData.nodes.length}) | Edges ({graphData.edges.length})</span>
              <div className="flex items-center space-x-4 text-[11px]">
                <span className="flex items-center space-x-1.5">
                  <span className="w-3 h-0.5 bg-emerald-500 inline-block" />
                  <span>REFINES</span>
                </span>
                <span className="flex items-center space-x-1.5">
                  <span className="w-3 h-0.5 bg-amber-500 inline-block" />
                  <span>CONSTRAINS</span>
                </span>
                <span className="flex items-center space-x-1.5">
                  <span className="w-3 h-0.5 bg-sky-500 inline-block" />
                  <span>DEPENDS_ON</span>
                </span>
              </div>
            </div>

            {/* Nodes Layout Grid */}
            <div className="flex-1 overflow-auto grid grid-cols-1 md:grid-cols-3 gap-6 p-4">
              {/* Column 1: Upstream Business Goals */}
              <div className="space-y-4">
                <h4 className="text-xs font-semibold text-slate-400 uppercase tracking-wider pb-2 border-b border-slate-800">
                  1. Business Goals & Objectives
                </h4>
                {graphData.nodes
                  .filter((n) => n.sectionPath.startsWith('1.') || n.reqCode.startsWith('BR-'))
                  .map((node) => (
                    <div
                      key={node.id}
                      onClick={() => setSelectedNodeId(node.id)}
                      className={`p-3.5 rounded-xl border bg-slate-900 cursor-pointer transition ${
                        selectedNodeId === node.id
                          ? 'border-emerald-500 ring-2 ring-emerald-500/20 bg-slate-850'
                          : 'border-slate-800 hover:border-slate-700'
                      }`}
                    >
                      <div className="flex items-center justify-between mb-1.5">
                        <span className="font-mono font-bold text-xs text-emerald-400">{node.reqCode}</span>
                        <span className="text-[10px] text-slate-400">{node.sectionPath}</span>
                      </div>
                      <p className="text-xs text-slate-200 leading-snug line-clamp-3">{node.label}</p>
                    </div>
                  ))}
              </div>

              {/* Column 2: Functional Requirements */}
              <div className="space-y-4">
                <h4 className="text-xs font-semibold text-slate-400 uppercase tracking-wider pb-2 border-b border-slate-800">
                  2. Functional Requirements
                </h4>
                {graphData.nodes
                  .filter((n) => n.reqType === 'FUNCTIONAL' && !n.sectionPath.startsWith('1.') && !n.reqCode.startsWith('BR-'))
                  .map((node) => (
                    <div
                      key={node.id}
                      onClick={() => setSelectedNodeId(node.id)}
                      className={`p-3.5 rounded-xl border bg-slate-900 cursor-pointer transition ${
                        selectedNodeId === node.id
                          ? 'border-sky-500 ring-2 ring-sky-500/20 bg-slate-850'
                          : 'border-slate-800 hover:border-slate-700'
                      }`}
                    >
                      <div className="flex items-center justify-between mb-1.5">
                        <span className="font-mono font-bold text-xs text-sky-400">{node.reqCode}</span>
                        <span className="text-[10px] text-slate-400">{node.sectionPath}</span>
                      </div>
                      <p className="text-xs text-slate-200 leading-snug line-clamp-3">{node.label}</p>
                    </div>
                  ))}
              </div>

              {/* Column 3: Non-Functional & Constraints */}
              <div className="space-y-4">
                <h4 className="text-xs font-semibold text-slate-400 uppercase tracking-wider pb-2 border-b border-slate-800">
                  3. NFR Constraints & Verification
                </h4>
                {graphData.nodes
                  .filter((n) => n.reqType === 'NON_FUNCTIONAL' || n.reqType === 'CONSTRAINT')
                  .map((node) => (
                    <div
                      key={node.id}
                      onClick={() => setSelectedNodeId(node.id)}
                      className={`p-3.5 rounded-xl border bg-slate-900 cursor-pointer transition ${
                        selectedNodeId === node.id
                          ? 'border-amber-500 ring-2 ring-amber-500/20 bg-slate-850'
                          : 'border-slate-800 hover:border-slate-700'
                      }`}
                    >
                      <div className="flex items-center justify-between mb-1.5">
                        <span className="font-mono font-bold text-xs text-amber-400">{node.reqCode}</span>
                        <span className="text-[10px] text-slate-400 font-mono">{node.nfrCategory}</span>
                      </div>
                      <p className="text-xs text-slate-200 leading-snug line-clamp-3">{node.label}</p>
                    </div>
                  ))}
              </div>
            </div>

            {/* Edge details drawer when a node is selected */}
            {selectedNodeId && (
              <div className="h-32 border-t border-slate-800 bg-slate-950 p-4 flex items-center justify-between text-xs">
                <div className="space-y-1">
                  <span className="text-slate-400 font-mono">Selected Requirement:</span>
                  <p className="text-white font-medium">
                    {graphData.nodes.find((n) => n.id === selectedNodeId)?.label}
                  </p>
                </div>
                <div className="flex items-center space-x-2">
                  <button
                    onClick={() => setSelectedNodeId(null)}
                    className="px-3 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded text-xs"
                  >
                    Clear Selection
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Manual Link Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-md p-6 space-y-4">
            <h3 className="text-sm font-semibold text-white">Create Traceability Link</h3>
            <form onSubmit={handleCreateLink} className="space-y-4 text-xs">
              <div>
                <label className="block text-slate-400 mb-1">Source Requirement</label>
                <select
                  value={sourceReqId}
                  onChange={(e) => setSourceReqId(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2.5 focus:border-sky-500"
                  required
                >
                  <option value="">Select source requirement...</option>
                  {graphData.nodes.map((n) => (
                    <option key={n.id} value={n.id}>
                      {n.reqCode} - {n.label.substring(0, 45)}...
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-slate-400 mb-1">Target Requirement (Parent / Target)</label>
                <select
                  value={targetReqId}
                  onChange={(e) => setTargetReqId(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2.5 focus:border-sky-500"
                  required
                >
                  <option value="">Select target requirement...</option>
                  {graphData.nodes.map((n) => (
                    <option key={n.id} value={n.id}>
                      {n.reqCode} - {n.label.substring(0, 45)}...
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-slate-400 mb-1">Traceability Link Type</label>
                <select
                  value={linkType}
                  onChange={(e) => setLinkType(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 text-slate-200 rounded-lg p-2.5 focus:border-sky-500"
                >
                  <option value="REFINES">REFINES (Refines upstream objective)</option>
                  <option value="CONSTRAINS">CONSTRAINS (NFR constrains FR)</option>
                  <option value="DEPENDS_ON">DEPENDS_ON (Prerequisite dependency)</option>
                  <option value="VERIFIED_BY">VERIFIED_BY (Verification criteria)</option>
                </select>
              </div>

              <div className="flex justify-end space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="px-3.5 py-2 text-slate-400 hover:text-white"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-sky-600 hover:bg-sky-500 text-white rounded-lg font-semibold"
                >
                  Create Link
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
