import StatusBadge from "./StatusBadge";

export default function DataTable({ rows, columns }) {
  return (
    <div className="overflow-x-auto rounded-xl bg-white">
      <table className="w-full text-left text-sm">
        <thead className="bg-gray-50 text-gray-500">
          <tr>
            {columns.map((column) => (
              <th key={column.key} className="p-4">
                {column.label}
              </th>
            ))}
          </tr>
        </thead>

        <tbody>
          {rows.map((row, index) => (
            <tr key={row.id || row.name || row.plate || index}
                className="border-t hover:bg-gray-50">
              {columns.map((column) => (
                <td key={column.key} className="p-4">
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
              <td colSpan={columns.length} className="p-6 text-center">
                No records found.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}