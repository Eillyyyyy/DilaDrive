const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const Module = require('node:module');
const esbuild = require('esbuild');

async function loader() { return import('../src/services/driverManagement.js'); }
const record = { driverId: 7, driverName: 'API driver' };
const error400 = { config: { url: '/drivers' }, response: { status: 400, data: { message: 'Invalid route' } } };
function client(failure) {
  return { get: async url => {
    if (url === failure) throw error400;
    if (!['/drivers', '/vehicles'].includes(url)) throw new Error('Unexpected request: ' + url);
    return { data: url === '/drivers' ? [record] : [] };
  } };
}

test('driver loading requests only driver and vehicle records', async () => {
  const { loadDriverManagement } = await loader();
  const calls = [];
  const liveClient = client();
  const result = await loadDriverManagement({ get: url => { calls.push(url); return liveClient.get(url); } });
  assert.deepEqual(calls.sort(), ['/drivers', '/vehicles']);
  assert.deepEqual(result.drivers, [record]);
  assert.deepEqual(result.vehicles, []);
  assert.deepEqual(result.errors, []);
});

test('vehicle lookup failure stays distinct from no assigned vehicle', async () => {
  const { loadDriverManagement } = await loader();
  const result = await loadDriverManagement(client('/vehicles'));
  assert.deepEqual(result.drivers, [record]);
  assert.equal(result.vehicles, null);
  assert.deepEqual(result.errors, [error400]);
});

test('driver request failures propagate without fabricated records', async () => {
  const { loadDriverManagement } = await loader();
  await assert.rejects(loadDriverManagement(client('/drivers')), error => error === error400);
  assert.deepEqual((await loadDriverManagement(client())).drivers, [record]);
});

test('Axios includes credentials and logs diagnostics without request secrets', async () => {
  const filename = path.join(__dirname, 'axios-config.cjs');
  const bundle = esbuild.buildSync({ entryPoints: [path.join(__dirname, '../src/services/api.js')],
    bundle: true, write: false, platform: 'node', format: 'cjs', external: ['axios'],
    define: { 'import.meta.env': '{}' } });
  const compiled = new Module(filename, module);
  compiled.filename = filename;
  compiled.paths = module.paths;
  compiled._compile(bundle.outputFiles[0].text, filename);
  const api = compiled.exports.default;
  assert.equal(api.defaults.withCredentials, true);
  const logs = [];
  const original = console.error;
  console.error = (...args) => logs.push(args);
  try {
    const error = new Error('Request failed');
    error.config = { method: 'get', url: '/drivers', withCredentials: true,
      data: { password: 'do-not-log-password' }, headers: { Cookie: 'do-not-log-cookie' } };
    error.response = { status: 400, data: { message: 'Invalid route' } };
    await assert.rejects(api.get('/drivers', { adapter: async () => { throw error; } }));
    assert.equal(logs[0][1].status, 400);
    assert.equal(logs[0][1].path, '/drivers');
    assert.doesNotMatch(JSON.stringify(logs), /do-not-log/);
  } finally { console.error = original; }
  const { getApiErrorMessage } = await import('../src/services/apiErrors.js');
  assert.match(getApiErrorMessage(error400), /Invalid route.*HTTP 400 on \/drivers/);
});
