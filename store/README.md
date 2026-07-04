# Play Store listing kit

Everything needed for the Play Console listing, minus the pieces only a real
device can produce.

## One-time account steps
1. https://play.google.com/console → register (US$25 one-time), verify ID.
2. Create app → "Bento Calendar", App (not game), Free.
3. Play App Signing: choose "Use an existing key" and follow the console's
   `pepk` instructions with `..\bento-release.keystore` (alias `bento`) so
   existing installs can migrate; otherwise let Play generate one (fine too —
   the AAB is signed with our key as the upload key either way).
4. Closed testing: create a track, upload `BentoCalendar-vX-play.aab` from the
   GitHub release assets, add tester emails (the console states the required
   tester count and 14-day duration for personal accounts).
5. Store listing:
   - Privacy policy URL: https://github.com/matisseduffield/productivity/blob/main/PRIVACY.md
   - Data safety form: "No data collected, no data shared" (true — see PRIVACY.md);
     declare the optional READ_CALENDAR permission as app functionality,
     processed ephemerally, never shared.
   - Content rating questionnaire: utility/productivity, no user-generated
     public content → Everyone.

## Assets
- **App icon 512×512 PNG**: export `icon-512.svg` (this folder) at 512px —
  any converter works (e.g. Inkscape: `inkscape icon-512.svg -w 512 -o icon.png`).
- **Feature graphic 1024×500 PNG**: export `feature-graphic.svg` the same way.
- **Screenshots (min 2, up to 8)**: take on the S25 — suggested set: Today
  (with events), Calendar month, Calendar day with overlapping events, Notes,
  Tasks, Settings, a home screen with widgets. Portrait PNG straight from the
  phone is accepted as-is.

## Ongoing releases
Every `v*` tag builds `BentoCalendar-vX-play.aab` (Play flavor: no
self-updater, no REQUEST_INSTALL_PACKAGES). Upload it to the Play track;
GitHub users keep updating in-app as before.
