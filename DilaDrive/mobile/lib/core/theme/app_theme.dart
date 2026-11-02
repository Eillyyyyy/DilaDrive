import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Design tokens from docs/DILADRIVE_BLUEPRINT.md §4 (maroon + cream + white cards).
class AppColors {
  static const primary900 = Color(0xFF3D0606);
  static const primary700 = Color(0xFF5B0A0A);
  static const primary500 = Color(0xFF8B1A1A);
  static const primary100 = Color(0xFFF3E3E3);
  static const bgCream = Color(0xFFFAF6F0);
  static const surface = Color(0xFFFFFFFF);
  static const border = Color(0xFFE8E0D6);
  static const accentGold = Color(0xFFC9A24B);
  static const textPrimary = Color(0xFF2B1B1B);
  static const textMuted = Color(0xFF7A6A6A);
  static const success = Color(0xFF2E7D32);
  static const warning = Color(0xFFED8B00);
  static const danger = Color(0xFFC62828);
  static const info = Color(0xFF1565C0);
}

class AppTheme {
  static ThemeData light() {
    final scheme = ColorScheme.fromSeed(
      seedColor: AppColors.primary700,
      brightness: Brightness.light,
    ).copyWith(
      primary: AppColors.primary700,
      onPrimary: Colors.white,
      secondary: AppColors.accentGold,
      error: AppColors.danger,
      surface: AppColors.surface,
      onSurface: AppColors.textPrimary,
    );

    final baseText = GoogleFonts.interTextTheme();
    final textTheme = baseText.copyWith(
      headlineSmall: GoogleFonts.poppins(fontSize: 24, fontWeight: FontWeight.w600, color: AppColors.textPrimary),
      titleLarge: GoogleFonts.poppins(fontSize: 20, fontWeight: FontWeight.w600, color: AppColors.textPrimary),
      titleMedium: GoogleFonts.poppins(fontSize: 16, fontWeight: FontWeight.w600, color: AppColors.textPrimary),
      bodyMedium: GoogleFonts.inter(fontSize: 14, color: AppColors.textPrimary),
      bodySmall: GoogleFonts.inter(fontSize: 12, color: AppColors.textMuted),
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: AppColors.bgCream,
      textTheme: textTheme,
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.primary700,
        foregroundColor: Colors.white,
        elevation: 0,
      ),
      cardTheme: CardThemeData(
        color: AppColors.surface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: const BorderSide(color: AppColors.border),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.primary700,
          foregroundColor: Colors.white,
          minimumSize: const Size.fromHeight(48), // >= 48dp tap target
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          textStyle: GoogleFonts.inter(fontWeight: FontWeight.w600),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.surface,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: AppColors.border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: AppColors.primary700, width: 2),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: AppColors.surface,
        indicatorColor: AppColors.primary100,
      ),
    );
  }
}
