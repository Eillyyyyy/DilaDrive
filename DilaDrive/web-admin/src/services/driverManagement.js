// Driver and assigned vehicle records come from the backend; dropdowns are local.
export async function loadDriverManagement(client, config = {}) {
  const [drivers, vehicles] = await Promise.allSettled([
    client.get("/drivers", config), client.get("/vehicles", config),
  ]);
  if (drivers.status === "rejected") throw drivers.reason;
  if (!Array.isArray(drivers.value.data)) throw new Error("The driver API returned an invalid record list.");
  if (vehicles.status === "fulfilled" && !Array.isArray(vehicles.value.data)) {
    throw new Error("The vehicle API returned an invalid record list.");
  }
  return {
    drivers: drivers.value.data,
    vehicles: vehicles.status === "fulfilled" ? vehicles.value.data : null,
    errors: [vehicles].filter(result => result.status === "rejected").map(result => result.reason),
  };
}
