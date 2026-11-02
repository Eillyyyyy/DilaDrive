import DataTable from "../components/DataTable";

const documents = [
  {
    id: "INV-001",
    customer: "Aina Logistics",
    type: "Invoice",
    amount: "RM 280",
    status: "Approved",
  },
  {
    id: "PO-001",
    customer: "Maju Jaya Enterprise",
    type: "Purchase Order",
    amount: "RM 420",
    status: "Pending",
  },
  {
    id: "REC-001",
    customer: "Aina Logistics",
    type: "Receipt",
    amount: "RM 280",
    status: "Completed",
  },
];

export default function Invoices() {
  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold">
            Financial Documents
          </h2>

          <p className="text-slate-500">
            Manage invoices, purchase orders and receipts.
          </p>
        </div>

        <div className="flex gap-3">
          <button className="rounded-xl bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">
            Generate Invoice
          </button>

          <button className="rounded-xl bg-orange-500 px-4 py-2 text-white hover:bg-orange-600">
            Generate PO
          </button>

          <button className="rounded-xl bg-green-600 px-4 py-2 text-white hover:bg-green-700">
            Generate Receipt
          </button>
        </div>
      </div>

      <DataTable
        rows={documents}
        columns={[
          { key: "id", label: "Document ID" },
          { key: "customer", label: "Customer" },
          { key: "type", label: "Document Type" },
          { key: "amount", label: "Amount" },
          { key: "status", label: "Status" },
        ]}
      />
    </div>
  );
}