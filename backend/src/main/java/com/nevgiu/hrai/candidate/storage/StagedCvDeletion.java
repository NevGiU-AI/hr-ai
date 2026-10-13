package com.nevgiu.hrai.candidate.storage;

/**
 * Provider-neutral receipt for a reversible file deletion. Neither key may be exposed to clients, audit details, or
 * general application logs because storage topology is private infrastructure metadata.
 */
public record StagedCvDeletion(String originalKey, String deletionKey) {
}
