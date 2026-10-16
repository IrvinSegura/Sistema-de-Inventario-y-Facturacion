package com.irvin.inventory.catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

import com.irvin.inventory.catalog.dto.ProductResponse;
import com.irvin.inventory.common.exception.BadRequestException;
import com.irvin.inventory.common.exception.NotFoundException;
import com.irvin.inventory.common.exception.ServiceUnavailableException;
import com.irvin.inventory.common.storage.ImageStorage;
import com.irvin.inventory.common.storage.ImageType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

// Product photos: the file goes to ImageStorage outside any transaction (a slow upload must not hold
// a DB connection), then only its URL is saved. The replaced file is deleted after the commit; the
// new one is deleted again if saving the URL fails.
@Service
public class ProductImageService {

    private final ProductRepository productRepository;
    private final ObjectProvider<ImageStorage> storageProvider;
    private final TransactionTemplate transactionTemplate;

    public ProductImageService(ProductRepository productRepository, ObjectProvider<ImageStorage> storageProvider,
            PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.storageProvider = storageProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ProductResponse replaceImage(Long productId, MultipartFile file) {
        ImageStorage storage = storage();
        byte[] content = read(file);
        ImageType type = ImageType.detect(content)
                .orElseThrow(() -> new BadRequestException("Image must be PNG, JPEG, WebP or GIF"));
        if (!productRepository.existsById(productId)) {
            throw notFound();
        }

        String url = storage.store("products/" + productId + "-" + UUID.randomUUID(), content, type);
        Saved saved;
        try {
            saved = transactionTemplate.execute(status -> setImageUrl(productId, url));
        } catch (RuntimeException ex) {
            storage.delete(url);
            throw ex;
        }
        storage.delete(saved.previousUrl());
        return saved.product();
    }

    public ProductResponse removeImage(Long productId) {
        ImageStorage storage = storage();
        Saved saved = transactionTemplate.execute(status -> setImageUrl(productId, null));
        storage.delete(saved.previousUrl());
        return saved.product();
    }

    private Saved setImageUrl(Long productId, String url) {
        Product product = productRepository.findWithCategoryById(productId).orElseThrow(ProductImageService::notFound);
        String previousUrl = product.getImageUrl();
        product.setImageUrl(url);
        // Flush so the response carries the incremented version.
        productRepository.flush();
        return new Saved(ProductResponse.from(product), previousUrl);
    }

    private ImageStorage storage() {
        ImageStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new ServiceUnavailableException("Image uploads are not configured");
        }
        return storage;
    }

    private static byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Image file is required");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Product not found");
    }

    private record Saved(ProductResponse product, String previousUrl) {
    }
}
