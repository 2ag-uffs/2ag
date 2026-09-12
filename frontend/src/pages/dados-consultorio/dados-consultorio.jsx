import React, { useState } from "react";
import './dados-consultorio.css';
import Header from "../../components/header/header.jsx";
import {useNavigate} from "react-router";

export default function DadosConsultorio() {
    const navigate = useNavigate();

    const [dadosConsultorio, setDadosConsultorio] = useState({
        nomeFantasia: 'Clinica Teste',
        fusoHorario: 'GMT - 03:00',
        cep: '',
        endereco: ''
    });

    const handleConsultorioChange = (campo, valor) => {
        setDadosConsultorio(prev => ({
            ...prev,
            [campo]: valor
        }));
    };

    const handleBack = () => {
        navigate(-1);
    };
    return (
        <div className="dados-consultorio-container">
            <Header
                title="Dr. Maria Santos - CRM 12345"
                showBackButton={true}
                backButtonText="Voltar"
                onBackClick={handleBack}
            />

            <div className="dados-content">
                <div className="tabs-container">
                    <div className="tabs">
                        <button className="tab active">Dados do consultório</button>
                        <button className="tab">Dados do profissional</button>
                    </div>
                </div>

                <div className="form-container">
                    <div className="company-section">
                        <div className="company-card">
                            <div className="photo-placeholder">
                            </div>
                            <div className="photo-actions">
                                    <label htmlFor="photo-upload" className="photo-upload-btn">
                                        Alterar foto
                                    </label>
                                </div>

                            <div className="company-info">
                                <h2>{dadosConsultorio.nomeFantasia}</h2>
                                <p>{dadosConsultorio.endereco}</p>
                            </div>
                        </div>
                    </div>

                    <div className="form-sections">
                        <div className="form-section">
                            <h3>Dados básicos</h3>
                            <div className="form-grid">
                                <div className="form-company">
                                    <label>Nome fantasia</label>
                                    <input
                                        type="text"
                                        value={dadosConsultorio.nomeFantasia}
                                        onChange={(e) => handleConsultorioChange('nomeFantasia', e.target.value)}
                                        className="form-input"
                                    />
                                </div>

                                <div className="form-company">
                                    <label>Fuso Horário</label>
                                    <select
                                        value={dadosConsultorio.fusoHorario}
                                        onChange={(e) => handleConsultorioChange('fusoHorario', e.target.value)}
                                        className="form-select"
                                    >
                                        <option value="GMT - 03:00">GMT - 03:00</option>
                                        <option value="GMT - 02:00">GMT - 02:00</option>
                                        <option value="GMT - 04:00">GMT - 04:00</option>
                                        <option value="GMT - 05:00">GMT - 05:00</option>
                                    </select>
                                </div>
                            </div>
                        </div>

                        <div className="form-section">
                            <h3>Localização</h3>
                            <div className="form-grid">
                                <div className="form-company">
                                    <label>CEP</label>
                                    <input
                                        type="text"
                                        className="form-input"
                                        placeholder="______-____"
                                        maxLength={9}
                                    />
                                    <small>Informe o CEP</small>
                                </div>

                                <div className="form-company span-2">
                                    <label>Endereço</label>
                                    <input
                                        type="text"
                                        value={dadosConsultorio.endereco}
                                        onChange={(e) => handleConsultorioChange('endereco', e.target.value)}
                                        className="form-input"
                                    />
                                </div>


                            </div>
                        </div>
                    </div>

                    <div className="form-actions">
                        <button className="btn btn-secondary" >
                            Cancelar
                        </button>
                        <button className="btn btn-primary" >
                            Salvar dados
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
