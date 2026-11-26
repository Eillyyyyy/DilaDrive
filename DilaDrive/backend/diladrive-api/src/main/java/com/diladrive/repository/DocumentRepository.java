package com.diladrive.repository;

import com.diladrive.entity.Document;
import com.diladrive.entity.DocumentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByOrderByCreatedAtDesc();

    List<Document> findByCustIdOrderByCreatedAtDesc(Long custId);

    Optional<Document> findByDocumentIdAndCustId(Long documentId, Long custId);

    List<Document> findByDocumentNumberContainingIgnoreCaseOrCustNameContainingIgnoreCaseOrderByCreatedAtDesc(
            String documentNumber,
            String custName);

    List<Document> findByDocumentTypeOrderByCreatedAtDesc(DocumentType documentType);

    Optional<Document> findByDocumentNumber(String documentNumber);

    boolean existsByDocumentTypeAndBookingId(DocumentType documentType, Long bookingId);

    boolean existsByDocumentTypeAndPaymentId(DocumentType documentType, Long paymentId);

    Optional<Document> findByDocumentTypeAndPaymentId(DocumentType documentType, Long paymentId);

    boolean existsByDocumentTypeAndSourceDocumentId(
            DocumentType documentType,
            Long sourceDocumentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.documentId = :id")
    Optional<Document> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT
                COUNT(*) FILTER (WHERE document_type = 'QUOTATION') AS "totalQuotations",
                COUNT(*) FILTER (WHERE document_type = 'PURCHASE_ORDER') AS "totalPurchaseOrders",
                COUNT(*) FILTER (WHERE document_type = 'INVOICE') AS "totalInvoices",
                COUNT(*) FILTER (WHERE document_type = 'RECEIPT') AS "totalReceipts",
                COALESCE(
                    SUM(d.amount) FILTER (WHERE d.document_type = 'RECEIPT' AND p.payment_status = 'VERIFIED'),
                    0.00
                ) AS "totalReceiptAmount"
            FROM documents d LEFT JOIN payment p ON p.payment_id = d.payment_id
            """, nativeQuery = true)
    DocumentDashboardProjection getDashboardStatistics();
    @Query(value = """
            select d.* from documents d join payment p on p.payment_id=d.payment_id
            join booking b on b.booking_id=p.booking_id
            where d.document_type='RECEIPT' and p.payment_status='VERIFIED'
              and d.booking_id=b.booking_id and d.cust_id=:customerId and b.cust_id=:customerId
              and (cast(:documentId as bigint) is null or d.document_id=:documentId)
            order by d.document_id desc
            """, nativeQuery = true)
    java.util.List<Document> findVerifiedReceipts(
            @Param("customerId") Long customerId, @Param("documentId") Long documentId);
}
