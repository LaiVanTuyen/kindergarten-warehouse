package com.kindergarten.warehouse.service;

import com.kindergarten.warehouse.exception.AppException;
import com.kindergarten.warehouse.exception.ErrorCode;
import com.kindergarten.warehouse.util.AppConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OTP service backed by Redis.
 *
 * <p>Xử lý mã one-time cho nhiều mục đích (verify email, reset password, …).
 * Dùng {@link SecureRandom} sinh mã và **Lua script atomic** cho toàn bộ flow
 * verify — tránh race giữa concurrent attempts khi check counter, get OTP,
 * delete key.
 */
@Service
@Slf4j
public class RedisOtpService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final Duration ATTEMPTS_TTL = Duration.ofMinutes(15);
    private static final int MAX_ATTEMPTS = 5;
    private static final int OTP_BOUND = 1_000_000; // 6-digit uniform

    /**
     * Atomic verify. Logic: tăng counter, nếu lần đầu thì set TTL, vượt ngưỡng
     * thì xóa OTP và trả {@code -1}; so khớp OTP đúng → xóa cả 2 key, trả {@code 1};
     * sai → trả {@code 0}.
     *
     * <pre>
     * KEYS[1] = attemptsKey
     * KEYS[2] = otpKey
     * ARGV[1] = otp input (string)
     * ARGV[2] = TTL cho attempts counter (seconds)
     * ARGV[3] = max attempts
     * </pre>
     */
    private static final DefaultRedisScript<Long> VERIFY_OTP = new DefaultRedisScript<>(
            """
            local attempts = redis.call('INCR', KEYS[1])
            if attempts == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            if attempts > tonumber(ARGV[3]) then
                redis.call('DEL', KEYS[2])
                return -1
            end
            local stored = redis.call('GET', KEYS[2])
            if not stored then
                return 0
            end
            if stored == ARGV[1] then
                redis.call('DEL', KEYS[2])
                redis.call('DEL', KEYS[1])
                return 1
            end
            return 0
            """,
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public RedisOtpService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public enum Purpose {
        EMAIL_VERIFY,
        PASSWORD_RESET,
        /** Flow cũ: admin khởi tạo reset cho user. Giữ để tương thích. */
        ADMIN_PASSWORD_RESET
    }

    /**
     * Sinh OTP mới và reset counter về 0.
     *
     * @return mã OTP 6 chữ số
     */
    public String generateOtp(Purpose purpose, String subject) {
        String otp = String.format("%06d", secureRandom.nextInt(OTP_BOUND));
        stringRedisTemplate.opsForValue().set(key(purpose, subject), otp, DEFAULT_TTL);
        stringRedisTemplate.delete(attemptsKey(purpose, subject));
        return otp;
    }

    /**
     * Verify OTP atomic qua Lua. Không throw nếu đúng.
     *
     * @throws AppException {@link ErrorCode#OTP_ATTEMPT_EXCEEDED} nếu vượt ngưỡng thử
     * @throws AppException {@link ErrorCode#INVALID_OTP} nếu sai hoặc đã hết hạn
     */
    public void verifyOtp(Purpose purpose, String subject, String otp) {
        String attemptsKey = attemptsKey(purpose, subject);
        String otpKey = key(purpose, subject);

        Long result = stringRedisTemplate.execute(
                VERIFY_OTP,
                List.of(attemptsKey, otpKey),
                otp,
                String.valueOf(ATTEMPTS_TTL.toSeconds()),
                String.valueOf(MAX_ATTEMPTS));

        long r = result == null ? 0L : result;
        if (r == 1L) {
            return; // OK
        }
        if (r == -1L) {
            Long ttl = stringRedisTemplate.getExpire(attemptsKey, TimeUnit.SECONDS);
            long retryAfter = ttl == null || ttl <= 0 ? ATTEMPTS_TTL.toSeconds() : ttl;
            throw AppException.withRetryAfter(ErrorCode.OTP_ATTEMPT_EXCEEDED, retryAfter);
        }
        throw new AppException(ErrorCode.INVALID_OTP);
    }

    private String key(Purpose purpose, String subject) {
        return AppConstants.REDIS_OTP_PREFIX + purpose.name().toLowerCase() + ":" + subject;
    }

    private String attemptsKey(Purpose purpose, String subject) {
        return AppConstants.REDIS_OTP_ATTEMPTS_PREFIX + purpose.name().toLowerCase() + ":" + subject;
    }
}
