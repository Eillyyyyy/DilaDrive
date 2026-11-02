export default function PurchaseOrders() {
  return (
    <div className="rounded-2xl bg-white p-6 shadow">
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-2xl font-bold">
          Purchase Orders
        </h2>

        <button className="rounded-xl bg-teal-500 px-4 py-2 text-white">
          Generate PO
        </button>
      </div>

      <table className="w-full">
        <thead>
          <tr className="border-b">
            <th className="p-3 text-left">PO Number</th>
            <th className="p-3 text-left">Supplier</th>
            <th className="p-3 text-left">Amount</th>
            <th className="p-3 text-left">Status</th>
          </tr>
        </thead>

        <tbody>
          <tr>
            <td className="p-3">PO-1001</td>
            <td className="p-3">ABC Vehicle Services</td>
            <td className="p-3">RM 2,500</td>
            <td className="p-3">Approved</td>
          </tr>

          <tr>
            <td className="p-3">PO-1002</td>
            <td className="p-3">Fuel Supplier Sdn Bhd</td>
            <td className="p-3">RM 3,800</td>
            <td className="p-3">Pending</td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}