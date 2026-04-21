package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.entity.User;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.util.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Quản lý vô hiệu hóa JWT:
 * <ul>
 *   <li><b>Blacklist theo token</b>: khi user logout, token cụ thể được blacklist.</li>
 *   <li><b>Token version theo user</b>: khi đổi mật khẩu / block / thay role, tất cả JWT
 *       đã phát hành cho user đó (bất kể token nào) bị từ chối nhờ check {@code tokenVersion}
 *       claim &lt; giá trị hiện tại.</li>
 * </ul>
 *
 * <p>Dùng {@link StringRedisTemplate} để value được serialize thành plain string —
 * cần thiết khi pipeline cùng lúc với các raw byte operation và khi đồng bộ với
 * Lua scripts khác (RateLimit, OTP).
 *
 * <p><b>Performance</b>: {@link #checkTokenState(String, Long, Long)} gộp 2 Redis call
 * (EXISTS blacklist + GET tokenVersion) thành <b>1 roundtrip</b> qua pipeline — giảm
 * ~50% latency cho request authenticated. Cache miss trên tv fallback DB (hiếm khi xảy ra).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRevocationService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final StringRedisTemplate stringRedisTemplate;

    public enum State {
        /** Token hợp lệ, có thể tiếp tục xử lý request. */
        VALID,
        /** Token nằm trong blacklist (user đã logout). */
        BLACKLISTED,
        /** Token bị revoke bằng cách tăng tokenVersion (đổi pass, block, thay role). */
        REVOKED
    }

    /**
     * Check 1-shot (pipelined) trạng thái token trong Redis.
     *
     * @param token             full JWT string để tra blacklist
     * @param userId            userId lấy từ claim
     * @param tokenVersionClaim tokenVersion lấy từ claim
     * @return trạng thái — filter dựa vào đây để 401 hoặc tiếp tục
     */
    public State checkTokenState(String token, Long userId, Long tokenVersionClaim) {
        if (userId == null || tokenVersionClaim == null) {
            return State.REVOKED;
        }

        byte[] blacklistKey = (AppConstants.REDIS_JWT_BLACKLIST + token).getBytes(StandardCharsets.UTF_8);
        byte[] tvKey = (AppConstants.REDIS_TOKEN_VERSION_PREFIX + userId).getBytes(StandardCharsets.UTF_8);

        List<Object> results = stringRedisTemplate.executePipelined((RedisCallback<Object>) conn -> {
            pipelineReadState(conn, blacklistKey, tvKey);
            return null;
        });

        Boolean isBlacklisted = results.size() > 0 && Boolean.TRUE.equals(results.get(0));
        if (isBlacklisted) {
            return State.BLACKLISTED;
        }

        long currentVersion;
        Object tvRaw = results.size() > 1 ? results.get(1) : null;
        if (tvRaw instanceof String s && !s.isEmpty()) {
            try {
                currentVersion = Long.parseLong(s);
            } catch (NumberFormatException e) {
                log.warn("Invalid tokenVersion in Redis for user {}: '{}'", userId, s);
                currentVersion = fallbackFromDb(userId);
            }
        } else {
            // Cache miss — hiếm khi xảy ra (đa số user active có cache hit)
            currentVersion = fallbackFromDb(userId);
        }

        return tokenVersionClaim >= currentVersion ? State.VALID : State.REVOKED;
    }

    /** Các command đọc raw bytes sẽ xếp hàng vào pipeline, reply sẽ quay về theo thứ tự. */
    private void pipelineReadState(RedisConnection conn, byte[] blacklistKey, byte[] tvKey) {
        conn.keyCommands().exists(blacklistKey);
        conn.stringCommands().get(tvKey);
    }

    /**
     * Tăng token version — tất cả JWT mang version cũ sẽ bị reject ở filter.
     * Nên gọi trong transaction của operation trigger (block/changePass/...) để đảm bảo
     * consistency DB↔Redis: DB commit → cache evict → filter đọc version mới.
     */
    @Transactional
    public long revokeAllTokens(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    user.incrementTokenVersion();
                    userRepository.save(user);
                    long newVersion = user.getTokenVersion();
                    stringRedisTemplate.opsForValue().set(
                            AppConstants.REDIS_TOKEN_VERSION_PREFIX + userId,
                            String.valueOf(newVersion),
                            CACHE_TTL);
                    return newVersion;
                })
                .orElse(0L);
    }

    private long fallbackFromDb(Long userId) {
        long version = userRepository.findById(userId)
                .map(User::getTokenVersion)
                .orElse(0L);
        stringRedisTemplate.opsForValue().set(
                AppConstants.REDIS_TOKEN_VERSION_PREFIX + userId,
                String.valueOf(version),
                CACHE_TTL);
        return version;
    }
}
