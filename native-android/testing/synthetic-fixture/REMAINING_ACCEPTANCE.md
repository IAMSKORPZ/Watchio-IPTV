# Remaining provider-removal acceptance

Use only disposable accounts in `com.iamskorpz.watchioiptv.syntheticfailovertest`. Obtain fresh approval before device access. Back up synthetic package data first. Use supervised input and restore the backup after testing.

## Final-four isolated harness

Use a fresh `com.iamskorpz.watchioiptv.providerremovaltest` installation for the
four remaining scenarios. This package shares no storage with the existing
`syntheticfailovertest` installation. Installation and S22 access require fresh
approval. Never clear, uninstall, or open another Watchio package.

- Zero downloads: sign in one synthetic account in the fresh package. Confirm Downloads is empty. Remove the provider and cold-restart. Expect Login as root.
- Incomplete-only: run a throttled authorised MP4 fixture, enqueue one download, wait for non-zero progress, pause it, and confirm no completed item exists. Remove the provider. Expect Login; the paused item must not become playable.
- Playback removal: add both synthetic accounts, select A, play authorised A media, leave playback through supported Back navigation, then remove A. Expect playback stopped, B selected, and Home as root.
- Catalogue race: start A with `--delay-catalogue`, start Refresh, and require `/__watchio_test__/catalogue/status` to report `in_flight=true`. Remove A, then call `/__watchio_test__/catalogue/release`. Expect B active, A absent, and no late A catalogue write after restart.

Each scenario starts from a newly installed or separately backed-up disposable
`providerremovaltest` state. Do not reuse state across scenarios. Preserve fixture
logs, UI evidence, crash/ANR checks, and restoration hashes.

## Common safety stops and evidence

Stop on wrong device/package, production-looking data, guard failure, unexpected app, disconnect, crash, ANR, or data outside the synthetic package. Capture privacy-safe UI state, guarded action audit IDs, fixture request summaries, expected/actual destination, crash/ANR evidence, and backup restoration hashes.

## 1. Active provider removal with valid alternate

- Setup: two valid synthetic providers; A selected; B inactive and valid.
- Current expected result: playback and active-provider state stop; A is deleted; B is validated, selected, and Home becomes root.
- Steps: open Provider Management, remove A, confirm, observe navigation, reopen Provider Management, restart app.
- PASS: B becomes selected; Home opens; A disappears immediately and after restart; no stale state or crash.
- FAIL: wrong provider selected, login/downloads route, stale A, playback survives, or relaunch restores A.

## 2. Last provider removal with no downloads

- Setup: one valid provider; no completed playable downloads.
- Current expected result: provider is deleted and Login becomes root.
- Steps: confirm Downloads has no playable item, remove provider, observe destination, restart.
- PASS: Login opens as root; provider remains absent; Back cannot reveal authenticated screens.
- FAIL: Home/Downloads opens, provider returns, or authenticated state remains.

## 3. Last provider removal with completed playable downloads

- Setup: one valid provider and one authorised completed cache-only download.
- Current expected result: provider is deleted and `downloads?offlineOnly=true` becomes root.
- Steps: prove completed download first, remove provider, verify Downloads-only route, play offline item with fixture unavailable, restart.
- PASS: Downloads-only opens; completed item remains playable without provider/network; provider stays absent; Back cannot reveal Home.
- FAIL: Login/Home opens, download disappears, playback contacts provider, or provider returns.

## 4. Last provider removal with incomplete downloads

- Setup: one valid provider; only queued/downloading/paused/failed items; no completed playable item.
- Current expected result: incomplete items do not satisfy `hasPlayableDownloads`; Login becomes root.
- Steps: create controlled incomplete state, remove provider, observe route and retained download state, restart.
- PASS: Login opens; no incomplete item is offered as playable; provider remains absent.
- FAIL: Downloads-only opens solely because of incomplete media, or incomplete media becomes playable.

## 5. Active provider removal during playback

- Setup: two valid providers; A selected; authorised A media playing; B valid.
- Current expected result: player stops before A deletion; A refresh work and provider state are cancelled/invalidated; B becomes selected; Home becomes root.
- Steps: begin playback, open Provider Management through supported navigation, remove A, confirm, inspect player/resource state and B selection.
- PASS: playback stops; no stale A request continues; B is selected; Home opens; no crash/ANR.
- FAIL: playback continues, stale request persists, wrong route appears, or A remains selected.

## 6. Offline account removal

- Setup: synthetic provider selected; fixture endpoints unavailable; choose either completed-download or no-download prerequisite explicitly.
- Current expected result: deletion itself is local. With no valid alternate, route depends only on completed playable downloads: Downloads-only when true, otherwise Login.
- Steps: stop fixtures, remove provider, verify the expected branch, restart offline.
- PASS: provider removal persists; route matches download prerequisite; no network loop, crash, or false provider restoration.
- FAIL: removal blocks on network, wrong route, provider returns, or repeated retry loop occurs.

## 7. Account removal during catalogue refresh

- Setup: two providers; A selected; deterministic slow A refresh; B valid.
- Current expected result: removing A cancels its refresh before deletion, invalidates A state, selects B, and routes Home.
- Steps: start A refresh, confirm active progress, remove A, inspect requests/state, restart.
- PASS: A refresh stops; no post-delete A writes; B selected; A absent immediately and after restart.
- FAIL: refresh continues, A data reappears, stale message/card remains, or crash/ANR occurs.

## 8. Relaunch persistence after active-provider removal

- Setup: complete scenario 1 successfully.
- Current expected result: B selection and A deletion persist through process restart.
- Steps: force-stop only the synthetic package, relaunch, inspect Home and Provider Management.
- PASS: Home identifies B; only B remains; no login or stale A card.
- FAIL: A returns, selection is missing/wrong, or navigation changes unexpectedly.
