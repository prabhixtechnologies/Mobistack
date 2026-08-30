package com.fixflow.integration;

import com.fixflow.imports.ImportService;
import com.fixflow.presence.RedisPresenceStore;
import com.fixflow.security.jwt.JwtService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the application.
 *
 * <p>That is the whole point of it, and until this existed nothing in this module did it. The unit
 * tests here construct their subjects with {@code new} and hand them mocks, so they say a great deal
 * about the code and nothing at all about whether Spring can assemble it. A missing bean, an
 * ambiguous constructor or a broken auto-configuration was therefore invisible: the suite passed,
 * the image built, and the fault appeared when a container started in production.
 *
 * <p>The upgrade to Spring Boot 4 is what made that gap urgent. Boot 4 no longer auto-configures a
 * Jackson 2 {@code ObjectMapper}, and eight classes in this module ask to be given one, so the
 * context could not be built at all — a failure no unit test here could see. Anything that stops the
 * application booting should fail here first from now on.
 *
 * <p>Tagged {@code integration} because it needs Docker: {@code -Pintegration} runs it, the default
 * build skips it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
class ApplicationContextIntegrationTest {

    /**
     * Started in a static initialiser rather than via {@code @Container}, which stops containers when
     * the first test class using them finishes and leaves any later class unable to connect. Ryuk
     * cleans these up when the JVM exits.
     */
    // Not PostgreSQLContainer<?>: Testcontainers 2 moved this out of org.testcontainers.containers,
    // where a deprecated generic copy still sits, and dropped the self-referential type parameter.
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("mobistack")
                    .withUsername("mobistack")
                    .withPassword("mobistack");

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    /**
     * Flyway is off in {@code application-test.yml} and switched back on here, because a context that
     * starts against a schema nothing created would prove much less. JPA validates rather than
     * generates, so drift between the entities and the baseline fails this test too.
     */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        // application-test.yml turns Redis off so the unit tests need nothing running. Here there is
        // a container, and leaving it off would exclude the Redis-conditional half of the graph from
        // the only test that assembles it.
        registry.add("fixflow.redis.enabled", () -> "true");
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theApplicationStarts() {
        assertThat(context).isNotNull();
    }

    /**
     * Asks for the beans whose construction depends on the JSON mapper the container supplies, which
     * is the specific thing the Boot 4 upgrade changed. Resolving them by type here means a mapper
     * that is present but of the wrong type fails as a test rather than as a 500 at runtime.
     */
    @Test
    void theBeansThatNeedAJsonMapperAreConstructed() {
        assertThat(context.getBean(JwtService.class)).isNotNull();
        assertThat(context.getBean(ImportService.class)).isNotNull();
        assertThat(context.getBean(RedisPresenceStore.class)).isNotNull();
    }

    /**
     * The baseline ran, and ran as one migration. A second row here would mean the squashed baseline
     * had been split again, or an old migration reintroduced beside it.
     */
    @Test
    void theSchemaCameFromASingleBaselineMigration() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE installed_rank = 1", String.class))
                .isEqualTo("1");
    }
}
