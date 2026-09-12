

-- lista todos os usuários e identificar quem é Paciente e quem é Prescritor
SELECT
    u.id,
    u.name,
    u.email,
    CASE
        WHEN p.id IS NOT NULL THEN 'Prescritor'
        WHEN pa.id IS NOT NULL THEN 'Paciente'
        ELSE 'Usuário sem papel definido'
    END AS user_role,
    p.profession,
    p.professional_code,
    pa.prescriber_id AS id_do_prescritor_responsavel
FROM
    users u
LEFT JOIN
    prescriber p ON u.id = p.id
LEFT JOIN
    patient pa ON u.id = pa.id;


-- lista todos os pacientes de um prescritor específico 
SELECT
    p.id AS patient_id,
    u.name AS patient_name,
    u.email,
    u.phone
FROM
    patient p
JOIN
    users u ON p.id = u.id
WHERE
    p.prescriber_id = 1;


-- ver o prontuário de um paciente: dados pessoais, consultas e prescrições
SELECT
    u.name AS patient_name,
    presc_u.name AS prescriber_name,
    a.date_time AS appointment_date,
    a.diagnosis,
    pr.product_description,
    pr.posology
FROM
    patient pa
JOIN
    users u ON pa.id = u.id
JOIN
    prescriber presc ON pa.prescriber_id = presc.id
JOIN
    users presc_u ON presc.id = presc_u.id
LEFT JOIN
    appointment a ON pa.id = a.patient_id
LEFT JOIN
    prescription pr ON a.id = pr.appointment_id
WHERE
    pa.id = 2 
ORDER BY
    a.date_time DESC;


-- ver todas as escalas atribuídas a um paciente
SELECT
    assigned_date,
    scale_type,
    status,
    completed_date
FROM
    assigned_scale
WHERE
    patient_id = 2 
ORDER BY
    assigned_date DESC;


-- listar as notificações não lidas de um usuário
SELECT
    created_at,
    title,
    message,
    link
FROM
    notification
WHERE
    user_id = 2 AND is_read = FALSE -- id do usuário (paciente)
ORDER BY
    created_at DESC;


-- gerar dados para o gráfico de progresso de um paciente
SELECT
    assessment_date,
    anxiety,
    sleep,
    mood
FROM
    follow_up
WHERE
    patient_id = 2
ORDER BY
    assessment_date ASC;


-- ver os detalhes de uma consulta específica e suas prescrições
SELECT
    a.id AS appointment_id,
    a.date_time,
    a.modality,
    a.status,
    a.diagnosis,
    p.product_description,
    p