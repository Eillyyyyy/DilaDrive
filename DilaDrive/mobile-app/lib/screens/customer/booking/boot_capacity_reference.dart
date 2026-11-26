import 'package:flutter/material.dart';

const _combinations = <String, List<String>>{
  'Sedan (6 units)': [
    'Fits: 6 Small = 6 units',
    'Fits: 3 Medium = 6 units',
    'Fits: 2 Large = 6 units',
    'Fits: 2 Small + 2 Medium = 6 units',
    'Fits: 1 Large + 1 Medium + 1 Small = 6 units',
    'Exceeds capacity: 3 Large = 9 units',
    'Exceeds capacity: 2 Large + 1 Medium + 1 Small = 9 units',
  ],
  'MPV (12 units)': [
    'Fits: 4 Large = 12 units',
    'Fits: 6 Medium = 12 units',
    'Fits: 12 Small = 12 units',
    'Fits: 2 Large + 2 Medium + 2 Small = 12 units',
    'Exceeds capacity: 5 Large = 15 units',
    'Exceeds capacity: 4 Large + 1 Medium = 14 units',
  ],
  'Premium MPV (15 units)': [
    'Fits: 5 Large = 15 units',
    'Fits: 3 Large + 2 Medium + 2 Small = 15 units',
    'Fits: 7 Medium + 1 Small = 15 units',
    'Exceeds capacity: 6 Large = 18 units',
  ],
  '10-Seater Van (18 units)': [
    'Fits: 6 Large = 18 units',
    'Fits: 9 Medium = 18 units',
    'Fits: 18 Small = 18 units',
    'Fits: 4 Large + 2 Medium + 2 Small = 18 units',
    'Exceeds capacity: 7 Large = 21 units',
  ],
  '18-Seater Van (24 units)': [
    'Fits: 8 Large = 24 units',
    'Fits: 12 Medium = 24 units',
    'Fits: 24 Small = 24 units',
    'Fits: 5 Large + 3 Medium + 3 Small = 24 units',
    'Exceeds capacity: 9 Large = 27 units',
  ],
};

const _recommendations = <String, List<String>>{
  'Example A — Sedan': [
    '2 passengers, 1 small, 1 medium, 1 large',
    'Boot usage: (1×1) + (1×2) + (1×3) = 6 units',
  ],
  'Example B — MPV': [
    '4 passengers, 2 small, 1 medium, 2 large',
    'Boot usage: (2×1) + (1×2) + (2×3) = 10 units',
  ],
  'Example C — MPV': [
    '1 passenger, 0 small, 0 medium, 4 large',
    'Boot usage: 4×3 = 12 units',
  ],
  'Example D — 10-Seater Van': [
    '8 passengers, 2 small, 3 medium, 3 large',
    'Boot usage: (2×1) + (3×2) + (3×3) = 17 units',
  ],
  'Example E — 18-Seater Van': [
    '14 passengers, 4 small, 4 medium, 4 large',
    'Boot usage: (4×1) + (4×2) + (4×3) = 24 units',
  ],
};

class BootCapacityReference extends StatelessWidget {
  const BootCapacityReference({super.key});

  @override
  Widget build(BuildContext context) {
    final headingStyle = Theme.of(context).textTheme.titleSmall;
    return ExpansionTile(
      tilePadding: EdgeInsets.zero,
      title: const Text('Boot Capacity Reference'),
      expandedCrossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
            'Small luggage = 1 unit, medium luggage = 2 units, and large luggage = 3 units.'),
        const SizedBox(height: 8),
        const Text(
          'These examples use the standard category capacities. Live '
          'recommendations depend on the available fleet and the trip schedule.',
        ),
        for (final entry in _combinations.entries)
          _example(entry.key, entry.value, headingStyle),
        const SizedBox(height: 12),
        Text('Vehicle Recommendation Examples', style: headingStyle),
        for (final entry in _recommendations.entries)
          _example(entry.key, entry.value, headingStyle),
        const SizedBox(height: 12),
        const Text(
          'Recommend the smallest eligible vehicle that can carry both the '
          'passengers and their luggage. Luggage units are calculated '
          'independently of passenger count.',
        ),
      ],
    );
  }

  Widget _example(String title, List<String> lines, TextStyle? headingStyle) {
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: headingStyle),
          for (final line in lines) Text(line),
        ],
      ),
    );
  }
}
