package flowops.apiinventory.repository;

import flowops.apiinventory.domain.entity.ApiHttpMethod;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.domain.entity.ApiInventorySource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiInventoryRepository extends JpaRepository<ApiInventory, Long> {
    List<ApiInventory> findByProjectIdOrderByIdDesc(Long projectId);

    List<ApiInventory> findByProjectIdAndRepositoryInfoIdOrderByIdDesc(Long projectId, Long repositoryId);

    List<ApiInventory> findByProjectIdAndRepositoryInfoIdAndBranchNameOrderByIdDesc(Long projectId, Long repositoryId, String branchName);

    Optional<ApiInventory> findByIdAndProjectId(Long id, Long projectId);

    @Query("""
            select inventory
            from ApiInventory inventory
            where inventory.project.id = :projectId
              and (:repositoryId is null or inventory.repositoryInfo.id = :repositoryId)
              and (:branchName is null or inventory.branchName = :branchName)
              and (:method is null or inventory.method = :method)
              and (:sourceType is null or inventory.sourceType = :sourceType)
            order by inventory.id desc
            """)
    List<ApiInventory> findByFilters(
            @Param("projectId") Long projectId,
            @Param("repositoryId") Long repositoryId,
            @Param("branchName") String branchName,
            @Param("method") ApiHttpMethod method,
            @Param("sourceType") ApiInventorySource sourceType
    );

    @Query("""
            select inventory
            from ApiInventory inventory
            where inventory.project.id = :projectId
              and (:repositoryId is null or inventory.repositoryInfo.id = :repositoryId)
              and (:branchName is null or inventory.branchName = :branchName)
              and (:method is null or inventory.method = :method)
              and (:sourceType is null or inventory.sourceType = :sourceType)
              and (
                    lower(inventory.endpointPath) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(inventory.operationId, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(inventory.summary, '')) like lower(concat('%', :keyword, '%'))
              )
            order by inventory.id desc
            """)
    List<ApiInventory> findByFiltersAndKeyword(
            @Param("projectId") Long projectId,
            @Param("repositoryId") Long repositoryId,
            @Param("branchName") String branchName,
            @Param("method") ApiHttpMethod method,
            @Param("sourceType") ApiInventorySource sourceType,
            @Param("keyword") String keyword
    );

    void deleteByRepositoryInfoIdAndBranchName(Long repositoryId, String branchName);
}
