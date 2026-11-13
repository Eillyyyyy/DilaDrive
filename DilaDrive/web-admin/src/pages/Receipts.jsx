export default function Receipts() {
  return (
    <div className="admin-page">
      <header className="admin-page-header">
        <h1 className="admin-page-title">
          Receipts
        </h1>

        <button className="rounded-xl bg-teal-500 px-4 py-2 text-white">
          Generate Receipt
        </button>
      </header>

      <section className="overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm">
      <table className="w-full">
        <thead>
          <tr className="border-b">
            <th className="p-3 text-left">No.</th>
            <th className="p-3 text-left">Receipt No</th>
            <th className="p-3 text-left">Customer</th>
            <th className="p-3 text-left">Amount</th>
            <th className="p-3 text-left">Date</th>
          </tr>
        </thead>

        <tbody>
          <tr>
            <td className="p-6 text-center text-slate-500" colSpan={5}>
              No receipts available.
            </td>
          </tr>
        </tbody>
      </table>
      </section>
    </div>
  );
}
