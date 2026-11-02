import 'package:flutter/material.dart';

class UploadVerificationPhotoPage extends StatefulWidget {
  const UploadVerificationPhotoPage({super.key});

  @override
  State<UploadVerificationPhotoPage> createState() =>
      _UploadVerificationPhotoPageState();
}

class _UploadVerificationPhotoPageState
    extends State<UploadVerificationPhotoPage> {
  String selectedType = "Pickup Verification";

  bool uploaded = false;

  @override
  Widget build(BuildContext context) {
    const primaryColor = Color(0xFF701A35);

    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),

      appBar: AppBar(
        title: const Text("Verification Photo"),
        backgroundColor: Colors.white,
        elevation: 0,
      ),

      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: primaryColor,
                borderRadius: BorderRadius.circular(20),
              ),
              child: const Column(
                crossAxisAlignment:
                    CrossAxisAlignment.start,
                children: [
                  Text(
                    "UPLOAD VERIFICATION",
                    style: TextStyle(
                      color: Colors.white70,
                    ),
                  ),

                  SizedBox(height: 10),

                  Text(
                    "Upload proof photo for pickup or delivery verification.",
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 25),

            Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 15),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius:
                    BorderRadius.circular(16),
              ),
              child: DropdownButtonFormField<String>(
                initialValue: selectedType,
                decoration: const InputDecoration(
                  border: InputBorder.none,
                  labelText: "Verification Type",
                ),
                items: const [
                  DropdownMenuItem(
                    value: "Pickup Verification",
                    child: Text(
                      "Pickup Verification",
                    ),
                  ),
                  DropdownMenuItem(
                    value: "Drop-off Verification",
                    child: Text(
                      "Drop-off Verification",
                    ),
                  ),
                ],
                onChanged: (value) {
                  setState(() {
                    selectedType = value!;
                  });
                },
              ),
            ),

            const SizedBox(height: 20),

            Container(
              width: double.infinity,
              height: 220,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius:
                    BorderRadius.circular(20),
                border: Border.all(
                  color: Colors.grey.shade300,
                ),
              ),
              child: Column(
                mainAxisAlignment:
                    MainAxisAlignment.center,
                children: [
                  const Icon(
                    Icons.image_outlined,
                    size: 70,
                    color: Colors.grey,
                  ),

                  const SizedBox(height: 12),

                  Text(
                    uploaded
                        ? "Photo Selected"
                        : "No Photo Selected",
                    style: const TextStyle(
                      fontWeight:
                          FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: OutlinedButton.icon(
                icon: const Icon(
                  Icons.camera_alt_outlined,
                ),
                label:
                    const Text("Choose Photo"),
                onPressed: () {
                  setState(() {
                    uploaded = true;
                  });

                  ScaffoldMessenger.of(context)
                      .showSnackBar(
                    const SnackBar(
                      content: Text(
                        "Photo selected successfully.",
                      ),
                    ),
                  );
                },
              ),
            ),

            const SizedBox(height: 15),

            if (uploaded)
              Container(
                width: double.infinity,
                padding:
                    const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.green.shade50,
                  borderRadius:
                      BorderRadius.circular(
                          16),
                ),
                child: const Row(
                  children: [
                    Icon(
                      Icons.check_circle,
                      color: Colors.green,
                    ),

                    SizedBox(width: 10),

                    Expanded(
                      child: Text(
                        "Verification photo ready to upload.",
                        style: TextStyle(
                          fontWeight:
                              FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),
              ),

            const SizedBox(height: 20),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: ElevatedButton.icon(
                icon: const Icon(
                  Icons.upload,
                  color: Colors.white,
                ),
                label: const Text(
                  "Upload Verification Photo",
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                  ),
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor:
                      primaryColor,
                  shape:
                      RoundedRectangleBorder(
                    borderRadius:
                        BorderRadius.circular(
                            15),
                  ),
                ),
                onPressed: () {
                  if (!uploaded) {
                    ScaffoldMessenger.of(
                            context)
                        .showSnackBar(
                      const SnackBar(
                        content: Text(
                          "Please choose a photo first.",
                        ),
                      ),
                    );
                    return;
                  }

                  ScaffoldMessenger.of(
                          context)
                      .showSnackBar(
                    SnackBar(
                      content: Text(
                        "$selectedType uploaded successfully.",
                      ),
                    ),
                  );
                },
              ),
            ),

            const SizedBox(height: 20),

            Container(
              width: double.infinity,
              padding:
                  const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: Colors.orange.shade50,
                borderRadius:
                    BorderRadius.circular(18),
              ),
              child: const Column(
                crossAxisAlignment:
                    CrossAxisAlignment.start,
                children: [
                  Text(
                    "Status",
                    style: TextStyle(
                      fontWeight:
                          FontWeight.bold,
                    ),
                  ),

                  SizedBox(height: 10),

                  Text(
                    "Waiting for upload.",
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}