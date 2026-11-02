SkyLyrics - Fabric 1.21.4 client MOD (this zip is SOURCE CODE; you need to build the jar)

Pops lyric lines up in the sky, one at a time: each line scales in with a little bounce,
hovers at a fixed spot in the sky (turn your head and it stays put), drifts upward, and fades.
Client-side only. It does not play audio - play your music yourself and press K when it starts.

GET THE JAR (pick one)
  A) No Java needed: upload this folder (including the hidden .github folder) to a free
     GitHub repo, open the Actions tab, wait for the green check, then download the .jar
     from the repo's Releases page.
  B) Windows: install JDK 21+, double-click BUILD.bat. The jar appears in JAR-READY.
     macOS/Linux: ./build.sh
  C) ./gradlew build  ->  build/libs/skylyrics-1.0.0.jar (not the -sources jar)

INSTALL
  Fabric profile on Minecraft 1.21.4. Put skylyrics-1.0.0.jar and Fabric API (1.21.4)
  in the mods folder.

USE
  1. Launch once. It creates .minecraft/config/skylyrics/ with a sample.lrc.
  2. Put YOUR OWN lyric files in that folder (.lrc or .txt). This mod ships no real lyrics.
  3. In game: K = play / stop, N = skip to next line, L = next song file.
     Change the keys in Options > Controls > Key Binds > SkyLyrics.

FILE FORMATS
  Synced (.lrc):   [00:12.50]First line
                   [00:16.00]Second line
                   [ti:Song Title]  [ar:Artist]   (optional, shown in the title banner)
                   One line can have several stamps: [00:30.00][01:45.00]Chorus line
  Plain (.txt):    one lyric per line; shown every "interval" seconds (see config).
                   Blank lines, lines starting with # and [Section] headers are ignored.
  Start the song and press K at the same moment the music starts so they line up.

CONFIG  (config/skylyrics/config.json, edit then press K to reload songs)
  interval   seconds between lines for plain .txt files   (default 3.5)
  lifetime   how long each line stays up                  (default 5.5)
  textScale  text size                                    (default 3.0)
  skyMode    true = fixed spots in the sky, false = fixed on screen
  showTitle  show the title banner when playback starts

LICENSE: MIT (see LICENSE). Bring lyrics you have the right to use for personal display.

SPOTIFY BROWSER LOGIN (new in 1.1.0)
  Press O in-game to open the Spotify connection menu.
  1. Create an app in https://developer.spotify.com/dashboard.
  2. Add this exact Redirect URI in the app settings:
       http://127.0.0.1:8888/callback
  3. Copy the app's Client ID into the SkyLyrics menu and choose Connect with Spotify.
  4. Sign in and approve access in the browser. The callback returns to Minecraft.
  Required scopes: user-read-playback-state, user-read-currently-playing.
  Authentication uses OAuth 2.0 Authorization Code with PKCE; no client secret is used.
  Session data is stored locally in config/skylyrics/spotify-session.json.
  Spotify developer dashboard access and Spotify API availability are required.
