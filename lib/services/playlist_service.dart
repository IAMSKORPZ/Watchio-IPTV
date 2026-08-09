import '../models/playlist_model.dart';
import 'database_service.dart';
import 'secure_storage_service.dart';

class PlaylistService {
  static Future<void> savePlaylist(Playlist playlist) async {
    await SecureStorageService.instance.saveProviderPassword(
      playlist.id,
      playlist.password,
    );
    await SecureStorageService.instance.saveProviderSecret(
      playlist.id,
      'username',
      playlist.username,
    );
    await DatabaseService.savePlaylist(_withoutSecret(playlist));
  }

  static Future<List<Playlist>> getPlaylists() async {
    return _hydrateAll(await DatabaseService.getPlaylists());
  }

  static Future<void> deletePlaylist(String id) async {
    await SecureStorageService.instance.deleteProviderPassword(id);
    await SecureStorageService.instance.deleteProviderSecret(id, 'username');
    await SecureStorageService.instance.deleteProviderSecret(id, 'stalker_mac');
    await SecureStorageService.instance.deleteProviderSecret(
      id,
      'stalker_token',
    );
    await DatabaseService.deletePlaylist(id);
  }

  static Future<void> updatePlaylist(Playlist playlist) async {
    await SecureStorageService.instance.saveProviderPassword(
      playlist.id,
      playlist.password,
    );
    await SecureStorageService.instance.saveProviderSecret(
      playlist.id,
      'username',
      playlist.username,
    );
    await DatabaseService.updatePlaylist(_withoutSecret(playlist));
  }

  static Future<Playlist?> getPlaylistById(String id) async {
    final playlist = await DatabaseService.getPlaylistById(id);
    return playlist == null ? null : _hydrate(playlist);
  }

  static Future<List<Playlist>> getXStreamPlaylists() async {
    return _hydrateAll(
      await DatabaseService.getPlaylistsByType(PlaylistType.xtream),
    );
  }

  static Future<List<Playlist>> getM3UPlaylists() async {
    return _hydrateAll(
      await DatabaseService.getPlaylistsByType(PlaylistType.m3u),
    );
  }

  static Playlist _withoutSecret(Playlist playlist) {
    return Playlist(
      id: playlist.id,
      name: playlist.name,
      type: playlist.type,
      url: playlist.url,
      username: playlist.username,
      password: null,
      createdAt: playlist.createdAt,
    );
  }

  static Future<Playlist> _hydrate(Playlist playlist) async {
    var password = await SecureStorageService.instance.readProviderPassword(
      playlist.id,
    );
    final username = await SecureStorageService.instance.readProviderSecret(
      playlist.id,
      'username',
    );

    if ((password == null || password.isEmpty) &&
        (playlist.password?.isNotEmpty ?? false)) {
      password = playlist.password;
      await SecureStorageService.instance.saveProviderPassword(
        playlist.id,
        password,
      );
      await DatabaseService.updatePlaylist(_withoutSecret(playlist));
    }

    return Playlist(
      id: playlist.id,
      name: playlist.name,
      type: playlist.type,
      url: playlist.url,
      username: username ?? playlist.username,
      password: password ?? playlist.password,
      createdAt: playlist.createdAt,
    );
  }

  static Future<List<Playlist>> _hydrateAll(List<Playlist> playlists) {
    return Future.wait(playlists.map(_hydrate));
  }
}
