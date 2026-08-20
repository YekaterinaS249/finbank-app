package com.finbank.model;

/**
 * User account status (not to be confused with {@link Account#getStatus()}
 * — there is no such field on Account today; this is specifically the
 * user's login eligibility). PENDING_VERIFICATION is intentionally not a
 * value here — email verification is explicitly out of scope for the
 * current Login spec (see Password/Login requirements docs) and may be
 * introduced later as its own module.
 */
public enum AccountStatus {
    ACTIVE,
    BLOCKED
}
