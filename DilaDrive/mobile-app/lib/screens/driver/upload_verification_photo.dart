import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../models/verification_photo.dart';
import '../../services/verification_photo_service.dart';
import '../../theme/app_theme.dart';
import '../auth/login.dart';

class UploadVerificationPhotoPage extends StatefulWidget {
  const UploadVerificationPhotoPage({
    super.key,
    required this.assignmentId,
    required this.allowUpload,
    this.service,
    this.pickImage,
  });
  final int assignmentId;
  final bool allowUpload;
  final VerificationPhotoService? service;
  final Future<XFile?> Function(ImageSource source)? pickImage;

  @override
  State<UploadVerificationPhotoPage> createState() =>
      _UploadVerificationPhotoPageState();
}

class _UploadVerificationPhotoPageState
    extends State<UploadVerificationPhotoPage> {
  final _picker = ImagePicker();
  final Map<int, Future<List<int>>> _photoReads = {};
  late final bool _ownsService = widget.service == null;
  late final VerificationPhotoService _service;
  late Future<List<VerificationPhoto>> _photos;
  bool _uploading = false;

  Future<List<int>> _readPhoto(int photoId) => _photoReads.putIfAbsent(
        photoId,
        () => _service.read(widget.assignmentId, photoId),
      );

  void _retryPhoto(int photoId) {
    final future = _service.read(widget.assignmentId, photoId);
    setState(() {
      _photoReads[photoId] = future;
    });
  }

  @override
  void initState() {
    super.initState();
    _service = widget.service ?? VerificationPhotoService();
    _photos = _service.list(widget.assignmentId);
  }

  @override
  void dispose() {
    if (_ownsService) _service.dispose();
    super.dispose();
  }

  Future<void> _upload(String type, ImageSource source) async {
    if (!mounted || _uploading) return;
    setState(() => _uploading = true);
    try {
      final image = await (widget.pickImage?.call(source) ??
          _picker.pickImage(source: source, imageQuality: 85));
      if (!mounted || image == null) return;
      await _service.upload(widget.assignmentId, type, image);
      if (!mounted) return;
      setState(() {
        _photos = _service.list(widget.assignmentId);
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${_label(type)} photo uploaded.')),
      );
    } catch (error) {
      if (mounted) {
        if (error is VerificationPhotoException && error.requiresLogin) {
          _openLogin();
          return;
        }
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(error.toString())));
      }
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  void _openLogin() {
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute<void>(builder: (_) => const LoginPage()),
      (_) => false,
    );
  }

  Future<void> _choosePhotoType() async {
    final selection = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(
            leading: const Icon(Icons.flight_takeoff),
            title: const Text('Pickup verification'),
            onTap: () => Navigator.pop(context, 'PICKUP'),
          ),
          ListTile(
            leading: const Icon(Icons.flag_outlined),
            title: const Text('Drop-off verification'),
            onTap: () => Navigator.pop(context, 'DROP'),
          ),
        ]),
      ),
    );
    if (selection == null || !mounted) return;
    final source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(
            leading: const Icon(Icons.camera_alt_outlined),
            title: const Text('Take a photo'),
            onTap: () => Navigator.pop(context, ImageSource.camera),
          ),
          ListTile(
            leading: const Icon(Icons.photo_library_outlined),
            title: const Text('Choose from gallery'),
            onTap: () => Navigator.pop(context, ImageSource.gallery),
          ),
        ]),
      ),
    );
    if (source != null && mounted) await _upload(selection, source);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: AppTheme.lightBlueBackground,
        appBar: AppBar(title: const Text('Verification photos')),
        body: FutureBuilder<List<VerificationPhoto>>(
          future: _photos,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              final requiresLogin = snapshot.error
                      is VerificationPhotoException &&
                  (snapshot.error as VerificationPhotoException).requiresLogin;
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Text(snapshot.error.toString(),
                        textAlign: TextAlign.center),
                    const SizedBox(height: 12),
                    OutlinedButton(
                      onPressed: requiresLogin
                          ? _openLogin
                          : () => setState(
                                () => _photos =
                                    _service.list(widget.assignmentId),
                              ),
                      child: Text(requiresLogin ? 'Sign in' : 'Retry'),
                    ),
                  ]),
                ),
              );
            }
            final photos = snapshot.data ?? const <VerificationPhoto>[];
            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(18),
                    child: Column(children: [
                      const Icon(Icons.verified_outlined,
                          size: 38, color: AppTheme.secondarySkyBlue),
                      const SizedBox(height: 8),
                      Text(
                        widget.allowUpload
                            ? 'Add trip evidence'
                            : 'Trip evidence',
                        style: const TextStyle(
                            color: AppTheme.primaryNavy,
                            fontSize: 18,
                            fontWeight: FontWeight.w700),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        widget.allowUpload
                            ? 'Photos are stored with this assigned trip and are visible only to its driver.'
                            : 'This trip is no longer active. Existing photos are still available below.',
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: AppTheme.secondaryText),
                      ),
                      if (widget.allowUpload) ...[
                        const SizedBox(height: 14),
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton.icon(
                            onPressed: _uploading ? null : _choosePhotoType,
                            icon: _uploading
                                ? const SizedBox.square(
                                    dimension: 18,
                                    child: CircularProgressIndicator(
                                        strokeWidth: 2))
                                : const Icon(Icons.add_a_photo_outlined),
                            label:
                                Text(_uploading ? 'Uploading…' : 'Add photo'),
                          ),
                        ),
                      ],
                    ]),
                  ),
                ),
                const SizedBox(height: 12),
                if (photos.isEmpty)
                  const Card(
                    child: Padding(
                      padding: EdgeInsets.all(20),
                      child: Text('No verification photos have been added.',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: AppTheme.secondaryText)),
                    ),
                  )
                else
                  ...photos.map((photo) => Card(
                        clipBehavior: Clip.antiAlias,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            ListTile(
                              leading: const Icon(Icons.image_outlined),
                              title: Text('${_label(photo.photoType)} photo'),
                              subtitle: Text(photo.uploadedAt == null
                                  ? 'Uploaded'
                                  : _dateLabel(photo.uploadedAt!)),
                            ),
                            FutureBuilder<List<int>>(
                              future: _readPhoto(photo.photoId),
                              builder: (context, image) {
                                if (image.hasError) {
                                  final requiresLogin = image.error
                                          is VerificationPhotoException &&
                                      (image.error
                                              as VerificationPhotoException)
                                          .requiresLogin;
                                  return Padding(
                                    padding: const EdgeInsets.all(16),
                                    child: requiresLogin
                                        ? TextButton(
                                            onPressed: _openLogin,
                                            child: const Text(
                                                'Session expired. Sign in again.'),
                                          )
                                        : TextButton.icon(
                                            onPressed: () =>
                                                _retryPhoto(photo.photoId),
                                            icon: const Icon(
                                                Icons.refresh_rounded),
                                            label: const Text(
                                                'Could not load this photo. Retry'),
                                          ),
                                  );
                                }
                                if (!image.hasData) {
                                  return const LinearProgressIndicator();
                                }
                                return Image.memory(
                                  Uint8List.fromList(image.data!),
                                  width: double.infinity,
                                  height: 220,
                                  fit: BoxFit.cover,
                                  errorBuilder: (_, __, ___) => const Padding(
                                    padding: EdgeInsets.all(16),
                                    child:
                                        Text('This photo file is unavailable.'),
                                  ),
                                );
                              },
                            ),
                          ],
                        ),
                      )),
              ],
            );
          },
        ),
      );
}

String _label(String type) => type == 'PICKUP' ? 'Pickup' : 'Drop-off';

String _dateLabel(DateTime date) =>
    '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year}';
