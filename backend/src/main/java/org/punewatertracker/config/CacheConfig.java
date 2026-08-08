package org.punewatertracker.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * In-memory only (Caffeine, no Redis) -- deliberate, not an oversight. A second (Redis) tier
 * only earns its complexity once there's more than one running instance needing cross-instance
 * cache invalidation, which isn't the case on Render's free tier (single instance). Revisit
 * this if that ever changes.
 */
@Configuration
@EnableCaching
public class CacheConfig {
    public static final String LOCALITIES_CACHE = "localities";

    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> caffeineCacheManagerCustomizer() {
        return cacheManager -> cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .maximumSize(500)
        );
    }
}
