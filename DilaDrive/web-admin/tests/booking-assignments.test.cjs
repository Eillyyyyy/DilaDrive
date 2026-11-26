const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const Module=require('node:module');
const esbuild=require('esbuild');
const React=require('react');
const {renderToStaticMarkup}=require('react-dom/server');

async function compile(file, hooks=false) {
 const result=await esbuild.build({entryPoints:[path.resolve(__dirname,'../src/'+file)],bundle:true,write:false,format:'cjs',platform:'node',jsx:'automatic',external:['react','react/jsx-runtime','lucide-react'],plugins:[{name:'harness',setup(build){
   if(hooks){build.onResolve({filter:/^react$/},args=>args.importer.endsWith(hooks === true ? 'Bookings.jsx' : hooks)?{path:'state',namespace:'harness'}:undefined);
     build.onLoad({filter:/.*/,namespace:'harness'},()=>({contents:`export const useState=()=>[globalThis.__bookingStates.shift(),()=>{}];export const useRef=v=>({current:v});export const useEffect=()=>{};export const useMemo=fn=>fn();`}));}
   build.onResolve({filter:/services\/api$/},()=>({path:'api',namespace:'stub'}));
   build.onLoad({filter:/.*/,namespace:'stub'},()=>({contents:'export default {};'}));
 }}]});
 const mod=new Module(path.join(__dirname,'booking-render.cjs'),module);mod.filename=path.join(__dirname,'booking-render.cjs');mod.paths=module.paths;mod._compile(result.outputFiles[0].text,mod.filename);return mod.exports;
}

test('legacy booking pointers do not masquerade as saved assignments',async()=>{
 const {bookingAssignment}=await compile('services/bookingAssignments.js');
 const result=bookingAssignment({bookingId:7,driverId:2,vehicleId:3},[]);
 assert.equal(result.driver,'');assert.equal(result.vehicle,'');assert.equal(result.tripStatus,'Not assigned');assert.equal(result.legacyRelationship,true);assert.equal(result.assignmentId,undefined);
});
test('booking details use active database assignment before terminal history',async()=>{
 const {bookingAssignment}=await compile('services/bookingAssignments.js');
 const result=bookingAssignment({bookingId:7,driverId:999},[
  {bookingId:7,assignmentId:20,assignmentStatus:'CANCELLED',driverName:'Old driver'},
  {bookingId:7,assignmentId:18,assignmentStatus:'ASSIGNED',driverName:'Actual driver',vehiclePlate:'PAIR',driverId:2,vehicleId:3},
  {bookingId:8,assignmentId:30,assignmentStatus:'ASSIGNED',driverName:'Other booking'}]);
 assert.equal(result.driver,'Actual driver');assert.equal(result.vehicle,'PAIR');assert.equal(result.tripStatus,'ASSIGNED');assert.equal(result.assignmentId,18);
});
test('terminal assignment remains visible as history without an active allocation',async()=>{
 const {bookingAssignment}=await compile('services/bookingAssignments.js');
 const result=bookingAssignment({bookingId:7},[{bookingId:7,assignmentId:20,assignmentStatus:'COMPLETED',driverName:'History driver'}]);
 assert.equal(result.driver,'History driver');assert.equal(result.tripStatus,'COMPLETED');assert.equal(result.assignmentId,undefined);
});

const row={id:7,customer:'Database customer',status:'Pending Verification',date:'2028-01-01',pickupTime:'09:00:00',pickup:'Pickup',dropoff:'Destination',fare:100};
async function render({details=null,confirmation=null,busy=false,driverId='',vehicles=[],rows=[row]}={}) {
 const {default:Bookings}=await compile('pages/Bookings.jsx',true);
 globalThis.__bookingStates=[rows,'','All',details,confirmation,'100','','',false,busy,'','2028-01-01T10:00',null];
 try{return renderToStaticMarkup(React.createElement(Bookings,{}));}finally{delete globalThis.__bookingStates;}
}
test('pending details offer Confirm & Assign and legacy warning without invented resource names',async()=>{
 const html=await render({details:{...row,legacyRelationship:true}});
 assert.match(html,/Verify &amp; Confirm Booking/);assert.match(html,/Legacy driver\/vehicle references/);assert.match(html,/Not assigned/);
});
test('distance-review bookings must confirm road distance before assignment',async()=>{
 const awaitingDistance={...row,roadDistancePending:true};
 const html=await render({details:awaitingDistance,rows:[awaitingDistance]});
 assert.match(html,/Confirm the measured road distance before assigning this booking/);
 assert.doesNotMatch(html,/Verify &amp; Confirm Booking/);
});
test('confirmation uses the shared live resource picker, requires planned end, and disables submit while saving',async()=>{
 const html=await render({confirmation:row,busy:true,driverId:'2',vehicles:[{driverId:2,vehicleId:3,vehiclePlateno:'ACTUALPLATE',vehicleModel:'Actual model',vehicleStatus:'AVAILABLE'}]});
 assert.match(html,/Verify &amp; Confirm Booking/);assert.match(html,/Recheck availability/);assert.match(html,/Suggested driver and vehicle/);assert.match(html,/type="datetime-local"/);assert.match(html,/disabled=""[^>]*><svg[^]*Saving/);
});
test('superseded history never appears as an active booking allocation',async()=>{
 const {bookingAssignment}=await compile('services/bookingAssignments.js');
 const result=bookingAssignment({bookingId:7},[{bookingId:7,assignmentId:10,assignmentStatus:'SUPERSEDED',driverName:'Previous driver'}]);
 assert.equal(result.assignmentId,undefined);
 assert.equal(result.tripStatus,'SUPERSEDED');
});
test('confirmed legacy booking offers explicit repair while confirmed assigned booking offers reassignment',async()=>{
 const legacy=await render({details:{...row,status:'Confirmed',legacyRelationship:true}});
 assert.match(legacy,/Complete missing assignment/);
 const assigned=await render({details:{...row,status:'Confirmed',assignmentId:9,driver:'Database driver',vehicle:'ACTUALPLATE',tripStatus:'ASSIGNED'}});
 assert.match(assigned,/View \/ Reassign Trip/);assert.doesNotMatch(assigned,/Complete missing assignment/);
});

test('unmapped legacy 404 explains the exact POST URL and required backend restart',async()=>{
 const {confirmationError}=await compile('services/bookingAssignments.js');
 const message=confirmationError({response:{status:404,data:{error:'Not Found',path:'/api/bookings/7/confirm-and-assign'}},config:{baseURL:'http://localhost:8080/api',url:'/bookings/7/confirm-and-assign'}});
 assert.match(message,/POST http:\/\/localhost:8080\/api\/bookings\/7\/confirm-and-assign/);
 assert.match(message,/Restart the updated/);
});
test('resource 404 preserves backend reason without incorrectly blaming a missing endpoint',async()=>{
 const {confirmationError}=await compile('services/bookingAssignments.js');
 for(const message of ['Booking not found.','Driver not found.','Linked vehicle not found.']) {
  assert.equal(confirmationError({response:{status:404,data:{message}}}),message);
 }
 assert.equal(confirmationError({response:{status:409,data:{message:'This booking already has an active assignment.'}}}),'This booking already has an active assignment.');
});
test('explicit missing-endpoint code gives recovery advice while other API errors stay intact',async()=>{
 const {confirmationError}=await compile('services/bookingAssignments.js');
 assert.match(confirmationError({response:{status:404,data:{code:'ENDPOINT_NOT_FOUND',message:'No endpoint matches POST /api/bookings/7/confirm-and-assign.'}}}),/Restart/);
 assert.equal(confirmationError({response:{status:400,data:'End time is required.'}}),'End time is required.');
});

test('eligible customer preference displays an already selected pair with no duplicate selectors', async () => {
 const {default:Picker}=await compile('components/AssignmentResourcePicker.jsx','AssignmentResourcePicker.jsx');
 const pair={vehicleId:3,vehicleModel:'Customer choice',vehiclePlate:'PAIR',driverId:2,driverName:'Linked driver',passengerCapacity:4};
 globalThis.__bookingStates=[{preferred:pair,alternatives:[pair]},pair,false,'',0];
 try {
  const html=renderToStaticMarkup(React.createElement(Picker,{bookingId:7,onChange(){}}));
  assert.match(html,/Linked driver/);
  assert.match(html,/eligible for this trip/);
  assert.match(html,/name="vehicleId" value="3"/);
  assert.match(html,/name="driverId" value="2"/);
  assert.doesNotMatch(html,/<select/);
 } finally {delete globalThis.__bookingStates;}
});

test('unavailable preference shows exact cause and alternative pair selection', async () => {
 const {default:Picker}=await compile('components/AssignmentResourcePicker.jsx','AssignmentResourcePicker.jsx');
 const preferred={vehicleId:3,vehicleModel:'Customer choice',vehiclePlate:'PAIR',driverId:2,driverName:'Linked driver'};
 const alternative={vehicleId:8,vehicleType:'MPV',driverId:9,driverName:'Replacement driver',customerAcceptanceRequired:true};
 globalThis.__bookingStates=[{preferred,unavailableReason:'Driver has an overlapping trip.',alternatives:[alternative]},null,false,'',0];
 try {
  const html=renderToStaticMarkup(React.createElement(Picker,{bookingId:7,onChange(){}}));
  assert.match(html,/Driver has an overlapping trip/);
  assert.match(html,/Replacement driver/);
  assert.match(html,/customer acceptance required/);
  assert.doesNotMatch(html,/name="driverId"/);
 } finally {delete globalThis.__bookingStates;}
});
test('assignment preview errors distinguish stale backend, missing booking and unavailable service', async () => {
 const {assignmentPreviewError, assignmentDuration}=await compile('services/bookingAssignments.js');
 assert.match(assignmentPreviewError({response:{status:404,data:{code:'ENDPOINT_NOT_FOUND',message:'No endpoint matches'}}}),/Restart the updated backend/);
 assert.match(assignmentPreviewError({response:{status:404,data:{message:'Booking not found.'}}}),/booking could not be found/);
 assert.match(assignmentPreviewError({response:{status:401}}),/Sign in again/);
 assert.match(assignmentPreviewError({response:{status:403}}),/does not have access/);
 assert.match(assignmentPreviewError({response:{status:500,data:{message:'sensitive diagnostic'}}}),/Could not verify/);
 assert.doesNotMatch(assignmentPreviewError({response:{status:500}}),/No eligible pair/);
 assert.equal(assignmentDuration(832), '13 min 52 s');
 assert.equal(assignmentDuration(1800), '30 min');
});

test('failed preview shows Retry instead of a successful empty result', async () => {
 const {default:Picker}=await compile('components/AssignmentResourcePicker.jsx','AssignmentResourcePicker.jsx');
 globalThis.__bookingStates=[null,null,false,'Restart the updated backend, then retry.',0];
 try {
  const html=renderToStaticMarkup(React.createElement(Picker,{bookingId:9,onChange(){}}));
  assert.match(html,/Retry/);
  assert.match(html,/Restart the updated backend/);
  assert.doesNotMatch(html,/No eligible pair/);
  assert.doesNotMatch(html,/name="driverId"/);
 } finally {delete globalThis.__bookingStates;}
});