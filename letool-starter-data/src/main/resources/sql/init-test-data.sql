-- =============================================
-- Database 包测试数据初始化脚本
-- 适用于 H2 数据库（兼容 MySQL）
-- =============================================

create database if not exists letool;
use letool;
-- =============================================
-- 1. 患者基本信息表 (patient_info)
-- =============================================
CREATE TABLE IF NOT EXISTS patient_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    patient_id VARCHAR(50) NOT NULL UNIQUE COMMENT '患者编号',
    name VARCHAR(100) NOT NULL COMMENT '姓名',
    gender VARCHAR(10) COMMENT '性别：男/女',
    age INT COMMENT '年龄',
    phone VARCHAR(20) COMMENT '电话',
    email VARCHAR(100) COMMENT '邮箱',
    address VARCHAR(255) COMMENT '地址',
    status TINYINT DEFAULT 1 COMMENT '状态：1-正常, 0-删除',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_patient_id (patient_id),
    INDEX idx_status (status),
    INDEX idx_age (age)
) COMMENT '患者基本信息表';

-- 插入患者基本信息测试数据
INSERT INTO patient_info (patient_id, name, gender, age, phone, email, address, status) VALUES
-- 第一批测试数据
('P001', '张三', '男', 35, '13800138001', 'zhangsan@example.com', '北京市朝阳区', 1),
('P002', '李四', '女', 28, '13800138002', 'lisi@example.com', '北京市海淀区', 1),
('P003', '王五', '男', 42, '13800138003', 'wangwu@example.com', '北京市西城区', 1),
('P004', '赵六', '女', 31, '13800138004', 'zhaoliu@example.com', '北京市东城区', 1),
('P005', '钱七', '男', 55, '13800138005', 'qianqi@example.com', '北京市丰台区', 1),

-- 第二批测试数据（不同年龄段）
('P006', '孙八', '女', 23, '13800138006', 'sunba@example.com', '北京市石景山区', 1),
('P007', '周九', '男', 67, '13800138007', 'zhoujiu@example.com', '北京市门头沟区', 1),
('P008', '吴十', '女', 45, '13800138008', 'wushi@example.com', '北京市房山区', 1),
('P009', '郑十一', '男', 19, '13800138009', 'zhengshiyi@example.com', '北京市通州区', 1),
('P010', '陈十二', '女', 38, '13800138010', 'chenshier@example.com', '北京市顺义区', 1),

-- 第三批测试数据
('P011', '刘一', '男', 29, '13800138011', 'liuyi@example.com', '北京市昌平区', 1),
('P012', '黄二', '女', 52, '13800138012', 'huanger@example.com', '北京市大兴区', 1),
('P013', '杨三', '男', 33, '13800138013', 'yangsan@example.com', '北京市怀柔区', 1),
('P014', '朱四', '女', 26, '13800138014', 'zhusi@example.com', '北京市平谷区', 1),
('P015', '秦五', '男', 48, '13800138015', 'qinwu@example.com', '北京市密云区', 1);

-- =============================================
-- 2. 门诊记录表 (outpat_record)
-- =============================================
CREATE TABLE IF NOT EXISTS outpat_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    patient_id VARCHAR(50) NOT NULL COMMENT '患者编号',
    visit_no VARCHAR(50) NOT NULL UNIQUE COMMENT '就诊号',
    visit_date TIMESTAMP COMMENT '就诊日期',
    dept_name VARCHAR(100) COMMENT '科室名称',
    doctor_name VARCHAR(50) COMMENT '医生姓名',
    diagnosis VARCHAR(500) COMMENT '诊断',
    status TINYINT DEFAULT 1 COMMENT '状态：1-正常, 0-删除',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_patient_id (patient_id),
    INDEX idx_visit_date (visit_date),
    INDEX idx_status (status),
    FOREIGN KEY (patient_id) REFERENCES patient_info(patient_id)
) COMMENT '门诊记录表';

-- 插入门诊记录测试数据
INSERT INTO outpat_record (patient_id, visit_no, visit_date, dept_name, doctor_name, diagnosis, status) VALUES
-- P001 患者的门诊记录（3条）
('P001', 'V20240101', '2024-01-15 09:30:00', '内科', '张医生', '上呼吸道感染', 1),
('P001', 'V20240102', '2024-03-20 14:15:00', '外科', '李医生', '急性阑尾炎', 1),
('P001', 'V20240103', '2024-05-10 10:00:00', '内科', '王医生', '高血压', 1),

-- P002 患者的门诊记录（2条）
('P002', 'V20240201', '2024-02-05 16:20:00', '妇科', '赵医生', '月经不调', 1),
('P002', 'V20240202', '2024-04-15 11:30:00', '儿科', '钱医生', '小儿发热', 1),

-- P003 患者的门诊记录（4条）
('P003', 'V20240301', '2024-01-20 08:45:00', '骨科', '孙医生', '腰椎间盘突出', 1),
('P003', 'V20240302', '2024-02-28 15:10:00', '骨科', '孙医生', '腰椎间盘突出复查', 1),
('P003', 'V20240303', '2024-04-05 09:00:00', '骨科', '周医生', '颈椎病', 1),
('P003', 'V20240304', '2024-05-20 14:30:00', '康复科', '吴医生', '康复治疗', 1),

-- P004 患者的门诊记录（2条）
('P004', 'V20240401', '2024-03-10 10:20:00', '内科', '郑医生', '糖尿病', 1),
('P004', 'V20240402', '2024-05-05 16:45:00', '眼科', '陈医生', '结膜炎', 1),

-- P005 患者的门诊记录（3条）
('P005', 'V20240501', '2024-02-15 11:00:00', '心内科', '刘医生', '冠心病', 1),
('P005', 'V20240502', '2024-04-20 09:30:00', '心内科', '刘医生', '冠心病复查', 1),
('P005', 'V20240503', '2024-05-25 15:20:00', '心内科', '黄医生', '心律失常', 1);

-- =============================================
-- 3. 住院记录表 (inpat_record)
-- =============================================
CREATE TABLE IF NOT EXISTS inpat_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    patient_id VARCHAR(50) NOT NULL COMMENT '患者编号',
    admission_no VARCHAR(50) NOT NULL UNIQUE COMMENT '住院号',
    admission_date TIMESTAMP COMMENT '入院日期',
    discharge_date TIMESTAMP COMMENT '出院日期',
    dept_name VARCHAR(100) COMMENT '科室名称',
    doctor_name VARCHAR(50) COMMENT '主治医生',
    bed_no VARCHAR(20) COMMENT '床号',
    status TINYINT DEFAULT 1 COMMENT '状态：1-正常, 0-删除',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_patient_id (patient_id),
    INDEX idx_admission_date (admission_date),
    INDEX idx_status (status),
    FOREIGN KEY (patient_id) REFERENCES patient_info(patient_id)
) COMMENT '住院记录表';

-- 插入住院记录测试数据
INSERT INTO inpat_record (patient_id, admission_no, admission_date, discharge_date, dept_name, doctor_name, bed_no, status) VALUES
-- P001 患者的住院记录（1条）
('P001', 'A202401', '2024-03-20 00:00:00', '2024-03-25 00:00:00', '外科', '李医生', '外科01', 1),

-- P002 患者的住院记录（1条）
('P002', 'A202402', '2024-04-15 00:00:00', '2024-04-20 00:00:00', '儿科', '钱医生', '儿科05', 1),

-- P003 患者的住院记录（2条）
('P003', 'A202403', '2024-02-28 00:00:00', '2024-03-05 00:00:00', '骨科', '孙医生', '骨科02', 1),
('P003', 'A202404', '2024-05-20 00:00:00', NULL, '康复科', '吴医生', '康复03', 1),

-- P005 患者的住院记录（1条）
('P005', 'A202405', '2024-04-20 00:00:00', NULL, '心内科', '刘医生', '心内01', 1);

-- =============================================
-- 4. 检验报告表 (lab_report)
-- =============================================
CREATE TABLE IF NOT EXISTS lab_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    patient_id VARCHAR(50) NOT NULL COMMENT '患者编号',
    report_no VARCHAR(50) NOT NULL UNIQUE COMMENT '报告编号',
    test_item VARCHAR(200) NOT NULL COMMENT '检验项目',
    result_value VARCHAR(500) COMMENT '检验结果',
    unit VARCHAR(50) COMMENT '单位',
    reference_range VARCHAR(200) COMMENT '参考范围',
    abnormal_flag TINYINT DEFAULT 0 COMMENT '异常标识：1-异常, 0-正常',
    test_time TIMESTAMP COMMENT '检验时间',
    report_time TIMESTAMP COMMENT '报告时间',
    status TINYINT DEFAULT 1 COMMENT '状态：1-正常, 0-删除',
    INDEX idx_patient_id (patient_id),
    INDEX idx_test_time (test_time),
    INDEX idx_status (status),
    FOREIGN KEY (patient_id) REFERENCES patient_info(patient_id)
) COMMENT '检验报告表';

-- 插入检验报告测试数据
INSERT INTO lab_report (patient_id, report_no, test_item, result_value, unit, reference_range, abnormal_flag, test_time, report_time, status) VALUES
-- P002 患者的检验报告（5条）
('P002', 'L20240101', '血常规-白细胞计数', '5.8', '10^9/L', '3.5-9.5', 0, '2024-01-15 10:00:00', '2024-01-15 14:00:00', 1),
('P002', 'L20240102', '血常规-红细胞计数', '4.2', '10^12/L', '3.8-5.1', 0, '2024-01-15 10:00:00', '2024-01-15 14:00:00', 1),
('P002', 'L20240201', '血常规-血红蛋白', '125', 'g/L', '115-150', 0, '2024-04-15 10:30:00', '2024-04-15 15:00:00', 1),
('P002', 'L20240202', '尿常规-蛋白质', '阴性', '', '阴性', 0, '2024-04-15 11:00:00', '2024-04-15 15:00:00', 1),
('P002', 'L20240203', '肝功能-ALT', '35', 'U/L', '0-40', 0, '2024-04-15 12:00:00', '2024-04-15 16:00:00', 1),

-- P003 患者的检验报告（6条）
('P003', 'L20240301', '血脂-总胆固醇', '6.5', 'mmol/L', '<5.2', 1, '2024-02-28 09:00:00', '2024-02-28 13:00:00', 1),
('P003', 'L20240302', '血脂-甘油三酯', '2.3', 'mmol/L', '<1.7', 1, '2024-02-28 09:00:00', '2024-02-28 13:00:00', 1),
('P003', 'L20240303', '血糖-空腹血糖', '7.8', 'mmol/L', '3.9-6.1', 1, '2024-02-28 09:00:00', '2024-02-28 13:00:00', 1),
('P003', 'L20240401', '血常规-白细胞计数', '6.2', '10^9/L', '3.5-9.5', 0, '2024-05-20 08:30:00', '2024-05-20 12:00:00', 1),
('P003', 'L20240402', '血常规-血小板计数', '180', '10^9/L', '100-300', 0, '2024-05-20 08:30:00', '2024-05-20 12:00:00', 1),
('P003', 'L20240403', '肾功能-肌酐', '85', 'umol/L', '44-133', 0, '2024-05-20 09:00:00', '2024-05-20 12:00:00', 1);

-- =============================================
-- 5. 检查报告表 (exam_report)
-- =============================================
CREATE TABLE IF NOT EXISTS exam_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    patient_id VARCHAR(50) NOT NULL COMMENT '患者编号',
    report_no VARCHAR(50) NOT NULL UNIQUE COMMENT '报告编号',
    exam_item VARCHAR(200) NOT NULL COMMENT '检查项目',
    exam_result TEXT COMMENT '检查结果',
    impression VARCHAR(500) COMMENT '检查结论',
    exam_time TIMESTAMP COMMENT '检查时间',
    report_time TIMESTAMP COMMENT '报告时间',
    status TINYINT DEFAULT 1 COMMENT '状态：1-正常, 0-删除',
    INDEX idx_patient_id (patient_id),
    INDEX idx_exam_time (exam_time),
    INDEX idx_status (status),
    FOREIGN KEY (patient_id) REFERENCES patient_info(patient_id)
) COMMENT '检查报告表';

-- 插入检查报告测试数据
INSERT INTO exam_report (patient_id, report_no, exam_item, exam_result, impression, exam_time, report_time, status) VALUES
-- P001 患者的检查报告（2条）
('P001', 'X20240101', '胸部CT', '双肺纹理增多，未见明显占位性病变', '双肺纹理增多', '2024-03-20 15:00:00', '2024-03-20 18:00:00', 1),
('P001', 'X20240102', '腹部B超', '肝胆脾胰未见明显异常', '腹部B超未见异常', '2024-05-10 16:30:00', '2024-05-10 19:00:00', 1),

-- P003 患者的检查报告（4条）
('P003', 'X20240201', '腰椎MRI', 'L4-L5椎间盘突出，压迫硬膜囊', '腰椎间盘突出', '2024-02-28 14:00:00', '2024-02-28 17:00:00', 1),
('P003', 'X20240202', '颈椎X线', '颈椎生理曲度变直', '颈椎退行性改变', '2024-04-05 15:30:00', '2024-04-05 18:30:00', 1),
('P003', 'X20240301', '腰椎CT', 'L4-L5椎间盘突出，L3-L4椎间盘膨出', '腰椎间盘突出症', '2024-05-20 16:00:00', '2024-05-20 19:00:00', 1),
('P003', 'X20240302', '骨密度检查', '骨密度T值：-1.5', '骨量减少', '2024-05-22 10:00:00', '2024-05-22 13:00:00', 1),

-- P005 患者的检查报告（2条）
('P005', 'X20240401', '心脏彩超', '左室舒张功能减退', '左室舒张功能减退', '2024-04-20 14:00:00', '2024-04-20 17:00:00', 1),
('P005', 'X20240402', '心电图', '窦性心律，ST段轻度压低', '心肌供血不足', '2024-05-25 11:00:00', '2024-05-25 14:00:00', 1);

-- =============================================
-- 数据统计
-- =============================================
SELECT '患者信息表记录数' as table_name, COUNT(*) as count FROM patient_info
UNION ALL
SELECT '门诊记录表记录数', COUNT(*) FROM outpat_record
UNION ALL
SELECT '住院记录表记录数', COUNT(*) FROM inpat_record
UNION ALL
SELECT '检验报告表记录数', COUNT(*) FROM lab_report
UNION ALL
SELECT '检查报告表记录数', COUNT(*) FROM exam_report;
