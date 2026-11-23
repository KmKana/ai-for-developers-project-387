import { defineConfig } from '@hey-api/openapi-ts';

// Генерация клиентского SDK из OpenAPI-спецификации:
// контракт (TypeSpec) -> api/openapi/openapi.yaml -> frontend/src/sdk
export default defineConfig({
  input: '../api/openapi/openapi.yaml',
  output: 'src/sdk',
  plugins: [
    // Коды ошибок нужны и как значения: адаптер строит наборы из сгенерированного контракта.
    { name: '@hey-api/typescript', enums: 'javascript' },
    '@hey-api/sdk',
    '@hey-api/client-fetch',
  ],
});
