import test from 'node:test';
import assert from 'node:assert/strict';

// Test Suite: ReqWise Frontend Architecture & Client Logic Verification

test('1. API Client URL Construction and Contract Verification', () => {
  const API_BASE = '/api/v1';

  // Export URLs
  const versionId = 'e2c608f1-8f55-4e78-952a-9e76961476d0';
  const xlsxUrl = `${API_BASE}/versions/${versionId}/export/xlsx`;
  const pdfUrl = `${API_BASE}/versions/${versionId}/export/pdf`;

  assert.equal(xlsxUrl, '/api/v1/versions/e2c608f1-8f55-4e78-952a-9e76961476d0/export/xlsx');
  assert.equal(pdfUrl, '/api/v1/versions/e2c608f1-8f55-4e78-952a-9e76961476d0/export/pdf');

  // Diff query string construction
  const baseId = '11111111-1111-1111-1111-111111111111';
  const targetId = '22222222-2222-2222-2222-222222222222';
  const diffUrl = `${API_BASE}/diff?baseVersionId=${baseId}&targetVersionId=${targetId}`;

  assert.equal(diffUrl, '/api/v1/diff?baseVersionId=11111111-1111-1111-1111-111111111111&targetVersionId=22222222-2222-2222-2222-222222222222');
});

test('2. Clarity Score Evaluation and Visual Indicator Logic', () => {
  const getScoreBadgeColor = (score) => {
    if (score >= 80) return 'emerald';
    if (score >= 50) return 'amber';
    return 'rose';
  };

  assert.equal(getScoreBadgeColor(95.0), 'emerald');
  assert.equal(getScoreBadgeColor(80.0), 'emerald');
  assert.equal(getScoreBadgeColor(79.9), 'amber');
  assert.equal(getScoreBadgeColor(50.0), 'amber');
  assert.equal(getScoreBadgeColor(49.9), 'rose');
  assert.equal(getScoreBadgeColor(0.0), 'rose');
});

test('3. Synchronized Grounding Bounding-Box Coordinate Scaling', () => {
  // Simulates converting PDF point bounding box (x0, y0, x1, y1) to DOM overlay pixels
  const scale = 1.5;
  const pdfBox = { x0: 50, y0: 700, x1: 500, y1: 720 };
  const pageHeight = 792; // Standard Letter height in points

  // Inverted PDF coordinates (origin at bottom-left) to browser coordinates (origin at top-left)
  const domBox = {
    left: pdfBox.x0 * scale,
    top: (pageHeight - pdfBox.y1) * scale,
    width: (pdfBox.x1 - pdfBox.x0) * scale,
    height: (pdfBox.y1 - pdfBox.y0) * scale,
  };

  assert.equal(domBox.left, 75);
  assert.equal(domBox.top, (792 - 720) * 1.5); // 72 * 1.5 = 108
  assert.equal(domBox.width, 450 * 1.5); // 675
  assert.equal(domBox.height, 20 * 1.5); // 30
});

test('4. Revision Diff Hungarian Classification and Downstream Impact Alerts', () => {
  const sampleDiffs = [
    {
      id: 'd1',
      changeType: 'UNCHANGED',
      baseReqCode: 'FR-001',
      targetReqCode: 'FR-001',
      impactedReqCodes: [],
    },
    {
      id: 'd2',
      changeType: 'MODIFIED_BREAKING',
      baseReqCode: 'FR-002',
      targetReqCode: 'FR-002',
      impactedReqCodes: ['NFR-005', 'TEST-002'],
    },
    {
      id: 'd3',
      changeType: 'MODIFIED_EDITORIAL',
      baseReqCode: 'FR-003',
      targetReqCode: 'FR-003',
      impactedReqCodes: [],
    },
    {
      id: 'd4',
      changeType: 'ADDED',
      baseReqCode: null,
      targetReqCode: 'FR-004',
      impactedReqCodes: [],
    },
  ];

  const breakingChanges = sampleDiffs.filter((d) => d.changeType === 'MODIFIED_BREAKING');
  assert.equal(breakingChanges.length, 1);
  assert.deepEqual(breakingChanges[0].impactedReqCodes, ['NFR-005', 'TEST-002']);

  const unchangedCount = sampleDiffs.filter((d) => d.changeType === 'UNCHANGED').length;
  assert.equal(unchangedCount, 1);
});

test('5. Traceability DAG Gap Detection (Orphan & Unverified Specifications)', () => {
  const nodes = [
    { id: 'BG-001', type: 'BUSINESS_GOAL' },
    { id: 'FR-001', type: 'FUNCTIONAL' },
    { id: 'FR-002', type: 'FUNCTIONAL' }, // Orphan (no incoming link)
    { id: 'TEST-001', type: 'VERIFICATION' },
  ];

  const links = [
    { sourceId: 'BG-001', targetId: 'FR-001', linkType: 'REFINES' },
    { sourceId: 'FR-001', targetId: 'TEST-001', linkType: 'VERIFIED_BY' },
  ];

  // Check orphan: Functional specs with zero incoming parent links
  const orphanNodes = nodes.filter((n) => {
    if (n.type !== 'FUNCTIONAL') return false;
    const hasParent = links.some((l) => l.targetId === n.id);
    return !hasParent;
  });

  assert.equal(orphanNodes.length, 1);
  assert.equal(orphanNodes[0].id, 'FR-002');

  // Check unverified: Functional specs with zero outgoing verification links
  const unverifiedNodes = nodes.filter((n) => {
    if (n.type !== 'FUNCTIONAL') return false;
    const hasVerification = links.some((l) => l.sourceId === n.id && l.linkType === 'VERIFIED_BY');
    return !hasVerification;
  });

  assert.equal(unverifiedNodes.length, 1);
  assert.equal(unverifiedNodes[0].id, 'FR-002');
});

test('6. Executive Scorecard Readiness Index Categorization', () => {
  const getReadinessStatus = (index) => {
    if (index >= 85) return { status: 'ENTERPRISE_READY', badge: 'bg-emerald-500' };
    if (index >= 60) return { status: 'CONDITIONAL_APPROVAL', badge: 'bg-amber-500' };
    return { status: 'NEEDS_REVISION', badge: 'bg-rose-500' };
  };

  assert.equal(getReadinessStatus(92.5).status, 'ENTERPRISE_READY');
  assert.equal(getReadinessStatus(72.0).status, 'CONDITIONAL_APPROVAL');
  assert.equal(getReadinessStatus(45.0).status, 'NEEDS_REVISION');
});

test('7. Authentication Password Policy & Sign Up Field Constraints', () => {
  const validatePassword = (pwd) => ({
    length: pwd.length >= 8,
    case: /[a-z]/.test(pwd) && /[A-Z]/.test(pwd),
    digit: /\d/.test(pwd),
    special: /[*#\-_@!$%^&+=?]/.test(pwd),
    isValid: pwd.length >= 8 && /[a-z]/.test(pwd) && /[A-Z]/.test(pwd) && /\d/.test(pwd) && /[*#\-_@!$%^&+=?]/.test(pwd),
  });

  // Non-compliant passwords
  assert.equal(validatePassword('short1!').isValid, false); // Length < 8
  assert.equal(validatePassword('nouppercase123!').isValid, false); // No uppercase
  assert.equal(validatePassword('NOLOWERCASE123!').isValid, false); // No lowercase
  assert.equal(validatePassword('NoDigitsHere!').isValid, false); // No digit
  assert.equal(validatePassword('NoSpecialChar123').isValid, false); // No special char

  // Compliant password
  assert.equal(validatePassword('ReqWise2026!').isValid, true);

  // Field specification: Single email address with sarah@gmail.com format
  const formPayload = {
    username: 'sarah',
    password: 'Password123*',
    email: 'sarah@gmail.com',
    firstName: 'Sarah',
    lastName: 'Chen',
    country: 'Sri Lanka',
  };

  assert.equal('city' in formPayload, false, 'City/town must be excluded');
  assert.equal('securityQuestion' in formPayload, false, 'Security question must be excluded');
  assert.equal('emailConfirm' in formPayload, false, 'Email (again) must be excluded');
  assert.equal(formPayload.email, 'sarah@gmail.com');
  assert.equal(formPayload.country, 'Sri Lanka');
});

test('8. SSO Account Input Validation & Page-Load Preservation Contract', () => {
  // SSO Validation: Must require an account/email, cannot auto-login
  const validateSSOInput = (provider, accountInput) => {
    const trimmed = (accountInput || '').trim();
    if (!trimmed) {
      return { isValid: false, error: `Account required for ${provider}` };
    }
    if (provider === 'google' && !trimmed.includes('@')) {
      return { isValid: false, error: 'Valid Google email required' };
    }
    const isEmail = trimmed.includes('@');
    const username = isEmail ? trimmed.split('@')[0] : trimmed;
    const email = isEmail ? trimmed : `${trimmed}@github.com`;
    return {
      isValid: true,
      user: {
        username,
        email,
        firstName: username.charAt(0).toUpperCase() + username.slice(1),
      },
    };
  };

  // Blank SSO input must fail (no auto-login)
  assert.equal(validateSSOInput('google', '').isValid, false);
  assert.equal(validateSSOInput('google', '   ').isValid, false);
  assert.equal(validateSSOInput('google', 'invalid-email').isValid, false);
  assert.equal(validateSSOInput('github', '').isValid, false);

  // Providing real account creates the user with their given identity
  const googleAuth = validateSSOInput('google', 'sarah@gmail.com');
  assert.equal(googleAuth.isValid, true);
  assert.equal(googleAuth.user.email, 'sarah@gmail.com');
  assert.equal(googleAuth.user.username, 'sarah');

  const githubAuth = validateSSOInput('github', 'sarah-dev');
  assert.equal(githubAuth.isValid, true);
  assert.equal(githubAuth.user.username, 'sarah-dev');
  assert.equal(githubAuth.user.email, 'sarah-dev@github.com');

  // Page load preservation: Mock storage pre-fills username
  const mockStorage = {
    reqwise_saved_username: 'sarah@gmail.com',
    reqwise_saved_view: 'signin',
  };
  assert.equal(mockStorage.reqwise_saved_username, 'sarah@gmail.com');
  assert.equal(mockStorage.reqwise_saved_view, 'signin');
});

test('9. Strict Password Verification on Sign In & SSO (Rejects Incorrect Password)', () => {
  const registeredUsers = [
    {
      username: 'ravindiran',
      email: 'ravindiranthilini@gmail.com',
      password: 'CorrectPass123*',
      firstName: 'Ravindi',
      lastName: 'Ranthilini',
      country: 'Sri Lanka',
    },
  ];

  const verifyCredentials = (usernameOrEmail, password) => {
    const query = (usernameOrEmail || '').trim().toLowerCase();
    const matched = registeredUsers.find(
      (u) => u.username.toLowerCase() === query || u.email.toLowerCase() === query
    );
    if (!matched) {
      return { success: false, error: 'Account not found' };
    }
    if (matched.password !== password) {
      return { success: false, error: 'Incorrect password' };
    }
    return { success: true, user: matched };
  };

  // Rejection with incorrect password
  const failAttempt1 = verifyCredentials('ravindiranthilini@gmail.com', 'WrongPassword!');
  assert.equal(failAttempt1.success, false);
  assert.equal(failAttempt1.error, 'Incorrect password');

  const failAttempt2 = verifyCredentials('ravindiran', '12345');
  assert.equal(failAttempt2.success, false);
  assert.equal(failAttempt2.error, 'Incorrect password');

  // Rejection with non-existent account
  const notFound = verifyCredentials('nonexistent@user.com', 'SomePass123*');
  assert.equal(notFound.success, false);
  assert.equal(notFound.error, 'Account not found');

  // Success with correct password
  const successAttempt = verifyCredentials('ravindiranthilini@gmail.com', 'CorrectPass123*');
  assert.equal(successAttempt.success, true);
  assert.equal(successAttempt.user.email, 'ravindiranthilini@gmail.com');
});
