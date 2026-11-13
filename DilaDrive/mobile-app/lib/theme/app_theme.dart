import 'package:flutter/material.dart';

abstract final class AppTheme {
  static const Color primaryNavy = Color(0xFF002B49);
  static const Color navyGradientEnd = Color(0xFF064D73);
  static const Color secondarySkyBlue = Color(0xFF00A8E8);
  static const Color lightBlueBackground = Color(0xFFF2F8FC);
  static const Color white = Color(0xFFFFFFFF);
  static const Color mainText = Color(0xFF1F2937);
  static const Color secondaryText = Color(0xFF6B7280);
  static const Color border = Color(0xFFD8E5ED);
  static const Color paleBlue = Color(0xFFE5F6FD);

  static final ThemeData lightTheme = ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: primaryNavy,
      primary: primaryNavy,
      secondary: secondarySkyBlue,
      surface: white,
      error: const Color(0xFFB3261E),
    ),
    scaffoldBackgroundColor: lightBlueBackground,
    appBarTheme: const AppBarTheme(
      backgroundColor: white,
      foregroundColor: primaryNavy,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      centerTitle: false,
    ),
    textTheme: const TextTheme(
      bodyLarge: TextStyle(color: mainText),
      bodyMedium: TextStyle(color: mainText),
      bodySmall: TextStyle(color: secondaryText),
      titleLarge: TextStyle(
        color: mainText,
        fontWeight: FontWeight.w600,
      ),
      titleMedium: TextStyle(
        color: mainText,
        fontWeight: FontWeight.w600,
      ),
    ),
    cardTheme: CardThemeData(
      color: white,
      surfaceTintColor: Colors.transparent,
      elevation: 1,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: border),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: white,
      contentPadding: const EdgeInsets.symmetric(
        horizontal: 16,
        vertical: 14,
      ),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: border),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: border),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: secondarySkyBlue, width: 1.5),
      ),
      labelStyle: const TextStyle(color: secondaryText),
      hintStyle: const TextStyle(color: secondaryText),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: primaryNavy,
        foregroundColor: white,
        disabledBackgroundColor: primaryNavy.withValues(alpha: 0.45),
        disabledForegroundColor: white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: primaryNavy),
    ),
    floatingActionButtonTheme: const FloatingActionButtonThemeData(
      backgroundColor: primaryNavy,
      foregroundColor: white,
    ),
    bottomNavigationBarTheme: const BottomNavigationBarThemeData(
      backgroundColor: white,
      selectedItemColor: secondarySkyBlue,
      unselectedItemColor: secondaryText,
      type: BottomNavigationBarType.fixed,
    ),
    dividerColor: border,
  );
}
