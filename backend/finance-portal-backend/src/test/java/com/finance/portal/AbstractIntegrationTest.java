package com.finance.portal;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test'ler için paylaşılan altyapı tabanı.
 *
 * <p>Tüm {@code *IT} sınıfları bunu {@code extends} eder. Önceden her IT kendi
 * {@link PostgreSQLContainer}'ını + {@code @DynamicPropertySource}'unu ayrı ayrı
 * tanımlıyordu (kopya kod, her IT farklı datasource URL → ayrı Spring context →
 * yavaş). Burada tek paylaşılan Postgres + Redis container'ı başlatılır ve aynı
 * Spring context'i tüm IT'ler arasında cache'lenir (DRY + hızlı).</p>
 *
 * <p><b>Singleton container pattern (kritik):</b> Container'lar {@code static}
 * blokta manuel {@code start()} edilir; bilerek {@code @Testcontainers}/
 * {@code @Container} <i>kullanılmaz</i>. O anotasyonlar container yaşam döngüsünü
 * <i>sınıf başına</i> yönetir: abstract base'e konunca JUnit extension ilk IT
 * sınıfı bitince container'ı {@code stop()} eder, sonraki sınıflar ölü container'a
 * bağlanmaya çalışır ("Connection refused"). Manuel başlatılan singleton
 * container ise ilk sınıf yüklenişinde bir kez ayağa kalkar, tüm IT sınıfları
 * boyunca yaşar ve JVM kapanışında Ryuk tarafından temizlenir.</p>
 *
 * <p><b>Neden Redis container'ı:</b> Uygulama startup'ta ve scheduled görevlerde
 * cache evict/warmup yapar (ShedLock dağıtık kilidi de Redis kullanır). Redis
 * yoksa bu yollar hata loglar (uygulama dayanıklıdır, çökmez — bkz. BistIndexService),
 * ama gerçek bir Redis container'ı (a) bu ERROR log gürültüsünü siler, (b) cache
 * hit/miss/eviction davranışını gerçek backend'le test eder.</p>
 *
 * <p>Subclass'lar yalnız kendi {@code @SpringBootTest}/{@code @AutoConfigureMockMvc}
 * anotasyonlarını taşır; container + property kayıtları buradan miras alınır.</p>
 */
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES;
    protected static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                .withDatabaseName("finance_it_test")
                .withUsername("test")
                .withPassword("test");
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        // Manuel başlatma — singleton container pattern (yukarıdaki javadoc'a bkz).
        // Bilerek stop() edilmez; Ryuk JVM kapanışında temizler.
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerSharedProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
