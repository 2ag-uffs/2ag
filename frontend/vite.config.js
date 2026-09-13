import {defineConfig, loadEnv} from "vite";
import react from "@vitejs/plugin-react-swc";

// em desenvolvimento o vite repassa as chamadas da api pro backend local
// assim o front e a api ficam na mesma origem igual em producao
// se a api rodar em outra porta eh so colocar API_URL no .env.local
export default defineConfig(({mode}) => {
    const envVariables = loadEnv(mode, import.meta.dirname, "");
    const apiUrl = envVariables.API_URL || "http://localhost:8080";

    return {
        plugins: [react()],
        server: {
            proxy: {
                "/api": apiUrl,
            },
        },
    };
});
