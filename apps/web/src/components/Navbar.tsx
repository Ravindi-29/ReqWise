import React from 'react';
import { FileText, GitCompare, Network, BarChart3, Upload, ShieldCheck, Sparkles } from 'lucide-react';

interface NavbarProps {
  activeTab: 'canvas' | 'rtm' | 'diff' | 'scorecard';
  setActiveTab: (tab: 'canvas' | 'rtm' | 'diff' | 'scorecard') => void;
  onOpenUpload: () => void;
  currentDocumentTitle?: string;
  currentVersionLabel?: string;
  readinessIndex?: number;
}

export const Navbar: React.FC<NavbarProps> = ({
  activeTab,
  setActiveTab,
  onOpenUpload,
  currentDocumentTitle,
  currentVersionLabel,
  readinessIndex,
}) => {
  return (
    <header className="h-16 border-b border-slate-800 bg-slate-900/90 backdrop-blur px-6 flex items-center justify-between sticky top-0 z-50">
      {/* Brand & Project Info */}
      <div className="flex items-center space-x-6">
        <div className="flex items-center space-x-3 cursor-pointer" onClick={() => setActiveTab('canvas')}>
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-sky-600 to-indigo-500 flex items-center justify-center shadow-lg shadow-sky-500/20">
            <Sparkles className="w-5 h-5 text-white" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <span className="font-bold text-lg tracking-tight text-white">ReqWise</span>
              <span className="text-[10px] font-semibold bg-sky-950 text-sky-400 border border-sky-800/60 px-1.5 py-0.5 rounded">
                ISO 29148
              </span>
            </div>
            <p className="text-xs text-slate-400 font-mono">SRS Intelligence Engine</p>
          </div>
        </div>

        {currentDocumentTitle && (
          <div className="hidden md:flex items-center space-x-2 pl-4 border-l border-slate-800 text-sm">
            <span className="text-slate-400">Document:</span>
            <span className="text-slate-200 font-medium">{currentDocumentTitle}</span>
            <span className="bg-slate-800 text-slate-300 text-xs px-2 py-0.5 rounded-full font-mono border border-slate-700">
              {currentVersionLabel || 'v1.0'}
            </span>
          </div>
        )}
      </div>

      {/* Navigation Tabs */}
      <nav className="flex items-center space-x-1 bg-slate-950/60 p-1 rounded-xl border border-slate-800">
        <button
          onClick={() => setActiveTab('canvas')}
          className={`flex items-center space-x-2 px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all ${
            activeTab === 'canvas'
              ? 'bg-sky-600 text-white shadow-sm shadow-sky-600/30'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
          }`}
        >
          <FileText className="w-4 h-4" />
          <span>Grounded Audit</span>
        </button>

        <button
          onClick={() => setActiveTab('rtm')}
          className={`flex items-center space-x-2 px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all ${
            activeTab === 'rtm'
              ? 'bg-sky-600 text-white shadow-sm shadow-sky-600/30'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
          }`}
        >
          <Network className="w-4 h-4" />
          <span>Traceability (RTM)</span>
        </button>

        <button
          onClick={() => setActiveTab('diff')}
          className={`flex items-center space-x-2 px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all ${
            activeTab === 'diff'
              ? 'bg-sky-600 text-white shadow-sm shadow-sky-600/30'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
          }`}
        >
          <GitCompare className="w-4 h-4" />
          <span>Revision Diff</span>
        </button>

        <button
          onClick={() => setActiveTab('scorecard')}
          className={`flex items-center space-x-2 px-3.5 py-1.5 rounded-lg text-xs font-medium transition-all ${
            activeTab === 'scorecard'
              ? 'bg-sky-600 text-white shadow-sm shadow-sky-600/30'
              : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
          }`}
        >
          <BarChart3 className="w-4 h-4" />
          <span>Scorecard</span>
        </button>
      </nav>

      {/* Action / Upload */}
      <div className="flex items-center space-x-4">
        {readinessIndex !== undefined && (
          <div className="hidden lg:flex items-center space-x-2 bg-emerald-950/40 border border-emerald-800/60 px-3 py-1 rounded-lg">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <span className="text-xs text-slate-300">Readiness:</span>
            <span className="text-xs font-bold text-emerald-400 font-mono">{readinessIndex}%</span>
          </div>
        )}

        <button
          onClick={onOpenUpload}
          className="flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-semibold bg-gradient-to-r from-sky-500 to-blue-600 hover:from-sky-400 hover:to-blue-500 text-white shadow-md shadow-sky-500/20 transition-all cursor-pointer"
        >
          <Upload className="w-4 h-4" />
          <span>Upload SRS</span>
        </button>
      </div>
    </header>
  );
};
