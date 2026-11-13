import DataTable from "../components/DataTable";

export default function Invoices() {
  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">
            Financial Documents
          </h1>

          <p className="admin-page-description">
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
        rows={[]}
        columns={[
          { key: "customer", label: "Customer" },
          { key: "type", label: "Document Type" },
          { key: "amount", label: "Amount" },
          { key: "status", label: "Status" },
        ]}
      />
    </div>
  );
}
