package dms.test3.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {
    @Bean
    public Cache<String, Boolean> requestDebounceCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(2000, TimeUnit.MILLISECONDS) // Игнорировать дубликаты в течение 500 мс
                .maximumSize(10000) // Ограничение размера кэша
                .build();
    }
}