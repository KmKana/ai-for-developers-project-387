import { spawnSync } from 'node:child_process';
import { cpSync, existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Проверка контрактной цепочки: TypeSpec -> OpenAPI -> SDK.
// Артефакты перегенерируются, сравниваются с git, а затем рабочее дерево
// возвращается в исходное состояние (включая накопления до проверки изменений).

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const artifacts = ['api/openapi/openapi.yaml', 'frontend/src/sdk'];
const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const operations = [
  'listMeetingTypes',
  'createMeetingType',
  'listAvailableSlots',
  'createBooking',
  'listBookings',
];
// Коды не дублируются в этой проверке: они выводятся из enum в TypeSpec.
// Так добавление кода в контракт автоматически попадает в трассировку цепочки.
function typeSpecErrorCodes(typeSpec) {
  const errorEnum = typeSpec.match(/enum ApiErrorCode \{([\s\S]*?)^\}/m);
  if (!errorEnum) {
    throw new Error('В TypeSpec не найден enum ApiErrorCode');
  }
  const codes = [...errorEnum[1].matchAll(/^\s+([A-Z][A-Z0-9_]*):\s*"([^"]+)"/gm)].map((match) => {
    if (match[1] !== match[2]) {
      throw new Error(`Несовпадение имени и значения кода: ${match[1]} / ${match[2]}`);
    }
    return match[1];
  });
  if (codes.length === 0 || new Set(codes).size !== codes.length) {
    throw new Error(`Некорректный набор кодов в ApiErrorCode: ${codes.join(', ')}`);
  }
  return codes;
}

const errorCodes = typeSpecErrorCodes(read('api/tsp/main.tsp'));

function run(command, args, cwd = root) {
  const result =
    process.platform === 'win32'
      ? spawnSync([command, ...args].join(' '), { cwd, stdio: 'inherit', shell: true })
      : spawnSync(command, args, { cwd, stdio: 'inherit' });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(' ')} завершился с кодом ${result.status}`);
  }
}

function artifactStatus() {
  const result = spawnSync('git', ['status', '--porcelain', '--', ...artifacts], {
    cwd: root,
    encoding: 'utf8',
  });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(`git status завершился с кодом ${result.status}`);
  }
  return result.stdout.trim();
}

function ensureInstalled(directory, marker) {
  if (!existsSync(join(root, marker))) {
    console.log(`Устанавливаем зависимости в ${directory}...`);
    run(npmCommand, ['ci'], join(root, directory));
  }
}

function reportDrift(label, status) {
  console.error(`${label}: сгенерированные артефакты расходятся с git:`);
  console.error(status);
  console.error('Запустите npm run generate в api/tsp и npm run generate-sdk в frontend.');
}

function read(relativePath) {
  return readFileSync(join(root, relativePath), 'utf8');
}

function countMatches(text, pattern, description) {
  const count = text.match(pattern)?.length ?? 0;
  if (count !== 1) {
    throw new Error(`Ожидался ровно один ${description}, найдено: ${count}`);
  }
}

function sdkNames(operationId) {
  const pascal = operationId
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('');
  return { pascal, camel: pascal.charAt(0).toLowerCase() + pascal.slice(1) };
}

// Проверяем не только побайтовое совпадение артефактов с git, но и то, что все
// операции и коды ошибок дошли до каждого звена цепочки TypeSpec -> OpenAPI -> SDK.
function assertContractChain() {
  const typeSpec = read('api/tsp/main.tsp');
  const openApi = read(artifacts[0]);
  const sdk = read('frontend/src/sdk/sdk.gen.ts');
  const sdkTypes = read('frontend/src/sdk/types.gen.ts');

  for (const operation of operations) {
    countMatches(
      typeSpec,
      new RegExp(`^\\s*${operation}\\s*\\(`, 'm'),
      `операции TypeSpec ${operation}`,
    );
  }

  const operationIds = [...openApi.matchAll(/^\s+operationId:\s*(\S+)\s*$/gm)].map(
    (match) => match[1],
  );
  const expectedOperationIds = operations.map((operation) => `Api_${operation}`);
  if (
    operationIds.length !== expectedOperationIds.length ||
    expectedOperationIds.some((id) => !operationIds.includes(id))
  ) {
    throw new Error(
      `Некорректный набор operationId в OpenAPI: ${operationIds.join(', ')}; ожидалось: ${expectedOperationIds.join(', ')}`,
    );
  }

  for (const operationId of expectedOperationIds) {
    const { pascal, camel } = sdkNames(operationId);
    countMatches(sdk, new RegExp(`export const ${camel}\\s*=`), `функция SDK ${camel}`);
    countMatches(
      sdkTypes,
      new RegExp(`export type ${pascal}Data\\s*=`),
      `тип SDK ${pascal}Data`,
    );
    countMatches(
      sdkTypes,
      new RegExp(`export type ${pascal}Responses\\s*=`),
      `тип SDK ${pascal}Responses`,
    );
  }

  countMatches(typeSpec, /^enum ApiErrorCode\s*\{/m, 'enum ApiErrorCode в TypeSpec');
  for (const code of errorCodes) {
    countMatches(typeSpec, new RegExp(`^\\s+${code}:\\s*"${code}"`, 'm'), `код TypeSpec ${code}`);
    countMatches(openApi, new RegExp(`^\\s+- ${code}$`, 'm'), `код OpenAPI ${code}`);
  }
  const sdkErrorCode = sdkTypes.match(/export type ApiErrorCode\s*=\s*([^;]+);/);
  if (!sdkErrorCode || errorCodes.some((code) => !sdkErrorCode[1].includes(`'${code}'`))) {
    throw new Error(`Некорректный ApiErrorCode в SDK: ${sdkErrorCode?.[1] ?? 'не найден'}`);
  }
  // Адаптер строит наборы кодов из runtime-значений сгенерированного enum.
  const sdkErrorCodeValues = sdkTypes.match(
    /export const ApiErrorCode\s*=\s*\{([\s\S]*?)\}\s*as const;/,
  );
  if (!sdkErrorCodeValues) {
    throw new Error('В SDK отсутствуют runtime-значения ApiErrorCode');
  }
  for (const code of errorCodes) {
    countMatches(
      sdkErrorCodeValues[1],
      new RegExp(`^\\s+${code}:\\s*'${code}'`, 'm'),
      `runtime-значение ApiErrorCode ${code}`,
    );
  }
}

let backup = null;
let failed = false;

try {
  ensureInstalled('api/tsp', 'api/tsp/node_modules/@typespec/compiler/package.json');
  ensureInstalled('frontend', 'frontend/node_modules/@hey-api/openapi-ts/package.json');

  const before = artifactStatus();
  if (before) {
    reportDrift('До проверки', before);
    process.exit(1);
  }

  backup = mkdtempSync(join(tmpdir(), 'contract-drift-'));
  cpSync(join(root, artifacts[0]), join(backup, 'openapi.yaml'));
  cpSync(join(root, artifacts[1]), join(backup, 'sdk'), { recursive: true });

  run(npmCommand, ['run', 'generate'], join(root, 'api/tsp'));
  run(npmCommand, ['run', 'generate-sdk'], join(root, 'frontend'));
  assertContractChain();

  const after = artifactStatus();
  if (after) {
    reportDrift('После генерации', after);
    failed = true;
  }
} catch (error) {
  console.error(error instanceof Error ? error.message : error);
  failed = true;
} finally {
  if (backup) {
    rmSync(join(root, artifacts[0]), { force: true });
    cpSync(join(backup, 'openapi.yaml'), join(root, artifacts[0]));
    rmSync(join(root, artifacts[1]), { recursive: true, force: true });
    cpSync(join(backup, 'sdk'), join(root, artifacts[1]), { recursive: true });
    rmSync(backup, { recursive: true, force: true });
  }
}

if (!failed && artifactStatus()) {
  reportDrift('После восстановления', artifactStatus());
  failed = true;
}

if (failed) {
  process.exit(1);
}

console.log(
  `OK: ${operations.length} операций и ${errorCodes.length} кодов ошибок прослеживаются в TypeSpec, OpenAPI и SDK; сгенерированные артефакты соответствуют git.`,
);
