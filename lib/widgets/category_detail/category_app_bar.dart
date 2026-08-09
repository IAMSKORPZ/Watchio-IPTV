import 'package:watchio/l10n/localization_extension.dart';
import 'package:flutter/material.dart';

class CategoryAppBar extends StatelessWidget {
  final String title;
  final bool isSearching;
  final TextEditingController searchController;
  final VoidCallback onSearchStart;
  final VoidCallback onSearchStop;
  final ValueChanged<String> onSearchChanged;

  const CategoryAppBar({
    super.key,
    required this.title,
    required this.isSearching,
    required this.searchController,
    required this.onSearchStart,
    required this.onSearchStop,
    required this.onSearchChanged,
  });

  @override
  Widget build(BuildContext context) {
    return SliverAppBar(
      floating: true,
      snap: true,
      title: isSearching
          ? _buildSearchField(context)
          : SelectableText(
              title,
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
      actions: [
        IconButton(
          icon: Icon(isSearching ? Icons.clear : Icons.search),
          onPressed: isSearching ? onSearchStop : onSearchStart,
        ),
      ],
    );
  }

  Widget _buildSearchField(BuildContext context) {
    return TextField(
      controller: searchController,
      decoration: InputDecoration(
        hintText: context.loc.search,
        border: InputBorder.none,
      ),
      autofocus: true,
      onChanged: onSearchChanged,
    );
  }
}

class CategoryHeader extends StatelessWidget {
  final String title;
  final bool isSearching;
  final TextEditingController searchController;
  final VoidCallback onSearchStart;
  final VoidCallback onSearchStop;
  final ValueChanged<String> onSearchChanged;

  const CategoryHeader({
    super.key,
    required this.title,
    required this.isSearching,
    required this.searchController,
    required this.onSearchStart,
    required this.onSearchStop,
    required this.onSearchChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Row(
        children: [
          IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () => Navigator.maybePop(context),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: isSearching
                ? TextField(
                    controller: searchController,
                    decoration: InputDecoration(
                      hintText: context.loc.search,
                      border: const OutlineInputBorder(),
                      isDense: true,
                    ),
                    autofocus: true,
                    onChanged: onSearchChanged,
                  )
                : Text(
                    title,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.w800,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
          ),
          const SizedBox(width: 8),
          IconButton(
            icon: Icon(isSearching ? Icons.clear : Icons.search),
            onPressed: isSearching ? onSearchStop : onSearchStart,
          ),
        ],
      ),
    );
  }
}
