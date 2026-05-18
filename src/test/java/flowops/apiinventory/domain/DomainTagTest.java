package flowops.apiinventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainTagTest {

    @Test
    void resolvesBlankDomainTagFromFirstPathSegment() {
        assertThat(DomainTag.resolve(null, "/users/likes/{mateId}")).isEqualTo("USERS");
    }

    @Test
    void normalizesExplicitDomainTag() {
        assertThat(DomainTag.resolve("user-profile", "/ignored")).isEqualTo("USER_PROFILE");
    }

    @Test
    void returnsNullWhenPathHasOnlyTemplateSegments() {
        assertThat(DomainTag.resolve(null, "/{tenantId}/{mateId}")).isNull();
    }
}
