package com.nevgiu.hrai.candidate.storage;

import java.time.Instant;

public record StoredCv(String storageKey, Instant storedAt) {
}
