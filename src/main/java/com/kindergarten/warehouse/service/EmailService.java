package com.kindergarten.warehouse.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Gửi email bất đồng bộ.
 *
 * <p>Trong môi trường dev, nếu SMTP chưa cấu hình, mã OTP sẽ được log ra console
 * ở mức {@code debug} — KHÔNG bao giờ log plaintext password/OTP ở mức info/warn
 * trong môi trường prod để tránh leak credential.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final Environment environment;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    @Async
    public void sendOtpForPasswordReset(String to, String otp) {
        sendHtml(to,
                "Mã OTP đặt lại mật khẩu - Kindergarten Warehouse",
                buildOtpEmail(otp, "đặt lại mật khẩu"),
                "reset-password OTP", otp);
    }

    @Async
    public void sendOtpForEmailVerification(String to, String otp) {
        sendHtml(to,
                "Xác thực email - Kindergarten Warehouse",
                buildOtpEmail(otp, "xác thực email"),
                "verify-email OTP", otp);
    }

    @Async
    public void sendNewPassword(String to, String password) {
        sendHtml(to,
                "Mật khẩu mới - Kindergarten Warehouse",
                buildNewPasswordEmail(password),
                "admin-reset-password",
                null /* không log password kể cả ở dev */);
    }

    @Async
    public void sendAccountBlockedNotification(String to, String reason) {
        sendHtml(to,
                "Tài khoản đã bị khóa - Kindergarten Warehouse",
                buildBlockedEmail(reason),
                "account blocked", null);
    }

    @Async
    public void sendAccountUnblockedNotification(String to) {
        sendHtml(to,
                "Tài khoản đã được mở khóa - Kindergarten Warehouse",
                buildUnblockedEmail(),
                "account unblocked", null);
    }

    private void sendHtml(String to, String subject, String htmlBody, String purpose, String devCodeToLog) {
        if (senderEmail == null || senderEmail.isBlank()) {
            logDevFallback(to, purpose, devCodeToLog);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(senderEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent: purpose={}, to={}", purpose, maskEmail(to));
        } catch (MessagingException e) {
            log.error("Failed to send email purpose={} to={}", purpose, maskEmail(to), e);
            logDevFallback(to, purpose, devCodeToLog);
        }
    }

    private void logDevFallback(String to, String purpose, String devCode) {
        if (!isDevProfile()) {
            log.warn("SMTP not configured. Skipping {} email to {}", purpose, maskEmail(to));
            return;
        }
        // Dev-only: in ra code để debug cục bộ
        if (devCode != null) {
            log.debug("[DEV-EMAIL][{}] to={} code={}", purpose, to, devCode);
        } else {
            log.debug("[DEV-EMAIL][{}] to={}", purpose, to);
        }
    }

    private boolean isDevProfile() {
        for (String p : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(p) || "local".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return environment.getActiveProfiles().length == 0;
    }

    private String maskEmail(String email) {
        if (email == null) return "null";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String buildOtpEmail(String otp, String purposeVi) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #eee; border-radius: 10px;">
                    <div style="text-align: center; margin-bottom: 20px;">
                        <h2 style="color: #4A90E2; margin: 0;">KINDERWAREHOUSE</h2>
                        <p style="color: #666; font-size: 14px; margin-top: 5px;">Hệ thống quản lý kho tài liệu mầm non</p>
                    </div>
                    <h3 style="color: #333; text-align: center;">Yêu cầu %s</h3>
                    <p>Xin chào,</p>
                    <p>Vui lòng sử dụng mã xác thực (OTP) dưới đây:</p>
                    <div style="background-color: #f8f9fa; padding: 20px; text-align: center; border-radius: 8px; margin: 25px 0;">
                        <span style="font-size: 32px; font-weight: bold; color: #2c3e50; letter-spacing: 8px;">%s</span>
                    </div>
                    <p style="color: #d63031; font-weight: bold;">Lưu ý:</p>
                    <ul style="color: #555;">
                        <li>Mã sẽ hết hạn sau <strong>5 phút</strong>.</li>
                        <li>Tuyệt đối không chia sẻ mã với bất kỳ ai.</li>
                    </ul>
                    <p>Nếu bạn không yêu cầu hành động này, vui lòng bỏ qua email.</p>
                    <hr style="border: none; border-top: 1px solid #eee;" />
                    <p style="font-size: 12px; color: #888; text-align: center;">© Kindergarten Warehouse</p>
                </div>
                """.formatted(purposeVi, otp);
    }

    private String buildNewPasswordEmail(String password) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #eee; border-radius: 10px;">
                    <h3 style="color: #2e7d32; text-align: center;">Mật khẩu đã được đặt lại</h3>
                    <p>Mật khẩu mới của bạn:</p>
                    <div style="background-color: #e8f5e9; padding: 20px; text-align: center; border-radius: 8px; margin: 25px 0; border: 1px dashed #2e7d32;">
                        <span style="font-size: 24px; font-weight: bold; color: #2e7d32;">%s</span>
                    </div>
                    <p style="color: #856404; background-color: #fff3cd; padding: 15px; border-radius: 5px;">
                        <strong>Quan trọng:</strong> Vui lòng đăng nhập và đổi lại mật khẩu ngay.
                    </p>
                </div>
                """.formatted(password);
    }

    private String buildBlockedEmail(String reason) {
        String safeReason = reason == null || reason.isBlank() ? "Không có lý do cụ thể" : reason;
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #eee; border-radius: 10px;">
                    <h3 style="color: #d63031; text-align: center;">Tài khoản của bạn đã bị khóa</h3>
                    <p>Lý do: <strong>%s</strong></p>
                    <p>Vui lòng liên hệ quản trị viên nếu cần hỗ trợ.</p>
                </div>
                """.formatted(safeReason);
    }

    private String buildUnblockedEmail() {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #eee; border-radius: 10px;">
                    <h3 style="color: #2e7d32; text-align: center;">Tài khoản đã được mở khóa</h3>
                    <p>Bạn có thể đăng nhập và sử dụng hệ thống bình thường.</p>
                </div>
                """;
    }
}
