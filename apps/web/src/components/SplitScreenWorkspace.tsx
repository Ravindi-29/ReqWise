import React, { useState, useEffect, useRef } from 'react';
import {
  Requirement,
  RequirementIssue,
  ParsedBlock,
} from '../types';
import { api } from '../api/client';
import {
  AlertTriangle,
  CheckCircle,
  Sparkles,
  ChevronRight,
  Filter,
  Search,
  Check,
  Edit2,
  Save,
  X,
  Layers,
  MapPin,
  HelpCircle,
} from 'lucide-react';

interface SplitScreenWorkspaceProps {
  versionId: string;
}

export const SplitScreenWorkspace: React.FC<SplitScreenWorkspaceProps> = ({ versionId }) => {
  const [requirements, setRequirements] = useState<Requirement[]>([]);
  const [issues, setIssues] = useState<RequirementIssue[]>([]);
  const [blocks, setBlocks] = useState<ParsedBlock[]>([]);
  const [selectedReqId, setSelectedReqId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [editingReqId, setEditingReqId] = useState<string | null>(null);
  const [editedText, setEditedText] = useState('');
  const [isLoading, setIsLoading] = useState(true);

  const blockRefs = useRef<{ [key: string]: HTMLDivElement | null }>({});

  useEffect(() => {
    loadData();
  }, [versionId]);

  const loadData = async () => {
    try {
      setIsLoading(true);
      const [reqData, issueData, blockData] = await Promise.all([
        api.getRequirements(versionId),
        api.getIssues(versionId),
        api.getVersionBlocks(versionId),
      ]);
      setRequirements(reqData);
      setIssues(issueData);
      setBlocks(blockData);

      if (reqData.length > 0) {
        setSelectedReqId(reqData[0].id);
      }
    } catch (err) {
      console.error('Failed to load workspace data:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSelectReq = (req: Requirement) => {
    setSelectedReqId(req.id);

    // Synchronized Grounding: Scroll left document canvas to corresponding section/page block
    const matchingBlock = blocks.find(
      (b) =>
        b.pageNumber === req.pageNumber &&
        (b.sectionPath === req.sectionPath || b.text.includes(req.rawText.substring(0, 30)))
    );

    if (matchingBlock) {
      const key = `${matchingBlock.pageNumber}-${matchingBlock.sectionPath}`;
      blockRefs.current[key]?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  };

  const handleApplyRewrite = async (issueId: string) => {
    try {
      await api.applyRewrite(issueId);
      await loadData();
    } catch (err) {
      console.error('Failed to apply rewrite:', err);
    }
  };

  const handleStatusChange = async (reqId: string, reviewStatus: 'PENDING' | 'APPROVED' | 'NEEDS_REVISION') => {
    try {
      await api.updateRequirement(reqId, { reviewStatus });
      setRequirements((prev) =>
        prev.map((r) => (r.id === reqId ? { ...r, reviewStatus } : r))
      );
    } catch (err) {
      console.error('Failed to update status:', err);
    }
  };

  const handleSaveEdit = async (reqId: string) => {
    try {
      await api.updateRequirement(reqId, { normalizedText: editedText });
      setRequirements((prev) =>
        prev.map((r) => (r.id === reqId ? { ...r, normalizedText: editedText } : r))
      );
      setEditingReqId(null);
    } catch (err) {
      console.error('Failed to save requirement edit:', err);
    }
  };

  // Filter requirements
  const filteredRequirements = requirements.filter((r) => {
    const matchesSearch =
      r.reqCode.toLowerCase().includes(searchQuery.toLowerCase()) ||
      r.normalizedText.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (r.sectionPath && r.sectionPath.toLowerCase().includes(searchQuery.toLowerCase()));

    const matchesType =
      typeFilter === 'ALL' ||
      (typeFilter === 'FUNCTIONAL' && r.reqType === 'FUNCTIONAL') ||
      (typeFilter === 'NON_FUNCTIONAL' && r.reqType === 'NON_FUNCTIONAL') ||
      (typeFilter === 'CONSTRAINT' && r.reqType === 'CONSTRAINT') ||
      (r.nfrCategory === typeFilter);

    const matchesStatus = statusFilter === 'ALL' || r.reviewStatus === statusFilter;

    return matchesSearch && matchesType && matchesStatus;
  });

  const getIssuesForReq = (reqId: string) => {
    return issues.filter((i) => i.primaryReqId === reqId);
  };

  if (isLoading) {
    return (
      <div className="h-[calc(100vh-4rem)] flex items-center justify-center text-slate-400">
        <div className="flex items-center space-x-3 font-mono text-sm">
          <div className="w-5 h-5 border-2 border-sky-500 border-t-transparent rounded-full animate-spin" />
          <span>Synchronizing document grounding and ISO 29148 canvas...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="h-[calc(100vh-4rem)] flex flex-col md:flex-row overflow-hidden bg-slate-950">
      {/* ========================================================= */}
      {/* LEFT PANE: Grounded Interactive Document Canvas           */}
      {/* ========================================================= */}
      <div className="w-full md:w-1/2 h-full border-r border-slate-800 flex flex-col bg-slate-900/60 overflow-hidden">
        {/* Canvas Toolbar */}
        <div className="h-12 border-b border-slate-800 px-4 flex items-center justify-between bg-slate-900 text-xs">
          <div className="flex items-center space-x-2 text-slate-300">
            <Layers className="w-4 h-4 text-sky-400" />
            <span className="font-semibold">Document Layout & Bounding Box Canvas</span>
          </div>
          <div className="flex items-center space-x-2 text-slate-400 text-[11px] font-mono">
            <span>{blocks.length} parsed blocks</span>
            <span>•</span>
            <span className="flex items-center space-x-1">
              <span className="w-2 h-2 rounded-full bg-emerald-500" />
              <span>Compliant</span>
            </span>
            <span className="flex items-center space-x-1">
              <span className="w-2 h-2 rounded-full bg-amber-500" />
              <span>Ambiguous</span>
            </span>
            <span className="flex items-center space-x-1">
              <span className="w-2 h-2 rounded-full bg-rose-500" />
              <span>Conflict</span>
            </span>
          </div>
        </div>

        {/* Document Pages Canvas Container */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {blocks.length === 0 ? (
            <div className="text-center py-20 text-slate-500 text-sm">
              No structural blocks found. Upload an SRS to view grounded layout.
            </div>
          ) : (
            blocks.map((block, idx) => {
              const key = `${block.pageNumber}-${block.sectionPath}`;
              const isSelected = requirements.some(
                (r) =>
                  r.id === selectedReqId &&
                  r.pageNumber === block.pageNumber &&
                  (r.sectionPath === block.sectionPath || block.text.includes(r.rawText.substring(0, 30)))
              );

              // Check if block has associated issues
              const hasCritical = issues.some(
                (i) => i.severity === 'CRITICAL' && block.text.includes(i.flaggedPhrase || '')
              );
              const hasAmbiguity = issues.some(
                (i) => i.issueCategory === 'AMBIGUOUS' && block.text.includes(i.flaggedPhrase || '')
              );

              let borderColor = 'border-slate-800';
              let badgeColor = 'bg-slate-800 text-slate-300';
              if (hasCritical) {
                borderColor = 'border-rose-500/60';
                badgeColor = 'bg-rose-950 text-rose-300 border border-rose-800';
              } else if (hasAmbiguity) {
                borderColor = 'border-amber-500/60';
                badgeColor = 'bg-amber-950 text-amber-300 border border-amber-800';
              }

              return (
                <div
                  key={idx}
                  ref={(el) => {
                    blockRefs.current[key] = el;
                  }}
                  onClick={() => {
                    const matchedReq = requirements.find(
                      (r) =>
                        r.pageNumber === block.pageNumber &&
                        (r.sectionPath === block.sectionPath || block.text.includes(r.rawText.substring(0, 30)))
                    );
                    if (matchedReq) setSelectedReqId(matchedReq.id);
                  }}
                  className={`relative p-5 rounded-xl border bg-slate-900/90 shadow-md transition-all cursor-pointer ${borderColor} ${
                    isSelected
                      ? 'ring-2 ring-sky-500 shadow-sky-500/20 bg-slate-850 animate-pulse-highlight'
                      : 'hover:border-slate-700'
                  }`}
                >
                  {/* Page & Section Badge */}
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-semibold text-sky-400 font-mono flex items-center space-x-1.5">
                      <MapPin className="w-3.5 h-3.5" />
                      <span>{block.sectionPath}</span>
                    </span>
                    <span className={`text-[10px] font-mono px-2 py-0.5 rounded-full ${badgeColor}`}>
                      Page {block.pageNumber}
                    </span>
                  </div>

                  {/* Bounding Box Coordinates Tag */}
                  {block.boundingBox && (
                    <div className="text-[10px] font-mono text-slate-400 mb-2">
                      bbox: [{Math.round(block.boundingBox.x0)}, {Math.round(block.boundingBox.y0)},{' '}
                      {Math.round(block.boundingBox.x1)}, {Math.round(block.boundingBox.y1)}]
                    </div>
                  )}

                  {/* Block Content */}
                  <p className="text-sm text-slate-200 leading-relaxed font-sans">{block.text}</p>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* ========================================================= */}
      {/* RIGHT PANE: Requirements, Ambiguities & Rewrite Feed      */}
      {/* ========================================================= */}
      <div className="w-full md:w-1/2 h-full flex flex-col bg-slate-950 overflow-hidden">
        {/* Search & Filter Header */}
        <div className="p-4 border-b border-slate-800 space-y-3 bg-slate-900/40">
          <div className="flex items-center space-x-3">
            <div className="relative flex-1">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search requirements by code, keyword, or section..."
                className="w-full bg-slate-900 border border-slate-800 rounded-xl pl-9 pr-4 py-2 text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-sky-500"
              />
            </div>
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="bg-slate-900 border border-slate-800 text-xs text-slate-300 rounded-xl px-3 py-2 focus:outline-none focus:border-sky-500"
            >
              <option value="ALL">All Statuses</option>
              <option value="PENDING">Pending Review</option>
              <option value="APPROVED">Approved</option>
              <option value="NEEDS_REVISION">Needs Revision</option>
            </select>
          </div>

          {/* Quick Filter Pills */}
          <div className="flex items-center space-x-1.5 overflow-x-auto pb-1 text-xs">
            {['ALL', 'FUNCTIONAL', 'SECURITY', 'PERFORMANCE', 'RELIABILITY', 'USABILITY', 'CONSTRAINT'].map(
              (cat) => (
                <button
                  key={cat}
                  onClick={() => setTypeFilter(cat)}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-medium whitespace-nowrap transition ${
                    typeFilter === cat
                      ? 'bg-sky-600 text-white'
                      : 'bg-slate-900 text-slate-400 hover:text-slate-200 hover:bg-slate-800'
                  }`}
                >
                  {cat}
                </button>
              )
            )}
          </div>
        </div>

        {/* Requirements Stream List */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {filteredRequirements.length === 0 ? (
            <div className="text-center py-20 text-slate-500 text-sm">
              No matching requirements found for the selected filters.
            </div>
          ) : (
            filteredRequirements.map((req) => {
              const isSelected = req.id === selectedReqId;
              const reqIssues = getIssuesForReq(req.id);
              const isEditing = editingReqId === req.id;

              let clarityBadgeColor = 'bg-emerald-950 text-emerald-300 border-emerald-800';
              if (req.clarityScore < 75) {
                clarityBadgeColor = 'bg-rose-950 text-rose-300 border-rose-800';
              } else if (req.clarityScore < 90) {
                clarityBadgeColor = 'bg-amber-950 text-amber-300 border-amber-800';
              }

              return (
                <div
                  key={req.id}
                  onClick={() => handleSelectReq(req)}
                  className={`p-4 rounded-xl border transition-all ${
                    isSelected
                      ? 'bg-slate-900 border-sky-500/80 shadow-lg shadow-sky-950/40'
                      : 'bg-slate-900/50 border-slate-800/80 hover:border-slate-700'
                  }`}
                >
                  {/* Card Header */}
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center space-x-2">
                      <span className="font-mono font-bold text-xs text-sky-400">{req.reqCode}</span>
                      <span className="text-[10px] font-mono bg-slate-800 text-slate-300 px-2 py-0.5 rounded">
                        {req.reqType}
                        {req.nfrCategory ? ` : ${req.nfrCategory}` : ''}
                      </span>
                      {req.earsPattern && (
                        <span className="text-[10px] font-mono bg-indigo-950 text-indigo-300 border border-indigo-800/60 px-1.5 py-0.5 rounded">
                          {req.earsPattern}
                        </span>
                      )}
                    </div>

                    <div className="flex items-center space-x-2">
                      {/* Clarity Score Badge */}
                      <span
                        className={`text-xs font-mono font-semibold px-2 py-0.5 rounded-full border ${clarityBadgeColor}`}
                      >
                        Clarity: {req.clarityScore}%
                      </span>

                      {/* Status Selector */}
                      <select
                        value={req.reviewStatus}
                        onChange={(e) => handleStatusChange(req.id, e.target.value as any)}
                        onClick={(e) => e.stopPropagation()}
                        className="text-[11px] font-medium bg-slate-950 border border-slate-800 text-slate-300 rounded px-2 py-0.5 focus:outline-none focus:border-sky-500"
                      >
                        <option value="PENDING">PENDING</option>
                        <option value="APPROVED">APPROVED</option>
                        <option value="NEEDS_REVISION">REVISE</option>
                      </select>
                    </div>
                  </div>

                  {/* Section Path */}
                  <div className="text-xs text-slate-400 mb-2">
                    Section: <span className="text-slate-300 font-medium">{req.sectionPath}</span>
                  </div>

                  {/* Statement Body (View or Edit) */}
                  {isEditing ? (
                    <div className="space-y-2 mt-2">
                      <textarea
                        value={editedText}
                        onChange={(e) => setEditedText(e.target.value)}
                        className="w-full bg-slate-950 border border-slate-700 rounded-lg p-2.5 text-xs text-slate-200 focus:outline-none focus:border-sky-500 font-mono"
                        rows={3}
                      />
                      <div className="flex justify-end space-x-2">
                        <button
                          onClick={() => setEditingReqId(null)}
                          className="px-2.5 py-1 text-xs text-slate-400 hover:text-white"
                        >
                          Cancel
                        </button>
                        <button
                          onClick={() => handleSaveEdit(req.id)}
                          className="flex items-center space-x-1 px-3 py-1 bg-sky-600 hover:bg-sky-500 text-white rounded text-xs font-semibold"
                        >
                          <Save className="w-3.5 h-3.5" />
                          <span>Save</span>
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className="group relative">
                      <p className="text-xs text-slate-200 leading-relaxed font-sans font-medium">
                        {req.normalizedText}
                      </p>
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setEditingReqId(req.id);
                          setEditedText(req.normalizedText);
                        }}
                        className="absolute right-0 top-0 opacity-0 group-hover:opacity-100 p-1 text-slate-400 hover:text-white transition"
                        title="Edit statement"
                      >
                        <Edit2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  )}

                  {/* Flagged INCOSE Issues & One-Click Rewrites */}
                  {reqIssues.length > 0 && (
                    <div className="mt-3 space-y-2 pt-2 border-t border-slate-800/80">
                      {reqIssues.map((issue) => (
                        <div
                          key={issue.id}
                          className="p-2.5 rounded-lg bg-slate-950/80 border border-slate-800 space-y-2 text-xs"
                        >
                          <div className="flex items-center justify-between">
                            <div className="flex items-center space-x-2">
                              <span
                                className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${
                                  issue.severity === 'CRITICAL'
                                    ? 'bg-rose-950 text-rose-400 border border-rose-800'
                                    : issue.severity === 'HIGH'
                                    ? 'bg-amber-950 text-amber-400 border border-amber-800'
                                    : 'bg-slate-800 text-slate-300'
                                }`}
                              >
                                {issue.issueCategory}
                              </span>
                              {issue.flaggedPhrase && (
                                <span className="font-mono text-[11px] text-amber-300">
                                  "{issue.flaggedPhrase}"
                                </span>
                              )}
                            </div>
                            <span className="text-[10px] text-slate-400">
                              {issue.isResolved ? 'Resolved' : 'Active Defect'}
                            </span>
                          </div>

                          <p className="text-slate-400 text-[11px] leading-relaxed">
                            {issue.explanation}
                          </p>

                          {/* Suggested IEEE 29148 Rewrite Banner */}
                          {issue.suggestedRewrite && !issue.isResolved && (
                            <div className="mt-2 p-2 rounded bg-sky-950/40 border border-sky-800/60 flex items-start justify-between space-x-3">
                              <div>
                                <span className="text-[10px] font-bold text-sky-400 uppercase tracking-wider block mb-1">
                                  Suggested IEEE 29148 Rewrite
                                </span>
                                <p className="text-xs text-sky-200 font-mono">
                                  {issue.suggestedRewrite}
                                </p>
                              </div>
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleApplyRewrite(issue.id);
                                }}
                                className="shrink-0 flex items-center space-x-1 px-2.5 py-1 rounded bg-sky-600 hover:bg-sky-500 text-white text-[11px] font-semibold shadow-sm transition"
                              >
                                <Sparkles className="w-3.5 h-3.5" />
                                <span>Accept Rewrite</span>
                              </button>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};
