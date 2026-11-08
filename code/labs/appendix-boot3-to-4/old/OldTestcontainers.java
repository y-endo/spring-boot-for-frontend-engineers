import org.testcontainers.containers.PostgreSQLContainer;

class OldTestcontainers {
    PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:18");
}
