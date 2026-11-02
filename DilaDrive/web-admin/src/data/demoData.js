export const bookingsSeed = [
  {
    id: "DD-1028",
    customer: "Aina Logistics",
    route: "Shah Alam → KLIA",
    date: "21 Oct 2026",
    status: "Pending",
    amount: "RM 280",
  },
  {
    id: "DD-1027",
    customer: "Maju Jaya Enterprise",
    route: "Subang → Port Klang",
    date: "21 Oct 2026",
    status: "Approved",
    amount: "RM 420",
  },
  {
    id: "DD-1026",
    customer: "Kencana Trading",
    route: "Klang → Putrajaya",
    date: "20 Oct 2026",
    status: "In Progress",
    amount: "RM 350",
  },
  {
    id: "DD-1025",
    customer: "Seri Murni Sdn Bhd",
    route: "Puchong → Shah Alam",
    date: "20 Oct 2026",
    status: "Completed",
    amount: "RM 190",
  },
];

export const customers = [
  {
    name: "Aina Logistics",
    email: "aina@example.com",
    phone: "012-555 0182",
    status: "Verified",
  },
  {
    name: "Maju Jaya Enterprise",
    email: "maju@example.com",
    phone: "013-555 0134",
    status: "Verified",
  },
  {
    name: "Kencana Trading",
    email: "kencana@example.com",
    phone: "016-555 0119",
    status: "Pending",
  },
];

export const drivers = [
  {
    name: "Amir Hakim",
    phone: "012-333 4455",
    vehicle: "VAN-1024",
    status: "Available",
  },
  {
    name: "Farid Iskandar",
    phone: "017-222 1199",
    vehicle: "LOR-2018",
    status: "On Trip",
  },
  {
    name: "Siti Mariam",
    phone: "019-888 2020",
    vehicle: "VAN-1008",
    status: "Available",
  },
];

export const vehicles = [
  {
    plate: "VAN-1024",
    type: "Panel Van",
    capacity: "1,000 kg",
    status: "Available",
  },
  {
    plate: "LOR-2018",
    type: "Lorry",
    capacity: "3,000 kg",
    status: "On Trip",
  },
  {
    plate: "VAN-1008",
    type: "Panel Van",
    capacity: "800 kg",
    status: "Available",
  },
];