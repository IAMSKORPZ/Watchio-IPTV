import 'package:watchio/l10n/localization_extension.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:watchio/models/category_view_model.dart';
import 'package:watchio/models/category_type.dart';
import 'package:watchio/models/playlist_content_model.dart';
import 'package:watchio/utils/navigate_by_content_type.dart';
import 'package:watchio/shared/widgets/glass_panel.dart';
import 'package:watchio/shared/widgets/sidebar_item.dart';
import 'package:watchio/widgets/tv_focusable.dart';
import 'package:watchio/utils/firestick_performance.dart';
import '../controllers/category_detail_controller.dart';
import '../widgets/category_detail/category_app_bar.dart';
import '../widgets/category_detail/content_states.dart';
import '../widgets/category_detail/content_grid.dart';

class CategoryDetailScreen extends StatelessWidget {
  final CategoryViewModel category;

  const CategoryDetailScreen({super.key, required this.category});

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => CategoryDetailController(category),
      child: const _CategoryDetailView(),
    );
  }
}

class _CategoryDetailView extends StatefulWidget {
  const _CategoryDetailView();

  @override
  State<_CategoryDetailView> createState() => _CategoryDetailViewState();
}

class _CategoryDetailViewState extends State<_CategoryDetailView> {
  final TextEditingController _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<CategoryDetailController>(
      builder: (context, controller, child) {
        return Scaffold(
          body: SafeArea(
            child: Column(
              children: [
                CategoryHeader(
                  title: controller.category.category.categoryName,
                  isSearching: controller.isSearching,
                  searchController: _searchController,
                  onSearchStart: controller.startSearch,
                  onSearchStop: () {
                    controller.stopSearch();
                    _searchController.clear();
                  },
                  onSearchChanged: controller.searchContent,
                ),
                Expanded(child: _buildBody(controller)),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildBody(CategoryDetailController controller) {
    if (controller.isLoading) return const LoadingState();
    if (controller.errorMessage != null) {
      return ErrorState(
        message: controller.errorMessage!,
        onRetry: controller.loadContent,
      );
    }
    if (controller.isEmpty) return const EmptyState();
    if (controller.category.category.type == CategoryType.live) {
      return _buildLiveBody(controller);
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 900;
        if (wide) {
          return Row(
            children: [
              SizedBox(
                width: 240,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 16, 0, 16),
                  child: _buildSidebar(controller),
                ),
              ),
              Expanded(
                child: ContentGrid(
                  items: controller.displayItems,
                  onItemTap: (item) => navigateByContentType(context, item),
                  onLoadMore: controller.loadNextPage,
                  isLoadingMore: controller.isLoadingMore,
                ),
              ),
            ],
          );
        }
        return Column(
          children: [
            if (controller.genres.isNotEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(
                  vertical: 8.0,
                  horizontal: 4.0,
                ),
                child: _buildGenreSelector(controller),
              ),
            Expanded(
              child: ContentGrid(
                items: controller.displayItems,
                onItemTap: (item) => navigateByContentType(context, item),
                onLoadMore: controller.loadNextPage,
                isLoadingMore: controller.isLoadingMore,
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _buildLiveBody(CategoryDetailController controller) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 980;
        if (!wide) {
          return Column(
            children: [
              if (controller.genres.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(
                    vertical: 8.0,
                    horizontal: 4.0,
                  ),
                  child: _buildGenreSelector(controller),
                ),
              Expanded(
                child: _LiveChannelList(
                  items: controller.displayItems,
                  onItemTap: (item) => navigateByContentType(context, item),
                  onLoadMore: controller.loadNextPage,
                  isLoadingMore: controller.isLoadingMore,
                ),
              ),
            ],
          );
        }

        final selected = controller.displayItems.isNotEmpty
            ? controller.displayItems.first
            : null;
        return Row(
          children: [
            SizedBox(
              width: 240,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 0, 16),
                child: _buildSidebar(controller),
              ),
            ),
            Expanded(
              child: _LiveChannelList(
                items: controller.displayItems,
                onItemTap: (item) => navigateByContentType(context, item),
                onLoadMore: controller.loadNextPage,
                isLoadingMore: controller.isLoadingMore,
              ),
            ),
            SizedBox(
              width: 300,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(0, 16, 16, 16),
                child: _LiveEpgPanel(item: selected),
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _buildSidebar(CategoryDetailController controller) {
    final genres = controller.genres;
    return GlassPanel(
      padding: const EdgeInsets.all(10),
      child: ListView(
        children: [
          SidebarItem(
            icon: Icons.grid_view,
            label: context.loc.all,
            selected: controller.selectedGenre == null,
            onTap: () => controller.filterByGenre(null),
          ),
          const SizedBox(height: 8),
          ...genres.map(
            (genre) => SidebarItem(
              icon: Icons.label_outline,
              label: _capitalizeGenre(genre),
              selected: controller.selectedGenre == genre,
              onTap: () => controller.filterByGenre(genre),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildGenreSelector(CategoryDetailController controller) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          ChoiceChip(
            label: Text(context.loc.all),
            selected: controller.selectedGenre == null,
            onSelected: (_) => controller.filterByGenre(null),
          ),
          ...controller.genres.map(
            (g) => Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: ChoiceChip(
                label: Text(_capitalizeGenre(g)),
                selected: controller.selectedGenre == g,
                onSelected: (_) => controller.filterByGenre(g),
              ),
            ),
          ),
        ],
      ),
    );
  }

  String _capitalizeGenre(String genre) {
    if (genre.isEmpty) return genre;
    return genre
        .split(' ')
        .map((word) {
          if (word.isEmpty) return word;
          final first = word.characters.first.toUpperCase();
          final rest = word.characters.skip(1).join();
          return '$first$rest';
        })
        .join(' ');
  }
}

class _LiveChannelList extends StatelessWidget {
  final List<ContentItem> items;
  final Function(ContentItem) onItemTap;
  final VoidCallback? onLoadMore;
  final bool isLoadingMore;

  const _LiveChannelList({
    required this.items,
    required this.onItemTap,
    this.onLoadMore,
    this.isLoadingMore = false,
  });

  @override
  Widget build(BuildContext context) {
    final totalItems = items.length + (isLoadingMore ? 1 : 0);
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification.metrics.extentAfter < 800) {
          onLoadMore?.call();
        }
        return false;
      },
      child: ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: totalItems,
        separatorBuilder: (_, _) => const SizedBox(height: 8),
        itemBuilder: (context, index) {
          if (index >= items.length) {
            return const Center(child: CircularProgressIndicator());
          }
          final item = items[index];
          return TvFocusable(
            onPressed: () => onItemTap(item),
            borderRadius: BorderRadius.circular(12),
            child: GlassPanel(
              padding: const EdgeInsets.all(10),
              child: Row(
                children: [
                  _ChannelLogo(url: item.imagePath),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          item.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          item.liveStream?.epgChannelId.isNotEmpty == true
                              ? item.liveStream!.epgChannelId
                              : 'Live Channel',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                  const Icon(Icons.play_circle_outline),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _LiveEpgPanel extends StatelessWidget {
  final ContentItem? item;

  const _LiveEpgPanel({required this.item});

  @override
  Widget build(BuildContext context) {
    return GlassPanel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'EPG',
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 16),
          if (item == null)
            Text(context.loc.not_found_in_category)
          else ...[
            Center(child: _ChannelLogo(url: item!.imagePath, size: 86)),
            const SizedBox(height: 16),
            Text(
              item!.name,
              style: Theme.of(
                context,
              ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 10),
            _EpgRow(label: 'Channel ID', value: item!.id),
            _EpgRow(
              label: 'EPG ID',
              value: item!.liveStream?.epgChannelId ?? '',
            ),
            const SizedBox(height: 20),
            const Divider(),
            const SizedBox(height: 10),
            const Text(
              'Now Playing',
              style: TextStyle(fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 6),
            const Text('Schedule unavailable'),
          ],
        ],
      ),
    );
  }
}

class _EpgRow extends StatelessWidget {
  final String label;
  final String value;

  const _EpgRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    if (value.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 84,
            child: Text(label, style: const TextStyle(color: Colors.white60)),
          ),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }
}

class _ChannelLogo extends StatelessWidget {
  final String url;
  final double size;

  const _ChannelLogo({required this.url, this.size = 48});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(8),
        ),
        child: url.isEmpty
            ? const Icon(Icons.live_tv)
            : ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: Image.network(
                  url,
                  fit: BoxFit.contain,
                  cacheWidth: firestickPerformanceMode
                      ? (size * 2).round()
                      : null,
                  cacheHeight: firestickPerformanceMode
                      ? (size * 2).round()
                      : null,
                  errorBuilder: (_, _, _) => const Icon(Icons.live_tv),
                ),
              ),
      ),
    );
  }
}
