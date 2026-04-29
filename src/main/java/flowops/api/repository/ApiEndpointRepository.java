package flowops.api.repository;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {

    @Query("""
            select a
            from ApiEndpoint a
            where a.app.id = :appId
              and (:domainTag is null or a.domainTag = :domainTag)
              and (:method is null or a.method = :method)
            order by a.path asc, a.method asc
            """)
    Page<ApiEndpoint> findByFilters(
            @Param("appId") Long appId,
            @Param("domainTag") String domainTag,
            @Param("method") ApiMethod method,
            Pageable pageable
    );

    Optional<ApiEndpoint> findFirstByMethodAndPath(ApiMethod method, String path);

    List<ApiEndpoint> findByAppId(Long appId);
}
