package flowops.github.client;

import flowops.github.dto.response.BranchResponse;
import flowops.github.dto.response.RepositoryFile;
import flowops.github.dto.response.RepositorySnapshot;
import java.util.List;
import java.util.Optional;

public interface GithubClient {
    RepositorySnapshot fetchRepository(String owner, String repositoryName);

    List<BranchResponse> fetchBranches(String owner, String repositoryName, String defaultBranch);

    List<RepositoryFile> findRepositoryFiles(String owner, String repositoryName, String branchName);

    List<RepositoryFile> findOpenApiSpecFiles(String owner, String repositoryName, String branchName);

    Optional<String> fetchFileContent(String owner, String repositoryName, String path, String branchName);
}
