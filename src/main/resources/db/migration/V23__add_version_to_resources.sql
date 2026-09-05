-- =========================================================
-- V23: Them cot version cho JPA @Version (optimistic lock) tren resources.
-- Phat hien xung dot khi 2 transaction dong thoi sua cung 1 resource row
-- (vi du 2 admin cung cap nhat / duyet). Lien quan DESIGN_REVIEW [DB-6]/[ARC-4].
--
-- Luu y: cac UPDATE dem view/download va recalc average_rating dung bulk JPQL
-- nen KHONG tang version -> khong gay xung dot voi luong chinh sua.
--
-- Vi sao la V23 chu khong phai V21:
-- Thay doi nay ban dau la V21 tren nhanh fix/senior-review-phase1, nhung
-- develop da phat hanh mot V21 KHAC (production_query_indexes_and_topic_slug_backfill).
-- Hai migration khac nhau cung chiem so 21 la va cham phien ban giua hai nhanh.
-- V21 va V22 da duoc ap dung o moi truong demo nen phai giu nguyen (bat bien);
-- thay doi nay chuyen sang so moi thay vi sua lai migration da phat hanh.
-- =========================================================

ALTER TABLE resources
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
