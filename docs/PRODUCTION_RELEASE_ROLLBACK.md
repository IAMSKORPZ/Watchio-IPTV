# Watchio Production Release Recovery

This procedure covers release publication failures without assuming Android can downgrade an installed app while retaining data.

## Before publication

1. Record the verified `main` source SHA, current `stable.json` SHA-256, current public tag, APK SHA-256, and signing certificate SHA-256.
2. Run the public workflow in dry-run mode. Verify package ID, version, certificate, canonical APK filename, checksum, and generated stable metadata.
3. Require production-environment approval before a non-dry-run job starts.
4. Preserve the previous stable manifest and release. Never overwrite or delete the previous APK.

## Publication failure before stable metadata changes

The existing stable release remains active. If the new GitHub release exists but stable metadata was not committed, users are not directed to it. Inspect the failure, delete the unpublished or invalid new release if necessary, and rerun only after correction.

## Incorrect stable metadata

Create a forward commit on `main` restoring the last verified stable manifest. Verify its JSON, immutable APK URL, checksum, and public availability before pushing. Do not rewrite Git history. The app will return to advertising the previous valid release.

## Broken APK or failed installation

Stop advertising the affected release by restoring the previous stable manifest. Keep evidence and withdraw the affected GitHub release only after confirming no recovery process depends on it. Android generally blocks version downgrades, and downgrading can require data loss. Prepare a higher-version forward-fix APK signed by the same public certificate.

## Database migration failure

Do not attempt an APK downgrade over a migrated database. Stop stable rollout, preserve failure evidence, and ship a higher-version forward fix with an additive migration. Restore from Watchio backup only through the supported in-app restore path and only with user approval.

## Post-recovery checks

- Stable manifest resolves from the public `main` URL.
- Manifest channel, version, filename, download URL, and checksum match the intended APK.
- APK is publicly downloadable and signed by the established public certificate.
- Existing installations can check for updates without parsing or checksum errors.
- Git history contains the corrective commit and no force push.
