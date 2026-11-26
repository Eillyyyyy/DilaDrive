const test = require("node:test");
const assert = require("node:assert/strict");
const path = require("node:path");
const Module = require("node:module");
const esbuild = require("esbuild");
const React = require("react");
const { renderToStaticMarkup } = require("react-dom/server");

const pending = {
  id: 1, name: "Pending Company", status: "PENDING", customerType: "Corporate",
  companyName: "Example Transport", companyRegistrationNumber: "REG-UC004",
};
const rejected = { id: 2, name: "Rejected customer", status: "REJECTED", customerType: "Normal" };
const unknown = { id: 3, name: "Legacy customer", status: "LEGACY", customerType: "Normal" };

async function render({ filter = "All", details = null, confirmation = null, decision = "APPROVE", busy = null } = {}) {
  const file = path.join(__dirname, "../src/pages/Customers.jsx");
  const compiled = await esbuild.build({
    entryPoints: [file], bundle: true, write: false, format: "cjs", platform: "node",
    jsx: "automatic", external: ["react", "react/jsx-runtime", "lucide-react"],
    plugins: [{
      name: "customer-verification-harness",
      setup(build) {
        build.onResolve({ filter: /^react$/ }, () => ({ path: "hooks", namespace: "test" }));
        build.onResolve({ filter: /services\/api$/ }, () => ({ path: "api", namespace: "test" }));
        build.onLoad({ filter: /hooks/, namespace: "test" }, () => ({
          contents: `
            export const useState = () => [globalThis.__customerStates.shift(), () => {}];
            export const useEffect = () => {};
            export const useCallback = fn => fn;
            export const useMemo = fn => fn();
            export const useRef = value => ({ current: value });
          `,
        }));
        build.onLoad({ filter: /api/, namespace: "test" }, () => ({ contents: "export default {};" }));
      },
    }],
  });
  const filename = path.join(__dirname, "customer-verification-render.cjs");
  const moduleInstance = new Module(filename, module);
  moduleInstance.filename = filename;
  moduleInstance.paths = module.paths;
  moduleInstance._compile(compiled.outputFiles[0].text, filename);
  globalThis.__customerStates = [
    "", filter, [pending, rejected, unknown], false, "", details, confirmation,
    busy, "", "", decision, "", false, null, false, "",
  ];
  try {
    return renderToStaticMarkup(React.createElement(moduleInstance.exports.default));
  } finally {
    delete globalThis.__customerStates;
  }
}

test("pending and rejected filters show only their exact statuses", async () => {
  const pendingList = await render({ filter: "PENDING" });
  assert.match(pendingList, /Pending Company/);
  assert.doesNotMatch(pendingList, /Rejected customer|Legacy customer/);
  const rejectedList = await render({ filter: "REJECTED" });
  assert.match(rejectedList, /Rejected customer/);
  assert.doesNotMatch(rejectedList, /Pending Company|Legacy customer|>Approve</);
});

test("corporate review exposes stored registration number and pending decisions", async () => {
  const html = await render({ details: pending, confirmation: pending });
  assert.match(html, /Company registration number/);
  assert.match(html, /REG-UC004/);
  assert.match(html, /Reject account/);
  assert.match(html, /Confirm approval/);
  assert.doesNotMatch(await render({ details: rejected }), /Reject account|Approve account/);
});

test("rejection dialog requests a bounded reason and disables in-flight actions", async () => {
  const form = await render({ confirmation: pending, decision: "REJECT" });
  assert.match(form, /Rejection reason/);
  assert.match(form, /maxLength="500"/i);
  assert.match(form, /Confirm rejection/);
  const busy = await render({ confirmation: pending, decision: "REJECT", busy: pending.id });
  assert.match(busy, /disabled=""[^>]*>[^<]*(?:<svg[^]*?)?Saving/);
});
