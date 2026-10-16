package com.irvin.inventory.common.storage;

// Where uploaded images live (never in the DB): Cloudinary when configured, otherwise a local folder.
public interface ImageStorage {

    // Saves the image under key (e.g. "products/12-<uuid>", no extension) and returns its public URL.
    String store(String key, byte[] content, ImageType type);

    // Best effort: an image this storage didn't create (e.g. an external URL) is ignored.
    void delete(String url);
}
