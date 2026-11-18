import React from 'react';
import { FileText, GitCompare, Network, BarChart3, Upload, ShieldCheck, Sparkles, LogOut, User as UserIcon } from 'lucide-react';
import { AuthUser } from '../types';

interface NavbarProps {
  activeTab: 'canvas' | 'rtm' | 'diff' | 'scorecard';
  setActiveTab: (tab: 'canvas' | 'rtm' | 'diff' | 'scorecard') => void;
  onOpenUpload: () => void;
  currentDocumentTitle?: string;
  currentVersionLabel?: string;
  readinessIndex?: number;
  currentUser?: AuthUser | null;
  onSignOut?: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({
  activeTab,
  setActiveTab,
  onOpenUpload,
  currentDocumentTitle,
  currentVersionLabel,
  readinessIndex,
  currentUser,
  onSignOut,
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

        {/* User Profile & Sign Out */}
        {currentUser && (
          <div className="flex items-center space-x-3 pl-3 border-l border-slate-800">
            <div className="flex items-center space-x-2">
              <div className="w-8 h-8 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-xs font-bold text-sky-400">
                {currentUser.firstName ? currentUser.firstName.charAt(0).toUpperCase() : (currentUser.username ? currentUser.username.charAt(0).toUpperCase() : 'U')}
              </div>
              <div className="hidden xl:block text-left">
                <p className="text-xs font-semibold text-slate-200 leading-tight">
                  {currentUser.firstName ? `${currentUser.firstName} ${currentUser.lastName || ''}`.trim() : currentUser.username}
                </p>
                <p className="text-[10px] text-slate-400 font-mono truncate max-w-[120px]">
                  {currentUser.email}
                </p>
              </div>
            </div>

            {onSignOut && (
              <button
                onClick={onSignOut}
                title="Sign Out"
                className="p-1.5 rounded-lg text-slate-400 hover:text-red-400 hover:bg-slate-800 transition-colors"
              >
                <LogOut className="w-4 h-4" />
              </button>
            )}
          </div>
        )}
      </div>
    </header>
  );
};
