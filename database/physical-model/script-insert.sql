
-- usuários base (prescritor e paciente inicial)
INSERT INTO users (id, name, cpf, email, password, birth_date, phone, street, "number", city, state, country)
VALUES (1, 'Bruna Varela', null, 'prescritor@email.com', '$2a$10$sia3ngmxp6wpkeiDLk2juo4l944dskzjgxoolfiygl2jlifuco2sw', null, null, null, null, null, null, null),
       (2, 'Paciente Teste da Silva', '00000000000', 'paciente@email.com', '$2a$10$tmocbknw2wlgs8r36j6h3u23emexg0txcX6b5i8rhexvr3q0sIIxs', '1990-05-15', null, null, null, null, null, null);

-- detalhes do prescritor
INSERT INTO prescriber (id, profession, registry_type, registry_number, professional_code)
VALUES (1, 'biomedica', 'CRBM', '12345', 'BRU71');

-- outro paciente de exemplo
INSERT INTO users (id, name, cpf, email, password, birth_date, phone, street, "number", city, state, country)
VALUES (3, 'João Henrique', '11111111111', 'joao@email.com', '$2a$10$outrapasshashsimulada3djnc83nf3', '1985-09-10', '99999-1234', 'Rua das Flores', '123', 'Porto Alegre', 'RS', 'Brasil');

-- vinculo pacientes aos prescritores
INSERT INTO patient (id, prescriber_id) VALUES (2, 1);
INSERT INTO patient (id, prescriber_id) VALUES (3, 1);

-- consultas
INSERT INTO appointment (date_time, modality, status, diagnosis, clinical_observation, therapeutic_plan, evolution, patient_id, prescriber_id)
VALUES ('2025-07-15 14:00:00', 'Online', 'Confirmado', 'Transtorno de Ansiedade', 'Paciente demonstrou melhora nos sintomas', 'Continuidade do tratamento com Canabidiol', 'Evolução positiva após 4 semanas', 2, 1),
       ('2025-07-20 10:00:00', 'Presencial', 'Confirmado', 'Transtorno Depressivo Leve', 'Humor deprimido, sem ideação suicida', 'Introdução de tratamento fitoterápico', 'Sem alterações significativas', 3, 1);

-- prescrições
INSERT INTO prescription (product_description, posology, brand, concentration, spectrum, observation, appointment_id)
VALUES ('Óleo de Canabidiol', '2 gotas manhã e noite', 'CBD Pharma', '50mg/ml', 'Amplo', 'Monitorar sono e humor', 1),
       ('Extrato de Valeriana', '1 cápsula antes de dormir', 'Valerimed', '250mg', 'Restrito', 'Avaliar resposta em 15 dias', 2);

-- anamnese
INSERT INTO anamnesis (assessment_date, patient_id, reason_for_visit, profession, diet, anxiety, observation, pain, adverse_reaction, previous_diagnosis, smoking_habits, expectations, current_medication, treatment_awareness, genetic_condition, previous_treatment, family_history, height, weight, alcohol_consumption, sleep_habits, substance_use, physical_activity)
VALUES ('2025-07-10', 2, 'Ansiedade crônica', 'Estudante', 'Vegetariana', 'Alta', 'Relata insônia e agitação', 'Dor de cabeça leve', 'Sem reações adversas', 'Nenhum', 'Não fuma', 'Reduzir ansiedade', 'Nenhum', 'Sim', 'Não', 'Nenhum', 'Caso de depressão na mãe', '170', '60', 'Esporádico', 'Sono leve', 'Não usa', 'Caminhada 3x por semana');

-- acompanhamento Semanal
INSERT INTO follow_up (assessment_date, patient_id, morning_drops, afternoon_drops, tremor, rigidity_spasticity, nausea, concentration, appetite, social_interaction, disposition, intestinal_function, anxiety, substance_reduction, pain, sports_performance, sleep, dermatological_disease, mood, comment)
VALUES ('2025-07-17', 2, 2, 2, 0, 0, 0, 7, 6, 8, 7, 5, 4, 0, 2, 6, 5, 0, 7, 'Sem efeitos adversos observados');

-- escala Hamilton
INSERT INTO hamilton_scale (assessment_date, patient_id, anxious_mood, tension, fears, insomnia, cognition, depressed_mood, somatic_motor, somatic_sensory, cardiovascular_symptoms, respiratory_symptoms, gastrointestinal_symptoms, genitourinary_symptoms, autonomic_symptoms, ham_score)
VALUES ('2025-07-17', 2, 2, 3, 2, 3, 2, 2, 1, 1, 2, 1, 1, 1, 1, 25);

-- escala Pittsburgh
INSERT INTO pittsburgh_scale (assessment_date, patient_id, usual_bed_time, minutes_to_fall_asleep, usual_wake_up_time, actual_sleep_hours, freq_cannot_fall_asleep, freq_wakes_up_middle_night, freq_wake_up_for_bathroom, freq_cannot_breathe, freq_cough_or_snore, freq_feel_cold, freq_feel_hot, freq_have_bad_dreams, freq_have_pain, other_reason_to_trouble_sleep, sleep_quality_rating, freq_use_sleep_medication, freq_trouble_staying_awake, trouble_with_enthusiasm, room_partner, psqi_score)
VALUES ('2025-07-17', 2, '23:00', 30, '07:00', 6.5, 2, 1, 1, 0, 1, 0, 1, 2, 1, 'Pensamentos acelerados', 2, 1, 1, 2, 1, 8);

-- tarefa de escala atribuída e concluída
INSERT INTO assigned_scale (patient_id, prescriber_id, scale_type, status, assigned_date, completed_date)
VALUES (2, 1, 'ESCALA_HAMILTON', 'CONCLUIDO', '2025-07-16', '2025-07-17');

-- notificação
INSERT INTO notification (user_id, title, message, is_read, type, link)
VALUES (2, 'Nova escala disponível', 'A Escala Hamilton foi atribuída a você.', false, 'FORM', '/escala-hamilton');

-- diário de Sono
INSERT INTO sleep_log (assessment_date, patient_id, bed_time, wake_up_time, time_in_bed, time_to_fall_asleep, times_woken_up, total_time_awake, total_sleep_time, total_awake_sleep, is_common_day, fatigue, stress, daytime_sleepiness, inattention, irritability, pain, health_perception, physical_activity_time, time_away_from_home, used_sleep_medication, alcohol_consumption, naps_time, coffee_consumption, nighttime_smoking, total_time_awake_during_night)
VALUES ('2025-07-10', 2, '22:30', '06:30', 480, 25, 2, 45, 435, 45, true, 4, 5, 3, 2, 3, 2, 7, 90, 30, false, 1, 15, 2, 0, 20);

-- MEEM
INSERT INTO mental_state_exam (appointment_id, temporal_orientation, spatial_orientation, registration, attention_and_calculation, recall, naming, repetition, command, score)
VALUES (1, 5, 5, 3, 4, 3, 2, 1, 2, 25);

-- diário de Dor
INSERT INTO pain_log (assessment_date, patient_id, basic_activity_interference, social_activity_interference, sleep_interference, productivity_interference, extra_medication, observation)
VALUES ('2025-06-25', 2, 2, 3, 4, 3, 0, 'Dor mais intensa no final do dia');

-- registro de TEA
INSERT INTO tealog (assessment_date, patient_id, freq_aggressiveness, freq_agitation, freq_sleep_issues, freq_social_interaction, freq_stereotypy, freq_appetite_issues, observation, tea_score)
VALUES ('2025-07-05', 2, 1, 2, 3, 1, 0, 2, 'Dificuldade de interação em novos ambientes', 9);