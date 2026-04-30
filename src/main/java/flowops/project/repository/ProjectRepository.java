package flowops.project.repository;

import flowops.project.domain.entity.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    boolean existsBySlug(String slug);

    List<Project> findAllByOrderByCreatedAtDesc();
}
