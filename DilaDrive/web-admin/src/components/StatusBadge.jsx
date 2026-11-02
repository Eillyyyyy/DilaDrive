export default function StatusBadge({ children }) {
  const colors = {
    Pending: "bg-amber-100 text-amber-700",
    Approved: "bg-blue-100 text-blue-700",
    "In Progress": "bg-purple-100 text-purple-700",
    Completed: "bg-green-100 text-green-700",
    Verified: "bg-green-100 text-green-700",
    Available: "bg-green-100 text-green-700",
    "On Trip": "bg-blue-100 text-blue-700",
  };

  return (
    <span className={`rounded-full px-3 py-1 text-xs font-semibold ${
      colors[children] || "bg-gray-100 text-gray-600"
    }`}>
      {children}
    </span>
  );
}