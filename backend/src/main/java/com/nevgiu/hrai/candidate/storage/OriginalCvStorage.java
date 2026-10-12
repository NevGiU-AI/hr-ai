package com.nevgiu.hrai.candidate.storage;

public interface OriginalCvStorage {

    /** Places untrusted bytes in an isolated namespace before malware scanning. */
    QuarantinedCv quarantine(String organizationId, byte[] content);

    /** Makes a scanner-approved object available to application workflows. */
    StoredCv promote(String quarantineKey);

    /** Reads only a promoted original; quarantine and deletion namespaces are not valid sources. */
    byte[] read(String storageKey);

    /**
     * Moves an original into a recoverable deletion namespace. The caller decides whether to purge or restore it after
     * the surrounding database transaction completes.
     */
    StagedCvDeletion stageDeletion(String storageKey);

    /** Compensates a rolled-back database deletion by returning the staged bytes to their original opaque key. */
    void restoreDeletion(StagedCvDeletion deletion);

    /** Permanently removes the object represented by the provider-specific key. */
    void delete(String storageKey);
}
