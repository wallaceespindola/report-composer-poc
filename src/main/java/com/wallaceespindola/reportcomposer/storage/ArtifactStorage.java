package com.wallaceespindola.reportcomposer.storage;

import java.io.InputStream;

/** Object storage for report artifacts. Put is an overwrite-by-key (idempotent). */
public interface ArtifactStorage {

    void put(String objectKey, byte[] content, String contentType);

    InputStream get(String objectKey);
}
