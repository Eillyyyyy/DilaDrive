const test = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const Module = require("node:module");
const esbuild = require("esbuild");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");

// Verify driver forms and details render without server-provided dropdown metadata.
async function loadDrivers() {
  const result = await esbuild.build({
    entryPoints: [path.resolve(__dirname, "../src/pages/Drivers.jsx")],
    bundle: true,
    write: false,
    format: "cjs",
    platform: "node",
    jsx: "automatic",
    external: ["react", "react/jsx-runtime", "lucide-react"],
    plugins: [
      {
        name: "render-harness",
        setup(build) {
          build.onResolve({ filter: /^react$/ }, (args) =>
            args.importer.endsWith("Drivers.jsx")
              ? { path: "state", namespace: "harness" }
              : undefined,
          );
          build.onLoad({ filter: /.*/, namespace: "harness" }, () => ({
            contents: `
        export const useState = () => [globalThis.__driverRenderStates.shift(), () => {}];
        export const useRef = initial => ({current: initial});
        export const useEffect = () => {};
      `,
          }));
          build.onResolve({ filter: /services\/api$/ }, () => ({
            path: "api",
            namespace: "api",
          }));
          build.onLoad({ filter: /.*/, namespace: "api" }, () => ({
            contents: "export default {};",
          }));
        },
      },
    ],
  });
  const compiled = new Module(
    path.join(__dirname, "driver-render.cjs"),
    module,
  );
  compiled.filename = path.join(__dirname, "driver-render.cjs");
  compiled.paths = module.paths;
  compiled._compile(result.outputFiles[0].text, compiled.filename);
  return compiled.exports.default;
}

const driver = {
  id: 7,
  name: "API driver",
  email: "driver@example.com",
  phone: "0123456789",
  icNo: "",
  licenseNo: "LICENSE7",
  licenseExpiry: "2027-12-01",
  driverType: "Company Driver",
  availability: "Available",
  accountStatus: "Active",
  vehicle: "TEST7",
};
const form = {
  ...driver,
  password: "",
  confirmPassword: "",
  vehicle: {
    model: "API vehicle",
    plate: "TEST7",
    type: "MPV",
    capacity: 7,
    luggageCapacity: 0,
    roadTaxExpiry: "",
    serviceExpiry: "",
    status: "Available",
  },
};
function render(Drivers, mode) {
  const currentForm =
    mode === "edit-no-vehicle" ? { ...form, vehicle: null } : form;
  globalThis.__driverRenderStates = [
    [driver],
    false,
    "",
    "All",
    "All",
    mode === "view" ? driver : null,
    mode.startsWith("edit") ? currentForm : null,
    false,
    mode === "create",
    currentForm,
    false,
    "",
    "",
    {},
  ];
  try {
    return renderToStaticMarkup(React.createElement(Drivers));
  } finally {
    delete globalThis.__driverRenderStates;
  }
}

test("create and edit forms render local dropdown choices without options state", async () => {
  const Drivers = await loadDrivers();
  for (const mode of ["create", "edit"]) {
    const html = render(Drivers, mode);
    assert.match(html, /Personal Information/);
    assert.match(html, /Driver Information/);
    assert.match(html, /Vehicle Information/);
    assert.match(html, /Associate Driver/);
    if (mode === "edit") assert.match(html, /Maintenance/);
    else assert.match(html, /Vehicle Model/);
    assert.doesNotMatch(html, /Pending Activation/);
    assert.match(html, /Active/);
    assert.match(html, mode === "create" ? /Create Account/ : /Save Changes/);
  }
});

test("driver view remains read-only and table retains edit action", async () => {
  const Drivers = await loadDrivers();
  const html = render(Drivers, "view");
  assert.match(html, /Driver Account Details/);
  assert.match(html, /API driver/);
  assert.match(html, /TEST7/);
  assert.match(html, /title="Edit driver"/);
  assert.doesNotMatch(html, /Edit Details|<form/);
});

test("editing without a vehicle renders a valid profile form", async () => {
  const Drivers = await loadDrivers();
  const html = render(Drivers, "edit-no-vehicle");
  assert.match(html, /legacy driver has no registered vehicle/);
  assert.match(html, /Save Changes/);
  assert.match(html, /driver@example.com/);
  assert.doesNotMatch(html, /No vehicle is assigned|Vehicle Model/);
});
