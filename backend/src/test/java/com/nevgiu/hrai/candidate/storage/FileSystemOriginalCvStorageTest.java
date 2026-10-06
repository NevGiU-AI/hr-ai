package com.nevgiu.hrai.candidate.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemOriginalCvStorageTest {

    @TempDir
    Path storageRoot;

    @Test
    void storesOriginalBytesUnderAnOpaqueTenantScopedKey() throws Exception {
        FileSystemOriginalCvStorage storage = storage();
        byte[] content = "%PDF-1.4\nprivate".getBytes();

        StoredCv stored = storage.store("tenant-a", content);

        assertThat(stored.storageKey()).doesNotContain("tenant-a");
        assertThat(stored.storageKey()).matches("[a-f0-9]{64}/[a-f0-9-]{36}\\.pdf");
        assertThat(Files.readAllBytes(storageRoot.resolve(stored.storageKey()))).isEqualTo(content);
    }

    @Test
    void deletesAStoredOriginal() {
        FileSystemOriginalCvStorage storage = storage();
        StoredCv stored = storage.store("tenant-a", "%PDF-1.4".getBytes());

        storage.delete(stored.storageKey());

        assertThat(storageRoot.resolve(stored.storageKey())).doesNotExist();
    }

    @Test
    void rejectsKeysThatEscapeTheStorageRoot() {
        assertThatThrownBy(() -> storage().delete("../outside.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private FileSystemOriginalCvStorage storage() {
        return new FileSystemOriginalCvStorage(
                new CvStorageProperties(storageRoot.toString(), Duration.ofDays(365)));
    }
}
