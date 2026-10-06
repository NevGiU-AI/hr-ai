package com.nevgiu.hrai.candidate.storage;

public interface OriginalCvStorage {

    QuarantinedCv quarantine(String organizationId, byte[] content);

    StoredCv promote(String quarantineKey);

    void delete(String storageKey);
}
