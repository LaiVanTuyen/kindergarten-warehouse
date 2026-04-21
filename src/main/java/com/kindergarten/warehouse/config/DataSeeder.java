package com.kindergarten.warehouse.config;

import com.kindergarten.warehouse.entity.Banner;
import com.kindergarten.warehouse.entity.Category;
import com.kindergarten.warehouse.entity.Topic;
import com.kindergarten.warehouse.repository.BannerRepository;
import com.kindergarten.warehouse.repository.CategoryRepository;
import com.kindergarten.warehouse.repository.TopicRepository;
import com.kindergarten.warehouse.repository.UserRepository;
import com.kindergarten.warehouse.service.MinioStorageService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class DataSeeder implements CommandLineRunner {

        private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

        private final CategoryRepository categoryRepository;
        private final TopicRepository topicRepository;
        private final BannerRepository bannerRepository;
        private final UserRepository userRepository;
        private final MinioStorageService minioStorageService;
        private final PasswordEncoder passwordEncoder;
        private final Environment environment;

        @org.springframework.beans.factory.annotation.Value("${app.admin.username:admin}")
        private String adminUsername;

        @org.springframework.beans.factory.annotation.Value("${app.admin.password:" + DEFAULT_ADMIN_PASSWORD + "}")
        private String adminPassword;

        @org.springframework.beans.factory.annotation.Value("${app.admin.email:admin@kindergarten.com}")
        private String adminEmail;

        public DataSeeder(CategoryRepository categoryRepository, TopicRepository topicRepository,
                        BannerRepository bannerRepository, UserRepository userRepository,
                        MinioStorageService minioStorageService,
                        PasswordEncoder passwordEncoder,
                        Environment environment) {
                this.categoryRepository = categoryRepository;
                this.topicRepository = topicRepository;
                this.bannerRepository = bannerRepository;
                this.userRepository = userRepository;
                this.minioStorageService = minioStorageService;
                this.passwordEncoder = passwordEncoder;
                this.environment = environment;
        }

        @Override
        public void run(String... args) throws Exception {
                seedUsers();
                seedCategories();
                seedBanners();
        }

        private void seedUsers() {
                log.info("[DataSeeder] Checking Users...");
                assertAdminPasswordSafeForEnv();

                // 1. Admin
                if (!userRepository.existsByUsername(adminUsername)) {
                        com.kindergarten.warehouse.entity.User admin = com.kindergarten.warehouse.entity.User.builder()
                                        .username(adminUsername)
                                        .email(adminEmail)
                                        .password(passwordEncoder.encode(adminPassword))
                                        .fullName("Super Admin")
                                        .status(com.kindergarten.warehouse.entity.UserStatus.ACTIVE)
                                        .emailVerified(true)
                                        .isDeleted(false)
                                        .roles(java.util.Set.of(com.kindergarten.warehouse.entity.Role.ADMIN,
                                                        com.kindergarten.warehouse.entity.Role.TEACHER))
                                        .build();
                        userRepository.save(admin);
                        log.info("[DataSeeder] Seeded user: {}", adminUsername);
                } else {
                        log.info("[DataSeeder] User '{}' already exists.", adminUsername);
                }

                // 2. Teacher demo — chỉ seed ở môi trường dev/local
                if (isDevLikeEnv() && !userRepository.existsByUsername("teacher_hoa")) {
                        com.kindergarten.warehouse.entity.User teacher = com.kindergarten.warehouse.entity.User
                                        .builder()
                                        .username("teacher_hoa")
                                        .email("teacher@kindergarten.com")
                                        .password(passwordEncoder.encode("teacher123"))
                                        .fullName("Cô Giáo Hoa")
                                        .status(com.kindergarten.warehouse.entity.UserStatus.ACTIVE)
                                        .emailVerified(true)
                                        .isDeleted(false)
                                        .roles(java.util.Set.of(com.kindergarten.warehouse.entity.Role.TEACHER))
                                        .build();
                        userRepository.save(teacher);
                        log.info("[DataSeeder] Seeded user: teacher_hoa");
                }
        }

        /**
         * Không cho phép boot với mật khẩu admin mặc định ở môi trường prod.
         * Thiếu cấu hình → crash sớm để tránh deploy ra prod với credentials yếu.
         */
        private void assertAdminPasswordSafeForEnv() {
                if (DEFAULT_ADMIN_PASSWORD.equals(adminPassword) && !isDevLikeEnv()) {
                        throw new IllegalStateException(
                                "Default admin password detected in non-dev profile. "
                                + "Please set app.admin.password (APP_ADMIN_PASSWORD env) to a strong value.");
                }
                if (DEFAULT_ADMIN_PASSWORD.equals(adminPassword)) {
                        log.warn("[DataSeeder] Using default admin password — change it before deploying to production!");
                }
        }

        private boolean isDevLikeEnv() {
                String[] profiles = environment.getActiveProfiles();
                if (profiles.length == 0) return true;
                for (String p : profiles) {
                        if ("dev".equalsIgnoreCase(p) || "local".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p)) {
                                return true;
                        }
                }
                return false;
        }

        private void seedCategories() {
                if (categoryRepository.count() > 0) {
                        log.info("[DataSeeder] Categories already seeded. Skipping.");
                        return;
                }

                log.info("[DataSeeder] Starting to seed Categories...");
                com.kindergarten.warehouse.entity.User adminUser = getAdminUser();

                // 1. Education
                createCategoryWithIcon(
                                "Hoạt Động Giáo Dục", "hoat-dong-giao-duc",
                                "Tài liệu phục vụ giảng dạy và học tập hàng ngày",
                                "icon_education.png",
                                List.of(
                                                new TopicSeed("Giáo án Mầm (3-4 tuổi)", "giao-an-mam",
                                                                "Kế hoạch bài dạy cho trẻ 3-4 tuổi"),
                                                new TopicSeed("Giáo án Chồi (4-5 tuổi)", "giao-an-choi",
                                                                "Kế hoạch bài dạy cho trẻ 4-5 tuổi"),
                                                new TopicSeed("Giáo án Lá (5-6 tuổi)", "giao-an-la",
                                                                "Kế hoạch bài dạy cho trẻ 5-6 tuổi"),
                                                new TopicSeed("Bài giảng Điện tử", "bai-giang-dien-tu",
                                                                "Giáo án điện tử, E-Learning, PowerPoint tương tác")),
                                adminUser);

                // 2. Resources
                createCategoryWithIcon(
                                "Kho Tài Nguyên", "kho-tai-nguyen",
                                "Thư viện hình ảnh, video, âm thanh hỗ trợ",
                                "icon_resources.png",
                                List.of(
                                                new TopicSeed("Thư viện Hình ảnh", "thu-vien-hinh-anh",
                                                                "Kho ảnh minh họa chủ đề, thẻ hình"),
                                                new TopicSeed("Video & Clip minh họa", "video-clip",
                                                                "Video bài giảng, phim hoạt hình giáo dục"),
                                                new TopicSeed("Âm nhạc & Bài hát", "am-nhac",
                                                                "Nhạc không lời, bài hát thiếu nhi theo chủ đề"),
                                                new TopicSeed("Thơ & Truyện kể", "tho-truyen",
                                                                "Tuyển tập thơ, truyện cổ tích, truyện ngụ ngôn")),
                                adminUser);

                // 3. Creative
                createCategoryWithIcon(
                                "Góc Sáng Tạo", "goc-sang-tao",
                                "Các hoạt động phát triển năng khiếu và tư duy",
                                "icon_creative.png",
                                List.of(
                                                new TopicSeed("Tạo hình & Thủ công", "tao-hinh-thu-cong",
                                                                "Hướng dẫn cắt dán, nặn, vẽ tranh"),
                                                new TopicSeed("Trò chơi vận động", "tro-choi-van-dong",
                                                                "Trò chơi dân gian, vận động ngoài trời"),
                                                new TopicSeed("Thí nghiệm vui", "thi-nghiem-vui",
                                                                "Khám phá khoa học dành cho trẻ mầm non")),
                                adminUser);

                // 4. Professional
                createCategoryWithIcon(
                                "Công Tác Chuyên Môn", "cong-tac-chuyen-mon",
                                "Văn bản, biểu mẫu và sáng kiến kinh nghiệm",
                                "icon_professional.png",
                                List.of(
                                                new TopicSeed("Kế hoạch giáo dục", "ke-hoach-giao-duc",
                                                                "Kế hoạch tuần, tháng, năm học"),
                                                new TopicSeed("Sáng kiến kinh nghiệm", "sang-kien-kinh-nghiem",
                                                                "Đề tài nghiên cứu, kinh nghiệm giảng dạy"),
                                                new TopicSeed("Biểu mẫu & Sổ sách", "bieu-mau-so-sach",
                                                                "Các loại sổ sách, biểu mẫu hành chính")),
                                adminUser);

                // 5. Parents
                createCategoryWithIcon(
                                "Góc Phụ Huynh", "goc-phu-huynh",
                                "Kênh thông tin phối hợp giữa gia đình và nhà trường",
                                "icon_parents.png",
                                List.of(
                                                new TopicSeed("Dinh dưỡng & Sức khỏe", "dinh-duong-suc-khoe",
                                                                "Thực đơn, chế độ dinh dưỡng cho trẻ"),
                                                new TopicSeed("Tâm lý trẻ thơ", "tam-ly-tre-tho",
                                                                "Kiến thức tâm lý lứa tuổi mầm non")),
                                adminUser);
        }

        private void createCategoryWithIcon(String name, String slug, String desc, String iconFilename,
                        List<TopicSeed> topics, com.kindergarten.warehouse.entity.User user) {
                try {
                        String iconUrl = uploadImage("icons", iconFilename);

                        Category category = new Category();
                        category.setName(name);
                        category.setSlug(slug);
                        category.setDescription(desc);
                        category.setIcon(iconUrl); // Set MinIO URL
                        category.setCreatedBy(user.getId());
                        category.setUpdatedBy(user.getId());

                        Category savedCategory = categoryRepository.save(category);
                        log.info("[DataSeeder] Seeded category: [{}]", name);

                        for (TopicSeed ts : topics) {
                                Topic topic = new Topic();
                                topic.setName(ts.name);
                                topic.setSlug(ts.slug);
                                topic.setDescription(ts.desc);
                                topic.setCategory(savedCategory);
                                topic.setCreatedBy(user.getId());
                                topic.setUpdatedBy(user.getId());
                                topicRepository.save(topic);
                        }

                } catch (Exception e) {
                        log.error("[DataSeeder] Failed to seed category: " + name, e);
                }
        }

        private void seedBanners() {
                if (bannerRepository.count() > 0) {
                        log.info("[DataSeeder] Banners already seeded. Skipping.");
                        return;
                }

                log.info("[DataSeeder] Starting to seed banners...");
                com.kindergarten.warehouse.entity.User adminUser = getAdminUser();

                List<BannerSeedData> seeds = new ArrayList<>();

                // Setup dates (valid for 5 years)
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime fiveYearsLater = now.plusYears(5);

                seeds.add(new BannerSeedData(
                                "banner_art.png",
                                "Khơi nguồn <span class=\"text-purple-600\">Sáng Tạo</span> & Nghệ Thuật",
                                "🎨 Khám phá ý tưởng thủ công, vẽ tranh và tạo hình độc đáo giúp bé phát triển tư duy toàn diện. ✨",
                                "creative", "from-purple-50", "to-amber-50",
                                "/resources/topic/tao-hinh-thu-cong", 1, "WEB", now, fiveYearsLater));

                seeds.add(new BannerSeedData(
                                "banner_fun_learning.png",
                                "Phương pháp <span class=\"text-blue-600\">Học Mà Chơi</span> Hiệu Quả",
                                "🚀 Kết hợp giáo dục và giải trí qua các bài học tương tác, giúp trẻ tiếp thu kiến thức đầy hứng khởi!",
                                "primary", "from-blue-50", "to-cyan-50",
                                "/resources/topic/bai-giang-dien-tu", 2, "WEB", now, fiveYearsLater));

                seeds.add(new BannerSeedData(
                                "banner_games.png",
                                "Thế giới <span class=\"text-orange-600\">Trò Chơi</span> Vận Động",
                                "🎲 Tổng hợp trò chơi dân gian và trí tuệ, được thiết kế riêng cho sự phát triển thể chất của bé. 🤾",
                                "warm", "from-orange-50", "to-rose-50",
                                "/resources/topic/tro-choi-van-dong", 3, "WEB", now, fiveYearsLater));

                seeds.add(new BannerSeedData(
                                "banner_teachers_parents.png",
                                "Đồng hành cùng <span class=\"text-green-600\">Sự Phát Triển</span> của Bé",
                                "🌱 Thư viện tài liệu chia sẻ kinh nghiệm nuôi dạy trẻ, cầu nối vững chắc giữa Gia đình & Nhà trường. ❤️",
                                "nature", "from-green-50", "to-emerald-50",
                                "/resources/category/goc-phu-huynh", 4, "WEB", now, fiveYearsLater));

                for (BannerSeedData seed : seeds) {
                        try {
                                String imageUrl = uploadImage("banners", seed.filename);

                                Banner banner = Banner.builder()
                                                .title(seed.title)
                                                .subtitle(seed.subtitle)
                                                .platform(seed.platform)
                                                .imageUrl(imageUrl)
                                                .bgFrom(seed.bgFrom)
                                                .bgTo(seed.bgTo)
                                                .link(seed.targetLink)
                                                .isActive(true)
                                                .displayOrder(seed.displayOrder)
                                                .startDate(seed.startDate)
                                                .endDate(seed.endDate)
                                                .build();

                                banner.setCreatedBy(adminUser.getId());
                                banner.setUpdatedBy(adminUser.getId());

                                bannerRepository.save(banner);
                                log.info("[DataSeeder] Seeded banner: [{}]", seed.title);

                        } catch (Exception e) {
                                log.error("[DataSeeder] Failed to seed banner: " + seed.filename, e);
                        }
                }
        }

        private com.kindergarten.warehouse.entity.User getAdminUser() {
                return userRepository.findByUsername("admin")
                                .orElse(null); // Should ensure admin exists via migration or error handling
        }

        private String uploadImage(String folder, String filename) {
                String imagePath = "assets/images/" + filename;
                ClassPathResource resource = new ClassPathResource(imagePath);

                if (!resource.exists()) {
                        throw new RuntimeException("Image not found: " + imagePath);
                }

                try (InputStream inputStream = resource.getInputStream()) {
                        // Upload to MinIO bucket
                        String contentType = "image/png";
                        if (filename.endsWith("jpg") || filename.endsWith("jpeg"))
                                contentType = "image/jpeg";

                        return minioStorageService.uploadFile(inputStream, folder, filename, contentType);
                } catch (Exception e) {
                        throw new RuntimeException("Upload failed", e);
                }
        }

        private record BannerSeedData(
                        String filename,
                        String title,
                        String subtitle,
                        String theme,
                        String bgFrom,
                        String bgTo,
                        String targetLink,
                        Integer displayOrder,
                        String platform,
                        LocalDateTime startDate,
                        LocalDateTime endDate) {
        }

        private record TopicSeed(String name, String slug, String desc) {
        }
}
