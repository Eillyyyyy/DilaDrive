/// Validates user-entered monetary values without relying on binary floating
/// point multiplication (for example, 1.15 * 100 is not always exactly 115).
final RegExp _moneyInputPattern = RegExp(r'^\d+(?:\.\d{1,2})?$');

bool isValidMoneyInput(String input) {
  final normalized = input.trim();
  if (!_moneyInputPattern.hasMatch(normalized)) return false;
  final amount = double.tryParse(normalized);
  return amount != null && amount.isFinite && amount >= 0.01;
}

bool hasAtMostTwoDecimalPlaces(double amount) =>
    amount.isFinite && amount >= 0.01 && _moneyInputPattern.hasMatch(amount.toString());
