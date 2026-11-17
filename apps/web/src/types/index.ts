export interface Workspace {
  id: string;
  name: string;
  createdAt: string;
}

export interface Project {
  id: string;
  workspaceId: string;
  name: string;
  description: string;
  createdAt: string;
}

export interface Document {
  id: string;
  projectId: string;
  title: string;
  createdAt: string;
  versions?: DocumentVersion[];
}

export interface DocumentVersion {
  id: string;
  documentId: string;
  versionNumber: number;
  versionLabel: string;
  sourceFilePath: string;
  previewPdfPath?: string;
  fileType: 'PDF' | 'DOCX';
  processingStatus: string;
  overallQualityScore?: number;
  executiveSummary?: string;
  createdAt: string;
}

export interface Requirement {
  id: string;
  versionId: string;
  reqCode: string;
  sectionPath: string;
  rawText: string;
  normalizedText: string;
  reqType: 'FUNCTIONAL' | 'NON_FUNCTIONAL' | 'CONSTRAINT';
  nfrCategory?: string;
  earsPattern?: string;
  priority?: string;
  clarityScore: number;
  pageNumber?: number;
  boundingBox?: string; // JSON: {"x0":..., "y0":..., "x1":..., "y1":...}
  reviewStatus: 'PENDING' | 'APPROVED' | 'NEEDS_REVISION';
  issueCount: number;
  createdAt: string;
}

export interface RequirementIssue {
  id: string;
  versionId: string;
  primaryReqId: string;
  primaryReqCode?: string;
  relatedReqId?: string;
  relatedReqCode?: string;
  issueCategory: 'AMBIGUOUS' | 'INCOMPLETE' | 'MISSING_METRIC' | 'DUPLICATE' | 'CONFLICT';
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  flaggedPhrase?: string;
  explanation: string;
  suggestedRewrite?: string;
  similarityScore?: number;
  isResolved: boolean;
  createdAt: string;
}

export interface BoundingBox {
  x0: number;
  y0: number;
  x1: number;
  y1: number;
}

export interface ParsedBlock {
  sectionPath: string;
  text: string;
  pageNumber: number;
  boundingBox: BoundingBox;
  isTableRow: boolean;
}

export interface RtmNode {
  id: string;
  reqCode: string;
  label: string;
  sectionPath: string;
  reqType: string;
  nfrCategory?: string;
  clarityScore: number;
  isOrphan: boolean;
  isUnverified: boolean;
}

export interface RtmEdge {
  id: string;
  sourceId: string;
  targetId: string;
  sourceReqCode: string;
  targetReqCode: string;
  linkType: 'REFINES' | 'DEPENDS_ON' | 'CONSTRAINS' | 'VERIFIED_BY';
  confidenceScore: number;
  isHumanVerified: boolean;
}

export interface RtmGraphResponse {
  nodes: RtmNode[];
  edges: RtmEdge[];
  totalRequirements: number;
  linkedRequirements: number;
  orphanCount: number;
  unverifiedCount: number;
  coveragePercentage: number;
}

export interface SectionHealthSummary {
  sectionPath: string;
  requirementCount: number;
  issueCount: number;
  averageClarity: number;
  hasCriticalConflict: boolean;
}

export interface ExecutiveScorecardResponse {
  readinessIndex: number;
  overallClarityScore: number;
  totalRequirements: number;
  totalIssues: number;
  defectDensity: number;
  traceabilityCoverage: number;
  severityBreakdown: Record<string, number>;
  categoryBreakdown: Record<string, number>;
  iso25010Breakdown: Record<string, number>;
  sectionHealth: SectionHealthSummary[];
}

export interface DiffItem {
  id: string;
  baseReqId?: string;
  baseReqCode?: string;
  baseText?: string;
  targetReqId?: string;
  targetReqCode?: string;
  targetText?: string;
  changeType: 'UNCHANGED' | 'MODIFIED_EDITORIAL' | 'MODIFIED_BREAKING' | 'ADDED' | 'DELETED';
  similarityScore: number;
  impactSummary?: string;
  impactedReqCodes: string[];
}

export interface VersionDiffResponse {
  baseVersionId: string;
  baseVersionLabel: string;
  targetVersionId: string;
  targetVersionLabel: string;
  totalUnchanged: number;
  totalEditorial: number;
  totalBreaking: number;
  totalAdded: number;
  totalDeleted: number;
  totalImpacted: number;
  diffs: DiffItem[];
}

export interface PipelineProgressEvent {
  versionId: string;
  stage: string;
  percentage: number;
  message: string;
  metadata?: Record<string, any>;
  timestamp: string;
}
