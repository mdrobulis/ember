# Ember — narrated presentation

A ten-slide, two-minute-forty-eight-second product presentation. All of it is
local: no CDN, no web fonts at runtime, no external images.

## Open the deck

```sh
cd habits
python3 -m http.server 4174
```

```text
http://127.0.0.1:4174/presentation/index.html
```

Use the arrow keys to move, **Play** for the current slide, or **Play all** to
hear the presentation and advance automatically. **Full audio** downloads the
continuous narration.

## Files

- `index.html` — the self-contained slide deck.
- `transcript.md` — every slide's narration with its timing.
- `audio/slide-01.mp3` … `audio/slide-10.mp3` — per-slide narration.
- `audio/narration.mp3` — the continuous 2:48 narration.
- `audio/timings.json` — start times and durations.
- `ember-deck.pdf` — a printable version (1920×1080 pages).
- `ember-presentation.mp4` — the narrated video.
- `assets/` — screenshots, fonts and the app icon used by the deck.

## Rebuild the assets

The deck is hand-authored HTML/CSS/JS. The slides are screenshots at
1920×1080; the audio comes from the workspace TTS tool:

```sh
../tools/tts/speak --provider edge --voice en-US-AndrewNeural --rate "+3%" --max 10000 --no-play "$(cat presentation/audio/text-01.txt)"
```

The exact per-slide text is in `presentation/audio/text-*.txt`, and the
continuous narration is a concatenation with 0.7-second gaps. The recording is
synthetic; listen to `narration.mp3` before presenting it as your own voice.

## What is claimed

The deck cites the primary sources and labels the Duolingo figures as company
A/B tests. It does not claim that Ember's own ladder has been validated in a
trial. The in-app transparency panel and `RESEARCH.md` are the longer version.
