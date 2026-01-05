package com.kindergarten.warehouse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@org.springframework.cache.annotation.EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer());
        return template;
    }

    @Bean
    public org.springframework.cache.CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        org.springframework.data.redis.cache.RedisCacheConfiguration config = org.springframework.data.redis.cache.RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(java.time.Duration.ofMinutes(60)) // Default TTL 1 hour
                .serializeKeysWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                                .fromSerializer(
                                        new org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer()))
                .computePrefixWith(cacheName -> "kindergarten:" + cacheName + ":")
                .disableCachingNullValues();

        return org.springframework.data.redis.cache.RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
