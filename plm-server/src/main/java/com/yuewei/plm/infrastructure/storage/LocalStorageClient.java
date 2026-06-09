package com.yuewei.plm.infrastructure.storage;

import org.springframework.stereotype.Component;

@Component
public class LocalStorageClient implements StorageClient {

    @Override
    public String generateDownloadUrl(String storageKey) {
        return "/mock-storage/" + storageKey;
    }
}
