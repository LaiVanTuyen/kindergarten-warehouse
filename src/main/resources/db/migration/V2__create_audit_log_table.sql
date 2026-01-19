CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    target VARCHAR(255),
    detail TEXT,
    timestamp datetime NOT NULL
);
