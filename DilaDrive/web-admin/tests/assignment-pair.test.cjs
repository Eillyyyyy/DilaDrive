const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const esbuild = require('esbuild');
const React = require('react');
const {renderToStaticMarkup} = require('react-dom/server');

test('reassignment form selects a linked pair and requires an end time', async () => {
  const page = path.resolve(__dirname, '../src/pages/Assignments.jsx');
  const result = await esbuild.build({stdin: {
    contents: fs.readFileSync(page,'utf8') + '\nexport {ReassignModal};',
    resolveDir: path.dirname(page), loader: 'jsx'}, bundle:true, write:false,
    format:'cjs', platform:'node', jsx:'automatic', external:['react','react/jsx-runtime','lucide-react','axios'],
    plugins:[{name:'api-stub',setup(build){
      build.onResolve({filter:/services\/api$/},()=>({path:'api',namespace:'stub'}));
      build.onLoad({filter:/.*/,namespace:'stub'},()=>({contents:'export default {};'}));
    }}]});
  const compiled = new Module(path.join(__dirname,'assignment-render.cjs'),module);
  compiled.filename = path.join(__dirname,'assignment-render.cjs'); compiled.paths = module.paths;
  compiled._compile(result.outputFiles[0].text,compiled.filename);
  const common = {drivers:[{driverId:7,driverName:'Linked driver'},{driverId:8,driverName:'Legacy missing vehicle'}],
    vehicles:[{driverId:7,vehicleId:9,vehiclePlateno:'PAIR7',vehicleModel:'Model'}],
    busy:false,error:'',onClose:()=>{},onSubmit:()=>{}};
  for(const component of [compiled.exports.ReassignModal]) {
    const html=renderToStaticMarkup(React.createElement(component,{...common,title:'Assignment',subtitle:'Schedule',
      bookings:[],assignment:{assignmentId:15,bookingId:7,driverName:'Old driver',vehiclePlate:'Old plate'}}));
    assert.doesNotMatch(html,/name="vehicleId"|name="driverId"/);
    assert.match(html,/Eligible replacement pairs/);
    assert.match(html,/name="reason" required/);
    assert.doesNotMatch(html,/Legacy missing vehicle/);
    assert.match(html,/name="scheduledEndAt"/);
    assert.match(html,/type="datetime-local"/);
    assert.match(html,/Recheck availability/);
    assert.match(html,/Previous assignment history will be preserved/);
  }
});
