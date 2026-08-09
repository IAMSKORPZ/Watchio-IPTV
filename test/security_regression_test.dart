import 'package:another_iptv_player/models/content_type.dart';
import 'package:another_iptv_player/models/playlist_content_model.dart';
import 'package:another_iptv_player/models/playlist_model.dart';
import 'package:another_iptv_player/models/provider_model.dart';
import 'package:another_iptv_player/services/app_state.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  tearDown(() {
    AppState.currentPlaylist = null;
  });

  test('provider JSON never writes password', () {
    final now = DateTime(2026);
    final provider = IptvProvider(
      id: 'provider-1',
      type: IptvProviderType.xtreamCodes,
      name: 'Provider',
      createdAt: now,
      updatedAt: now,
      serverUrl: 'https://example.com',
      username: 'user',
      password: 'secret',
    );

    expect(provider.toJson().containsKey('password'), isFalse);
  });

  test('playlist JSON never writes password', () {
    final playlist = Playlist(
      id: 'playlist-1',
      name: 'Playlist',
      type: PlaylistType.xtream,
      url: 'https://example.com/player_api.php',
      username: 'user',
      password: 'secret',
      createdAt: DateTime(2026),
    );

    expect(playlist.toJson().containsKey('password'), isFalse);
  });

  test('media URL debug log does not include credentials', () {
    final messages = <String>[];
    final previousDebugPrint = debugPrint;
    debugPrint = (String? message, {int? wrapWidth}) {
      if (message != null) messages.add(message);
    };

    try {
      AppState.currentPlaylist = Playlist(
        id: 'playlist-1',
        name: 'Playlist',
        type: PlaylistType.xtream,
        url: 'https://example.com/player_api.php',
        username: 'user',
        password: 'secret',
        createdAt: DateTime(2026),
      );

      final item = ContentItem('123', 'Channel', '', ContentType.liveStream);

      expect(item.url, 'https://example.com/live/user/secret/123.ts');
      expect(messages.join('\n'), isNot(contains('user')));
      expect(messages.join('\n'), isNot(contains('secret')));
      expect(messages.join('\n'), isNot(contains('/live/')));
    } finally {
      debugPrint = previousDebugPrint;
    }
  });
}
