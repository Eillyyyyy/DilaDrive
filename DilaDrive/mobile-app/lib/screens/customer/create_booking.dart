import 'package:flutter/material.dart';

class CreateBookingPage extends StatefulWidget {
  const CreateBookingPage({super.key});

  @override
  State<CreateBookingPage> createState() =>
      _CreateBookingPageState();
}

class _CreateBookingPageState
    extends State<CreateBookingPage> {
  String selectedVehicle = "Van";
  String selectedCapacity = "Small";

  final pickupController = TextEditingController();
  final dropoffController = TextEditingController();
  final remarksController = TextEditingController();

  DateTime? selectedDate;
  TimeOfDay? selectedTime;

  String availabilityResult = "";
  String fareResult = "";

  double calculatedFare = 0;

  Future<void> pickDate() async {
    final date = await showDatePicker(
      context: context,
      initialDate: DateTime.now(),
      firstDate: DateTime.now(),
      lastDate: DateTime(2030),
    );

    if (date != null) {
      setState(() {
        selectedDate = date;
      });
    }
  }

  Future<void> pickTime() async {
    final time = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.now(),
    );

    if (time != null) {
      setState(() {
        selectedTime = time;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    const primaryColor = Color(0xFF701A35);

    return Scaffold(
      backgroundColor: const Color(0xFFF6F7FB),

      appBar: AppBar(
        title: const Text("Create Booking"),
        automaticallyImplyLeading: false,
        backgroundColor: Colors.white,
        elevation: 0,
      ),

      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: primaryColor,
                borderRadius: BorderRadius.circular(24),
              ),
              child: const Column(
                crossAxisAlignment:
                    CrossAxisAlignment.start,
                children: [
                  Text(
                    "BOOK TRANSPORT",
                    style: TextStyle(
                      color: Colors.white70,
                    ),
                  ),
                  SizedBox(height: 10),
                  Text(
                    "Create a transport request quickly and easily.",
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 25),

            buildTextField(
              controller: pickupController,
              label: "Pickup Location",
              icon: Icons.location_on_outlined,
            ),

            const SizedBox(height: 15),

            buildTextField(
              controller: dropoffController,
              label: "Drop-off Location",
              icon: Icons.flag_outlined,
            ),

            const SizedBox(height: 15),

            Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 15),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
              ),
              child: DropdownButtonFormField<String>(
                initialValue: selectedVehicle,
                decoration: const InputDecoration(
                  border: InputBorder.none,
                  labelText: "Vehicle Type",
                ),
                items: const [
                  DropdownMenuItem(
                    value: "Van",
                    child: Text("Van"),
                  ),
                  DropdownMenuItem(
                    value: "Pickup Truck",
                    child: Text("Pickup Truck"),
                  ),
                  DropdownMenuItem(
                    value: "Lorry",
                    child: Text("Lorry"),
                  ),
                ],
                onChanged: (value) {
                  setState(() {
                    selectedVehicle = value!;
                  });
                },
              ),
            ),

            const SizedBox(height: 15),

            Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 15),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
              ),
              child: DropdownButtonFormField<String>(
                initialValue: selectedCapacity,
                decoration: const InputDecoration(
                  border: InputBorder.none,
                  labelText: "Capacity Required",
                ),
                items: const [
                  DropdownMenuItem(
                    value: "Small",
                    child: Text("Small (< 500 KG)"),
                  ),
                  DropdownMenuItem(
                    value: "Medium",
                    child: Text("Medium (500 KG - 2 Ton)"),
                  ),
                  DropdownMenuItem(
                    value: "Large",
                    child: Text("Large (> 2 Ton)"),
                  ),
                ],
                onChanged: (value) {
                  setState(() {
                    selectedCapacity = value!;
                  });
                },
              ),
            ),

            const SizedBox(height: 15),

            GestureDetector(
              onTap: pickDate,
              child: Container(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.calendar_month),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        selectedDate == null
                            ? "Select Pickup Date"
                            : "${selectedDate!.day}/${selectedDate!.month}/${selectedDate!.year}",
                      ),
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 15),

            GestureDetector(
              onTap: pickTime,
              child: Container(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.access_time),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        selectedTime == null
                            ? "Select Pickup Time"
                            : selectedTime!
                                .format(context),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 15),

            TextField(
              controller: remarksController,
              maxLines: 4,
              decoration: InputDecoration(
                hintText: "Additional Notes / Remarks",
                filled: true,
                fillColor: Colors.white,
                border: OutlineInputBorder(
                  borderRadius:
                      BorderRadius.circular(16),
                  borderSide: BorderSide.none,
                ),
              ),
            ),

            const SizedBox(height: 25),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: OutlinedButton(
                onPressed: () {
                  setState(() {
                    availabilityResult =
                        "Available Drivers : 3\nAvailable Vehicles : 2\nStatus : Available";
                  });
                },
                child: const Text(
                  "Check Availability",
                ),
              ),
            ),

            if (availabilityResult.isNotEmpty)
              Container(
                width: double.infinity,
                margin: const EdgeInsets.only(top: 15),
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.green.shade50,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Text(
                  availabilityResult,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),

            const SizedBox(height: 15),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.orange,
                ),
                onPressed: () {
                  double fare = 50;

                  if (selectedVehicle == "Van") {
                    fare += 30;
                  }

                  if (selectedVehicle ==
                      "Pickup Truck") {
                    fare += 60;
                  }

                  if (selectedVehicle == "Lorry") {
                    fare += 120;
                  }

                  if (selectedCapacity ==
                      "Medium") {
                    fare += 25;
                  }

                  if (selectedCapacity ==
                      "Large") {
                    fare += 60;
                  }

                  setState(() {
                    calculatedFare = fare;

                    fareResult =
                        "Estimated Fare : RM ${fare.toStringAsFixed(2)}";
                  });
                },
                child: const Text(
                  "Calculate Fare",
                  style: TextStyle(
                    color: Colors.white,
                  ),
                ),
              ),
            ),

            if (fareResult.isNotEmpty)
              Container(
                width: double.infinity,
                margin: const EdgeInsets.only(top: 15),
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: Colors.orange.shade50,
                  borderRadius: BorderRadius.circular(18),
                ),
                child: Column(
                  crossAxisAlignment:
                      CrossAxisAlignment.start,
                  children: [
                    const Text(
                      "Trip Fare",
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),

                    const SizedBox(height: 10),

                    Text(
                      fareResult,
                      style: const TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ),

            const SizedBox(height: 25),

            SizedBox(
              width: double.infinity,
              height: 55,
              child: ElevatedButton.icon(
                icon: const Icon(
                  Icons.local_shipping,
                  color: Colors.white,
                ),
                label: const Text(
                  "Submit Booking",
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                  ),
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor: primaryColor,
                  shape: RoundedRectangleBorder(
                    borderRadius:
                        BorderRadius.circular(16),
                  ),
                ),
                onPressed: () {
                  if (fareResult.isEmpty) {
                    ScaffoldMessenger.of(context)
                        .showSnackBar(
                      const SnackBar(
                        content: Text(
                          "Please calculate trip fare first.",
                        ),
                      ),
                    );
                    return;
                  }

                  ScaffoldMessenger.of(context)
                      .showSnackBar(
                    const SnackBar(
                      content: Text(
                        "Booking submitted successfully.",
                      ),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget buildTextField({
    required TextEditingController controller,
    required String label,
    required IconData icon,
  }) {
    return TextField(
      controller: controller,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon),
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
      ),
    );
  }
}