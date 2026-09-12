-- pra começar do zero
DROP DATABASE IF EXISTS doisag;
DROP TYPE IF EXISTS scale_type_enum;
DROP TYPE IF EXISTS assignment_status_enum;

-- crio o banco e me conecta a ele
CREATE DATABASE doisag;
\c doisag

-- enums uteis
CREATE TYPE assignment_status_enum AS ENUM (
    'PENDENTE',
    'CONCLUIDO'
);

CREATE TYPE scale_type_enum AS ENUM (
    'ESCALA_HAMILTON',
    'ESCALA_PITTSBURGH',
    'MINI_EXAME_ESTADO_MENTAL',
    'REGISTRO_DOR',
    'REGISTRO_SONO',
    'REGISTRO_TEA',
    'ANAMNESE',
    'ACOMPANHAMENTO_SEMANAL'
);

-- mias entidades, são anotadas como @Entity no backend:

-- tabela base para todos os usuários
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    cpf VARCHAR(14),
    email VARCHAR(255) UNIQUE NOT NULL, 
    password VARCHAR(255) NOT NULL, 
    birth_date DATE,
    phone VARCHAR(50),
    street VARCHAR(255),
    "number" VARCHAR(255),
    city VARCHAR(255),
    state VARCHAR(255),
    country VARCHAR(255)
);

-- tabela de prescritores, especialização de users
CREATE TABLE prescriber (
    id BIGINT PRIMARY KEY,
    profession VARCHAR(255),
    registry_type VARCHAR(255),
    registry_number VARCHAR(255),
    professional_code VARCHAR(255) UNIQUE,
    CONSTRAINT fk_prescriber_users FOREIGN KEY (id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_registry_type_number UNIQUE (registry_type, registry_number)
);

-- tabela de pacientes, especialização de users
CREATE TABLE patient (
    id BIGINT PRIMARY KEY,
    prescriber_id BIGINT,
    CONSTRAINT fk_patient_users FOREIGN KEY (id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_patient_prescriber FOREIGN KEY (prescriber_id) REFERENCES prescriber(id) ON DELETE SET NULL
);

-- tabela de consultas e agendamentos
CREATE TABLE appointment (
    id BIGSERIAL PRIMARY KEY,
    date_time TIMESTAMP,
    modality VARCHAR(255),
    status VARCHAR(255),
    diagnosis TEXT,
    clinical_observation TEXT,
    therapeutic_plan TEXT,
    evolution TEXT,
    patient_id BIGINT NOT NULL,
    prescriber_id BIGINT NOT NULL,
    FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE,
    FOREIGN KEY (prescriber_id) REFERENCES prescriber(id) ON DELETE CASCADE
);

-- tabela de prescrições de produtos
CREATE TABLE prescription (
    id BIGSERIAL PRIMARY KEY,
    product_description TEXT,
    posology TEXT,
    brand VARCHAR(255),
    concentration VARCHAR(255),
    spectrum VARCHAR(255),
    observation TEXT,
    appointment_id BIGINT NOT NULL,
    FOREIGN KEY (appointment_id) REFERENCES appointment(id) ON DELETE CASCADE
);

-- tabela de anamnese
CREATE TABLE anamnesis (
    id BIGSERIAL PRIMARY KEY,
    assessment_date DATE NOT NULL,
    patient_id BIGINT NOT NULL,
    reason_for_visit TEXT,
    profession VARCHAR(255),
    diet TEXT,
    anxiety TEXT,
    observation TEXT,
    pain TEXT,
    adverse_reaction TEXT,
    previous_diagnosis TEXT,
    smoking_habits TEXT,
    expectations TEXT,
    current_medication TEXT,
    treatment_awareness TEXT,
    genetic_condition TEXT,
    previous_treatment TEXT,
    family_history TEXT,
    height VARCHAR(10),
    weight VARCHAR(10),
    alcohol_consumption TEXT,
    sleep_habits TEXT,
    substance_use TEXT,
    physical_activity TEXT,
    FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE
);

-- tabela de acompanhamento semanal
CREATE TABLE follow_up (
    id BIGSERIAL PRIMARY KEY,
    assessment_date DATE NOT NULL,
    patient_id BIGINT NOT NULL,
    morning_drops INTEGER,
    afternoon_drops INTEGER,
    comment TEXT,
    tremor INTEGER,
    rigidity_spasticity INTEGER,
    nausea INTEGER,
    concentration INTEGER,
    appetite INTEGER,
    social_interaction INTEGER,
    disposition INTEGER,
    intestinal_function INTEGER,
    anxiety INTEGER,
    substance_reduction INTEGER,
    pain INTEGER,
    sports_performance INTEGER,
    sleep INTEGER,
    dermatological_disease INTEGER,
    mood INTEGER,
    FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE
);

-- tabela da escala de ansiedade de hamilton
CREATE TABLE hamilton_scale (
    id BIGSERIAL PRIMARY KEY,
    assessment_date DATE NOT NULL,
    patient_id BIGINT NOT NULL,
    anxious_mood INTEGER,
    tension INTEGER,
    fears INTEGER,
    insomnia INTEGER,
    cognition INTEGER,
    depressed_mood INTEGER,
    somatic_motor INTEGER,
    somatic_sensory INTEGER,
    cardiovascular_symptoms INTEGER,
    respiratory_symptoms INTEGER,
    gastrointestinal_symptoms INTEGER,
    genitourinary_symptoms INTEGER,
    autonomic_symptoms INTEGER,
    ham_score INTEGER,
    FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE
);

-- tabela para registro diário de dor
CREATE TABLE pain_log (
    id BIGSERIAL PRIMARY KEY,
    assessment_date DATE NOT NULL,
    patient_id BIGINT NOT NULL,
    basic_activity_interference INTEGER,
    social_activity_interference INTEGER,
    sleep_interference INTEGER,
    productivity_interference INTEGER,
    extra_medication INTEGER,
    observation TEXT,
    FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE
);

-- tabela do mini-exame do estado mental (meem)
CREATE TABLE mental_state_exam (
    id BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    temporal_orientation INTEGER,
    spatial_orientation INTEGER,
    registration INTEGER,
    attention_and_calculation INTEGER,
    recall INTEGER,
    naming INTEGER,
    repetition INTEGER,
    command INTEGER,
    score INTEGER,
    FOREIGN KEY (appointment_id) REFERENCES appointment(id) ON DELETE CASCADE
);

-- tabela da escala de qualidade de sono de pittsburgh
CREATE TABLE pittsburgh_scale (
    id BIGSERIAL PRIMARY KEY,
    assessment_date DATE NOT NULL,
    patient_id BIGINT NOT NULL,
    usual_bed_time TIME,
    minutes_to_fall_asleep INTEGER,
    usual_wake_up_time TIME,
    actual_sleep_hours REAL,
    freq_cannot_fall_asleep INTEGER,
    freq_wakes_up_middle_night INTEGER,
    freq_wake_up_for_bathroom INTEGER,
    freq_cannot_breathe INTEGER,
    freq_cough_or_snore INTEGER,
    freq_feel_cold INTEGER,
    freq_feel_hot INTEGER,
    freq_have_bad_dreams INTEGER,
    freq_have_pain INTEGER,
    other_reason_to_trouble_sleep TEXT,
    sleep_quality_rating INTEGER,
    freq_use_sleep_medication INTEGER,
    freq_trouble_staying_awake INTEGER,
    trouble_with_enthusiasm INTEGER,
    room_partner INTEGER,
    psqi_score INTEGER,
    FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE
);

-- tabela para registro diário de sono
CREATE TABLE sleep_log (
    id BIGSERIAL PRIMARY KEY,
    assessment_date DATE NOT NULL,
    patient_id BIGINT NOT NULL,
    bed_time TIME,
    wake_up_time TIME,
    time_in_bed REAL,
    time_to_fall_asleep INTEGER,
    times_woken_up INTEGER,
    total_time_awake INTEGER,
    total_sleep_time REAL,
    total_awake_sleep REAL,
    is_common_day BOOLEAN,
    fatigue INTEGER,
    stress INTEGER,
    daytime_sleepiness INTEGER,
inattention INTEGER,
    irritability INTEGER,
    pain INTEGER,
    health_perception INTEGER,
    physical_activity_time REAL,
    time_away_from_home REAL,
    used_sleep_medication BOOLEAN,
    alcohol_consumption INTEGER,
    naps_time INTEGER,
    coffee_consumption INTEGER,
    nighttime_smoking INTEGER,
    total_time_awake_during_night INTEGER,
    FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE
);

-- tabela para registro de acompanhamento de TEA
CREATE TABLE tea_log (
    id BIGSERIAL PRIMARY KEY,
    assessment_date DATE NOT NULL,
    patient_id BIGINT NOT NULL,
    freq_aggressiveness INTEGER,
    freq_agitation INTEGER,
    freq_sleep_issues INTEGER,
    freq_social_interaction INTEGER,
    freq_stereotypy INTEGER,
    freq_appetite_issues INTEGER,
    observation TEXT,
    tea_score INTEGER,
    FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE
);

-- tabela para controlar as escalas que o prescritor envia pro paciente
CREATE TABLE assigned_scale (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    prescriber_id BIGINT NOT NULL,
    scale_type scale_type_enum NOT NULL,
    status assignment_status_enum NOT NULL,
    assigned_date DATE,
    completed_date DATE,
    FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE,
    FOREIGN KEY (prescriber_id) REFERENCES prescriber(id) ON DELETE CASCADE
);

-- tabela para as notificações do sistema
CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(512) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    type VARCHAR(255) NOT NULL,
    link VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);