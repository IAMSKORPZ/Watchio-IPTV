import 'package:watchio/models/m3u_item.dart';
import 'package:watchio/models/playlist_model.dart';
import 'package:watchio/models/provider_model.dart';
import 'package:watchio/repositories/iptv_repository.dart';
import 'package:watchio/repositories/m3u_repository.dart';

abstract class AppState {
  static IptvProvider? currentProvider;
  static Playlist? currentPlaylist;
  static IptvRepository? xtreamCodeRepository;
  static M3uRepository? m3uRepository;
  static List<M3uItem>? m3uItems;
}
