import DataTable from "../components/DataTable";

export default function Quotations() {
  return (
    <div className="admin-page">
      <header className="admin-page-header">
      <div>
      <h1 className="admin-page-title">
        Quotation Management
      </h1>

      <p className="admin-page-description">
        Manage transport quotations for customers.
      </p>
      </div>
      </header>

      <DataTable
        rows={[]}
        columns={[
          { key: "customer", label: "Customer" },
          { key: "date", label: "Date" },
          { key: "amount", label: "Amount" },
          { key: "status", label: "Status" },
        ]}
      />
    </div>
  );
}
