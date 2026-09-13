import {defineConfig} from "vite";
import react from "@vitejs/plugin-react-swc";

// em desenvolvimento o vite repassa as chamadas da api pro backend local
// assim o front e a api ficam na mesma origem igual em producao
export default defineConfig({
    plugins: [react()],
    server: {
        proxy: {
            "/api": "http://localhost:8080",
        },
    },
});
