# Candidate Data Governance Decisions

## Status

**Approved 6 October 2026 as the project governance baseline.**

This is the minimum policy needed to continue engineering. It is not legal advice. Each customer organization remains
responsible for confirming its recruitment purpose, lawful basis, candidate notice, and local legal requirements. The
baseline follows the official [GDPR principles and rights](https://eur-lex.europa.eu/eli/reg/2016/679/oj/eng/).

## Proposed baseline

### 1. Candidate notice

- Use CV data only for the stated recruitment process and human review.
- Do not use CVs for advertising, unrelated profiling, or model training.
- The customer organization must approve its lawful basis and candidate privacy notice; the application does not assume
  that consent is always the correct lawful basis.

### 2. Malware protection

- Do not extract, download, reprocess, or run OCR until ClamAV reports the file as clean.
- Keep the upload in an isolated quarantine location only while scanning.
- Delete infected uploads immediately.
- If scanning is unavailable or inconclusive, delete the temporary copy, create no candidate/document, and return a
  retryable error.
- Scan existing unverified originals before any future download, correction, reprocessing, or OCR.

### 3. Retention

- Default retention is **365 days from successful clean storage**.
- Reprocessing does not restart the retention period.
- Scheduled expiry deletes the original and derived candidate data unless an approved legal hold exists.
- The privacy owner must confirm that 365 days is appropriate before this becomes final policy.

### 4. Access

- Derive the organization from the authenticated server session; never accept it from the client.
- `ADMIN`: read, correct/reprocess, delete, and manage legal holds within the organization.
- `RECRUITER`: read and correct/reprocess within the organization.
- `REVIEWER` and `READ_ONLY`: no original-file download or destructive operation for the initial release.
- Return `404` for cross-organization identifiers.

### 5. Correction and reprocessing

- Never silently modify the submitted original file.
- Store corrections as versioned metadata or a replacement document.
- Do not create a duplicate candidate during reprocessing.
- Mark derived evaluations, embeddings, indexes, and chat evidence as replaced or obsolete when their source changes.

### 6. Deletion and legal hold

- Candidate erasure removes the original, extracted text, candidate/document records, evaluations, and future derived AI
  data as one confirmed operation.
- `ADMIN` performs deletion and legal-hold actions within the organization.
- A legal hold blocks automatic expiry and deletion until it is released.
- Releasing a hold immediately re-evaluates the original retention deadline.

### 7. Residency, providers, and backups

- Store active candidate data and encrypted backups in approved EU/EEA regions by default.
- Do not send candidate content to an OCR, AI, speech, messaging, monitoring, or backup provider until its region,
  retention, subprocessors, and training use are approved.
- Back up PostgreSQL and original files as one encrypted restore set.
- Ensure deleted data ages out of backups, and replay deletions after restoring an older backup.

### 8. Audit

Record actor, organization, safe target ID, timestamp, action, and outcome for:

- malware rejection and scanner failure;
- original-file access;
- correction and reprocessing;
- deletion and expiry;
- legal-hold changes; and
- backup/restore verification.

Never place CV content, filenames, storage keys, antivirus signature names, or document hashes in general logs or audit
details.

## Approval checklist

- [x] Product/privacy owner approves the notice responsibility and 365-day retention.
- [x] Security owner approves immediate infected-file deletion and fail-closed scanning.
- [x] Product owner approves the role permissions, deletion scope, and correction workflow.
- [x] Operations owner approves EU/EEA residency and the paired backup/restore rules.
- [x] Engineering accepts the approved decisions as requirements for tests and implementation tasks.

## Current pull request

The malware-quarantine implementation matches the approved section 2 behavior for new uploads. Access, correction,
deletion, legal hold, scheduled expiry, legacy rescanning, and paired backup/restore remain separate implementation
slices and are not marked complete by this policy approval.
