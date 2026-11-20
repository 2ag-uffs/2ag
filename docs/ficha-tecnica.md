
## Ficha Técnica do 2AG

| Camada | Tecnologias e Versões | Resumo do funcionamento |
| :--- | :--- | :--- |
| **Backend** | Java 17 · Spring Boot 4.1 (Tomcat 11.0.27) · Spring Data JPA (Hibernate 7.4.5) · Spring Security 7.1.1 · `jjwt` 0.13 · Jackson 3.1.7 · Springdoc OpenAPI · Maven (`mvnw`) | API REST em `/api`, fuso fixo `America/Sao_Paulo`, autenticação stateless via JWT em cookie `httpOnly` (`__Host-session`) e `SameSite=Strict`, senhas em BCrypt (custo 10, máx. 72 bytes) e Swagger gerado sob perfil `-Pdocs`. |
| **Frontend** | React 19 · Vite 8 (Node 22.22+) · React Router 8 · `react-icons` · CSS Puro e CSS Modules | SPA *mobile-first* com carregamento de telas sob demanda (*lazy loading*), gráficos de evolução clínica em SVG próprio (sem biblioteca de gráfico), fontes locais (**Yaldevi** e **Belleza**) e paleta com tema claro/escuro via variáveis CSS. |
| **Banco de Dados** | PostgreSQL 17 (driver `42.7.13`) · H2 em memória · Flyway 12.4.0 | PostgreSQL isolado na rede interna do Docker em produção, H2 para testes/demonstração local e migrações SQL versionadas pelo Flyway (Hibernate opera apenas em modo `validate`). |
| **Servidor e Operação** | Docker e Docker Compose · Nginx (`nginx:alpine`) · Certbot (Let's Encrypt) · `age` · OpenSSL | Nginx serve o front e faz proxy de `/api` na mesma origem (sem CORS), termina TLS 1.2/1.3 (porta 443), aplica HSTS/CSP/Gzip/Rate Limit (`limit_req_zone`) e scripts usam `age` para cifrar dumps do banco. |
| **Testes e Qualidade** | JUnit + MockMvc · Vitest + Testing Library · ESLint 10.15 · GitHub Actions · Dependabot · CodeQL | Testes de rota/vínculo, cálculo de escalas e concorrência (H2 e Postgres), smoke test subindo o Compose em HTTPS no CI e varredura automática de dependências e código. |

---
