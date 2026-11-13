import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:pdfrx/pdfrx.dart';

import '../../models/business_document.dart';
import '../../services/business_document_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';

class BusinessDocumentsPage extends StatefulWidget {
  const BusinessDocumentsPage({super.key, this.documentService});

  final BusinessDocumentService? documentService;

  @override
  State<BusinessDocumentsPage> createState() => _BusinessDocumentsPageState();
}

class _BusinessDocumentsPageState extends State<BusinessDocumentsPage> {
  late final bool _ownsService = widget.documentService == null;
  late final BusinessDocumentService _service =
      widget.documentService ?? BusinessDocumentService();
  late Future<List<BusinessDocument>> _documents;

  @override
  void initState() {
    super.initState();
    _documents = _service.getMyDocuments();
  }

  @override
  void dispose() {
    if (_ownsService) _service.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    final future = _service.getMyDocuments();
    setState(() {
      _documents = future;
    });
    try {
      await future;
    } catch (_) {
      // FutureBuilder shows the retryable request error.
    }
  }

  void _retryOrSignIn(Object? error) {
    if (error is BusinessDocumentException && error.requiresLogin) {
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute<void>(builder: (_) => const LoginPage()),
        (_) => false,
      );
    } else {
      setState(() {
        _documents = _service.getMyDocuments();
      });
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(title: const Text('Business documents')),
        body: FutureBuilder<List<BusinessDocument>>(
          future: _documents,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              final error = snapshot.error;
              final needsLogin =
                  error is BusinessDocumentException && error.requiresLogin;
              return _MessageState(
                icon: Icons.cloud_off_outlined,
                message: error is BusinessDocumentException
                    ? error.message
                    : 'Your documents could not be loaded.',
                action: FilledButton.tonal(
                  onPressed: () => _retryOrSignIn(error),
                  child: Text(needsLogin ? 'Sign in' : 'Try again'),
                ),
              );
            }
            final documents = snapshot.data ?? const <BusinessDocument>[];
            return RefreshIndicator(
              onRefresh: _refresh,
              child: documents.isEmpty
                  ? ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.all(24),
                      children: const [
                        SizedBox(height: 100),
                        Icon(Icons.description_outlined,
                            size: 48, color: AppTheme.secondarySkyBlue),
                        SizedBox(height: 14),
                        Text('No business documents yet',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                                fontSize: 17, fontWeight: FontWeight.w700)),
                        SizedBox(height: 6),
                        Text(
                            'Approved quotations, invoices and receipts will appear here.',
                            textAlign: TextAlign.center,
                            style: TextStyle(color: AppTheme.secondaryText)),
                      ],
                    )
                  : ListView.separated(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.all(16),
                      itemCount: documents.length,
                      separatorBuilder: (_, __) => const SizedBox(height: 10),
                      itemBuilder: (context, index) =>
                          _DocumentCard(document: documents[index]),
                    ),
            );
          },
        ),
      );
}

class _DocumentCard extends StatelessWidget {
  const _DocumentCard({required this.document});
  final BusinessDocument document;

  @override
  Widget build(BuildContext context) {
    final color = _statusColor(document.status);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: EdgeInsets.zero,
        child: InkWell(
          onTap: () => Navigator.push<void>(
            context,
            MaterialPageRoute(
              builder: (_) => BusinessDocumentDetailsPage(document: document),
            ),
          ),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                const Icon(Icons.description_outlined,
                    color: AppTheme.primaryNavy),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(document.number,
                      style: const TextStyle(fontWeight: FontWeight.w700)),
                ),
                DecoratedBox(
                  decoration: BoxDecoration(
                    color: color.withValues(alpha: .12),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Padding(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                    child: Text(document.status,
                        style: TextStyle(
                            color: color,
                            fontSize: 12,
                            fontWeight: FontWeight.w700)),
                  ),
                ),
                const SizedBox(width: 8),
                const Icon(Icons.chevron_right, color: AppTheme.secondaryText),
              ]),
              const SizedBox(height: 10),
              Text(_documentType(document.type),
                  style: const TextStyle(color: AppTheme.secondaryText)),
              const SizedBox(height: 4),
              Text('Booking ${document.bookingReference}'),
              const SizedBox(height: 4),
              Text('RM ${document.amount.toStringAsFixed(2)}',
                  style: const TextStyle(fontWeight: FontWeight.w600)),
              if (document.createdAt != null) ...[
                const SizedBox(height: 4),
                Text(
                  MaterialLocalizations.of(context)
                      .formatMediumDate(document.createdAt!),
                  style: const TextStyle(
                      color: AppTheme.secondaryText, fontSize: 12),
                ),
              ],
            ]),
          ),
        ),
      ),
    );
  }
}

class BusinessDocumentDetailsPage extends StatelessWidget {
  const BusinessDocumentDetailsPage({
    super.key,
    required this.document,
  });

  final BusinessDocument document;

  @override
  Widget build(BuildContext context) {
    final statusColor = _statusColor(document.status);
    return Scaffold(
      backgroundColor: AppTheme.lightBlueBackground,
      appBar: AppBar(title: Text(_documentType(document.type))),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            color: AppTheme.primaryNavy,
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(document.number,
                      style: const TextStyle(
                          color: Colors.white,
                          fontSize: 20,
                          fontWeight: FontWeight.w700)),
                  const SizedBox(height: 10),
                  DecoratedBox(
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 6),
                      child: Text(document.status,
                          style: TextStyle(
                              color: statusColor, fontWeight: FontWeight.w700)),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          _DocumentDetailCard(title: 'Document details', items: {
            'Type': _documentType(document.type),
            'Booking': document.bookingReference,
            'Amount': 'RM ${document.amount.toStringAsFixed(2)}',
            if (document.createdAt != null)
              'Created': MaterialLocalizations.of(context)
                  .formatMediumDate(document.createdAt!),
            if (document.sourceDocumentId != null)
              'Source document': '#${document.sourceDocumentId}',
          }),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Remarks',
                      style: TextStyle(
                          color: AppTheme.primaryNavy,
                          fontWeight: FontWeight.w700)),
                  const SizedBox(height: 8),
                  Text(
                    document.remarks?.trim().isNotEmpty == true
                        ? document.remarks!.trim()
                        : 'No remarks were recorded for this document.',
                    style:
                        const TextStyle(color: AppTheme.mainText, height: 1.45),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: () => Navigator.of(context).push<void>(
              MaterialPageRoute(
                builder: (_) => BusinessDocumentPdfPage(document: document),
              ),
            ),
            icon: const Icon(Icons.picture_as_pdf_outlined),
            label: const Text('View PDF'),
          ),
        ],
      ),
    );
  }
}

class BusinessDocumentPdfPage extends StatefulWidget {
  const BusinessDocumentPdfPage({super.key, required this.document});

  final BusinessDocument document;

  @override
  State<BusinessDocumentPdfPage> createState() =>
      _BusinessDocumentPdfPageState();
}

class _BusinessDocumentPdfPageState extends State<BusinessDocumentPdfPage> {
  late final BusinessDocumentService _service = BusinessDocumentService();
  late Future<List<int>> _pdf = _service.getDocumentPdf(widget.document.id);

  @override
  void dispose() {
    _service.dispose();
    super.dispose();
  }

  void _retry(Object? error) {
    if (error is BusinessDocumentException && error.requiresLogin) {
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute<void>(builder: (_) => const LoginPage()),
        (_) => false,
      );
      return;
    }
    setState(() => _pdf = _service.getDocumentPdf(widget.document.id));
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(title: Text(widget.document.number)),
        body: FutureBuilder<List<int>>(
          future: _pdf,
          builder: (context, snapshot) {
            if (snapshot.hasError) {
              final error = snapshot.error;
              return _MessageState(
                icon: Icons.picture_as_pdf_outlined,
                message: error is BusinessDocumentException
                    ? error.message
                    : 'The PDF could not be loaded.',
                action: FilledButton.tonal(
                  onPressed: () => _retry(error),
                  child: Text(
                      error is BusinessDocumentException && error.requiresLogin
                          ? 'Sign in'
                          : 'Try again'),
                ),
              );
            }
            final bytes = snapshot.data;
            if (bytes == null) {
              return const Center(child: CircularProgressIndicator());
            }
            return PdfViewer.data(
              Uint8List.fromList(bytes),
              sourceName: '${widget.document.number}.pdf',
              params: const PdfViewerParams(backgroundColor: Colors.white),
            );
          },
        ),
      );
}

class _DocumentDetailCard extends StatelessWidget {
  const _DocumentDetailCard({required this.title, required this.items});

  final String title;
  final Map<String, String> items;

  @override
  Widget build(BuildContext context) => Card(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title,
                  style: const TextStyle(
                      color: AppTheme.primaryNavy,
                      fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              ...items.entries.map((entry) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 7),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(
                          width: 120,
                          child: Text(entry.key,
                              style: const TextStyle(
                                  color: AppTheme.secondaryText)),
                        ),
                        Expanded(
                          child: Text(entry.value,
                              style: const TextStyle(
                                  color: AppTheme.mainText,
                                  fontWeight: FontWeight.w600)),
                        ),
                      ],
                    ),
                  )),
            ],
          ),
        ),
      );
}

class _MessageState extends StatelessWidget {
  const _MessageState(
      {required this.icon, required this.message, required this.action});
  final IconData icon;
  final String message;
  final Widget action;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(icon, size: 42, color: AppTheme.secondaryText),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 12),
            action,
          ]),
        ),
      );
}

String _documentType(String type) => switch (type.toUpperCase()) {
      'QUOTATION' => 'Quotation',
      'PURCHASE_ORDER' => 'Purchase order',
      'INVOICE' => 'Invoice',
      'RECEIPT' => 'Receipt',
      _ => type,
    };

Color _statusColor(String status) => switch (status.toUpperCase()) {
      'PAID' || 'APPROVED' => Colors.green,
      'REJECTED' || 'CANCELLED' => Colors.red,
      'PENDING' || 'DRAFT' || 'ISSUED' => Colors.orange,
      _ => AppTheme.secondarySkyBlue,
    };
