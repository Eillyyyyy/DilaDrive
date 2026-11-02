export default function Receipts() {
  return (
    <div className="rounded-2xl bg-white p-6 shadow">
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-2xl font-bold">
          Receipts
        </h2>

        <button className="rounded-xl bg-teal-500 px-4 py-2 text-white">
          Generate Receipt
        </button>
      </div>

      <table className="w-full">
        <thead>
          <tr className="border-b">
            <th className="p-3 text-left">Receipt No</th>
            <th className="p-3 text-left">Customer</th>
            <th className="p-3 text-left">Amount</th>
            <th className="p-3 text-left">Date</th>
          </tr>
        </thead>

        <tbody>
          <tr>
            <td className="p-3">RC-1001</td>
            <td className="p-3">Aina Logistics</td>
            <td className="p-3">RM 350</td>
            <td className="p-3">21 Oct 2026</td>
          </tr>

          <tr>
            <td className="p-3">RC-1002</td>
            <td className="p-3">Kencana Trading</td>
            <td className="p-3">RM 500</td>
            <td className="p-3">20 Oct 2026</td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}