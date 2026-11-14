import 'package:flutter/material.dart';

/// 简约风格配色：白底、浅灰分层、单一主色，少量语义色。
class AppColors {
  static const primary = Color(0xFF2F6BFF);
  static const primarySoft = Color(0xFFEDF3FF);
  static const bg = Color(0xFFF6F7F9);
  static const surface = Colors.white;
  static const text = Color(0xFF1B1F26);
  static const sub = Color(0xFF5F6670);
  static const muted = Color(0xFF9AA1AB);
  static const border = Color(0xFFEEF0F3);
  static const success = Color(0xFF10A56A);
  static const successSoft = Color(0xFFE8F7F0);
  static const warning = Color(0xFFF08A24);
  static const warningSoft = Color(0xFFFEF3E7);
  static const danger = Color(0xFFE0483E);
  static const dangerSoft = Color(0xFFFDEDEC);
  static const info = Color(0xFF2F6BFF);
  static const infoSoft = Color(0xFFEDF3FF);
  static const purple = Color(0xFF7A5AF8);
  static const purpleSoft = Color(0xFFF2EFFF);
  static const teal = Color(0xFF0E9384);
  static const tealSoft = Color(0xFFE6F5F3);
}

ThemeData buildAppTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: AppColors.primary,
    brightness: Brightness.light,
  ).copyWith(
    primary: AppColors.primary,
    surface: AppColors.surface,
    onSurface: AppColors.text,
    error: AppColors.danger,
  );

  final base = ThemeData(useMaterial3: true, colorScheme: scheme);

  return base.copyWith(
    scaffoldBackgroundColor: AppColors.bg,
    splashFactory: InkSparkle.splashFactory,
    dividerColor: AppColors.border,
    textTheme: base.textTheme.apply(
      bodyColor: AppColors.text,
      displayColor: AppColors.text,
      fontSizeFactor: 1.0,
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: AppColors.bg,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: false,
      titleTextStyle: TextStyle(
        color: AppColors.text,
        fontSize: 17,
        fontWeight: FontWeight.w600,
      ),
      iconTheme: IconThemeData(color: AppColors.text, size: 22),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      height: 64,
      indicatorColor: AppColors.primarySoft,
      elevation: 0,
      labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
      labelTextStyle: WidgetStateProperty.resolveWith((states) {
        final selected = states.contains(WidgetState.selected);
        return TextStyle(
          fontSize: 11,
          height: 1.2,
          fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
          color: selected ? AppColors.primary : AppColors.muted,
        );
      }),
      iconTheme: WidgetStateProperty.resolveWith((states) {
        final selected = states.contains(WidgetState.selected);
        return IconThemeData(
          size: 22,
          color: selected ? AppColors.primary : AppColors.muted,
        );
      }),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      isDense: true,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      hintStyle: const TextStyle(color: AppColors.muted, fontSize: 14),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: AppColors.border),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: AppColors.border),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: AppColors.primary, width: 1.2),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: AppColors.primary,
        foregroundColor: Colors.white,
        minimumSize: const Size(0, 48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: AppColors.primary,
        textStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: AppColors.text,
        minimumSize: const Size(0, 44),
        side: const BorderSide(color: AppColors.border),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
    chipTheme: base.chipTheme.copyWith(
      backgroundColor: Colors.white,
      selectedColor: AppColors.primarySoft,
      side: const BorderSide(color: AppColors.border),
      labelStyle: const TextStyle(fontSize: 13, color: AppColors.sub),
      secondaryLabelStyle: const TextStyle(fontSize: 13, color: AppColors.primary, fontWeight: FontWeight.w600),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      showCheckmark: false,
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: AppColors.text,
      contentTextStyle: const TextStyle(color: Colors.white, fontSize: 13),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ),
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
    ),
    progressIndicatorTheme: const ProgressIndicatorThemeData(
      color: AppColors.primary,
      linearTrackColor: AppColors.border,
    ),
  );
}

/// 业务状态 → 语义色映射。
({Color color, Color background}) statusColors(String status) {
  if (status.contains('在读') || status.contains('已上') || status.contains('已发布') || status.contains('出勤') || status.contains('正常') || status.contains('已完成') || status.contains('启用') || status.contains('已开班')) {
    return (color: AppColors.success, background: AppColors.successSoft);
  }
  if (status.contains('停课') || status.contains('待') || status.contains('草稿') || status.contains('招生') || status.contains('请假') || status.contains('晚到')) {
    return (color: AppColors.warning, background: AppColors.warningSoft);
  }
  if (status.contains('结课') || status.contains('取消') || status.contains('作废') || status.contains('退') || status.contains('已结班') || status.contains('缺勤') || status.contains('欠')) {
    return (color: AppColors.danger, background: AppColors.dangerSoft);
  }
  if (status.contains('休课')) {
    return (color: AppColors.purple, background: AppColors.purpleSoft);
  }
  return (color: AppColors.sub, background: Color(0xFFF2F3F5));
}
