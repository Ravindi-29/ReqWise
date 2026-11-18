import React, { useState, useEffect } from 'react';
import { Navbar } from './components/Navbar';
import { SplitScreenWorkspace } from './components/SplitScreenWorkspace';
import { RtmWorkspace } from './components/RtmWorkspace';
import { DiffWorkspace } from './components/DiffWorkspace';
import { ScorecardWorkspace } from './components/ScorecardWorkspace';
import { UploadModal } from './components/UploadModal';
import { AuthPage } from './components/AuthPage';
import { api } from './api/client';
import { Workspace, Project, Document, DocumentVersion, AuthUser } from './types';
import { Sparkles, FileText, ArrowRight, Upload } from 'lucide-react';

export const App: React.FC = () => {
  const [authUser, setAuthUser] = useState<AuthUser | null>(() => {
    const saved = localStorage.getItem('reqwise_auth_user');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch {
        return null;
      }
    }
    return null;
  });

  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [activeWorkspace, setActiveWorkspace] = useState<Workspace | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [activeProject, setActiveProject] = useState<Project | null>(null);
  const [activeDocument, setActiveDocument] = useState<Document | null>(null);
  const [documentVersions, setDocumentVersions] = useState<DocumentVersion[]>([]);
  const [activeVersion, setActiveVersion] = useState<DocumentVersion | null>(null);

  const [activeTab, setActiveTab] = useState<'canvas' | 'rtm' | 'diff' | 'scorecard'>('canvas');
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    bootstrapWorkspace();
  }, []);

  const bootstrapWorkspace = async () => {
    try {
      setIsLoading(true);
      let wsList = await api.getWorkspaces();

      // If no workspace exists, create a default Enterprise one
      if (wsList.length === 0) {
        const defaultWs = await api.createWorkspace('Defense & Enterprise Systems');
        wsList = [defaultWs];
      }

      const currentWs = wsList[0];
      setWorkspaces(wsList);
      setActiveWorkspace(currentWs);

      let projList = await api.getProjects(currentWs.id);
      if (projList.length === 0) {
        const defaultProj = await api.createProject(
          currentWs.id,
          'Avionics Flight Management SRS',
          'Automated ISO/IEC/IEEE 29148 Requirements Intelligence'
        );
        projList = [defaultProj];
      }

      const currentProj = projList[0];
      setProjects(projList);
      setActiveProject(currentProj);
    } catch (err) {
      console.error('Failed to initialize workspace:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleVersionReady = async (versionId: string) => {
    try {
      const version = await api.getVersion(versionId);
      setActiveVersion(version);
      setDocumentVersions((prev) => [version, ...prev]);

      if (version.documentId) {
        const doc = await api.getDocument(version.documentId);
        setActiveDocument(doc);
      }
    } catch (err) {
      console.error('Failed to load new version details:', err);
    }
  };

  const handleSignOut = () => {
    localStorage.removeItem('reqwise_auth_user');
    setAuthUser(null);
  };

  // If user is not authenticated, render the ReqWise Sign In / Sign Up portal
  if (!authUser) {
    return <AuthPage onAuthenticated={(user) => setAuthUser(user)} />;
  }

  if (isLoading) {
    return (
      <div className="h-screen w-screen flex items-center justify-center bg-slate-950 text-slate-400 font-mono text-sm">
        <div className="flex items-center space-x-3">
          <div className="w-5 h-5 border-2 border-sky-500 border-t-transparent rounded-full animate-spin" />
          <span>Initializing ReqWise Intelligence Workspace...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      {/* Global Navbar */}
      <Navbar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        onOpenUpload={() => setIsUploadModalOpen(true)}
        currentDocumentTitle={activeDocument?.title || activeProject?.name}
        currentVersionLabel={activeVersion?.versionLabel}
        readinessIndex={activeVersion?.overallQualityScore ? Math.round(activeVersion.overallQualityScore) : undefined}
        currentUser={authUser}
        onSignOut={handleSignOut}
      />

      {/* Main Content Area */}
      <main className="flex-1 overflow-hidden">
        {!activeVersion ? (
          /* Empty State: Welcome Hero & SRS Upload CTA */
          <div className="h-[calc(100vh-4rem)] flex flex-col items-center justify-center p-6 text-center space-y-6">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-tr from-sky-500 to-indigo-600 flex items-center justify-center shadow-2xl shadow-sky-500/20">
              <Sparkles className="w-8 h-8 text-white" />
            </div>

            <div className="space-y-2 max-w-lg">
              <h2 className="text-2xl font-extrabold text-white tracking-tight">
                Welcome to ReqWise
              </h2>
              <p className="text-sm text-slate-400 leading-relaxed">
                Automated Software Requirements Specification (SRS) audit platform. Transform static PDF
                and Word documents into grounded, traceable, and ISO/IEC/IEEE 29148 compliant blueprints.
              </p>
            </div>

            <button
              onClick={() => setIsUploadModalOpen(true)}
              className="flex items-center space-x-2 px-6 py-3 rounded-xl text-sm font-semibold bg-gradient-to-r from-sky-500 to-blue-600 hover:from-sky-400 hover:to-blue-500 text-white shadow-xl shadow-sky-500/25 transition-all transform hover:-translate-y-0.5 cursor-pointer"
            >
              <Upload className="w-4 h-4" />
              <span>Upload SRS Document (.pdf / .docx)</span>
            </button>
          </div>
        ) : (
          /* Active Workspaces */
          <>
            {activeTab === 'canvas' && <SplitScreenWorkspace versionId={activeVersion.id} />}
            {activeTab === 'rtm' && <RtmWorkspace versionId={activeVersion.id} />}
            {activeTab === 'diff' && (
              <DiffWorkspace
                currentVersionId={activeVersion.id}
                documentVersions={documentVersions.length > 0 ? documentVersions : [activeVersion]}
              />
            )}
            {activeTab === 'scorecard' && <ScorecardWorkspace versionId={activeVersion.id} />}
          </>
        )}
      </main>

      {/* Ingestion & Upload Modal */}
      {activeProject && (
        <UploadModal
          isOpen={isUploadModalOpen}
          onClose={() => setIsUploadModalOpen(false)}
          projectId={activeProject.id}
          onVersionReady={handleVersionReady}
        />
      )}
    </div>
  );
};
