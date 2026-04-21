package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.util.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

/**
 * Quản lý "phiên bản token" theo user để có thể vô hiệu hóa toàn bộ JWT đã cấp
 * khi: đổi mật khẩu, admin block, reset password, "logout all devices".
 *
 * <p>Mỗi user có một {@code tokenVersion} lưu trong DB. JWT mang claim
 * {@link AppConstants#JWT_CLAIM_TOKEN_VERSION}. Khi filter verify, nếu
 * claim < tokenVersion hiện tại → token bị từ chối.
 *
 * <p>Để tránh query DB mỗi request, version được cache 5 phút trong Redis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRevocationService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public long getCurrentVersion(Long userId) {
        String key = AppConstants.REDIS_TOKEN_VERSION_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof Number num) {
            return num.longValue();
        }

        long version = userRepository.findById(userId)
                .map(User::getTokenVersion)
                .orElse(0L);
        redisTemplate.opsForValue().set(key, version, CACHE_TTL);
        return version;
    }

    /**
     * Tăng token version — tất cả JWT mang version cũ sẽ bị reject ở filter.
     * Phải gọi bên trong transaction (hoặc sau commit) để đảm bảo consistency.
     */
    @Transactional
    public long revokeAllTokens(Long userId) {
        Optional<User> opt = userRepository.findById(userId);
        if (opt.isEmpty()) {
            return 0L;
        }
        User user = opt.get();
        user.incrementTokenVersion();
        userRepository.save(user);

        long newVersion = user.getTokenVersion();
        redisTemplate.opsForValue().set(
                AppConstants.REDIS_TOKEN_VERSION_PREFIX + userId, newVersion, CACHE_TTL);
        return newVersion;
    }

    public boolean isTokenStillValid(Long userId, Long tokenVersionClaim) {
        if (tokenVersionClaim == null) {
            return false;
        }
        return tokenVersionClaim >= getCurrentVersion(userId);
    }
}
