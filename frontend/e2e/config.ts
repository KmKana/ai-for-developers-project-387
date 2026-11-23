/**
 * E2E использует отдельные порты: dev-серверы на 8080/5173 могут быть запущены,
 * а каждому прогону нужен свежий бэкенд с пустыми данными в памяти.
 */
export const E2E_BACKEND_PORT = Number(process.env.E2E_BACKEND_PORT ?? 18080);
export const E2E_FRONTEND_PORT = Number(process.env.E2E_FRONTEND_PORT ?? 15173);

// Vite слушает localhost (IPv6 ::1 на Windows), поэтому используем то же имя хоста.
export const backendUrl = `http://localhost:${E2E_BACKEND_PORT}`;
export const frontendUrl = `http://localhost:${E2E_FRONTEND_PORT}`;
