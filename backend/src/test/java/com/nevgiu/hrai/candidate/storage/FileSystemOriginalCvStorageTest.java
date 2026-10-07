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
    void quarantinesThenPromotesOriginalBytesUnderAnOpaqueTenantScopedKey() throws Exception {
        FileSystemOriginalCvStorage storage = storage();
        byte[] content = "%PDF-1.4\nprivate".getBytes();

        QuarantinedCv quarantined = storage.quarantine("tenant-a", content);
        assertThat(quarantined.storageKey()).startsWith("_quarantine/");
        assertThat(Files.readAllBytes(storageRoot.resolve(quarantined.storageKey()))).isEqualTo(content);

        StoredCv stored = storage.promote(quarantined.storageKey());

        assertThat(stored.storageKey()).doesNotContain("tenant-a");
        assertThat(stored.storageKey()).matches("[a-f0-9]{64}/[a-f0-9-]{36}\\.pdf");
        assertThat(Files.readAllBytes(storageRoot.resolve(stored.storageKey()))).isEqualTo(content);
        assertThat(storageRoot.resolve(quarantined.storageKey())).doesNotExist();
    }

    @Test
    void deletesAStoredOriginal() {
        FileSystemOriginalCvStorage storage = storage();
        StoredCv stored = storage.promote(storage.quarantine("tenant-a", "%PDF-1.4".getBytes()).storageKey());

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
