package com.kindergarten.warehouse.listener;

import com.kindergarten.warehouse.event.ResourceRejectedEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final JavaMailSender mailSender;
    // We assume the application properties has spring.mail.username defined
    // but standard practice is to inject a specific sender address if needed.
    // For now, we will rely on default spring boot starter mail autoconfiguration.

    @Async
    @EventListener
    public void handleResourceRejectedEvent(ResourceRejectedEvent event) {
        log.info("🔔 [ASYNC] Handling ResourceRejectedEvent for document: {}, uploader email: {}",
                event.getDocumentTitle(), event.getUploaderEmail());

        if (event.getUploaderEmail() == null || event.getUploaderEmail().isEmpty()) {
            log.warn("❌ [ASYNC] Cannot send rejection email because uploader email is null/empty for resource: {}",
                    event.getDocumentTitle());
            return;
        }

        try {
            sendRejectionEmail(event);
            log.info("✅ [ASYNC] Successfully sent rejection email to {}", event.getUploaderEmail());
        } catch (Exception e) {
            log.error("💥 [ASYNC] Failed to send rejection email to {}: {}", event.getUploaderEmail(), e.getMessage(),
                    e);
            // We catch generic Exception to prevent the main thread from knowing or caring
            // about this failure
            // This ensures full decoupling - the API transaction continues even if SMTP is
            // completely down
        }
    }

    private void sendRejectionEmail(ResourceRejectedEvent event) throws MessagingException {
        if (event.getUploaderEmail() == null) {
            return; // Safety check
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name());

        helper.setTo(event.getUploaderEmail());
        helper.setSubject("Thông báo: Tài liệu của bạn đã bị từ chối phê duyệt");

        String htmlTemplate = """
                <html>
                <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e1e4e8; border-radius: 8px;">
                        <h2 style="color: #d73a49; border-bottom: 2px solid #d73a49; padding-bottom: 10px;">
                            Kho Tài Liệu - Thông Báo Từ Chối
                        </h2>

                        <p>Chào <strong>%s</strong>,</p>

                        <p>Chúng tôi rất tiếc phải thông báo rằng tài liệu có tiêu đề: <strong>"%s"</strong> mà bạn vừa tải lên chưa đạt yêu cầu phê duyệt để hiển thị công khai trên hệ thống.</p>

                        <div style="background-color: #ffeef0; padding: 15px; border-left: 5px solid #d73a49; margin: 20px 0;">
                            <h4 style="margin-top: 0; color: #cb2431;">Lý do từ chối:</h4>
                            <p style="margin-bottom: 0;">%s</p>
                        </div>

                        <p><strong>Hướng dẫn khắc phục:</strong></p>
                        <ul>
                            <li>Vui lòng đăng nhập vào trang quản trị.</li>
                            <li>Vào mục <strong>Tài Liệu Của Tôi</strong>.</li>
                            <li>Tìm kiếm tài liệu bị từ chối.</li>
                            <li>Nhấn <strong>Chỉnh sửa</strong>, cập nhật thông tin/file theo lý do lỗi bên trên.</li>
                            <li>Lưu lại. Hệ thống sẽ tự động đưa tài liệu của bạn trở lại trạng thái Chờ Duyệt (PENDING).</li>
                        </ul>

                        <p>Cảm ơn bạn đã đóng góp nội dung cho Kho Tài Liệu Mầm Non!</p>
                        <hr style="border: 0; border-top: 1px solid #e1e4e8; margin: 20px 0;">
                        <p style="font-size: 12px; color: #6a737d; text-align: center;">Đây là email tự động, vui lòng không trả lời qua địa chỉ này.</p>
                    </div>
                </body>
                </html>
                """;

        String reason = event.getReason() != null ? event.getReason() : "Chưa tuân thủ quy định đăng tải.";
        String htmlContent = String.format(
                htmlTemplate,
                event.getUploaderName() != null ? event.getUploaderName() : "Giáo viên",
                event.getDocumentTitle(),
                reason);

        helper.setText(htmlContent, true); // Set parameter true indicates this is an HTML email

        mailSender.send(message);
    }
}
