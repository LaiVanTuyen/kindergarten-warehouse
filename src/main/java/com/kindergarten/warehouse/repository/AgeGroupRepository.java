package com.kindergarten.warehouse.repository;

import com.kindergarten.warehouse.entity.AgeGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgeGroupRepository extends JpaRepository<AgeGroup, Long> {
    boolean existsBySlug(String slug);

    /**
     * Nạp nhóm tuổi cho <strong>toàn bộ resource của một trang</strong> bằng
     * MỘT truy vấn.
     *
     * <p>Đường list dùng projection nên không có collection {@code ageGroups}
     * để lazy-load. Nạp collection ngay trong truy vấn phân trang thì Hibernate
     * sẽ phân trang trong bộ nhớ, nên phải tách ra như thế này.
     *
     * <p>Trả về từng cặp {@code [resourceId, AgeGroup]}; service gom lại thành
     * map. Số truy vấn cố định, không phụ thuộc số bản ghi trong trang.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT r.id, ag FROM Resource r JOIN r.ageGroups ag WHERE r.id IN :resourceIds")
    java.util.List<Object[]> findAgeGroupsByResourceIds(
            @org.springframework.data.repository.query.Param("resourceIds")
            java.util.Collection<String> resourceIds);
}
