package com.diladrive.dto;

import java.math.BigDecimal;

public record DocumentDashboardDTO(
        long totalQuotations,
        long totalPurchaseOrders,
        long totalInvoices,
        long totalReceipts,
        BigDecimal totalReceiptAmount) {
}
