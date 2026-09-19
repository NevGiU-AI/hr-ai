package com.nevgiu.hrai.candidate.storage;

public interface OriginalCvStorage {

    StoredCv store(String organizationId, byte[] content);

    void delete(String storageKey);
}
