import { useMemo, useState } from "react";
import {
  FileText,
  ClipboardList,
  Receipt,
  FileSpreadsheet,
  Download,
  Eye,
  Search,
  X,
  Filter,
  CheckCircle,
  Clock,
  CreditCard,
  FilePlus2,
  Printer,
  Pencil,
  CalendarDays,
  Building2,
  Mail,
  Phone,
  MapPin,
} from "lucide-react";

/*
  DILADRIVE DOCUMENT MANAGEMENT

  Quotation:
  - Create, edit, preview, download and print.

  Purchase Order, Invoice, Receipt:
  - Generate, preview, download and print.
  - Read-only after generation.

  Demo data is stored in React state.
  Connect to your backend/database for permanent storage.
*/

const COMPANY = {
  name: "DilaDrive",
  tagline: "Transport & Logistics Management",
  email: "support@diladrive.com",
  phone: "+60 12-345 6789",
  address: "Malaysia",
};

const CUSTOMER_OPTIONS = [
  "Aina Logistics",
  "Maju Jaya Enterprise",
  "Mega Transport",
];

const DOCUMENT_TYPES = {
  Quotation: {
    prefix: "QT",
    icon: FileText,
    color: "blue",
  },
  "Purchase Order": {
    prefix: "PO",
    icon: ClipboardList,
    color: "orange",
  },
  Invoice: {
    prefix: "INV",
    icon: FileSpreadsheet,
    color: "green",
  },
  Receipt: {
    prefix: "REC",
    icon: Receipt,
    color: "purple",
  },
};

const initialDocuments = [
  {
    id: "QT-001",
    type: "Quotation",
    customer: "Aina Logistics",
    customerEmail: "aina@example.com",
    customerPhone: "012-111 2222",
    amount: 550,
    status: "Pending",
    date: "2026-10-20",
    validUntil: "2026-11-20",
    description: "Delivery and transportation service",
    bookingId: "DD-1028",
  },
  {
    id: "PO-001",
    type: "Purchase Order",
    customer: "Maju Jaya Enterprise",
    customerEmail: "maju@example.com",
    customerPhone: "012-333 4444",
    amount: 1200,
    status: "Approved",
    date: "2026-10-19",
    description: "Transport service order",
    bookingId: "DD-1027",
  },
  {
    id: "INV-001",
    type: "Invoice",
    customer: "Mega Transport",
    customerEmail: "mega@example.com",
    customerPhone: "012-555 6666",
    amount: 800,
    status: "Generated",
    date: "2026-10-18",
    description: "Transportation service charges",
    bookingId: "DD-1026",
  },
  {
    id: "REC-001",
    type: "Receipt",
    customer: "Aina Logistics",
    customerEmail: "aina@example.com",
    customerPhone: "012-111 2222",
    amount: 550,
    status: "Paid",
    date: "2026-10-17",
    description: "Payment received for delivery service",
    bookingId: "DD-1028",
    paymentMethod: "Online Banking",
    paymentReference: "PAY-001",
  },
];

const formatCurrency = (amount) =>
  new Intl.NumberFormat("en-MY", {
    style: "currency",
    currency: "MYR",
  }).format(Number(amount) || 0);

const formatDate = (date) => {
  if (!date) return "—";

  const parsed = new Date(`${date}T00:00:00`);

  if (Number.isNaN(parsed.getTime())) return "—";

  return parsed.toLocaleDateString("en-MY", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
};

const today = () => new Date().toISOString().slice(0, 10);

const statusStyles = {
  Pending: "bg-amber-50 text-amber-700 ring-amber-200",
  Approved: "bg-green-50 text-green-700 ring-green-200",
  Generated: "bg-blue-50 text-blue-700 ring-blue-200",
  Paid: "bg-emerald-50 text-emerald-700 ring-emerald-200",
};

const escapeHtml = (value) =>
  String(value ?? "").replace(/[&<>"']/g, (character) => {
    const entities = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    };

    return entities[character];
  });

function getDocumentTemplate(doc) {
  const common = {
    Quotation: {
      heading: "QUOTATION",
      referenceLabel: "Quotation No.",
      descriptionLabel: "Service Description",
      footer:
        "This quotation is subject to service availability and the terms agreed by both parties.",
    },
    "Purchase Order": {
      heading: "PURCHASE ORDER",
      referenceLabel: "Purchase Order No.",
      descriptionLabel: "Order Description",
      footer:
        "Please refer to the agreed purchase order terms and service arrangements.",
    },
    Invoice: {
      heading: "INVOICE",
      referenceLabel: "Invoice No.",
      descriptionLabel: "Service Description",
      footer:
        "Please use the invoice number as your payment reference. Contact DilaDrive if you have questions.",
    },
    Receipt: {
      heading: "PAYMENT RECEIPT",
      referenceLabel: "Receipt No.",
      descriptionLabel: "Payment Description",
      footer:
        "This receipt records the payment information entered into the system.",
    },
  };

  return common[doc.type] || common.Quotation;
}

/*
  Generates a standalone HTML document.

  Download:
  Saves the generated document as an HTML file.

  Print:
  Opens the same HTML template in a new window for printing
  or saving as PDF through the browser print dialog.
*/

function createDocumentHtml(doc) {
  const template = getDocumentTemplate(doc);
  const isQuotation = doc.type === "Quotation";
  const isReceipt = doc.type === "Receipt";

  const paymentDetails = isReceipt
    ? `
      <div class="detail">
        <span>Payment Method</span>
        <strong>${escapeHtml(doc.paymentMethod || "Not specified")}</strong>
      </div>
      <div class="detail">
        <span>Payment Reference</span>
        <strong>${escapeHtml(doc.paymentReference || doc.id)}</strong>
      </div>
    `
    : "";

  const quotationValidity = isQuotation
    ? `
      <div class="detail">
        <span>Valid Until</span>
        <strong>${escapeHtml(formatDate(doc.validUntil))}</strong>
      </div>
    `
    : "";

  const bookingDetails = doc.bookingId
    ? `
      <div class="detail">
        <span>Booking Reference</span>
        <strong>${escapeHtml(doc.bookingId)}</strong>
      </div>
    `
    : "";

  const receiptPaidNotice = isReceipt
    ? `<div class="paid-notice">PAYMENT RECORDED</div>`
    : "";

  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>${escapeHtml(doc.id)} - ${escapeHtml(doc.type)}</title>
<style>
  * { box-sizing: border-box; }

  body {
    margin: 0;
    padding: 32px;
    color: #1e293b;
    background: #f1f5f9;
    font-family: Arial, Helvetica, sans-serif;
    font-size: 13px;
    line-height: 1.6;
  }

  .toolbar {
    max-width: 850px;
    margin: 0 auto 16px;
    text-align: right;
  }

  .toolbar button {
    border: 0;
    border-radius: 7px;
    background: #2563eb;
    color: white;
    padding: 11px 18px;
    font-weight: bold;
    cursor: pointer;
  }

  .paper {
    max-width: 850px;
    min-height: 950px;
    margin: auto;
    padding: 48px;
    background: white;
    box-shadow: 0 8px 30px rgba(15, 23, 42, .08);
  }

  .header {
    display: flex;
    justify-content: space-between;
    gap: 24px;
    padding-bottom: 26px;
    border-bottom: 3px solid #2563eb;
  }

  .brand {
    color: #2563eb;
    font-size: 30px;
    font-weight: 800;
    letter-spacing: -1px;
  }

  .muted { color: #64748b; }

  .heading {
    margin: 28px 0;
    color: #0f172a;
    font-size: 28px;
    font-weight: 800;
    letter-spacing: 1px;
  }

  .details {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 24px;
    margin-bottom: 30px;
  }

  .section-title {
    margin-bottom: 8px;
    color: #64748b;
    font-size: 10px;
    font-weight: bold;
    letter-spacing: 1px;
    text-transform: uppercase;
  }

  .detail {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    padding: 10px 0;
    border-bottom: 1px solid #e2e8f0;
  }

  .detail span { color: #64748b; }
  .detail strong { text-align: right; overflow-wrap: anywhere; }

  table {
    width: 100%;
    border-collapse: collapse;
    margin-top: 16px;
  }

  th {
    background: #eff6ff;
    color: #1e40af;
    text-align: left;
    padding: 13px;
  }

  td {
    padding: 16px 13px;
    border-bottom: 1px solid #e2e8f0;
  }

  .right { text-align: right; }

  .total {
    width: 300px;
    max-width: 100%;
    margin: 20px 0 0 auto;
  }

  .grand-total {
    margin-top: 10px;
    padding: 16px;
    background: #eff6ff;
    color: #1e40af;
    font-size: 17px;
    font-weight: 800;
  }

  .paid-notice {
    margin: 24px 0;
    padding: 12px;
    border: 2px solid #059669;
    color: #047857;
    text-align: center;
    font-weight: 800;
    letter-spacing: 2px;
  }

  .footer {
    margin-top: 60px;
    padding-top: 20px;
    border-top: 1px solid #e2e8f0;
    color: #64748b;
    font-size: 11px;
  }

  @media print {
    @page { size: A4; margin: 12mm; }

    body {
      padding: 0;
      background: white;
      print-color-adjust: exact;
      -webkit-print-color-adjust: exact;
    }

    .toolbar { display: none; }

    .paper {
      max-width: none;
      min-height: 0;
      padding: 10mm;
      box-shadow: none;
    }

    .heading, .header, table, .total, .details {
      break-inside: avoid;
    }
  }

  @media (max-width: 600px) {
    body { padding: 10px; }
    .paper { padding: 22px; }
    .header { flex-direction: column; }
    .details { grid-template-columns: 1fr; }
  }
</style>
</head>
<body>
  <div class="toolbar">
    <button onclick="window.print()">Print / Save as PDF</button>
  </div>

  <main class="paper">
    <header class="header">
      <div>
        <div class="brand">${escapeHtml(COMPANY.name)}</div>
        <div class="muted">${escapeHtml(COMPANY.tagline)}</div>
        <div>${escapeHtml(COMPANY.address)}</div>
        <div>${escapeHtml(COMPANY.email)}</div>
        <div>${escapeHtml(COMPANY.phone)}</div>
      </div>

      <div style="text-align:right">
        <div class="muted">${escapeHtml(template.referenceLabel)}</div>
        <strong>${escapeHtml(doc.id)}</strong>
        <div class="muted" style="margin-top:12px">Document Date</div>
        <strong>${escapeHtml(formatDate(doc.date))}</strong>
      </div>
    </header>

    <h1 class="heading">${escapeHtml(template.heading)}</h1>

    ${receiptPaidNotice}

    <section class="details">
      <div>
        <div class="section-title">Bill To / Customer</div>
        <strong>${escapeHtml(doc.customer || "Customer not specified")}</strong>
        <div>${escapeHtml(doc.customerEmail || "")}</div>
        <div>${escapeHtml(doc.customerPhone || "")}</div>
      </div>

      <div>
        <div class="section-title">Document Information</div>

        <div class="detail">
          <span>Status</span>
          <strong>${escapeHtml(doc.status)}</strong>
        </div>

        ${bookingDetails}
        ${quotationValidity}
        ${paymentDetails}
      </div>
    </section>

    <div class="section-title">${escapeHtml(template.descriptionLabel)}</div>

    <table>
      <thead>
        <tr>
          <th>Description</th>
          <th class="right">Amount (MYR)</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>
            <strong>${escapeHtml(doc.description || doc.type)}</strong>
            <div class="muted">Reference: ${escapeHtml(doc.bookingId || doc.id)}</div>
          </td>
          <td class="right">${escapeHtml(formatCurrency(doc.amount))}</td>
        </tr>
      </tbody>
    </table>

    <div class="total">
      <div class="detail">
        <span>Subtotal</span>
        <strong>${escapeHtml(formatCurrency(doc.amount))}</strong>
      </div>

      <div class="grand-total">
        <div style="display:flex;justify-content:space-between;gap:12px">
          <span>Total (MYR)</span>
          <span>${escapeHtml(formatCurrency(doc.amount))}</span>
        </div>
      </div>
    </div>

    <footer class="footer">
      <strong>${escapeHtml(COMPANY.name)}</strong>
      <p>${escapeHtml(template.footer)}</p>
      <p>
        This document was generated by the DilaDrive document management
        interface. Please verify all information before using it for official
        business purposes.
      </p>
    </footer>
  </main>
</body>
</html>`;
}

function downloadDocument(doc) {
  const html = createDocumentHtml(doc);
  const blob = new Blob([html], {
    type: "text/html;charset=utf-8",
  });

  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");

  link.href = url;
  link.download = `${doc.id}.html`;

  document.body.appendChild(link);
  link.click();
  link.remove();

  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function printDocument(doc) {
  const html = createDocumentHtml(doc);
  const printWindow = window.open("", "_blank", "width=900,height=750");

  if (!printWindow) {
    window.alert(
      "The print window was blocked. Please allow pop-ups for this website and try again."
    );
    return;
  }

  printWindow.document.open();
  printWindow.document.write(html);
  printWindow.document.close();
  printWindow.focus();
}

export default function Documents() {
  const [documents, setDocuments] = useState(initialDocuments);
  const [searchTerm, setSearchTerm] = useState("");
  const [typeFilter, setTypeFilter] = useState("All");
  const [statusFilter, setStatusFilter] = useState("All");
  const [selectedDocument, setSelectedDocument] = useState(null);
  const [showGenerateMenu, setShowGenerateMenu] = useState(false);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createType, setCreateType] = useState("Quotation");
  const [notice, setNotice] = useState("");

  const filteredDocuments = useMemo(() => {
    const search = searchTerm.toLowerCase().trim();

    return documents.filter((doc) => {
      const matchesSearch =
        doc.id.toLowerCase().includes(search) ||
        (doc.customer || "").toLowerCase().includes(search) ||
        doc.type.toLowerCase().includes(search) ||
        (doc.bookingId || "").toLowerCase().includes(search);

      const matchesType =
        typeFilter === "All" || doc.type === typeFilter;

      const matchesStatus =
        statusFilter === "All" || doc.status === statusFilter;

      return matchesSearch && matchesType && matchesStatus;
    });
  }, [documents, searchTerm, typeFilter, statusFilter]);

  const countByType = (type) =>
    documents.filter((doc) => doc.type === type).length;

  const paidTotal = documents
    .filter((doc) => doc.type === "Receipt" && doc.status === "Paid")
    .reduce((total, doc) => total + Number(doc.amount || 0), 0);

  const pendingCount = documents.filter(
    (doc) => doc.status === "Pending"
  ).length;

  const handleGenerate = (type) => {
    setCreateType(type);
    setShowGenerateMenu(false);
    setShowCreateForm(true);
  };

  const handleCreateDocument = (event) => {
    event.preventDefault();

    const form = new FormData(event.currentTarget);
    const customer = String(form.get("customer") || "").trim();
    const amount = Number(form.get("amount"));
    const description = String(form.get("description") || "").trim();
    const bookingId = String(form.get("bookingId") || "").trim();

    if (!customer) {
      setNotice("Please select a customer.");
      return;
    }

    if (!Number.isFinite(amount) || amount <= 0) {
      setNotice("Please enter an amount greater than RM 0.");
      return;
    }

    if (!description) {
      setNotice("Please enter a service description.");
      return;
    }

    if (
      createType === "Receipt" &&
      form.get("paymentConfirmed") !== "on"
    ) {
      setNotice(
        "Confirm that payment has been verified before generating a receipt."
      );
      return;
    }

    const existingNumbers = documents
      .filter((doc) => doc.type === createType)
      .map((doc) => Number(doc.id.split("-")[1]))
      .filter(Number.isFinite);

    const nextNumber =
      existingNumbers.length > 0
        ? Math.max(...existingNumbers) + 1
        : 1;

    const prefix = DOCUMENT_TYPES[createType].prefix;

    const customerEmail = String(form.get("customerEmail") || "").trim();
    const customerPhone = String(form.get("customerPhone") || "").trim();

    const newDocument = {
      id: `${prefix}-${String(nextNumber).padStart(3, "0")}`,
      type: createType,
      customer,
      customerEmail,
      customerPhone,
      amount,
      status:
        createType === "Quotation"
          ? "Pending"
          : createType === "Purchase Order"
          ? "Approved"
          : createType === "Receipt"
          ? "Paid"
          : "Generated",
      date: today(),
      description,
      bookingId,
      validUntil:
        createType === "Quotation"
          ? String(form.get("validUntil") || "")
          : undefined,
      paymentMethod:
        createType === "Receipt"
          ? String(form.get("paymentMethod") || "Not specified")
          : undefined,
      paymentReference:
        createType === "Receipt"
          ? String(form.get("paymentReference") || "")
          : undefined,
    };

    setDocuments((current) => [...current, newDocument]);
    setShowCreateForm(false);
    setNotice(`${newDocument.id} generated successfully.`);
    setSearchTerm("");
    setTypeFilter("All");
    setStatusFilter("All");

    // Automatically open the generated document's print-ready template.
    printDocument(newDocument);
  };

  const handleUpdateQuotation = (updatedDocument) => {
    setDocuments((current) =>
      current.map((doc) =>
        doc.id === updatedDocument.id ? updatedDocument : doc
      )
    );

    setSelectedDocument(updatedDocument);
    setNotice(`${updatedDocument.id} quotation updated successfully.`);
  };

  return (
    <div className="min-h-screen space-y-6 bg-slate-50/70 p-4 md:p-7">
      {/* Header */}
      <div className="flex flex-col justify-between gap-4 lg:flex-row lg:items-center">
        <div>
          <div className="mb-2 flex items-center gap-2 text-sm text-slate-500">
            <span>Management</span>
            <span>/</span>
            <span className="font-medium text-blue-600">Documents</span>
          </div>

          <h1 className="text-2xl font-bold tracking-tight text-slate-900 md:text-3xl">
            Document Management
          </h1>

          <p className="mt-2 text-sm text-slate-500">
            Generate, manage, print and download transport documents.
          </p>
        </div>

        <div className="relative">
          <button
            onClick={() => setShowGenerateMenu((current) => !current)}
            className="flex items-center justify-center gap-2 rounded-xl bg-blue-600 px-5 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-700"
          >
            <FilePlus2 size={18} />
            Generate Document
          </button>

          {showGenerateMenu && (
            <div className="absolute right-0 z-20 mt-2 w-64 rounded-xl border border-slate-200 bg-white p-2 shadow-xl">
              {Object.entries(DOCUMENT_TYPES).map(([type, config]) => {
                const Icon = config.icon;

                return (
                  <button
                    key={type}
                    onClick={() => handleGenerate(type)}
                    className="flex w-full items-center gap-3 rounded-lg px-3 py-3 text-left text-sm text-slate-700 transition hover:bg-slate-50"
                  >
                    <Icon size={18} className="text-blue-600" />
                    <span>
                      <span className="block font-semibold">
                        {type}
                      </span>
                      <span className="mt-0.5 block text-xs text-slate-500">
                        {type === "Quotation"
                          ? "Create and manage quotation"
                          : `Generate ${type.toLowerCase()}`}
                      </span>
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* Notification */}
      {notice && (
        <div className="flex items-start justify-between gap-3 rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-800">
          <span>{notice}</span>
          <button
            onClick={() => setNotice("")}
            className="shrink-0 rounded p-1 hover:bg-blue-100"
            aria-label="Dismiss notification"
          >
            <X size={16} />
          </button>
        </div>
      )}

      {/* Summary Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryCard
          title="Quotations"
          value={countByType("Quotation")}
          subtitle="Manage and update quotations"
          icon={FileText}
          iconStyle="bg-blue-50 text-blue-600"
        />

        <SummaryCard
          title="Purchase Orders"
          value={countByType("Purchase Order")}
          subtitle="Generated purchase orders"
          icon={ClipboardList}
          iconStyle="bg-orange-50 text-orange-600"
        />

        <SummaryCard
          title="Invoices"
          value={countByType("Invoice")}
          subtitle="Generated billing documents"
          icon={FileSpreadsheet}
          iconStyle="bg-green-50 text-green-600"
        />

        <SummaryCard
          title="Receipts"
          value={countByType("Receipt")}
          subtitle={`${formatCurrency(paidTotal)} recorded as paid`}
          icon={Receipt}
          iconStyle="bg-purple-50 text-purple-600"
        />
      </div>

      {/* Financial overview */}
      <div className="grid gap-4 md:grid-cols-2">
        <OverviewCard
          title="Pending Quotations"
          value={pendingCount}
          description="Documents awaiting action"
          icon={Clock}
          iconStyle="bg-amber-50 text-amber-600"
        />

        <OverviewCard
          title="Recorded Receipt Total"
          value={formatCurrency(paidTotal)}
          description="Total of receipts marked as paid"
          icon={CreditCard}
          iconStyle="bg-emerald-50 text-emerald-600"
        />
      </div>

      {/* Table */}
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col justify-between gap-4 border-b border-slate-100 p-5 md:flex-row md:items-center md:p-6">
          <div>
            <h2 className="text-lg font-bold text-slate-900">
              Document Records
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              View document details or open the printable template.
            </p>
          </div>

          <span className="text-sm text-slate-500">
            {filteredDocuments.length} records found
          </span>
        </div>

        {/* Search and filters */}
        <div className="grid gap-3 border-b border-slate-100 p-4 md:grid-cols-3 md:p-5">
          <div className="relative">
            <Search
              size={18}
              className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
            />
            <input
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              placeholder="Search document, customer or booking..."
              className="w-full rounded-xl border border-slate-200 py-3 pl-10 pr-4 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            />
          </div>

          <div className="relative">
            <Filter
              size={16}
              className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
            />
            <select
              value={typeFilter}
              onChange={(event) => setTypeFilter(event.target.value)}
              className="w-full appearance-none rounded-xl border border-slate-200 bg-white py-3 pl-9 pr-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            >
              <option value="All">All document types</option>
              {Object.keys(DOCUMENT_TYPES).map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </div>

          <select
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
            className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
          >
            <option value="All">All statuses</option>
            <option value="Pending">Pending</option>
            <option value="Approved">Approved</option>
            <option value="Generated">Generated</option>
            <option value="Paid">Paid</option>
          </select>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-[900px] w-full text-left">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-4 font-semibold">Document</th>
                <th className="px-5 py-4 font-semibold">Customer</th>
                <th className="px-5 py-4 font-semibold">Date</th>
                <th className="px-5 py-4 font-semibold">Amount</th>
                <th className="px-5 py-4 font-semibold">Status</th>
                <th className="px-5 py-4 text-right font-semibold">
                  Actions
                </th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100">
              {filteredDocuments.map((doc) => {
                const Icon = DOCUMENT_TYPES[doc.type]?.icon || FileText;

                return (
                  <tr key={doc.id} className="transition hover:bg-slate-50">
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <div className="rounded-xl bg-slate-100 p-2.5 text-slate-600">
                          <Icon size={19} />
                        </div>
                        <div>
                          <p className="font-semibold text-slate-900">
                            {doc.id}
                          </p>
                          <p className="mt-1 text-xs text-slate-500">
                            {doc.type}
                          </p>
                        </div>
                      </div>
                    </td>

                    <td className="px-5 py-4 text-sm font-medium text-slate-700">
                      {doc.customer}
                      {doc.bookingId && (
                        <p className="mt-1 text-xs text-slate-400">
                          {doc.bookingId}
                        </p>
                      )}
                    </td>

                    <td className="px-5 py-4 text-sm text-slate-500">
                      {formatDate(doc.date)}
                    </td>

                    <td className="px-5 py-4 text-sm font-semibold text-slate-800">
                      {formatCurrency(doc.amount)}
                    </td>

                    <td className="px-5 py-4">
                      <span
                        className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold ring-1 ring-inset ${
                          statusStyles[doc.status] ||
                          "bg-slate-100 text-slate-600 ring-slate-200"
                        }`}
                      >
                        {doc.status === "Paid" ||
                        doc.status === "Approved" ? (
                          <CheckCircle size={13} />
                        ) : doc.status === "Pending" ? (
                          <Clock size={13} />
                        ) : (
                          <FileText size={13} />
                        )}
                        {doc.status}
                      </span>
                    </td>

                    <td className="px-5 py-4">
                      <div className="flex justify-end gap-2">
                        <button
                          onClick={() => setSelectedDocument(doc)}
                          title="Preview document"
                          aria-label={`Preview ${doc.id}`}
                          className="rounded-lg border border-slate-200 p-2 text-slate-600 hover:border-blue-200 hover:bg-blue-50 hover:text-blue-600"
                        >
                          <Eye size={17} />
                        </button>

                        {doc.type === "Quotation" && (
                          <button
                            onClick={() => setSelectedDocument(doc)}
                            title="Manage quotation"
                            aria-label={`Manage ${doc.id}`}
                            className="rounded-lg border border-slate-200 p-2 text-slate-600 hover:border-amber-200 hover:bg-amber-50 hover:text-amber-600"
                          >
                            <Pencil size={17} />
                          </button>
                        )}

                        <button
                          onClick={() => printDocument(doc)}
                          title="Print document"
                          aria-label={`Print ${doc.id}`}
                          className="rounded-lg border border-slate-200 p-2 text-slate-600 hover:border-green-200 hover:bg-green-50 hover:text-green-600"
                        >
                          <Printer size={17} />
                        </button>

                        <button
                          onClick={() => downloadDocument(doc)}
                          title="Download document template"
                          aria-label={`Download ${doc.id}`}
                          className="rounded-lg border border-slate-200 p-2 text-slate-600 hover:border-purple-200 hover:bg-purple-50 hover:text-purple-600"
                        >
                          <Download size={17} />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}

              {filteredDocuments.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-6 py-14 text-center">
                    <Search
                      size={28}
                      className="mx-auto text-slate-300"
                    />
                    <p className="mt-3 font-semibold text-slate-800">
                      No documents found
                    </p>
                    <p className="mt-1 text-sm text-slate-500">
                      Try another search term or change the filters.
                    </p>
                    <button
                      onClick={() => {
                        setSearchTerm("");
                        setTypeFilter("All");
                        setStatusFilter("All");
                      }}
                      className="mt-4 text-sm font-semibold text-blue-600 hover:text-blue-700"
                    >
                      Clear filters
                    </button>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Generate document form */}
      {showCreateForm && (
        <CreateDocumentModal
          type={createType}
          onClose={() => setShowCreateForm(false)}
          onSubmit={handleCreateDocument}
        />
      )}

      {/* Preview and quotation management */}
      {selectedDocument && (
        <DocumentDetailsModal
          document={selectedDocument}
          onClose={() => setSelectedDocument(null)}
          onSave={handleUpdateQuotation}
          onDownload={downloadDocument}
          onPrint={printDocument}
        />
      )}
    </div>
  );
}

function SummaryCard({
  title,
  value,
  subtitle,
  icon: Icon,
  iconStyle,
}) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:shadow-md">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-medium text-slate-500">{title}</p>
          <p className="mt-3 text-3xl font-bold tracking-tight text-slate-900">
            {value}
          </p>
        </div>
        <div className={`rounded-xl p-3 ${iconStyle}`}>
          <Icon size={22} />
        </div>
      </div>
      <p className="mt-4 text-xs text-slate-500">{subtitle}</p>
    </div>
  );
}

function OverviewCard({
  title,
  value,
  description,
  icon: Icon,
  iconStyle,
}) {
  return (
    <div className="flex items-center gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className={`rounded-xl p-3 ${iconStyle}`}>
        <Icon size={23} />
      </div>
      <div>
        <p className="text-sm text-slate-500">{title}</p>
        <p className="mt-1 text-2xl font-bold text-slate-900">{value}</p>
        <p className="mt-1 text-xs text-slate-500">{description}</p>
      </div>
    </div>
  );
}

function CreateDocumentModal({ type, onClose, onSubmit }) {
  const [customer, setCustomer] = useState(CUSTOMER_OPTIONS[0]);

  const isQuotation = type === "Quotation";
  const isReceipt = type === "Receipt";
  const Icon = DOCUMENT_TYPES[type]?.icon || FileText;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/50 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        className="my-auto w-full max-w-xl overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-start justify-between border-b border-slate-100 p-5">
          <div className="flex items-center gap-3">
            <div className="rounded-xl bg-blue-50 p-3 text-blue-600">
              <Icon size={22} />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                Generate {type}
              </h2>
              <p className="mt-1 text-sm text-slate-500">
                Enter the information for the document template.
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
          >
            <X size={19} />
          </button>
        </div>

        <form onSubmit={onSubmit} className="space-y-4 p-5">
          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">
              Customer *
            </label>
            <select
              name="customer"
              value={customer}
              onChange={(event) => setCustomer(event.target.value)}
              required
              className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            >
              {CUSTOMER_OPTIONS.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
            <p className="mt-1 text-xs text-slate-500">
              Demo customer list. Connect this field to your Customers module.
            </p>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Customer Email
              </label>
              <input
                name="customerEmail"
                type="email"
                placeholder="customer@example.com"
                className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />
            </div>

            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Customer Phone
              </label>
              <input
                name="customerPhone"
                placeholder="01X-XXXXXXX"
                className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />
            </div>
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">
              Booking Reference
            </label>
            <input
              name="bookingId"
              placeholder="e.g. DD-1028"
              className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">
              Service Description *
            </label>
            <textarea
              name="description"
              rows={3}
              required
              placeholder="Describe the transport or delivery service..."
              className="w-full resize-y rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Amount (RM) *
              </label>
              <input
                name="amount"
                type="number"
                min="0.01"
                step="0.01"
                required
                placeholder="550.00"
                className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
              />
            </div>

            {isQuotation ? (
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Valid Until *
                </label>
                <input
                  name="validUntil"
                  type="date"
                  min={today()}
                  required
                  defaultValue={`${new Date(
                    Date.now() + 30 * 86400000
                  )
                    .toISOString()
                    .slice(0, 10)}`}
                  className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
            ) : isReceipt ? (
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Payment Method *
                </label>
                <select
                  name="paymentMethod"
                  required
                  className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                >
                  <option value="Online Banking">Online Banking</option>
                  <option value="Bank Transfer">Bank Transfer</option>
                  <option value="Card">Card</option>
                  <option value="Cash">Cash</option>
                </select>
              </div>
            ) : (
              <div className="flex items-center gap-3 rounded-xl bg-slate-50 p-3 text-sm text-slate-600">
                <CalendarDays size={19} className="shrink-0 text-blue-600" />
                <span>
                  Document date
                  <strong className="mt-1 block text-slate-800">
                    {formatDate(today())}
                  </strong>
                </span>
              </div>
            )}
          </div>

          {isReceipt && (
            <>
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Payment Reference
                </label>
                <input
                  name="paymentReference"
                  placeholder="e.g. PAY-001"
                  className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>

              <label className="flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                <input
                  name="paymentConfirmed"
                  type="checkbox"
                  className="mt-1"
                />
                <span>
                  <strong>Payment verified</strong>
                  <span className="mt-1 block">
                    Confirm only after checking the payment record. Do not
                    generate a receipt for an unverified payment.
                  </span>
                </span>
              </label>
            </>
          )}

          <div className="flex flex-col-reverse gap-3 border-t border-slate-100 pt-4 sm:flex-row sm:justify-end">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-slate-200 px-5 py-3 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="flex items-center justify-center gap-2 rounded-xl bg-blue-600 px-5 py-3 text-sm font-semibold text-white hover:bg-blue-700"
            >
              <FilePlus2 size={17} />
              Generate & Print
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function DocumentDetailsModal({
  document,
  onClose,
  onSave,
  onDownload,
  onPrint,
}) {
  const [formData, setFormData] = useState({ ...document });
  const isQuotation = document.type === "Quotation";
  const Icon = DOCUMENT_TYPES[document.type]?.icon || FileText;

  const updateField = (field, value) => {
    setFormData((current) => ({
      ...current,
      [field]: value,
    }));
  };

  const handleSubmit = (event) => {
    event.preventDefault();

    if (!isQuotation) return;

    if (!formData.customer.trim()) {
      window.alert("Please enter a customer name.");
      return;
    }

    if (
      !Number.isFinite(Number(formData.amount)) ||
      Number(formData.amount) <= 0
    ) {
      window.alert("Please enter a valid amount greater than RM 0.");
      return;
    }

    if (!formData.description.trim()) {
      window.alert("Please enter a service description.");
      return;
    }

    if (
      formData.validUntil &&
      formData.validUntil < formData.date
    ) {
      window.alert("The validity date cannot be earlier than the quotation date.");
      return;
    }

    onSave({
      ...formData,
      customer: formData.customer.trim(),
      description: formData.description.trim(),
      amount: Number(formData.amount),
    });
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/50 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        className="my-auto w-full max-w-xl overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-start justify-between border-b border-slate-100 p-5">
          <div className="flex items-center gap-3">
            <div className="rounded-xl bg-blue-50 p-3 text-blue-600">
              <Icon size={22} />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                {isQuotation ? "Manage Quotation" : "Document Details"}
              </h2>
              <p className="mt-1 text-sm text-slate-500">
                {document.id} · {document.type}
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            aria-label="Close"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
          >
            <X size={19} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 p-5">
          <div className="rounded-xl bg-slate-50 p-4">
            <p className="text-xs text-slate-500">Document status</p>
            <p className="mt-1 font-semibold text-slate-800">
              {formData.status}
            </p>
            <p className="mt-2 text-xs text-slate-500">
              Document date: {formatDate(formData.date)}
            </p>
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">
              Customer
            </label>
            <input
              value={formData.customer || ""}
              onChange={(event) => updateField("customer", event.target.value)}
              readOnly={!isQuotation}
              required
              className={`w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none ${
                isQuotation
                  ? "focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                  : "cursor-not-allowed bg-slate-50 text-slate-500"
              }`}
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-slate-700">
                Amount (RM)
              </label>
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={formData.amount}
                onChange={(event) => updateField("amount", event.target.value)}
                readOnly={!isQuotation}
                required
                className={`w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none ${
                  isQuotation
                    ? "focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                    : "cursor-not-allowed bg-slate-50 text-slate-500"
                }`}
              />
            </div>

            {isQuotation ? (
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Valid Until
                </label>
                <input
                  type="date"
                  min={formData.date}
                  value={formData.validUntil || ""}
                  onChange={(event) =>
                    updateField("validUntil", event.target.value)
                  }
                  className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                />
              </div>
            ) : (
              <div>
                <label className="mb-1.5 block text-sm font-medium text-slate-700">
                  Booking Reference
                </label>
                <input
                  value={formData.bookingId || "—"}
                  readOnly
                  className="w-full cursor-not-allowed rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-500"
                />
              </div>
            )}
          </div>

          <div>
            <label className="mb-1.5 block text-sm font-medium text-slate-700">
              Service Description
            </label>
            <textarea
              rows={3}
              value={formData.description || ""}
              onChange={(event) =>
                updateField("description", event.target.value)
              }
              readOnly={!isQuotation}
              required
              className={`w-full resize-y rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none ${
                isQuotation
                  ? "focus:border-blue-500 focus:ring-2 focus:ring-blue-100"
                  : "cursor-not-allowed bg-slate-50 text-slate-500"
              }`}
            />
          </div>

          <div className="rounded-xl border border-slate-200 p-4">
            <p className="text-sm font-semibold text-slate-800">
              Document Template
            </p>
            <p className="mt-1 text-sm text-slate-500">
              Preview, download the HTML template, or print it and choose
              “Save as PDF” in the browser print dialog.
            </p>
          </div>

          <div className="flex flex-col gap-3 border-t border-slate-100 pt-4 sm:flex-row sm:flex-wrap sm:justify-end">
            <button
              type="button"
              onClick={() => onPrint(formData)}
              className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 px-4 py-3 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              <Printer size={17} />
              Print / PDF
            </button>

            <button
              type="button"
              onClick={() => onDownload(formData)}
              className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 px-4 py-3 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              <Download size={17} />
              Download
            </button>

            {isQuotation && (
              <button
                type="submit"
                className="flex items-center justify-center gap-2 rounded-xl bg-blue-600 px-5 py-3 text-sm font-semibold text-white hover:bg-blue-700"
              >
                <Pencil size={16} />
                Save Quotation
              </button>
            )}

            {!isQuotation && (
              <button
                type="button"
                onClick={onClose}
                className="rounded-xl bg-slate-900 px-5 py-3 text-sm font-semibold text-white hover:bg-slate-800"
              >
                Done
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  );
}
