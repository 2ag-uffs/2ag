import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from "../../components/header/header.jsx";
import './perfil.css';
import '../../styles/colors.css';
import '../../styles/fonts.css';
import '../../styles/button.css';
import '../../styles/input.css';

export default function Perfil() {
    const navigate = useNavigate();
    const [profileData, setProfileData] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);
    const [isEditing, setIsEditing] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [showPasswordModal, setShowPasswordModal] = useState(false);
    const [passwordData, setPasswordData] = useState({
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
    });
    const [passwordError, setPasswordError] = useState('');

    useEffect(() => {
        // Verifica se o usuário está autenticado
        if (!isAuthenticated()) {
            navigate("/login");
            return;
        }

        const fetchProfile = async () => {
            try {
                const data = await profileService.getUserProfile();
                setProfileData(data);
            } catch (err) {
                setError(err.message);
                console.error("Erro ao carregar perfil:", err);
            } finally {
                setIsLoading(false);
            }
        };

        fetchProfile();
    }, [navigate]);

    const handleInputChange = (field, value) => {
        setProfileData(prev => ({
            ...prev,
            [field]: value
        }));
    };

    const handleSave = async () => {
        setIsSaving(true);
        setError(null);

        try {
            const updatedProfile = await profileService.updateUserProfile(profileData);
            setProfileData(updatedProfile);
            setIsEditing(false);
            alert('Perfil atualizado com sucesso!');
        } catch (err) {
            setError(err.message);
            console.error("Erro ao salvar perfil:", err);
        } finally {
            setIsSaving(false);
        }
    };

    const handlePasswordChange = async () => {
        if (passwordData.newPassword !== passwordData.confirmPassword) {
            setPasswordError('As senhas não coincidem');
            return;
        }

        if (passwordData.newPassword.length < 6) {
            setPasswordError('A nova senha deve ter pelo menos 6 caracteres');
            return;
        }

        try {
            await profileService.changePassword(passwordData.currentPassword, passwordData.newPassword);
            setShowPasswordModal(false);
            setPasswordData({ currentPassword: '', newPassword: '', confirmPassword: '' });
            setPasswordError('');
            alert('Senha alterada com sucesso!');
        } catch (err) {
            setPasswordError(err.message || 'Erro ao alterar senha');
        }
    };

    const handleBack = () => {
        const currentUser = getCurrentUser();
        if (currentUser.role === 'PATIENT') {
            navigate('/dashboard-paciente');
        } else {
            navigate('/dashboard-prescritor');
        }
    };

    if (isLoading) {
        return (
            <div className="perfil-loading">
                <h1>Carregando perfil...</h1>
            </div>
        );
    }

    if (error) {
        return (
            <div className="perfil-error">
                <h1>Erro ao carregar perfil</h1>
                <p>{error}</p>
                <button onClick={handleBack}>Voltar</button>
            </div>
        );
    }

    return (
        <div className="perfil-container">
            <Header
                userName={profileData?.name || 'Usuário'}
                userRole={profileData?.role === 'PATIENT' ? 'Paciente' : 'Prescritor'}
                onBack={handleBack}
            />

            <main className="perfil-main">
                <div className="perfil-header">
                    <h1>Meu Perfil</h1>
                    <div className="perfil-actions">
                        {!isEditing ? (
                            <>
                                <button
                                    className="button-secondary"
                                    onClick={() => setShowPasswordModal(true)}
                                >
                                    Alterar Senha
                                </button>
                                <button
                                    className="button"
                                    onClick={() => setIsEditing(true)}
                                >
                                    Editar Perfil
                                </button>
                            </>
                        ) : (
                            <>
                                <button
                                    className="button-secondary"
                                    onClick={() => setIsEditing(false)}
                                    disabled={isSaving}
                                >
                                    Cancelar
                                </button>
                                <button
                                    className="button"
                                    onClick={handleSave}
                                    disabled={isSaving}
                                >
                                    {isSaving ? 'Salvando...' : 'Salvar'}
                                </button>
                            </>
                        )}
                    </div>
                </div>

                <div className="perfil-content">
                    {/* Informações Pessoais */}
                    <section className="perfil-section">
                        <h2>Informações Pessoais</h2>
                        <div className="perfil-grid">
                            <div className="input-group">
                                <label>Nome Completo</label>
                                <input
                                    type="text"
                                    value={profileData?.name || ''}
                                    onChange={(e) => handleInputChange('name', e.target.value)}
                                    disabled={!isEditing}
                                />
                            </div>
                            <div className="input-group">
                                <label>Email</label>
                                <input
                                    type="email"
                                    value={profileData?.email || ''}
                                    disabled={true}
                                    className="input-disabled"
                                />
                                <small>O email não pode ser alterado</small>
                            </div>
                            <div className="input-group">
                                <label>Telefone</label>
                                <input
                                    type="tel"
                                    value={profileData?.phone || ''}
                                    onChange={(e) => handleInputChange('phone', e.target.value)}
                                    disabled={!isEditing}
                                />
                            </div>
                            <div className="input-group">
                                <label>Data de Nascimento</label>
                                <input
                                    type="date"
                                    value={profileData?.birthDate || ''}
                                    onChange={(e) => handleInputChange('birthDate', e.target.value)}
                                    disabled={!isEditing}
                                />
                            </div>
                            <div className="input-group">
                                <label>CPF</label>
                                <input
                                    type="text"
                                    value={profileData?.cpf || ''}
                                    disabled={true}
                                    className="input-disabled"
                                />
                                <small>O CPF não pode ser alterado</small>
                            </div>
                        </div>
                    </section>

                    {/* Endereço */}
                    <section className="perfil-section">
                        <h2>Endereço</h2>
                        <div className="perfil-grid">
                            <div className="input-group full-width">
                                <label>Endereço</label>
                                <input
                                    type="text"
                                    value={profileData?.address || ''}
                                    onChange={(e) => handleInputChange('address', e.target.value)}
                                    disabled={!isEditing}
                                />
                            </div>
                            <div className="input-group">
                                <label>Cidade</label>
                                <input
                                    type="text"
                                    value={profileData?.city || ''}
                                    onChange={(e) => handleInputChange('city', e.target.value)}
                                    disabled={!isEditing}
                                />
                            </div>
                            <div className="input-group">
                                <label>Estado</label>
                                <input
                                    type="text"
                                    value={profileData?.state || ''}
                                    onChange={(e) => handleInputChange('state', e.target.value)}
                                    disabled={!isEditing}
                                />
                            </div>
                            <div className="input-group">
                                <label>CEP</label>
                                <input
                                    type="text"
                                    value={profileData?.zipCode || ''}
                                    onChange={(e) => handleInputChange('zipCode', e.target.value)}
                                    disabled={!isEditing}
                                />
                            </div>
                        </div>
                    </section>

                    {/* Informações Específicas do Prescritor */}
                    {profileData?.role === 'PRESCRIBER' && (
                        <section className="perfil-section">
                            <h2>Informações Profissionais</h2>
                            <div className="perfil-grid">
                                <div className="input-group">
                                    <label>Código Profissional</label>
                                    <input
                                        type="text"
                                        value={profileData?.professionalCode || ''}
                                        onChange={(e) => handleInputChange('professionalCode', e.target.value)}
                                        disabled={!isEditing}
                                    />
                                </div>
                                <div className="input-group">
                                    <label>Especialidade</label>
                                    <input
                                        type="text"
                                        value={profileData?.specialty || ''}
                                        onChange={(e) => handleInputChange('specialty', e.target.value)}
                                        disabled={!isEditing}
                                    />
                                </div>
                                <div className="input-group full-width">
                                    <label>Instituição</label>
                                    <input
                                        type="text"
                                        value={profileData?.institution || ''}
                                        onChange={(e) => handleInputChange('institution', e.target.value)}
                                        disabled={!isEditing}
                                    />
                                </div>
                            </div>
                        </section>
                    )}

                    {/* Informações Específicas do Paciente */}
                    {profileData?.role === 'PATIENT' && (
                        <section className="perfil-section">
                            <h2>Informações de Emergência</h2>
                            <div className="perfil-grid">
                                <div className="input-group">
                                    <label>Contato de Emergência</label>
                                    <input
                                        type="text"
                                        value={profileData?.emergencyContact || ''}
                                        onChange={(e) => handleInputChange('emergencyContact', e.target.value)}
                                        disabled={!isEditing}
                                    />
                                </div>
                                <div className="input-group">
                                    <label>Telefone de Emergência</label>
                                    <input
                                        type="tel"
                                        value={profileData?.emergencyPhone || ''}
                                        onChange={(e) => handleInputChange('emergencyPhone', e.target.value)}
                                        disabled={!isEditing}
                                    />
                                </div>
                                <div className="input-group full-width">
                                    <label>Histórico Médico</label>
                                    <textarea
                                        value={profileData?.medicalHistory || ''}
                                        onChange={(e) => handleInputChange('medicalHistory', e.target.value)}
                                        disabled={!isEditing}
                                        rows="4"
                                    />
                                </div>
                            </div>
                        </section>
                    )}
                </div>
            </main>

            {/* Modal de Alteração de Senha */}
            {showPasswordModal && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h2>Alterar Senha</h2>
                        {passwordError && (
                            <div className="error-message">{passwordError}</div>
                        )}
                        <div className="modal-form">
                            <div className="input-group">
                                <label>Senha Atual</label>
                                <input
                                    type="password"
                                    value={passwordData.currentPassword}
                                    onChange={(e) => setPasswordData(prev => ({
                                        ...prev,
                                        currentPassword: e.target.value
                                    }))}
                                />
                            </div>
                            <div className="input-group">
                                <label>Nova Senha</label>
                                <input
                                    type="password"
                                    value={passwordData.newPassword}
                                    onChange={(e) => setPasswordData(prev => ({
                                        ...prev,
                                        newPassword: e.target.value
                                    }))}
                                />
                            </div>
                            <div className="input-group">
                                <label>Confirmar Nova Senha</label>
                                <input
                                    type="password"
                                    value={passwordData.confirmPassword}
                                    onChange={(e) => setPasswordData(prev => ({
                                        ...prev,
                                        confirmPassword: e.target.value
                                    }))}
                                />
                            </div>
                        </div>
                        <div className="modal-actions">
                            <button
                                className="button-secondary"
                                onClick={() => {
                                    setShowPasswordModal(false);
                                    setPasswordError('');
                                    setPasswordData({ currentPassword: '', newPassword: '', confirmPassword: '' });
                                }}
                            >
                                Cancelar
                            </button>
                            <button
                                className="button"
                                onClick={handlePasswordChange}
                            >
                                Alterar Senha
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
