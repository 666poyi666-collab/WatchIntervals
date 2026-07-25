package com.poyi.watchintervals.phone;

/** Pure decision logic for revision-protected, idempotent phone API writes. */
final class MutationGuard {
    enum Decision { EXECUTE, DUPLICATE, REQUEST_ID_REUSED, REVISION_CONFLICT }

    private MutationGuard() {}

    static Decision decide(String requestId, String requestHash, String cachedHash,
                           boolean hasExpectedRevision, long expectedRevision,
                           long actualRevision) {
        if (requestId != null && !requestId.isEmpty() && cachedHash != null) {
            return cachedHash.equals(requestHash) ? Decision.DUPLICATE : Decision.REQUEST_ID_REUSED;
        }
        if (hasExpectedRevision && expectedRevision != actualRevision) {
            return Decision.REVISION_CONFLICT;
        }
        return Decision.EXECUTE;
    }
}
