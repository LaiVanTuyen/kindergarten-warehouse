package com.kindergarten.warehouse.entity;

/**
 * Mức hiển thị của Category, Topic, Resource và Banner.
 *
 * <p>Thứ tự chặt dần: {@code PUBLIC < INTERNAL < PRIVATE} (BUSINESS_RULES_V1
 * §3.1). Mức chặt được khai báo tường minh qua {@code restrictionLevel} chứ
 * không dựa vào {@link Enum#ordinal()} — chèn thêm một hằng số vào giữa sẽ làm
 * ordinal đổi nghĩa mà không có lỗi biên dịch nào cảnh báo.
 *
 * <p>Cột DB lưu dạng chuỗi ({@code @Enumerated(EnumType.STRING)}) nên thứ tự
 * khai báo không ảnh hưởng dữ liệu đã có.
 */
public enum Visibility {

    /** Ai cũng xem được, kể cả khách chưa đăng nhập. */
    PUBLIC(0),

    /** Chỉ tài khoản đã đăng nhập. */
    INTERNAL(1),

    /** Chỉ chủ sở hữu và ADMIN. */
    PRIVATE(2);

    private final int restrictionLevel;

    Visibility(int restrictionLevel) {
        this.restrictionLevel = restrictionLevel;
    }

    public int getRestrictionLevel() {
        return restrictionLevel;
    }

    public boolean isMoreRestrictiveThan(Visibility other) {
        return this.restrictionLevel > other.restrictionLevel;
    }

    /**
     * Trả về mức chặt nhất trong các giá trị truyền vào.
     *
     * <p><strong>Fail-closed:</strong> {@code null} được coi là {@link #PRIVATE}.
     * Một mắt xích thiếu dữ liệu phải dẫn tới ẩn đi, không phải lộ ra. Mảng rỗng
     * hoặc {@code null} cũng trả {@link #PRIVATE} vì lý do tương tự.
     */
    public static Visibility mostRestrictive(Visibility... values) {
        if (values == null || values.length == 0) {
            return PRIVATE;
        }
        Visibility result = PUBLIC;
        for (Visibility value : values) {
            Visibility current = (value == null) ? PRIVATE : value;
            if (current.isMoreRestrictiveThan(result)) {
                result = current;
            }
        }
        return result;
    }
}
