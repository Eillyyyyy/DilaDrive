package com.diladrive.repository;

import java.math.BigDecimal;

public interface DocumentDashboardProjection {
    Long getTotalQuotations();

    Long getTotalPurchaseOrders();

    Long getTotalInvoices();

    Long getTotalReceipts();

    BigDecimal getTotalReceiptAmount();
}
