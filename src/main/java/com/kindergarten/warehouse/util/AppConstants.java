package com.kindergarten.warehouse.util;

public final class AppConstants {

    private AppConstants() {
    }

    // Storage folders
    public static final String BUCKET_RESOURCES = "resources";
    public static final String FOLDER_FILES = "files";
    public static final String FOLDER_THUMBNAILS = "thumbnails";
    public static final String FOLDER_AVATARS = "avatars";
    public static final String FOLDER_BANNERS = "banners";
    public static final String FOLDER_CATEGORIES = "categories";

    // Roles
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_TEACHER = "TEACHER";
    public static final String ROLE_USER = "USER";

    // Paging defaults
    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String DEFAULT_PAGE_SIZE = "10";
    public static final String DEFAULT_SORT_BY = "id";
    public static final String DEFAULT_SORT_DIRECTION = "asc";

    // Auth / security constants
    public static final String COOKIE_ACCESS_TOKEN = "accessToken";
    public static final long AVATAR_MAX_BYTES = 5L * 1024 * 1024; // 5 MB

    // Redis key namespaces
    public static final String REDIS_NS = "kindergarten:";
    public static final String REDIS_JWT_BLACKLIST = REDIS_NS + "blacklist:";
    public static final String REDIS_OTP_PREFIX = REDIS_NS + "otp:";
    public static final String REDIS_OTP_ATTEMPTS_PREFIX = REDIS_NS + "otp:attempts:";
    public static final String REDIS_LOGIN_ATTEMPTS_PREFIX = REDIS_NS + "login:attempts:";
    public static final String REDIS_TOKEN_VERSION_PREFIX = REDIS_NS + "tv:";

    // JWT claims
    public static final String JWT_CLAIM_TOKEN_VERSION = "tv";
    public static final String JWT_CLAIM_USER_ID = "uid";
}
