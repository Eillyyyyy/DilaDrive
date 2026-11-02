import DataTable from "../components/DataTable";

const quotations = [
  {
    id: "QT-001",
    customer: "Aina Logistics",
    date: "21 Oct 2026",
    amount: "RM 280",
    status: "Pending",
  },
  {
    id: "QT-002",
    customer: "Maju Jaya Enterprise",
    date: "20 Oct 2026",
    amount: "RM 420",
    status: "Approved",
  },
];

export default function Quotations() {
  return (
    <div>
      <h2 className="mb-2 text-2xl font-bold">
        Quotation Management
      </h2>

      <p className="mb-6 text-gray-500">
        Manage transport quotations for customers.
      </p>

      <DataTable
        rows={quotations}
        columns={[
          { key: "id", label: "Quotation ID" },
          { key: "customer", label: "Customer" },
          { key: "date", label: "Date" },
          { key: "amount", label: "Amount" },
          { key: "status", label: "Status" },
        ]}
      />
    </div>
  );
}