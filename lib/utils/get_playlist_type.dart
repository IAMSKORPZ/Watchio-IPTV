import 'package:watchio/models/playlist_model.dart';
import 'package:watchio/services/app_state.dart';

PlaylistType getPlaylistType() {
  return AppState.currentPlaylist!.type;
}

bool get isXtreamCode {
  if (AppState.currentPlaylist == null) return false;
  return getPlaylistType() == PlaylistType.xtream;
}

bool get isM3u {
  if (AppState.currentPlaylist == null) return false;
  return getPlaylistType() == PlaylistType.m3u;
}

bool get isStalker {
  if (AppState.currentPlaylist == null) return false;
  return getPlaylistType() == PlaylistType.stalker;
}
