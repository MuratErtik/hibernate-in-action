-- 1. Customer Tablosu
CREATE TABLE IF NOT EXISTS customer (
                                        id BIGINT NOT NULL AUTO_INCREMENT,
                                        name VARCHAR(255),
    surname VARCHAR(255),
    balance DECIMAL(19, 2),
    phone VARCHAR(20),
    email VARCHAR(255),
    age INTEGER,
    gender VARCHAR(50),
    version BIGINT DEFAULT 0,
    customer_type VARCHAR(100),
    created_at TIMESTAMP NULL, -- Metadata'dan gelir
    updated_at TIMESTAMP NULL, -- Metadata'dan gelir
    PRIMARY KEY (id)
    );

-- 2. Revinfo Tablosu (Envers için)
-- 1. Revinfo Tablosu
CREATE TABLE IF NOT EXISTS revinfo (
                                       rev INTEGER NOT NULL,      -- Java'daki 'id' buna denk gelir
                                       revtstmp BIGINT,           -- Java'daki 'timestamp' buna denk gelir
                                       user VARCHAR(255),
    PRIMARY KEY (rev)
    );

-- 2. Revinfo Sequence Tablosu
-- Senin Java kodunda SEQUENCE kullandığın için bu şart:
CREATE TABLE IF NOT EXISTS revinfo_seq (
                                           next_val BIGINT
) ENGINE=InnoDB;

-- Eğer tablo boşsa başlangıç değerini 1 yap
INSERT INTO revinfo_seq (next_val)
SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM revinfo_seq);

-- 4. Customer Audit Tablosu
CREATE TABLE IF NOT EXISTS customer_AUD (
                                            id BIGINT NOT NULL,
                                            rev INTEGER NOT NULL,
                                            revtype TINYINT,
                                            name VARCHAR(255),
    surname VARCHAR(255),
    balance DECIMAL(19, 2),
    phone VARCHAR(20),
    email VARCHAR(255),
    age INTEGER,
    gender VARCHAR(50),
    version BIGINT,
    customer_type VARCHAR(100),
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    PRIMARY KEY (id, rev),
    CONSTRAINT FK_customer_AUD_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
    );