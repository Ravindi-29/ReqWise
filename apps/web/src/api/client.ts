import {
  Workspace,
  Project,
  Document,
  DocumentVersion,
  Requirement,
  RequirementIssue,
  ParsedBlock,
  RtmGraphResponse,
  ExecutiveScorecardResponse,
  VersionDiffResponse,
  PipelineProgressEvent,
} from '../types';

const API_BASE = '/api/v1';

export const api = {
  // Workspaces & Projects
  async getWorkspaces(): Promise<Workspace[]> {
    const res = await fetch(`${API_BASE}/workspaces`);
    if (!res.ok) throw new Error('Failed to fetch workspaces');
    return res.json();
  },

  async createWorkspace(name: string): Promise<Workspace> {
    const res = await fetch(`${API_BASE}/workspaces`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name }),
    });
    if (!res.ok) throw new Error('Failed to create workspace');
    return res.json();
  },

  async getProjects(workspaceId: string): Promise<Project[]> {
    const res = await fetch(`${API_BASE}/workspaces/${workspaceId}/projects`);
    if (!res.ok) throw new Error('Failed to fetch projects');
    return res.json();
  },

  async createProject(workspaceId: string, name: string, description: string): Promise<Project> {
    const res = await fetch(`${API_BASE}/workspaces/${workspaceId}/projects`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, description }),
    });
    if (!res.ok) throw new Error('Failed to create project');
    return res.json();
  },

  // Ingestion & Documents
  async uploadDocument(projectId: string, file: File): Promise<DocumentVersion> {
    const formData = new FormData();
    formData.append('file', file);
    const res = await fetch(`${API_BASE}/projects/${projectId}/documents/upload`, {
      method: 'POST',
      body: formData,
    });
    if (!res.ok) throw new Error('Failed to upload document');
    return res.json();
  },

  async getDocument(documentId: string): Promise<Document> {
    const res = await fetch(`${API_BASE}/documents/${documentId}`);
    if (!res.ok) throw new Error('Failed to fetch document');
    return res.json();
  },

  async getVersion(versionId: string): Promise<DocumentVersion> {
    const res = await fetch(`${API_BASE}/versions/${versionId}`);
    if (!res.ok) throw new Error('Failed to fetch version');
    return res.json();
  },

  async getVersionBlocks(versionId: string): Promise<ParsedBlock[]> {
    const res = await fetch(`${API_BASE}/versions/${versionId}/blocks`);
    if (!res.ok) throw new Error('Failed to fetch blocks');
    return res.json();
  },

  // Requirements & Issues
  async getRequirements(
    versionId: string,
    params?: { reqType?: string; reviewStatus?: string; minClarity?: number }
  ): Promise<Requirement[]> {
    const url = new URL(`${window.location.origin}${API_BASE}/versions/${versionId}/requirements`);
    if (params?.reqType) url.searchParams.append('reqType', params.reqType);
    if (params?.reviewStatus) url.searchParams.append('reviewStatus', params.reviewStatus);
    if (params?.minClarity) url.searchParams.append('minClarity', params.minClarity.toString());

    const res = await fetch(url.toString().replace(window.location.origin, ''));
    if (!res.ok) throw new Error('Failed to fetch requirements');
    return res.json();
  },

  async updateRequirement(
    id: string,
    data: { normalizedText?: string; reqType?: string; reviewStatus?: string }
  ): Promise<Requirement> {
    const res = await fetch(`${API_BASE}/requirements/${id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    if (!res.ok) throw new Error('Failed to update requirement');
    return res.json();
  },

  async getIssues(
    versionId: string,
    params?: { severity?: string; category?: string; isResolved?: boolean }
  ): Promise<RequirementIssue[]> {
    const url = new URL(`${window.location.origin}${API_BASE}/versions/${versionId}/issues`);
    if (params?.severity) url.searchParams.append('severity', params.severity);
    if (params?.category) url.searchParams.append('category', params.category);
    if (params?.isResolved !== undefined) url.searchParams.append('isResolved', String(params.isResolved));

    const res = await fetch(url.toString().replace(window.location.origin, ''));
    if (!res.ok) throw new Error('Failed to fetch issues');
    return res.json();
  },

  async resolveIssue(id: string, isResolved = true): Promise<RequirementIssue> {
    const res = await fetch(`${API_BASE}/issues/${id}/resolve`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ isResolved }),
    });
    if (!res.ok) throw new Error('Failed to resolve issue');
    return res.json();
  },

  async applyRewrite(issueId: string): Promise<RequirementIssue> {
    const res = await fetch(`${API_BASE}/issues/${issueId}/apply-rewrite`, {
      method: 'POST',
    });
    if (!res.ok) throw new Error('Failed to apply suggested rewrite');
    return res.json();
  },

  // RTM Graph
  async getRtmGraph(versionId: string): Promise<RtmGraphResponse> {
    const res = await fetch(`${API_BASE}/versions/${versionId}/rtm`);
    if (!res.ok) throw new Error('Failed to fetch RTM graph');
    return res.json();
  },

  async createRtmLink(
    versionId: string,
    data: { sourceReqId: string; targetReqId: string; linkType?: string }
  ) {
    const res = await fetch(`${API_BASE}/versions/${versionId}/rtm/links`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    if (!res.ok) throw new Error('Failed to create traceability link');
    return res.json();
  },

  async deleteRtmLink(linkId: string): Promise<void> {
    const res = await fetch(`${API_BASE}/rtm/links/${linkId}`, {
      method: 'DELETE',
    });
    if (!res.ok) throw new Error('Failed to delete traceability link');
  },

  // Scorecard & Exports
  async getScorecard(versionId: string): Promise<ExecutiveScorecardResponse> {
    const res = await fetch(`${API_BASE}/versions/${versionId}/scorecard`);
    if (!res.ok) throw new Error('Failed to fetch executive scorecard');
    return res.json();
  },

  async getVersionDiff(baseVersionId: string, targetVersionId: string): Promise<VersionDiffResponse> {
    const res = await fetch(`${API_BASE}/diff?baseVersionId=${baseVersionId}&targetVersionId=${targetVersionId}`);
    if (!res.ok) throw new Error('Failed to compute version diff');
    return res.json();
  },

  getXlsxExportUrl(versionId: string): string {
    return `${API_BASE}/versions/${versionId}/export/xlsx`;
  },

  getPdfExportUrl(versionId: string): string {
    return `${API_BASE}/versions/${versionId}/export/pdf`;
  },

  // Real-Time SSE Pipeline Streaming
  subscribePipelineStream(
    versionId: string,
    onProgress: (event: PipelineProgressEvent) => void,
    onComplete: (event: PipelineProgressEvent) => void,
    onError: (err: any) => void
  ): () => void {
    const eventSource = new EventSource(`${API_BASE}/versions/${versionId}/stream`);

    eventSource.addEventListener('progress', (e) => {
      try {
        const data = JSON.parse(e.data);
        onProgress(data);
      } catch (err) {
        console.error('SSE parse error:', err);
      }
    });

    eventSource.addEventListener('complete', (e) => {
      try {
        const data = JSON.parse(e.data);
        onComplete(data);
        eventSource.close();
      } catch (err) {
        console.error('SSE parse error:', err);
      }
    });

    eventSource.addEventListener('error', (e) => {
      onError(e);
      eventSource.close();
    });

    return () => {
      eventSource.close();
    };
  },
};
