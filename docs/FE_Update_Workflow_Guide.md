# Front-End Integration Guide: Professional Resource Update Workflow

This document outlines the professional workflow implemented in the backend for handling Resource Updates, specifically focusing on the `approve`, `reject`, and `update` logic to ensure a seamless experience for both Uploaders and Administrators.

## 1. Overview of the Logic

The Backend now strictly separates the behavior of **Uploaders (Resource Owners)** and **Administrators** to match professional CMS systems (like Docs.vn, SlideShare, etc.).

### Rule A: When an Uploader updates a resource
- If a resource is currently `REJECTED` (Từ chối) or `APPROVED` (Đã duyệt).
- And the Uploader goes into the Portal to edit it (e.g., fixes a typo, uploads a new file).
- ➡️ **Backend Behavior:** The Backend will **automatically** change the status back to `PENDING` (Chờ duyệt) and erase the previous rejection reason.
- **Why?** Because the Uploader modified the content, it must be re-evaluated by an Admin.

### Rule B: When an Admin updates a resource
- If a resource is currently `REJECTED` (Từ chối) or `APPROVED`.
- And an Admin goes into the Admin Panel to edit it (e.g., fixing a small spelling mistake for the user).
- ➡️ **Backend Behavior:** The Backend will **keep the original status** (e.g., it stays `REJECTED`). It will *not* automatically revert to `PENDING`.
- **Why?** Admins shouldn't have to re-approve a document just because they fixed a minor typo. 

## 2. Front-End Responsibilities (Admin Panel)

Because the Backend preserves the status when an Admin edits, the Admin Front-End must provide a way for the Admin to explicitly change the status during an update if they want to.

### 2.1 The "Status" Dropdown in the Edit Form
In the Admin Dashboard's **Edit Resource Modal/Page**, you should implement a `Status` dropdown.

*   **API Payload Field:** `status` (Enum: `PENDING`, `APPROVED`, `REJECTED`)
*   **Behavior:**
    *   By default, the dropdown should bind to the resource's current status.
    *   If the Admin fixes the document and wants to approve it immediately, they change the dropdown to `APPROVED` and click "Save".
    *   The FE sends `status: "APPROVED"` in the `PUT /api/v1/resources/{id}` payload.
    *   The Backend will respect this explicit status and update it accordingly.

### 2.2 Re-Approve / Re-Reject Buttons (Optional but Recommended)
For better UX, outside of the Edit Modal, in the `REJECTED` tab list:
- Provide an **"Approve"** quick-action button that calls `PUT /api/v1/resources/{id}/approve`.
- This allows Admins to bypass the edit form if they just want to approve something they previously rejected.

## 3. Front-End Responsibilities (Uploader Portal)

### 3.1 Handling the "Rejected" State
- When displaying a user's resources in the Portal, visually highlight `REJECTED` resources.
- Display the `rejectionReason` field clearly to the user so they know what to fix.

### 3.2 The Edit Flow
- When the user clicks "Edit" on a `REJECTED` resource, they use the standard `PUT /api/v1/resources/{id}` API.
- **Important:** The FE does *not* need to send any `status` field.
- **Post-Update UI:** After the API returns `200 OK`, the FE must locally update the resource's status in the UI to `PENDING` (or trigger a refetch of the list), because the Backend will have automatically moved it to the Pending queue. Tell the user: *"Your document has been updated and is awaiting review."*

## 4. Audit Logs (Bonus)
The Backend now generates beautiful, human-readable Audit Logs for status changes!
- Instead of raw UUIDs, the log will read: `Approved document: [Document Title]` or `Rejected document: [Document Title] | Reason: [Reason]`.
- FE doesn't need to change anything; just display the `detail` column of the Audit Log API.
