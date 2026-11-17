-- ReqWise Initial Schema Definition

CREATE TABLE IF NOT EXISTS workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_projects_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_documents_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS document_versions (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    version_number INT NOT NULL,
    version_label VARCHAR(64) NOT NULL,
    source_file_path TEXT NOT NULL,
    preview_pdf_path TEXT,
    file_type VARCHAR(16) NOT NULL,
    processing_status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    overall_quality_score NUMERIC(5,2),
    executive_summary TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_versions_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT uq_document_version UNIQUE (document_id, version_number)
);

CREATE TABLE IF NOT EXISTS requirements (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL,
    req_code VARCHAR(64) NOT NULL,
    section_path VARCHAR(255),
    raw_text TEXT NOT NULL,
    normalized_text TEXT NOT NULL,
    req_type VARCHAR(32) NOT NULL,
    nfr_category VARCHAR(64),
    ears_pattern VARCHAR(64),
    priority VARCHAR(32),
    clarity_score NUMERIC(5,2) NOT NULL DEFAULT 100.0,
    page_number INT,
    bounding_box TEXT,
    embedding TEXT,
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_requirements_version FOREIGN KEY (version_id) REFERENCES document_versions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_requirements_version ON requirements(version_id);

CREATE TABLE IF NOT EXISTS requirement_issues (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL,
    primary_req_id UUID NOT NULL,
    related_req_id UUID,
    issue_category VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    flagged_phrase TEXT,
    explanation TEXT NOT NULL,
    suggested_rewrite TEXT,
    similarity_score NUMERIC(5,4),
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_issues_version FOREIGN KEY (version_id) REFERENCES document_versions(id) ON DELETE CASCADE,
    CONSTRAINT fk_issues_primary_req FOREIGN KEY (primary_req_id) REFERENCES requirements(id) ON DELETE CASCADE,
    CONSTRAINT fk_issues_related_req FOREIGN KEY (related_req_id) REFERENCES requirements(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS traceability_links (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL,
    source_node_id UUID NOT NULL,
    target_node_id UUID NOT NULL,
    link_type VARCHAR(32) NOT NULL,
    confidence_score NUMERIC(5,4) NOT NULL,
    is_human_verified BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_links_version FOREIGN KEY (version_id) REFERENCES document_versions(id) ON DELETE CASCADE,
    CONSTRAINT fk_links_source FOREIGN KEY (source_node_id) REFERENCES requirements(id) ON DELETE CASCADE,
    CONSTRAINT fk_links_target FOREIGN KEY (target_node_id) REFERENCES requirements(id) ON DELETE CASCADE,
    CONSTRAINT uq_traceability_link UNIQUE (source_node_id, target_node_id, link_type)
);

CREATE TABLE IF NOT EXISTS requirement_diffs (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    base_version_id UUID NOT NULL,
    target_version_id UUID NOT NULL,
    base_req_id UUID,
    target_req_id UUID,
    change_type VARCHAR(32) NOT NULL,
    similarity_score NUMERIC(5,4),
    impact_summary TEXT,
    impacted_req_ids TEXT DEFAULT '[]',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_diffs_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_diffs_base_ver FOREIGN KEY (base_version_id) REFERENCES document_versions(id) ON DELETE CASCADE,
    CONSTRAINT fk_diffs_target_ver FOREIGN KEY (target_version_id) REFERENCES document_versions(id) ON DELETE CASCADE,
    CONSTRAINT fk_diffs_base_req FOREIGN KEY (base_req_id) REFERENCES requirements(id) ON DELETE SET NULL,
    CONSTRAINT fk_diffs_target_req FOREIGN KEY (target_req_id) REFERENCES requirements(id) ON DELETE SET NULL
);
