# Watchio synthetic provider fixture

Host-only, loopback Xtream fixture for deterministic Watchio authentication, provider-removal, and endpoint-failover testing. It never contacts production services.

## Requirements

- Python 3.10 or newer.
- Ports `18081` and `18082` available on `127.0.0.1`.
- A small local or publicly licensed, non-DRM media file when playback is required.

## Synthetic identities

These identities are disposable test data, not production credentials.

| Account | Username | Password | Stable account ID |
| --- | --- | --- | --- |
| A | `watchio_test_a` | `synthetic_only_a` | `watchio-synthetic-a` |
| B | `watchio_test_b` | `synthetic_only_b` | `watchio-synthetic-b` |

Credentials are case-sensitive. Unknown usernames, wrong passwords, and cross-account passwords are rejected.

## Run fixture tests

From this directory:

```powershell
python .\watchio-synthetic-provider-safe-test.py
```

Tests cover both accounts on both endpoints, deterministic identities and catalogues, invalid credentials, disabled/expired accounts, primary failure with secondary success, loopback binding, and unknown routes.

## Start endpoints

Open two terminals. Replace `<AUTHORISED_MEDIA>` with an explicitly authorised local file:

```powershell
python .\watchio-synthetic-provider-safe.py --port 18081 --media <AUTHORISED_MEDIA>
python .\watchio-synthetic-provider-safe.py --port 18082 --media <AUTHORISED_MEDIA>
```

Both servers bind only to `127.0.0.1`. Default endpoints are:

- Primary: `http://127.0.0.1:18081`
- Secondary: `http://127.0.0.1:18082`

For provider-removal acceptance, build `providerremovaltest`. It uses the same
loopback-only endpoint source under the separate application ID
`com.iamskorpz.watchioiptv.providerremovaltest`. A fresh installation therefore
cannot read or change `syntheticfailovertest`, Debug, UITEST, or production data.

Run preflight while both servers are healthy:

```powershell
python .\watchio-synthetic-two-account-preflight.py
```

## Failure modes

Use one mode per server:

```text
healthy
http500
reject
disabled
expired
broken-stream
drop-stream
```

Example primary failure:

```powershell
python .\watchio-synthetic-provider-safe.py --port 18081 --media <AUTHORISED_MEDIA> --mode http500
```

`--stream-status` can return a chosen media HTTP status. `--throttle-delay` controls local transfer pacing.

Use the one-shot catalogue gate for the refresh/removal race:

```powershell
python .\watchio-synthetic-provider-safe.py --port 18081 --media <AUTHORISED_MEDIA> --delay-catalogue
```

The first authenticated catalogue request pauses until released. These loopback-only
control URLs contain no provider credentials:

- `GET /__watchio_test__/catalogue/status`
- `GET /__watchio_test__/catalogue/release`

`status` reports the safe action name and `in_flight` state. `release` unblocks the
single delayed response. Later catalogue requests run normally.

## Stop and safety

Stop each server with `Ctrl+C`. Confirm ports `18081` and `18082` no longer listen.

- Never supply production media, provider URLs, accounts, tokens, or credentials.
- Never expose this fixture beyond loopback.
- The `syntheticfailovertest` variant has isolated package data and no production endpoint fallback.
- Device tests require explicit approval for the named device, package, actions, installation, and data handling.
- Use supervised input and verify the synthetic package before every device action.
- Never uninstall or clear app data unless separately authorised.
- Remove only task-created reverse mappings and restore approved backups after physical tests.
