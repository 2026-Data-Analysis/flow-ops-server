package flowops.testgeneration.domain.entity;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.apiinventory.domain.entity.ApiInventory;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "test_generation_api_selections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestGenerationApiSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generation_id", nullable = false)
    private TestGeneration generation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_id", nullable = false)
    private ApiEndpoint apiEndpoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_inventory_id")
    private ApiInventory apiInventory;

    @Builder
    private TestGenerationApiSelection(TestGeneration generation, ApiEndpoint apiEndpoint, ApiInventory apiInventory) {
        this.generation = generation;
        this.apiEndpoint = apiEndpoint;
        this.apiInventory = apiInventory;
    }
}
