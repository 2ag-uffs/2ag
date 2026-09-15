import {defineConfig, loadEnv} from "vite";
import react from "@vitejs/plugin-react";

// em desenvolvimento o vite repassa as chamadas da api pro backend local
// assim o front e a api ficam na mesma origem igual em producao
// se a api rodar em outra porta eh so colocar API_URL no .env.local
export default defineConfig(({mode}) => {
    const envVariables = loadEnv(mode, import.meta.dirname, "");
    const apiUrl = envVariables.API_URL || "http://localhost:8080";

    return {
        plugins: [react()],
        // os testes rodam num navegador de mentira e o setup limpa a tela entre um e outro
        test: {
            environment: "jsdom",
            setupFiles: ["./src/test/setup.js"],
        },
        // react e react-router quase n mudam entao vao num arquivo proprio
        // o navegador guarda esse arquivo e numa versao nova do sistema so baixa o codigo q mudou
        build: {
            rolldownOptions: {
                output: {
                    codeSplitting: {
                        groups: [
                            {name: "react", test: /node_modules[\\/](react|react-dom|react-router|scheduler)[\\/]/},
                        ],
                    },
                },
            },
        },
        server: {
            proxy: {
                "/api": apiUrl,
            },
        },
    };
});
