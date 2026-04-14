package cl.cesarg.siiproxyHA.infrastructure.persistence;

import cl.cesarg.siiproxyHA.domain.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("select case when count(p)>0 then true else false end from Product p where p.tenant.id = :tenantId and p.id = :productId")
    boolean existsByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("productId") UUID productId);
}
