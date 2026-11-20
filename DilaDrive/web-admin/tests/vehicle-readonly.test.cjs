const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const Module = require('node:module');
const esbuild = require('esbuild');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

// Isolate API-independent rendering, including the details branch that uses Luggage.
async function loadVehicles() {
  const result = await esbuild.build({
    entryPoints: [path.resolve(__dirname, '../src/pages/Vehicles.jsx')],
    bundle: true, write: false, format: 'cjs', platform: 'node', jsx: 'automatic',
    external: ['react', 'react/jsx-runtime', 'lucide-react'],
    plugins: [{ name: 'render-harness', setup(build) {
      build.onResolve({ filter: /^react$/ }, args =>
        args.importer.endsWith('Vehicles.jsx') ? { path: 'state', namespace: 'harness' } : undefined);
      build.onLoad({ filter: /.*/, namespace: 'harness' }, () => ({ contents: `
        export const useState = () => [globalThis.__vehicleRenderStates.shift(), () => {}];
        export const useMemo = fn => fn();
        export const useEffect = () => {};
      ` }));
      build.onResolve({ filter: /services\/api$/ }, () => ({ path: 'api', namespace: 'api' }));
      build.onLoad({ filter: /.*/, namespace: 'api' }, () => ({ contents: 'export default {};' }));
    } }],
  });
  const compiled = new Module(path.join(__dirname, 'vehicle-render.cjs'), module);
  compiled.filename = path.join(__dirname, 'vehicle-render.cjs');
  compiled.paths = module.paths;
  compiled._compile(result.outputFiles[0].text, compiled.filename);
  return compiled.exports.default;
}

const vehicle = { vehicleId: 7, plate: 'TEST7', model: 'Test model', type: 'MPV',
  capacity: 8, luggageCapacity: 0, status: 'Available', driverName: 'Assigned Driver', assignedDriver: {name: 'Assigned Driver', email: 'assigned@example.com', phone: '0191234567', type: 'Company Driver', status: 'ACTIVE'},
  roadTaxExpiry: '', serviceExpiry: '' };

function render(Vehicles, { selected = null, search = '', status = 'All', editing = null } = {}) {
  globalThis.__vehicleRenderStates = [[vehicle], false, null, search, status, selected, '', [], editing, false, ''];
  try { return renderToStaticMarkup(React.createElement(Vehicles)); }
  finally { delete globalThis.__vehicleRenderStates; }
}

test('vehicle view remains read-only and table offers editing with its linked driver', async () => {
  const Vehicles = await loadVehicles();
  const html = render(Vehicles, { selected: vehicle });
  assert.match(html, /Vehicle Details/);
  assert.match(html, /Luggage Capacity/);
  assert.match(html, /0 items/);
  assert.match(html, /Assigned Driver/);
  assert.doesNotMatch(html, /<form|Delete Vehicle|Create Vehicle/);
});

test('vehicle search and status filters use loaded records', async () => {
  const Vehicles = await loadVehicles();
  assert.match(render(Vehicles, { search: 'assigned driver' }), /TEST7/);
  assert.match(render(Vehicles, { search: 'no matching vehicle' }), /No vehicles found/);
  assert.match(render(Vehicles, { status: 'Maintenance' }), /No vehicles found/);
  assert.doesNotMatch(render(Vehicles), /<option[^>]*>Maintenance<\/option>/);
});

test('vehicle edit form keeps the linked driver read-only and edits existing vehicle fields', async () => {
  const Vehicles = await loadVehicles();
  const html = render(Vehicles, {editing: vehicle});
  assert.match(html, /Edit Vehicle/);
  assert.match(html, /Assigned Driver/);
  assert.match(html, /Save Changes/);
  assert.match(html, /Inactive/);
  for (const label of ["Driver Name", "Driver Email", "Driver Phone Number", "Driver Type", "Driver Status"]) assert.ok(html.includes(label));
  assert.match(html, /assigned@example.com/);
  const inputs = html.match(/<input[^>]*>/g) || [];
  assert.equal(inputs.some(input => /assigned@example.com|Assigned Driver|0191234567/.test(input)), false);
  assert.doesNotMatch(html, /name="driverId"|Create Vehicle|Delete Vehicle/);
});

test('vehicle edit explicitly handles an absent assigned driver', async () => {
  const Vehicles = await loadVehicles();
  const html = render(Vehicles, {editing: {...vehicle, assignedDriver: null, driverName: ''}});
  assert.match(html, /No assigned driver/);
  assert.match(html, /Save Changes/);
});
