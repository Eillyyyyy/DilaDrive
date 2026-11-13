import StatusBadge from "./StatusBadge";

export default function DataTable({ rows, columns }) {
  return (
    <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm">
      <table className="w-full min-w-[640px] text-left text-sm">
        <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-slate-500">
          <tr>
              <th className="px-5 py-3.5">No.</th>
            {columns.map((column) => (
              <th key={column.key} className="px-5 py-3.5">
                {column.label}
              </th>
            ))}
          </tr>
        </thead>

        <tbody>
          {rows.map((row, index) => (
            <tr key={row.id || row.name || row.plate || index}
                className="border-t border-slate-100 transition hover:bg-slate-50/70">
              <td className="px-5 py-4 font-medium text-slate-500">{index + 1}</td>
              {columns.map((column) => (
                <td key={column.key} className="px-5 py-4 text-slate-700">
                  {column.key === "status" ? (
                    <StatusBadge>{row.status}</StatusBadge>
                  ) : (
                    row[column.key] ?? "-"
                  )}
                </td>
              ))}
            </tr>
          ))}

          {rows.length === 0 && (
            <tr>
              <td colSpan={columns.length + 1} className="px-5 py-12 text-center text-slate-500">
                No records found.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
