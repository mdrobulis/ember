# Ember — keep the fire alive

**Ember** is a local-first habit tracker for one person and one phone. It gives
a small daily ritual a flame: light it once a day, protect the unbroken chain,
and earn more room only after the evidence says you can carry it.

- **No backend, no accounts, no analytics, no third-party requests.**
- **Installable** as a PWA on iPhone, Android and desktop.
- **Limited by design:** one active ritual to start, five at the most. Best
  streaks of 3, 7, 14 and 30 days unlock the extra slots. A slot is never
  purchased and, once earned, never taken away.
- **Streaks with a landing net:** seven real completions earn a shield. One
  shield can cover a missed *yesterday* — it never counts as a completion and
  never unlocks a slot.
- **Timed rituals** with a system notification while Ember is open, and a
  one-tap daily calendar alarm for when it is closed.
- **Dark, playful, accessible:** Tailwind's class-based dark theme, self-hosted
  fonts, inline SVG, keyboard support, visible focus, 320 px layouts.

The brief is in [BUILD-PLAN.md](BUILD-PLAN.md); the evidence and the rejected
dark patterns are in [RESEARCH.md](RESEARCH.md).

## The loop

1. Onboard with one ritual, a cue ("After morning coffee"), an optional time,
   and a one-line reason.
2. Open **Today**. The hero shows how many flames are lit and the longest live
   streak.
3. Tap the flame. It lights, the chain grows, and the next-slot bar moves.
4. Miss a day. The chain breaks. Ember offers to spend an earned shield on
   yesterday — not to buy the streak back.
5. Open **Progress** for the honest footnotes: best streak, 30-day rate, weekly
   columns, five-week heatmaps, the ledger.
6. **Settings** holds install, reminders, data export/import, archive, and a
   plain-language account of how the app persuades you.

## The slot ladder

| Active rituals | Unlocked by |
|---|---|
| 1 | Starting |
| 2 | A 3-day best streak |
| 3 | A 7-day best streak |
| 4 | A 14-day best streak |
| 5 | A 30-day best streak |

Slots are based on the best **real** completion run across all rituals,
including archived ones. Repairs do not count. Once earned, a slot is carried
forward even if the history is later edited, archived or deleted.

## Install it

The service worker — and therefore installation and offline support — needs a
secure context: **HTTPS**, or `localhost` on the same machine. Opening a LAN
address over plain HTTP loads the page but does not install.

**iPhone / iPad (Safari)** — Share → **Add to Home Screen** → **Add**, then open
Ember from the new icon.

**Android (Chrome)** — the three-dot menu → **Install app** (or **Add to Home
screen**). When Chrome offers its install prompt, Ember also shows a button for
it.

**Desktop (Chrome / Edge)** — the install icon in the address bar, or the
three-dot menu → **Install Ember**.

The in-app **Install** button and Settings → **Show me how** repeat these steps
with the limitations spelled out.

## Publish it with GitHub Pages

The repository includes [.github/workflows/pages.yml](.github/workflows/pages.yml).
It runs `npm test`, builds `public/`, and deploys the result with GitHub Pages.
Treat **this directory** (`habits/`) as the repository root when you create the
GitHub repository.

1. Create a public repository and push this directory to `main`.
2. In the repository: **Settings → Pages → Build and deployment → Source:
   GitHub Actions**.
3. Open **Actions → Deploy Ember to GitHub Pages** and let the workflow finish.
   If the first run happened before Pages was enabled, re-run it.

The site appears at `https://<user>.github.io/<repo>/`. Because every asset path
is relative, the app works under that project subpath; the service worker scope
stays inside the repository. Open that URL in Safari on iPhone, or Chrome on
Android, and use the install flow described above.

One caveat: all project sites under the same `username.github.io` host share a
browser origin, and therefore share `localStorage`. The key is namespaced, but a
custom domain is the clean way to isolate Ember's data from other Pages
projects.

## Run it

```sh
npm install
```

```sh
npm run dev
```

This builds the Tailwind stylesheet once and then starts shadow-cljs on
[http://localhost:8080](http://localhost:8080) with hot reload. CSS classes are
scanned from the ClojureScript; after adding new classes, run `npm run css`.

```sh
npm run css
```

```sh
npm run css:watch
```

If the CSS watcher and shadow-cljs are both running in separate terminals, you
get live styling and live ClojureScript.

```sh
npm run build
```

Builds the minified stylesheet and the production ClojureScript bundle in
`public/`. That directory is the entire static site.

```sh
npm test
```

Runs the Node test build over the pure domain.

## What is where

```
public/
  index.html             shell, iOS meta tags, skip link, mount point
  manifest.webmanifest   install metadata, icons, shortcuts
  sw.js                  offline cache and notification click handling
  css/app.css            compiled Tailwind output
  fonts/                 self-hosted DM Sans + Space Grotesk + OFL licences
  icons/                 SVG sources and generated PNGs
  screenshots/           install-prompt and README screenshots
src/habits/
  model.cljs             pure domain: dates, streaks, slots, shields, reminders, iCalendar
  storage.cljs           the only localStorage/file I/O, with namespace-safe JSON
  platform.cljs          browser capabilities: notifications, standalone, install prompt
  icons.cljs             inline SVG icon set
  ui.cljs                shared visual primitives, modal focus trap, confetti
  today.cljs             hero, ritual cards, slot ladder
  progress.cljs          totals, weekly chart, heatmaps, ledger
  settings.cljs          install, notifications, data, archive, transparency
  views.cljs             root, navigation, onboarding, shared modals
  main.cljs              one atom, pure reducer, persistence, clock, notifications
src/styles/input.css     Tailwind source and component layer
tailwind.config.js       dark palette, type, shadows, keyframes
test/habits/model_test.cljs
tools/make-icons.py      regenerates the PNG icons with Pillow + numpy
presentation/            narrated deck, transcript, PDF, audio and video
```

The split is deliberate:

- **`model.cljs` is pure.** Ids, dates, the clock and the keyboard are passed
  in. The whole slot/shield/reminder economy is tested in Node without a browser.
- **`storage.cljs` is the only namespace that performs storage or file I/O**,
  and its functions are named with `!`.
- **`platform.cljs` is the only namespace that reaches for browser capabilities.**
- **`main.cljs` wires them together.** It owns the one atom, the uuid
  generator, the clock, and the service worker.

## The data

State is a plain map with namespaced keys:

```clojure
{:habits/list [{:habit/id          "6f1c…"
                :habit/name        "Read 10 pages"
                :habit/created-at  "2026-10-24"
                :habit/completions #{"2026-10-24"}
                :habit/repairs     #{}
                :habit/cue         "After morning coffee"
                :habit/reminder    "07:30"
                :habit/why         "So my mornings start with my own mind."
                :habit/archived?   false}]
 :app/today        "2026-10-24"
 :app/earned-slots 1}          ; capacity floor; once earned, never removed
```

Stored in `localStorage` under `habits/state/v1`, and exported as JSON with the
same namespaced keys:

```json
{
  "habits/version": 1,
  "habits/exported-at": "2026-10-24",
  "habits/earned-slots": 1,
  "habits/list": [
    {
      "habit/id": "…",
      "habit/name": "Read 10 pages",
      "habit/created-at": "2026-10-24",
      "habit/completions": ["2026-10-24"],
      "habit/repairs": [],
      "habit/cue": "After morning coffee",
      "habit/reminder": "07:30",
      "habit/why": "So my mornings start with my own mind.",
      "habit/archived?": false
    }
  ]
}
```

Dates are `YYYY-MM-DD` in the device's calendar. Day arithmetic runs at 12:00
UTC so a daylight-saving change cannot shift a day. `clj->js` is **not** used
for persistence or export because it drops keyword namespaces; `storage.cljs`
has a small `json-ready` conversion that preserves them. Imports still accept
the older, namespace-less `"list"` shape.

## Timed rituals and the no-server limit

Two honest layers:

1. **While Ember is open**, a minute tick checks timed, unlit rituals and shows
   a system notification through the service worker. If permission is not
   granted, the same event becomes an in-app alert. Reminder keys are stored per
   day so a reload does not repeat one.
2. **When Ember is closed**, a serverless web app cannot schedule a local
   notification. Settings → **Add all to calendar** downloads a `.ics` file with
   one daily, alarmed event per timed ritual. Your calendar is the scheduler.

iOS requires the Home Screen version for web push, and web push needs a server
to send from. Ember has no server, so it does not pretend. The copy in the app
says this openly.

## The psychology, and the line we did not cross

Ember uses mechanics commonly found in persuasive products: a visible streak,
loss framing after 20:00, goal-gradient progress, scarcity of slots, a variable
celebration line, implementation intentions, a weekly fresh start, and a
commitment device (the written cue and reason).

It does **not** use confirm-shaming, fake urgency, invented social proof,
pre-ticked consent, hidden costs, a roach motel, or a countdown that is not a
real clock. "Dark patterns for your benefit" is a dangerous brief; this is the
transparent version. Settings → **How Ember persuades** lists every lever and
its guardrail, and the research brief records what was rejected and why.

## Known limits

- **iOS can evict `localStorage`.** Safari removes site data under storage
  pressure. Export a JSON copy periodically; that is the only backup.
- **No sync.** Two devices are two separate datasets. There is no account.
- **No background timer.** Notifications while closed need the calendar export.
- **No undo for delete or import.** Both are confirmed; archive is the reversible
  exit.
- **No theme switcher.** The app is dark by design; the manifest and meta theme
  colour match.
- **Company A/B tests are labelled as such.** The Duolingo numbers in the
  research brief are design evidence, not independent proof.

## The narrated presentation

A ten-slide, 2:48 presentation is in [presentation/](presentation/):

- [presentation/index.html](presentation/index.html) — the deck, with per-slide
  audio and a Play all control.
- [presentation/ember-deck.pdf](presentation/ember-deck.pdf) — printable slides.
- [presentation/ember-presentation.mp4](presentation/ember-presentation.mp4) —
  the narrated video.
- [presentation/transcript.md](presentation/transcript.md) — the script with
  timings.
- [presentation/audio/narration.mp3](presentation/audio/narration.mp3) — the
  continuous narration.

Open it through a local server, because the deck's audio files are relative:

```sh
cd habits
python3 -m http.server 4174
```

```text
http://127.0.0.1:4174/presentation/index.html
```

The narration is synthetic (`en-US-AndrewNeural` via the workspace's Edge TTS
tool). Listen to it before presenting it as your own voice.

## Verification

The checks that were actually run are recorded in the final handoff. The
mechanical commands are:

```sh
npm test
```

```sh
npm run build
```

The browser exercise covered onboarding, lighting a habit, persistence across a
reload, shield repair, archive, the slot limit and its persistent floor, JSON
export/import, calendar download, modal keyboard focus/Escape, 320/390/768 px
widths, and an offline reload of the installed shell. An axe-core WCAG 2.1 AA
sweep reported **0 violations** on onboarding, Today, Progress, Settings and the
edit modal. Chrome reported **0 installability errors** for the manifest.

Not verified on hardware: notification delivery on a physical iPhone or
Android, a real Home Screen install, opening the `.ics` file in a device
calendar, and Safari's storage-eviction behavior. Those need a device, not a
headless browser. The narration was verified by duration and playback, not by
listening.)

## Credits

Type: [DM Sans](https://fonts.google.com/specimen/DM+Sans) and
[Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk), both under the
SIL Open Font License; the licence files are in `public/fonts/`.

Icons are original inline SVG. The PNG app icons are generated by
`tools/make-icons.py` with Pillow and numpy.
