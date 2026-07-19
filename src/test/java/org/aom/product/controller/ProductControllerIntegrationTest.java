package org.aom.product.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level integration test combining:
 *   - Testcontainers Approach 2 (@Testcontainers + @Container) for the database
 *   - @SpringBootTest for the full application context
 *   - MockMvc for HTTP-level assertions
 *
 * <p>This tests the complete request path: HTTP → Controller → Service → Repository → Postgres.
 * Nothing is mocked — every layer is real.
 *
 * <p>Compare with ProductControllerIntegrationUsesInitializerTest which achieves the same
 * result but uses a shared external initializer class instead of an inline @Container field.
 */

// @SpringBootTest(webEnvironment = RANDOM_PORT):
//   Boots the ENTIRE Spring application (all beans, JPA, web layer).
//   RANDOM_PORT starts a real embedded Tomcat on a free port, avoiding port conflicts.
//   This is the heaviest test slice — use it when you need end-to-end coverage.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)

// @AutoConfigureMockMvc:
//   Auto-creates a MockMvc bean that is pre-configured to talk to the started application.
//   MockMvc simulates HTTP calls internally without going over the network,
//   making it faster than a real RestTemplate/WebClient HTTP call to the server.
@AutoConfigureMockMvc

// @Testcontainers:
//   Activates the Testcontainers JUnit 5 extension which manages the lifecycle of
//   any @Container-annotated fields in this class.
@Testcontainers
class ProductControllerIntegrationTest {

    // MockMvc is injected by @AutoConfigureMockMvc — used to perform HTTP requests in tests.
    @Autowired
    private MockMvc mockMvc;

    // @Container: the Testcontainers extension will start this before tests and stop it after.
    // static: one container shared for all test methods (not restarted per test).
    // PostgreSQLContainer wraps the postgres:16 Docker image with helper methods
    // like getJdbcUrl(), getUsername(), getPassword() that return the actual runtime values.
    @Container
    static PostgreSQLContainer<?> postgresqlContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    // @DynamicPropertySource:
    //   Overrides Spring's datasource properties at runtime with the container's actual
    //   connection details (URL with random port, auto-generated username and password).
    //   Spring calls this static method AFTER the container starts but BEFORE the
    //   application context is fully initialized — so all beans get the correct DB config.
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgresqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresqlContainer::getUsername);
        registry.add("spring.datasource.password", postgresqlContainer::getPassword);
    }

    @Test
    void getAllProducts() throws Exception {
        // Performs GET /product-service/getAllProducts through the full stack:
        // MockMvc → DispatcherServlet → ProductController → ProductService
        //        → ProductRepository → real Postgres container
        //
        // .andExpect(status().isOk()) asserts HTTP 200.
        // The data.sql seed file runs on startup so at least the seeded products exist.
        mockMvc.perform(get("/product-service/getAllProducts"))
                .andExpect(status().isOk());
    }
}