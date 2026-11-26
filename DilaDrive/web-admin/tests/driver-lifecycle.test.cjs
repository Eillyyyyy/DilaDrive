const test = require('node:test');
const assert = require('node:assert/strict');
const formModule = () => import('../src/services/driverForm.js');
const apiDriver = {driverId: 7, driverName: 'Driver', driverEmail: 'driver@example.com',
  driverPhoneno: '0123456789', driverIcno: 'IC7', driverLicenseno: 'LICENSE7',
  driverLicenseExpiry: '2028-01-01', driverType: 'Company Driver', driverStatus: 'ACTIVE',
  driverAvailability: 'AVAILABLE', vehicle: null};

test('null vehicle edit initialization preserves driver information and sends no vehicle update', async () => {
  const {toDriverEditForm, toDriverCreatePayload, validateDriverFields} = await formModule();
  const form = toDriverEditForm(apiDriver);
  assert.equal(form.vehicle, null);
  assert.equal(Object.hasOwn(form, 'icNo'), false);
  assert.equal(form.licenseNo, 'LICENSE7');
  assert.equal(form.licenseExpiry, '2028-01-01');
  assert.deepEqual(validateDriverFields(form, form.id), {});
  const payload = toDriverCreatePayload({...form, availability: 'Busy'});
  assert.equal(payload.vehicle, null);
  assert.equal(payload.driverAvailability, 'BUSY');
  assert.equal(payload.driverStatus, 'ACTIVE');
});

test('driver creation requires vehicle details and defaults to active', async () => {
  const {emptyDriver, toDriverCreatePayload, validateDriverFields} = await formModule();
  const form = {...emptyDriver, name: 'Driver', email: 'driver@example.com', phone: '0123456789',
    password: 'password123', confirmPassword: 'password123', vehicle: {model: 'Test model', plate: 'TEST7', type: 'MPV', capacity: 7, luggageCapacity: 0, status: 'Available'}};
  assert.deepEqual(validateDriverFields(form), {});
  assert.equal(toDriverCreatePayload(form).vehicle.vehiclePlateno, "TEST7");
  assert.ok(validateDriverFields({...form, vehicle: null}).vehicle);
  assert.equal(toDriverCreatePayload(form).driverStatus, 'ACTIVE');
});

test('assigned vehicle updates are explicit and driver-only edits bypass incomplete vehicle details', async () => {
  const {toDriverEditForm, toDriverCreatePayload, validateDriverFields} = await formModule();
  const form = toDriverEditForm({...apiDriver, vehicle: {vehicleModel: '', vehiclePlateno: 'TEST7',
    vehicleType: 'MPV', vehicleCapacity: 7, vehicleLuggageCapacity: 0, vehicleStatus: 'AVAILABLE'}});
  assert.equal(form.vehicle.plate, 'TEST7');
  assert.deepEqual(validateDriverFields(form, 7), {});
  assert.equal(toDriverCreatePayload(form).vehicle, null);
  const errors = validateDriverFields({...form, updateVehicle: true}, 7);
  assert.ok(errors['vehicle.model']);
  const optedIn = {...form, updateVehicle: true, vehicle: {...form.vehicle, model: 'Updated model'}};
  assert.deepEqual(validateDriverFields(optedIn, 7), {});
  assert.equal(toDriverCreatePayload(optedIn).vehicle.vehicleModel, 'Updated model');
});

test('field validation reports errors at the relevant driver fields', async () => {
  const {emptyDriver, validateDriverFields} = await formModule();
  const errors = validateDriverFields(emptyDriver);
  for (const key of ['name', 'email', 'phone', 'password', 'confirmPassword']) assert.ok(errors[key]);
});

test('expected cancellations are not logged as HTTP failures', async () => {
  const {logApiError} = await import('../src/services/apiErrors.js');
  const original = console.error;
  const logs = [];
  console.error = (...args) => logs.push(args);
  try { logApiError({code: 'ERR_CANCELED', message: 'canceled'}); }
  finally { console.error = original; }
  assert.deepEqual(logs, []);
});

test('legacy vehicle completion sends an explicit registration flag and complete vehicle data', async () => {
  const {toDriverEditForm,toDriverCreatePayload,validateDriverFields} = await formModule();
  const legacy=toDriverEditForm(apiDriver);
  const completion={...legacy,registerVehicle:true,updateVehicle:true,vehicle:{model:'Registered model',plate:'REPAIR7',
    type:'MPV',capacity:7,luggageCapacity:2,roadTaxExpiry:'',serviceExpiry:'',status:'Available'}};
  assert.deepEqual(validateDriverFields(completion,7),{});
  const payload=toDriverCreatePayload(completion);
  assert.equal(payload.registerMissingVehicle,true);
  assert.equal(payload.vehicle.vehiclePlateno,'REPAIR7');
});
