import React, { useState, useEffect } from 'react';
import { ExecutiveScorecardResponse } from '../types';
import { api } from '../api/client';
import {
  BarChart3,
  Download,
  ShieldCheck,
  FileSpreadsheet,
  FileText,
  AlertTriangle,
  CheckCircle2,
  TrendingUp,
  Layers,
} from 'lucide-react';

interface ScorecardWorkspaceProps {
  versionId: string;
}

export const ScorecardWorkspace: React.FC<ScorecardWorkspaceProps> = ({ versionId }) => {
  const [scorecard, setScorecard] = useState<ExecutiveScorecardResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    loadScorecard();
  }, [versionId]);

  const loadScorecard = async () => {
    try {
      setIsLoading(true);
      const data = await api.getScorecard(versionId);
      setScorecard(data);
    } catch (err) {
      console.error('Failed to load executive scorecard:', err);
    } finally {
      setIsLoading(false);
    }
  };

  if (isLoading || !scorecard) {
    return (
      <div className="h-[calc(100vh-4rem)] flex items-center justify-center text-slate-400 font-mono text-sm">
        <div className="w-5 h-5 border-2 border-sky-500 border-t-transparent rounded-full animate-spin mr-3" />
        <span>Synthesizing Executive Scorecard & ISO 25010 Analytics...</span>
      </div>
    );
  }

  return (
    <div className="h-[calc(100vh-4rem)] flex flex-col bg-slate-950 overflow-y-auto p-8 space-y-8">
      {/* Top Banner & Export Actions */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-slate-800">
        <div>
          <div className="flex items-center space-x-2">
            <h2 className="text-xl font-bold text-white tracking-tight">Executive Quality Scorecard</h2>
            <span className="bg-sky-950 text-sky-400 border border-sky-800 text-[11px] font-mono font-semibold px-2 py-0.5 rounded">
              ISO/IEC/IEEE 29148
            </span>
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Readiness diagnostic, defect density, ISO 25010 categorization, and audit sign-off packages.
          </p>
        </div>

        {/* Export Buttons */}
        <div className="flex items-center space-x-3">
          <a
            href={api.getXlsxExportUrl(versionId)}
            download
            className="flex items-center space-x-2 px-4 py-2.5 rounded-xl text-xs font-semibold bg-emerald-600 hover:bg-emerald-500 text-white shadow-lg shadow-emerald-600/20 transition cursor-pointer"
          >
            <FileSpreadsheet className="w-4 h-4" />
            <span>Download Multi-Sheet Excel (.xlsx)</span>
          </a>

          <a
            href={api.getPdfExportUrl(versionId)}
            download
            className="flex items-center space-x-2 px-4 py-2.5 rounded-xl text-xs font-semibold bg-rose-600 hover:bg-rose-500 text-white shadow-lg shadow-rose-600/20 transition cursor-pointer"
          >
            <FileText className="w-4 h-4" />
            <span>Download Audit Report (.pdf)</span>
          </a>
        </div>
      </div>

      {/* KPI Cards Row */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-5">
        {/* Readiness Index */}
        <div className="p-5 rounded-2xl bg-gradient-to-br from-slate-900 to-slate-950 border border-slate-800 shadow-xl space-y-2">
          <div className="flex items-center justify-between text-xs text-slate-400 font-medium">
            <span>Readiness Index</span>
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="flex items-baseline space-x-2">
            <span className="text-3xl font-extrabold text-white font-mono">{scorecard.readinessIndex}</span>
            <span className="text-xs text-slate-500 font-mono">/ 100</span>
          </div>
          <p className="text-[11px] text-slate-400">
            {scorecard.readinessIndex >= 80
              ? 'Ready for engineering kickoff'
              : 'Requires resolution of flagged defects'}
          </p>
        </div>

        {/* Overall Clarity Score */}
        <div className="p-5 rounded-2xl bg-gradient-to-br from-slate-900 to-slate-950 border border-slate-800 shadow-xl space-y-2">
          <div className="flex items-center justify-between text-xs text-slate-400 font-medium">
            <span>Overall Clarity Score</span>
            <TrendingUp className="w-4 h-4 text-sky-400" />
          </div>
          <div className="flex items-baseline space-x-2">
            <span className="text-3xl font-extrabold text-white font-mono">{scorecard.overallClarityScore}%</span>
          </div>
          <p className="text-[11px] text-slate-400">
            Evaluated against INCOSE & IEEE linguistic rules
          </p>
        </div>

        {/* Traceability Coverage */}
        <div className="p-5 rounded-2xl bg-gradient-to-br from-slate-900 to-slate-950 border border-slate-800 shadow-xl space-y-2">
          <div className="flex items-center justify-between text-xs text-slate-400 font-medium">
            <span>RTM Traceability</span>
            <Layers className="w-4 h-4 text-indigo-400" />
          </div>
          <div className="flex items-baseline space-x-2">
            <span className="text-3xl font-extrabold text-white font-mono">{scorecard.traceabilityCoverage}%</span>
          </div>
          <p className="text-[11px] text-slate-400">
            Connected across business & verification paths
          </p>
        </div>

        {/* Defect Density */}
        <div className="p-5 rounded-2xl bg-gradient-to-br from-slate-900 to-slate-950 border border-slate-800 shadow-xl space-y-2">
          <div className="flex items-center justify-between text-xs text-slate-400 font-medium">
            <span>Defect Density</span>
            <AlertTriangle className="w-4 h-4 text-amber-400" />
          </div>
          <div className="flex items-baseline space-x-2">
            <span className="text-3xl font-extrabold text-white font-mono">{scorecard.defectDensity}</span>
            <span className="text-xs text-slate-500 font-mono">defects/req</span>
          </div>
          <p className="text-[11px] text-slate-400">
            {scorecard.totalIssues} total defects across {scorecard.totalRequirements} specs
          </p>
        </div>
      </div>

      {/* Analytics Breakdown Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* ISO 25010 Requirements Distribution */}
        <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800 space-y-4">
          <div className="flex items-center space-x-2">
            <BarChart3 className="w-4 h-4 text-sky-400" />
            <h3 className="text-sm font-semibold text-white">ISO 25010 Quality Categorization</h3>
          </div>

          <div className="space-y-3">
            {Object.entries(scorecard.iso25010Breakdown).map(([category, count]) => {
              const percentage = Math.round((count / (scorecard.totalRequirements || 1)) * 100);
              return (
                <div key={category} className="space-y-1">
                  <div className="flex justify-between text-xs font-mono">
                    <span className="text-slate-300">{category}</span>
                    <span className="text-slate-400">
                      {count} ({percentage}%)
                    </span>
                  </div>
                  <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-gradient-to-r from-sky-500 to-indigo-500 rounded-full"
                      style={{ width: `${percentage}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Defect Severity & Category Distribution */}
        <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800 space-y-4">
          <div className="flex items-center space-x-2">
            <AlertTriangle className="w-4 h-4 text-amber-400" />
            <h3 className="text-sm font-semibold text-white">Defect Severity & Category Breakdown</h3>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 space-y-2">
              <span className="text-xs font-semibold text-slate-400">By Severity</span>
              <div className="space-y-1.5 text-xs font-mono">
                {Object.entries(scorecard.severityBreakdown).map(([sev, cnt]) => (
                  <div key={sev} className="flex justify-between">
                    <span className="text-slate-400">{sev}:</span>
                    <span className="text-white font-bold">{cnt}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 space-y-2">
              <span className="text-xs font-semibold text-slate-400">By Category</span>
              <div className="space-y-1.5 text-xs font-mono">
                {Object.entries(scorecard.categoryBreakdown).map(([cat, cnt]) => (
                  <div key={cat} className="flex justify-between">
                    <span className="text-slate-400">{cat}:</span>
                    <span className="text-white font-bold">{cnt}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Section Health Diagnostic Table */}
      <div className="p-6 rounded-2xl bg-slate-900/60 border border-slate-800 space-y-4">
        <h3 className="text-sm font-semibold text-white">Document Section Health Diagnostics</h3>
        <div className="border border-slate-800 rounded-xl overflow-hidden">
          <table className="w-full text-left text-xs border-collapse">
            <thead>
              <tr className="bg-slate-900 border-b border-slate-800 text-slate-400 font-semibold text-[11px] uppercase font-mono">
                <th className="py-3 px-4">Section Path</th>
                <th className="py-3 px-4">Requirements</th>
                <th className="py-3 px-4">Flagged Issues</th>
                <th className="py-3 px-4">Avg Clarity</th>
                <th className="py-3 px-4">Conflict Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {scorecard.sectionHealth.map((sec, idx) => (
                <tr key={idx} className="hover:bg-slate-800/40 transition">
                  <td className="py-3 px-4 font-mono font-medium text-slate-200">{sec.sectionPath}</td>
                  <td className="py-3 px-4 font-mono">{sec.requirementCount}</td>
                  <td className="py-3 px-4 font-mono text-amber-400">{sec.issueCount}</td>
                  <td className="py-3 px-4 font-mono font-bold text-sky-400">{sec.averageClarity}%</td>
                  <td className="py-3 px-4">
                    {sec.hasCriticalConflict ? (
                      <span className="text-rose-400 font-mono font-semibold text-[10px] bg-rose-950 px-2 py-0.5 rounded border border-rose-800">
                        CRITICAL CONFLICT DETECTED
                      </span>
                    ) : (
                      <span className="text-emerald-400 font-mono text-[10px] bg-emerald-950 px-2 py-0.5 rounded border border-emerald-800">
                        HEALTHY
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
