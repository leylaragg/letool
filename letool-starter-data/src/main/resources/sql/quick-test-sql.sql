-- =============================================
-- 快速测试 SQL
-- =============================================

-- 1. 查询所有患者
SELECT * FROM patient_info WHERE status = 1 ORDER BY age DESC;

-- 2. 查询特定患者
SELECT * FROM patient_info WHERE patient_id = 'P001' AND status = 1;

-- 3. 查询某个患者的门诊记录
SELECT * FROM outpat_record WHERE patient_id = 'P001' AND status = 1 ORDER BY visit_date DESC;

-- 4. 查询某个患者的检验报告
SELECT * FROM lab_report WHERE patient_id = 'P002' AND status = 1 ORDER BY test_time DESC;

-- 5. 查询某个患者的检查报告
SELECT * FROM exam_report WHERE patient_id = 'P003' AND status = 1 ORDER BY exam_time DESC;

-- 6. 统计每个患者的就诊次数
SELECT
    p.patient_id,
    p.name,
    p.age,
    COUNT(DISTINCT o.id) as visit_count,
    COUNT(DISTINCT l.id) as lab_count,
    COUNT(DISTINCT e.id) as exam_count
FROM patient_info p
LEFT JOIN outpat_record o ON p.patient_id = o.patient_id AND o.status = 1
LEFT JOIN lab_report l ON p.patient_id = l.patient_id AND l.status = 1
LEFT JOIN exam_report e ON p.patient_id = e.patient_id AND e.status = 1
WHERE p.status = 1
GROUP BY p.patient_id, p.name, p.age
ORDER BY p.patient_id;

-- 7. 查询异常检验结果
SELECT
    lr.*,
    p.name as patient_name
FROM lab_report lr
JOIN patient_info p ON lr.patient_id = p.patient_id
WHERE lr.abnormal_flag = 1 AND lr.status = 1
ORDER BY lr.test_time DESC;

-- 8. 查询当前住院患者
SELECT
    i.*,
    p.name,
    p.phone
FROM inpat_record i
JOIN patient_info p ON i.patient_id = p.patient_id
WHERE i.discharge_date IS NULL AND i.status = 1
ORDER BY i.admission_date DESC;

-- 9. 统计各科室就诊人数
SELECT
    dept_name,
    COUNT(DISTINCT patient_id) as patient_count,
    COUNT(*) as visit_count
FROM outpat_record
WHERE status = 1
GROUP BY dept_name
ORDER BY visit_count DESC;

-- 10. 年龄分布统计
SELECT
    CASE
        WHEN age < 20 THEN '0-19'
        WHEN age < 30 THEN '20-29'
        WHEN age < 40 THEN '30-39'
        WHEN age < 50 THEN '40-49'
        WHEN age < 60 THEN '50-59'
        ELSE '60+'
    END as age_group,
    COUNT(*) as count
FROM patient_info
WHERE status = 1
GROUP BY age_group
ORDER BY age_group;
