package org.aom.product.controller;

import org.aom.product.infra.PostgresDatabaseContainerInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testcontainers Approach 4b — Full integration test using the shared initializer.
 *
 * <p>This is a CONTROLLER-level integration test. Unlike the repository tests that only
 * load the JPA layer, this test starts the FULL Spring Boot application context including
 * the web layer, and makes real HTTP requests through MockMvc.
 *
 * <p>The database is provided by PostgresDatabaseContainerInitializer — a shared helper
 * class that starts one Postgres container reused by all test classes that reference it.
 *
 * <p>Compare this with ProductControllerIntegrationTest which achieves the same result
 * but declares the container directly inside the test class (Approach 2 style).
 */

// @SpringBootTest:
//   Loads the FULL Spring application context — all beans, all layers.
//   This means @SpringBootApplication is found and everything is wired up:
//   controllers, services, repositories, JPA configuration, and the web layer.
//
//   webEnvironment = RANDOM_PORT:
//     Starts an embedded web server (Tomcat) on a random available port.
//     Using a random port avoids conflicts if multiple test classes run concurrently,
//     or if port 8081 is already in use on your machine.
//
//   This is slower than @DataJpaTest because it loads everything, but it is the only
//   way to test the full request/response cycle from HTTP down to the database.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)

// @AutoConfigureMockMvc:
//   Creates and configures a MockMvc instance and makes it available for @Autowired injection.
//   MockMvc lets you perform HTTP requests (GET, POST, etc.) against the running application
//   in your test code without needing a real HTTP client like Postman or curl.
//   It talks directly to the DispatcherServlet, going through the full Spring MVC pipeline
//   (interceptors, argument resolvers, response converters, etc.).
@AutoConfigureMockMvc

// @ContextConfiguration(initializers = {...}):
//   Registers one or more ApplicationContextInitializer classes to run during context setup.
//   PostgresDatabaseContainerInitializer (in src/test/java/org/aom/product/infra/) starts
//   a real Postgres Docker container and injects its connection URL into Spring's environment —
//   all before any beans are loaded.
//
//   This is the Approach 4 alternative to declaring @Container + @DynamicPropertySource directly
//   in the test class. The benefit: many test classes can reuse the same initializer without
//   repeating the container setup code.
@ContextConfiguration(initializers = {PostgresDatabaseContainerInitializer.class})
class ProductControllerIntegrationUsesInitializerTest {

    // MockMvc is injected by @AutoConfigureMockMvc.
    // It is the test stand-in for a real HTTP client — you build requests with it
    // and assert on the responses.
    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAllProducts() throws Exception {
        // mockMvc.perform(...):
        //   Executes an HTTP request through the full Spring MVC pipeline.
        //   get("/product-service/getAllProducts") builds a GET request to that path.
        //
        // .andExpect(status().isOk()):
        //   Asserts that the HTTP response status is 200 OK.
        //   If the controller throws an exception or the DB is unreachable, the status
        //   would be 500 and this assertion would fail — catching real integration problems.
        mockMvc.perform(get("/product-service/getAllProducts"))
                .andExpect(status().isOk());
    }
}