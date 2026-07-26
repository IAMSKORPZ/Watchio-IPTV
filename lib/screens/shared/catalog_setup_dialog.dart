import 'package:flutter/material.dart';

import '../../models/category_view_model.dart';
import '../../repositories/iptv_repository.dart';

class CatalogSetupSettings {
  final Set<String> hiddenCategoryIds;
  final bool showPoster;
  final bool showTitle;
  final bool showRating;
  final String posterSize;
  final String sortOrder;

  const CatalogSetupSettings({
    required this.hiddenCategoryIds,
    required this.showPoster,
    required this.showTitle,
    required this.showRating,
    required this.posterSize,
    required this.sortOrder,
  });

  CatalogSetupSettings copyWith({
    Set<String>? hiddenCategoryIds,
    bool? showPoster,
    bool? showTitle,
    bool? showRating,
    String? posterSize,
    String? sortOrder,
  }) {
    return CatalogSetupSettings(
      hiddenCategoryIds: hiddenCategoryIds ?? this.hiddenCategoryIds,
      showPoster: showPoster ?? this.showPoster,
      showTitle: showTitle ?? this.showTitle,
      showRating: showRating ?? this.showRating,
      posterSize: posterSize ?? this.posterSize,
      sortOrder: sortOrder ?? this.sortOrder,
    );
  }
}

Future<CatalogSetupSettings?> showCatalogSetupDialog({
  required BuildContext context,
  required String title,
  required List<CategoryViewModel> categories,
  required CatalogSetupSettings initialSettings,
}) {
  final hidden = Set<String>.from(initialSettings.hiddenCategoryIds);
  var showPoster = initialSettings.showPoster;
  var showTitle = initialSettings.showTitle;
  var showRating = initialSettings.showRating;
  var posterSize = initialSettings.posterSize;
  var sortOrder = initialSettings.sortOrder;
  final hideableCategories = categories
      .where((category) => _canHideCategory(category.category.categoryId))
      .toList();

  return showDialog<CatalogSetupSettings>(
    context: context,
    builder: (dialogContext) => StatefulBuilder(
      builder: (context, setDialogState) => AlertDialog(
        insetPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        titlePadding: const EdgeInsets.fromLTRB(20, 16, 20, 8),
        contentPadding: const EdgeInsets.symmetric(horizontal: 12),
        actionsPadding: const EdgeInsets.fromLTRB(16, 6, 16, 12),
        backgroundColor: const Color(0xFF111525),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: const BorderSide(color: Color(0xFFC12CFF), width: 1.4),
        ),
        title: Row(
          children: [
            const Icon(Icons.tune_rounded, color: Color(0xFFC12CFF)),
            const SizedBox(width: 10),
            Text(
              '$title Setup',
              style: const TextStyle(color: Colors.white, fontSize: 20),
            ),
          ],
        ),
        content: SizedBox(
          width: MediaQuery.sizeOf(context).width * 0.62,
          height: MediaQuery.sizeOf(context).height * 0.68,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      _setupSwitchTile(
                        title: 'Show posters',
                        value: showPoster,
                        onChanged: (value) =>
                            setDialogState(() => showPoster = value),
                      ),
                      _setupSwitchTile(
                        title: 'Show titles',
                        value: showTitle,
                        onChanged: (value) =>
                            setDialogState(() => showTitle = value),
                      ),
                      _setupSwitchTile(
                        title: 'Show ratings',
                        value: showRating,
                        onChanged: (value) =>
                            setDialogState(() => showRating = value),
                      ),
                      const Divider(color: Colors.white12, height: 18),
                      _setupChoiceGroup(
                        title: 'Poster size',
                        value: posterSize,
                        options: const [
                          ('compact', 'Compact'),
                          ('normal', 'Normal'),
                          ('large', 'Large'),
                        ],
                        onChanged: (value) =>
                            setDialogState(() => posterSize = value),
                      ),
                      const Divider(color: Colors.white12, height: 18),
                      _setupChoiceGroup(
                        title: 'Sort order',
                        value: sortOrder,
                        options: const [
                          ('server', 'Provider order'),
                          ('recent', 'Recently added'),
                          ('az', 'A-Z'),
                          ('za', 'Z-A'),
                          ('rating', 'Rating high first'),
                          ('year', 'Year newest first'),
                        ],
                        onChanged: (value) =>
                            setDialogState(() => sortOrder = value),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  children: [
                    const Align(
                      alignment: Alignment.centerLeft,
                      child: Padding(
                        padding: EdgeInsets.fromLTRB(8, 0, 8, 8),
                        child: Text(
                          'Visible categories',
                          style: TextStyle(
                            color: Colors.white70,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                      ),
                    ),
                    Expanded(
                      child: ListView.builder(
                        itemCount: hideableCategories.length,
                        itemBuilder: (context, index) {
                          final category = hideableCategories[index].category;
                          final visible = !hidden.contains(category.categoryId);
                          return CheckboxListTile(
                            dense: true,
                            value: visible,
                            activeColor: const Color(0xFFC12CFF),
                            checkColor: Colors.white,
                            title: Text(
                              category.categoryName,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(color: Colors.white),
                            ),
                            onChanged: (value) {
                              setDialogState(() {
                                if (value == true) {
                                  hidden.remove(category.categoryId);
                                } else {
                                  hidden.add(category.categoryId);
                                }
                              });
                            },
                          );
                        },
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('CANCEL'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(
              dialogContext,
              CatalogSetupSettings(
                hiddenCategoryIds: hidden,
                showPoster: showPoster,
                showTitle: showTitle,
                showRating: showRating,
                posterSize: posterSize,
                sortOrder: sortOrder,
              ),
            ),
            child: const Text('SAVE'),
          ),
        ],
      ),
    ),
  );
}

Widget _setupSwitchTile({
  required String title,
  required bool value,
  required ValueChanged<bool> onChanged,
}) {
  return SwitchListTile(
    dense: true,
    value: value,
    activeThumbColor: const Color(0xFFC12CFF),
    title: Text(title, style: const TextStyle(color: Colors.white)),
    onChanged: onChanged,
  );
}

Widget _setupChoiceGroup({
  required String title,
  required String value,
  required List<(String, String)> options,
  required ValueChanged<String> onChanged,
}) {
  return Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      Align(
        alignment: Alignment.centerLeft,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          child: Text(
            title,
            style: const TextStyle(
              color: Colors.white70,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
      ),
      for (final option in options)
        ListTile(
          dense: true,
          visualDensity: const VisualDensity(vertical: -2),
          leading: Icon(
            value == option.$1
                ? Icons.radio_button_checked_rounded
                : Icons.radio_button_off_rounded,
            color: value == option.$1
                ? const Color(0xFFC12CFF)
                : Colors.white54,
          ),
          title: Text(option.$2, style: const TextStyle(color: Colors.white)),
          onTap: () => onChanged(option.$1),
        ),
    ],
  );
}

bool _canHideCategory(String categoryId) {
  return categoryId != IptvRepository.virtualAll &&
      categoryId != IptvRepository.virtualFavorites &&
      categoryId != IptvRepository.virtualHistory;
}
