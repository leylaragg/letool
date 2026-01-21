-- =============================================
-- H2 数据库测试表结构
-- 在 Spring Boot 测试启动时自动执行
-- =============================================

-- =============================================
-- 1. 患者基本信息表 (patient_info)
-- =============================================
CREATE TABLE IF NOT EXISTS patient_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    gender VARCHAR(10),
    age INT,
    phone VARCHAR(20),
    email VARCHAR(100),
    address VARCHAR(255),
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 2. 门诊记录表 (outpat_record)
-- =============================================
CREATE TABLE IF NOT EXISTS outpat_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id VARCHAR(50) NOT NULL,
    visit_no VARCHAR(50) NOT NULL UNIQUE,
    visit_date TIMESTAMP,
    dept_name VARCHAR(100),
    doctor_name VARCHAR(50),
    diagnosis VARCHAR(500),
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 3. 住院记录表 (inpat_record)
-- =============================================
CREATE TABLE IF NOT EXISTS inpat_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id VARCHAR(50) NOT NULL,
    admission_no VARCHAR(50) NOT NULL UNIQUE,
    admission_date TIMESTAMP,
    discharge_date TIMESTAMP,
    dept_name VARCHAR(100),
    doctor_name VARCHAR(50),
    bed_no VARCHAR(20),
    status TINYINT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 4. 检验报告表 (lab_report)
-- =============================================
CREATE TABLE IF NOT EXISTS lab_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id VARCHAR(50) NOT NULL,
    report_no VARCHAR(50) NOT NULL UNIQUE,
    test_item VARCHAR(200) NOT NULL,
    result_value VARCHAR(500),
    unit VARCHAR(50),
    reference_range VARCHAR(200),
    abnormal_flag TINYINT DEFAULT 0,
    test_time TIMESTAMP,
    report_time TIMESTAMP,
    status TINYINT DEFAULT 1
);

-- =============================================
-- 5. 检查报告表 (exam_report)
-- =============================================
CREATE TABLE IF NOT EXISTS exam_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id VARCHAR(50) NOT NULL,
    report_no VARCHAR(50) NOT NULL UNIQUE,
    exam_item VARCHAR(200) NOT NULL,
    exam_result TEXT,
    impression VARCHAR(500),
    exam_time TIMESTAMP,
    report_time TIMESTAMP,
    status TINYINT DEFAULT 1
);
