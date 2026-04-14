package cl.cesarg.siiproxyHA.application.service;

import cl.cesarg.siiproxyHA.infrastructure.persistence.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public void validateItemsBelongToTenant(UUID tenantId, List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return;
        for (String id : itemIds) {
            try {
                UUID pid = UUID.fromString(id);
                boolean ok = productRepository.existsByTenantIdAndId(tenantId, pid);
                if (!ok) throw new IllegalArgumentException("Item with id " + id + " does not belong to tenant");
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid item id: " + id);
            }
        }
    }
}
