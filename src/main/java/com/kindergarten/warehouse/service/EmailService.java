package com.kindergarten.warehouse.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Async
    public void sendOtp(String to, String otp) {
        // Fallback for Development (Log to console if no SMTP configured)
        if (senderEmail == null || senderEmail.isEmpty()) {
            logOtpToConsole(to, otp);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(to);
            helper.setSubject("Mã OTP xác thực đặt lại mật khẩu - Kindergarten Warehouse");

            String htmlContent = getOtpEmailTemplate(otp);
            helper.setText(htmlContent, true); // true = isHtml

            mailSender.send(message);
            log.info("OTP sent successfully to {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send OTP email to {}", to, e);
            // Fallback logging in case of error so dev/admin can still see the code
            logOtpToConsole(to, otp);
        }
    }

    @Async
    public void sendNewPassword(String to, String password) {
        // Fallback for Development
        if (senderEmail == null || senderEmail.isEmpty()) {
            logPasswordToConsole(to, password);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(to);
            helper.setSubject("Mật khẩu mới của bạn - Kindergarten Warehouse");

            String htmlContent = getNewPasswordTemplate(password);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("New password sent successfully to {}", to);

        } catch (MessagingException e) {
            log.error("Failed to send new password email to {}", to, e);
            logPasswordToConsole(to, password);
        }
    }

    private void logOtpToConsole(String to, String otp) {
        log.warn("SMTP not configured or failed. Logging OTP for debugging.");
        log.info("--------------------------------------------------");
        log.info(" [DEBUG-EMAIL] To: {}", to);
        log.info(" OTP Code: {}", otp);
        log.info("--------------------------------------------------");
    }

    private void logPasswordToConsole(String to, String password) {
        log.warn("SMTP not configured or failed. Logging Password for debugging.");
        log.info("--------------------------------------------------");
        log.info(" [DEBUG-EMAIL] To: {}", to);
        log.info(" New Password: {}", password);
        log.info("--------------------------------------------------");
    }

    private String getOtpEmailTemplate(String otp) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #eee; border-radius: 10px;">
                    <div style="text-align: center; margin-bottom: 20px;">
                        <h2 style="color: #4A90E2; margin: 0;">KINDERWAREHOUSE</h2>
                        <p style="color: #666; font-size: 14px; margin-top: 5px;">Hệ thống quản lý kho mầm non</p>
                    </div>

                    <h3 style="color: #333; text-align: center;">Yêu cầu đặt lại mật khẩu</h3>
                    <p>Xin chào,</p>
                    <p>Hệ thống nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. Vui lòng sử dụng mã xác thực (OTP) dưới đây để tiếp tục:</p>

                    <div style="background-color: #f8f9fa; padding: 20px; text-align: center; border-radius: 8px; margin: 25px 0;">
                        <span style="font-size: 32px; font-weight: bold; color: #2c3e50; letter-spacing: 8px;">%s</span>
                    </div>

                    <p style="color: #d63031; font-weight: bold;">Lưu ý:</p>
                    <ul style="color: #555;">
                        <li>Mã này sẽ hết hạn sau <strong>5 phút</strong>.</li>
                        <li>Tuyệt đối không chia sẻ mã này cho bất kỳ ai.</li>
                    </ul>
                    <p>Nếu bạn không gửi yêu cầu này, vui lòng bỏ qua email này.</p>
                    <br>
                    <hr style="border: none; border-top: 1px solid #eee;" />
                    <p style="font-size: 12px; color: #888; text-align: center;">© 2026 Kindergarten Warehouse System. All rights reserved.</p>
                </div>
                """
                .formatted(otp);
    }

    private String getNewPasswordTemplate(String password) {
        return """
                 <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #eee; border-radius: 10px;">
                    <div style="text-align: center; margin-bottom: 20px;">
                        <h2 style="color: #4A90E2; margin: 0;">KINDERWAREHOUSE</h2>
                    </div>

                    <h3 style="color: #2e7d32; text-align: center;">Mật khẩu đặt lại thành công</h3>
                    <p>Xin chào,</p>
                    <p>Mật khẩu của bạn đã được đặt lại thành công. Dưới đây là thông tin đăng nhập mới:</p>

                    <div style="background-color: #e8f5e9; padding: 20px; text-align: center; border-radius: 8px; margin: 25px 0; border: 1px dashed #2e7d32;">
                        <span style="font-size: 24px; font-weight: bold; color: #2e7d32;">%s</span>
                    </div>

                    <div style="background-color: #fff3cd; padding: 15px; border-radius: 5px; color: #856404; font-size: 14px;">
                        <strong>Quan trọng:</strong> Vì lý do bảo mật, vui lòng đăng nhập và đổi lại mật khẩu cá nhân ngay lập tức.
                    </div>

                    <br>
                    <p>Trân trọng,<br>Đội ngũ hỗ trợ kỹ thuật</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin-top: 20px;" />
                    <p style="font-size: 12px; color: #888; text-align: center;">© 2026 Kindergarten Warehouse System</p>
                </div>
                """
                .formatted(password);
    }
}
