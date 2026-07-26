import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../models/football_models.dart';
import '../../services/football_data_service.dart';
import '../../services/config_service.dart';
import '../../shared/widgets/glass_panel.dart';
import '../../models/live_stream.dart';
import '../../models/playlist_content_model.dart';
import '../../models/content_type.dart';
import '../../utils/navigate_by_content_type.dart';
import '../../services/app_state.dart';

class SportsHubScreen extends StatefulWidget {
  const SportsHubScreen({super.key});

  @override
  State<SportsHubScreen> createState() => _SportsHubScreenState();
}

class _SportsHubScreenState extends State<SportsHubScreen> {
  final FootballDataService _footballService = FootballDataService();
  bool _isLoading = true;
  List<FootballMatch> _allMatches = [];
  String _selectedSection = 'MATCHES';

  late Timer _clockTimer;
  DateTime _now = DateTime.now();

  @override
  void initState() {
    super.initState();
    _clockTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (mounted) setState(() => _now = DateTime.now());
    });
    _loadData();
  }

  @override
  void dispose() {
    _clockTimer.cancel();
    super.dispose();
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    try {
      final List<FootballMatch> matches;
      if (_selectedSection == 'UPCOMING') {
        matches = await _footballService.getUpcomingMatches();
      } else if (_selectedSection == 'TODAY') {
        matches = await _footballService.getTodayMatches();
      } else {
        matches = await _footballService.getCurrentAndNextMatches();
      }

      if (mounted) {
        setState(() {
          _allMatches = matches;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isLoading = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error loading sports data: $e')),
        );
      }
    }
  }

  List<FootballMatch> get _filteredMatches {
    final now = DateTime.now();
    switch (_selectedSection) {
      case 'LIVE':
        return _sortMatchList(_allMatches.where((m) => m.isLive).toList());
      case 'UPCOMING':
        return _sortMatchList(
          _allMatches
              .where((m) => !m.isFinished && m.utcDate.toLocal().isAfter(now))
              .toList(),
        );
      case 'TODAY':
        return _sortMatchList(
          _allMatches.where((m) => _isSameLocalDay(m.utcDate, now)).toList(),
        );
      case 'MATCHES':
      default:
        return _sortMatchList(
          _allMatches.where((m) {
            if (m.isLive) return true;
            if (m.isFinished) return false;
            final localDate = m.utcDate.toLocal();
            final tomorrow = now.add(const Duration(days: 1));
            final isTodayOrTomorrow =
                _isSameLocalDay(localDate, now) ||
                _isSameLocalDay(localDate, tomorrow);
            return isTodayOrTomorrow && localDate.isAfter(now);
          }).toList(),
        );
    }
  }

  List<FootballMatch> _sortMatchList(List<FootballMatch> matches) {
    matches.sort((a, b) {
      if (a.isLive != b.isLive) return a.isLive ? -1 : 1;
      return a.utcDate.compareTo(b.utcDate);
    });
    return matches;
  }

  bool _isSameLocalDay(DateTime a, DateTime b) {
    final left = a.toLocal();
    final right = b.toLocal();
    return left.year == right.year &&
        left.month == right.month &&
        left.day == right.day;
  }

  @override
  Widget build(BuildContext context) {
    final config = context.watch<ConfigService>().config;
    final homeBg = config.backgrounds.home;

    return Scaffold(
      backgroundColor: const Color(0xFF050812),
      body: Container(
        width: double.infinity,
        height: double.infinity,
        decoration: BoxDecoration(
          color: const Color(0xFF050812),
          image: DecorationImage(
            image: (homeBg.isNotEmpty)
                ? NetworkImage(homeBg)
                : const AssetImage('assets/images/background.png')
                      as ImageProvider,
            fit: BoxFit.cover,
          ),
        ),
        child: Container(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                const Color(0xFF050812).withValues(alpha: 0.2),
                const Color(0xFF050812).withValues(alpha: 0.6),
                const Color(0xFF050812).withValues(alpha: 0.9),
              ],
            ),
          ),
          child: SafeArea(
            child: Column(
              children: [
                _buildHeader(context),
                _buildSectionTabs(),
                Expanded(
                  child: _isLoading
                      ? const Center(
                          child: CircularProgressIndicator(
                            color: Color(0xFFC12CFF),
                          ),
                        )
                      : _buildMatchList(),
                ),
                _buildFooter(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 8),
      child: Row(
        children: [
          _HeaderIconButton(
            icon: Icons.arrow_back_rounded,
            onTap: () => Navigator.pop(context),
          ),
          const SizedBox(width: 16),
          Image.asset(
            'assets/images/App_Logo.png',
            height: 50,
            fit: BoxFit.contain,
          ),
          const Spacer(),
          Column(
            children: [
              Text(
                DateFormat('hh:mm a').format(_now),
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 20,
                  fontWeight: FontWeight.w900,
                ),
              ),
              Text(
                DateFormat('MMM d, yyyy').format(_now),
                style: const TextStyle(
                  color: Color(0xFFC12CFF),
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
          const Spacer(),
          _HeaderIconButton(
            icon: Icons.refresh_rounded,
            onTap: _loadData,
          ), // Change to refresh
          const SizedBox(width: 12),
          _HeaderIconButton(icon: Icons.more_vert_rounded, onTap: () {}),
        ],
      ),
    );
  }

  Widget _buildSectionTabs() {
    final sections = ['MATCHES', 'LIVE', 'UPCOMING'];
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: sections
            .map(
              (s) => _SectionTab(
                label: s,
                isSelected: _selectedSection == s,
                onTap: () {
                  setState(() => _selectedSection = s);
                  _loadData();
                },
              ),
            )
            .toList(),
      ),
    );
  }

  Widget _buildMatchList() {
    final matches = _filteredMatches;
    if (matches.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              _selectedSection == 'TODAY'
                  ? 'No matches today'
                  : _selectedSection == 'MATCHES'
                  ? 'No current or upcoming matches'
                  : 'No matches found for $_selectedSection',
              style: const TextStyle(color: Colors.white54, fontSize: 18),
            ),
            if (_selectedSection != 'UPCOMING') ...[
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: () {
                  setState(() => _selectedSection = 'UPCOMING');
                  _loadData();
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFFC12CFF),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: const Text(
                  'View upcoming matches',
                  style: TextStyle(color: Colors.white),
                ),
              ),
            ],
          ],
        ),
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.symmetric(horizontal: 40, vertical: 8),
      itemCount: matches.length,
      separatorBuilder: (_, _) => const SizedBox(height: 16),
      itemBuilder: (context, index) =>
          _MatchCard(match: matches[index], currentSection: _selectedSection),
    );
  }

  Widget _buildFooter() {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 24),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            'Powered by football-data.org',
            style: TextStyle(
              color: Colors.white.withValues(alpha: 0.3),
              fontSize: 10,
            ),
          ),
        ],
      ),
    );
  }
}

class _SectionTab extends StatefulWidget {
  final String label;
  final bool isSelected;
  final VoidCallback onTap;

  const _SectionTab({
    required this.label,
    required this.isSelected,
    required this.onTap,
  });

  @override
  State<_SectionTab> createState() => _SectionTabState();
}

class _SectionTabState extends State<_SectionTab> {
  bool _isFocused = false;

  @override
  Widget build(BuildContext context) {
    final active = _isFocused || widget.isSelected;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      child: FocusableActionDetector(
        onFocusChange: (v) => setState(() => _isFocused = v),
        shortcuts: const {
          SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
          SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
          SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
          SingleActivator(LogicalKeyboardKey.gameButtonA): ActivateIntent(),
        },
        actions: {
          ActivateIntent: CallbackAction<ActivateIntent>(
            onInvoke: (_) {
              widget.onTap();
              return null;
            },
          ),
        },
        child: InkWell(
          onTap: widget.onTap,
          borderRadius: BorderRadius.circular(20),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 10),
            decoration: BoxDecoration(
              color: active
                  ? const Color(0xFFC12CFF).withValues(alpha: 0.2)
                  : Colors.white.withValues(alpha: 0.05),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(
                color: active ? const Color(0xFFC12CFF) : Colors.white10,
                width: 1.5,
              ),
              boxShadow: _isFocused
                  ? [
                      BoxShadow(
                        color: const Color(0xFFC12CFF).withValues(alpha: 0.3),
                        blurRadius: 10,
                      ),
                    ]
                  : [],
            ),
            child: Text(
              widget.label,
              style: TextStyle(
                color: active ? Colors.white : Colors.white60,
                fontWeight: FontWeight.w900,
                fontSize: 14,
                letterSpacing: 1.2,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _MatchCard extends StatefulWidget {
  final FootballMatch match;
  final String currentSection;

  const _MatchCard({required this.match, required this.currentSection});

  @override
  State<_MatchCard> createState() => _MatchCardState();
}

class _MatchCardState extends State<_MatchCard> {
  bool _isFocused = false;
  List<ContentItem> _matchingChannels = [];
  bool _isCheckingChannels = true;

  @override
  void initState() {
    super.initState();
    _findIptvChannels();
  }

  Future<void> _findIptvChannels() async {
    final repo = AppState.xtreamCodeRepository;
    if (repo == null) {
      setState(() => _isCheckingChannels = false);
      return;
    }

    try {
      final matches = <ContentItem>[];

      // Match real sports/broadcaster channels. Avoid team-name search: it often
      // finds unrelated random channels.
      final competitionKeywords = _getCompetitionKeywords(
        widget.match.competitionName,
      );
      final compResults = <LiveStream>[];
      for (var kw in competitionKeywords) {
        final res = await repo.searchLiveStreams(kw, limit: 8);
        compResults.addAll(res);
      }

      compResults.sort((a, b) {
        final aScore = _channelScore(a.name);
        final bScore = _channelScore(b.name);
        return bScore.compareTo(aScore);
      });

      final seenIds = <String>{};
      for (var ls in compResults) {
        if (!seenIds.contains(ls.streamId)) {
          matches.add(
            ContentItem(
              ls.streamId,
              ls.name,
              ls.streamIcon,
              ContentType.liveStream,
              liveStream: ls,
            ),
          );
          seenIds.add(ls.streamId);
        }
      }

      if (mounted) {
        setState(() {
          _matchingChannels = matches;
          _isCheckingChannels = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _isCheckingChannels = false);
    }
  }

  List<String> _getCompetitionKeywords(String name) {
    final value = name.toLowerCase();
    if (value.contains('world cup') || value.contains('fifa')) {
      return [
        'FIFA EVENTS',
        'WORLD CUP',
        'BBC ONE',
        'BBC 1',
        'ITV1',
        'ITV',
        'CHANNEL 4',
      ];
    }
    if (value.contains('premier league')) {
      return [
        'Sky Sports Main Event',
        'Sky Sports Premier League',
        'TNT Sports 1',
        'TNT Sports 2',
      ];
    }
    if (value.contains('champions league') || value.contains('europa')) {
      return ['TNT Sports 1', 'TNT Sports 2', 'TNT Sports 3', 'TNT Sports'];
    }
    if (value.contains('fa cup') || value.contains('efl')) {
      return [
        'BBC ONE',
        'ITV1',
        'Sky Sports Football',
        'Sky Sports Main Event',
      ];
    }
    if (value.contains('la liga')) return ['LaLiga TV', 'Premier Sports'];
    if (value.contains('bundesliga')) return ['Sky Sports Football'];
    if (value.contains('serie a')) return ['TNT Sports', 'Premier Sports'];
    return [
      'Sky Sports Main Event',
      'Sky Sports Football',
      'Sky Sports Premier League',
      'TNT Sports',
      'FIFA EVENTS',
      'BBC ONE',
      'ITV1',
    ];
  }

  int _channelScore(String name) {
    final n = name.toLowerCase();
    var score = 0;
    if (n.contains('main event')) score += 100;
    if (n.contains('fifa')) score += 90;
    if (n.contains('world cup')) score += 80;
    if (n.contains('premier league')) score += 70;
    if (n.contains('football')) score += 60;
    if (n.contains('tnt sports 1')) score += 55;
    if (n.contains('sky sports')) score += 50;
    if (n.contains('bbc one') || n.contains('bbc 1')) score += 45;
    if (n.contains('itv1') || n.contains('itv')) score += 40;
    if (n.contains('hd')) score += 10;
    if (n.contains('vm')) score -= 5;
    return score;
  }

  @override
  Widget build(BuildContext context) {
    return FocusableActionDetector(
      onFocusChange: (v) => setState(() => _isFocused = v),
      child: AnimatedScale(
        scale: _isFocused ? 1.02 : 1.0,
        duration: const Duration(milliseconds: 200),
        child: GlassPanel(
          padding: const EdgeInsets.all(24),
          border: Border.all(
            color: _isFocused ? const Color(0xFFC12CFF) : Colors.white10,
            width: _isFocused ? 2 : 1,
          ),
          child: Row(
            children: [
              // Competition Info
              Expanded(
                flex: 2,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.match.competitionName.toUpperCase(),
                      style: const TextStyle(
                        color: Color(0xFF00B7FF),
                        fontSize: 10,
                        fontWeight: FontWeight.w900,
                        letterSpacing: 1,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _statusLabel(widget.match),
                      style: TextStyle(
                        color: widget.match.isLive
                            ? Colors.redAccent
                            : Colors.white38,
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ),

              // Teams & Score
              Expanded(
                flex: 5,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Expanded(
                      child: Text(
                        widget.match.homeTeam.name,
                        textAlign: TextAlign.right,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 20),
                      child: widget.match.isLive || widget.match.isFinished
                          ? Text(
                              '${widget.match.score.homeScore ?? 0} - ${widget.match.score.awayScore ?? 0}',
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 24,
                                fontWeight: FontWeight.w900,
                              ),
                            )
                          : Text(
                              _kickoffLabel(widget.match),
                              style: const TextStyle(
                                color: Colors.white70,
                                fontSize: 18,
                                fontWeight: FontWeight.w900,
                              ),
                            ),
                    ),
                    Expanded(
                      child: Text(
                        widget.match.awayTeam.name,
                        textAlign: TextAlign.left,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              // Action Button
              Expanded(
                flex: 2,
                child: Align(
                  alignment: Alignment.centerRight,
                  child: widget.match.isFinished
                      ? _WatchButton(
                          label: 'STATS',
                          onTap: () => _showStats(context, widget.match),
                        )
                      : _isCheckingChannels
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : _matchingChannels.isNotEmpty
                      ? _WatchButton(
                          label: 'WATCH',
                          onTap: () => navigateByContentType(
                            context,
                            _matchingChannels.first,
                          ),
                        )
                      : const Text(
                          'NO COVERAGE',
                          style: TextStyle(
                            color: Colors.white24,
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _showStats(BuildContext context, FootballMatch match) async {
    final localTime = DateFormat(
      'EEE d MMM yyyy HH:mm',
    ).format(match.utcDate.toLocal());
    final lastUpdated = match.lastUpdated == null
        ? 'Unknown'
        : DateFormat(
            'EEE d MMM yyyy HH:mm',
          ).format(match.lastUpdated!.toLocal());
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Match Stats'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _statRow('Competition', match.competitionName),
              _statRow('Area', match.areaName),
              _statRow('Stage', match.stage ?? 'Unknown'),
              _statRow('Group', match.group ?? 'Unknown'),
              _statRow('Matchday', match.matchday?.toString() ?? 'Unknown'),
              const Divider(),
              _statRow(
                'Full time',
                '${match.homeTeam.name} ${match.score.homeScore ?? 0} - ${match.score.awayScore ?? 0} ${match.awayTeam.name}',
              ),
              _statRow(
                'Half time',
                '${match.halfTimeHomeScore ?? '-'} - ${match.halfTimeAwayScore ?? '-'}',
              ),
              _statRow('Winner', _winnerLabel(match)),
              _statRow('Duration', match.duration ?? 'Unknown'),
              const Divider(),
              _statRow('Status', match.status),
              _statRow('Kick off', localTime),
              _statRow('Last updated', lastUpdated),
              _statRow(
                'Referee',
                match.refereeName == null
                    ? 'Unknown'
                    : '${match.refereeName} (${match.refereeNationality ?? 'Unknown'})',
              ),
              const SizedBox(height: 8),
              const Text(
                'Free API does not include possession, shots, corners, cards, or lineups.',
              ),
            ],
          ),
        ),
        actions: [
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('CLOSE'),
          ),
        ],
      ),
    );
  }

  String _statusLabel(FootballMatch match) {
    if (match.isLive) return 'LIVE NOW';
    if (match.isFinished) return 'FINISHED';
    if (match.status == 'TIMED' || match.status == 'SCHEDULED') {
      return 'UPCOMING';
    }
    return match.status;
  }

  String _kickoffLabel(FootballMatch match) {
    final local = match.utcDate.toLocal();
    final now = DateTime.now();
    final tomorrow = now.add(const Duration(days: 1));
    final time = DateFormat('HH:mm').format(local);

    if (_sameDay(local, now)) return 'Today $time';
    if (_sameDay(local, tomorrow)) return 'Tomorrow $time';
    return DateFormat('EEE d MMM HH:mm').format(local);
  }

  bool _sameDay(DateTime a, DateTime b) {
    return a.year == b.year && a.month == b.month && a.day == b.day;
  }

  Widget _statRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Text('$label: $value'),
    );
  }

  String _winnerLabel(FootballMatch match) {
    switch (match.winner) {
      case 'HOME_TEAM':
        return match.homeTeam.name;
      case 'AWAY_TEAM':
        return match.awayTeam.name;
      case 'DRAW':
        return 'Draw';
      default:
        return 'Unknown';
    }
  }
}

class _WatchButton extends StatefulWidget {
  final VoidCallback onTap;
  final String label;
  const _WatchButton({required this.onTap, required this.label});

  @override
  State<_WatchButton> createState() => _WatchButtonState();
}

class _WatchButtonState extends State<_WatchButton> {
  bool _isFocused = false;

  @override
  Widget build(BuildContext context) {
    return FocusableActionDetector(
      onFocusChange: (v) => setState(() => _isFocused = v),
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.gameButtonA): ActivateIntent(),
      },
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) {
            widget.onTap();
            return null;
          },
        ),
      },
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(12),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [Color(0xFF6A11CB), Color(0xFF2575FC)],
            ),
            borderRadius: BorderRadius.circular(12),
            boxShadow: _isFocused
                ? [
                    BoxShadow(
                      color: const Color(0xFF2575FC).withValues(alpha: 0.4),
                      blurRadius: 15,
                    ),
                  ]
                : [],
          ),
          child: Text(
            widget.label,
            style: const TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.w900,
              fontSize: 14,
            ),
          ),
        ),
      ),
    );
  }
}

class _HeaderIconButton extends StatefulWidget {
  final IconData icon;
  final VoidCallback onTap;
  const _HeaderIconButton({required this.icon, required this.onTap});

  @override
  State<_HeaderIconButton> createState() => _HeaderIconButtonState();
}

class _HeaderIconButtonState extends State<_HeaderIconButton> {
  bool _isFocused = false;
  @override
  Widget build(BuildContext context) {
    return FocusableActionDetector(
      onFocusChange: (v) => setState(() => _isFocused = v),
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.select): ActivateIntent(),
        SingleActivator(LogicalKeyboardKey.gameButtonA): ActivateIntent(),
      },
      actions: {
        ActivateIntent: CallbackAction<ActivateIntent>(
          onInvoke: (_) {
            widget.onTap();
            return null;
          },
        ),
      },
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: BorderRadius.circular(12),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: _isFocused
                ? Colors.white.withValues(alpha: 0.2)
                : Colors.white.withValues(alpha: 0.05),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: _isFocused ? const Color(0xFFC12CFF) : Colors.white10,
            ),
          ),
          child: Icon(
            widget.icon,
            color: _isFocused ? Colors.white : Colors.white70,
            size: 22,
          ),
        ),
      ),
    );
  }
}
