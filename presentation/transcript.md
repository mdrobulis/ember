# Ember — presentation transcript

Narration voice: `en-US-AndrewNeural` (Edge TTS), rate +3%. Total duration: **3:08.9**.

Files: `presentation/index.html` (the deck), `presentation/audio/slide-01.mp3` … `slide-10.mp3` (per-slide audio), `presentation/audio/narration.mp3` (continuous audio), `presentation/ember-presentation.mp4` (narrated video), `presentation/ember-deck.pdf` (print deck).

## 1. Title — Keep the fire alive

*9.3s · starts at 0.0s*

Ember is a habit tracker built around one small flame. It is local-first, installable, and deliberately limits how many habits you can carry at once.

## 2. The problem — the day after the miss

*21.7s · starts at 10.0s*

Most trackers give you an unlimited list and a checkbox. That optimises for logging, not returning. The hard moment is not the first day. It is the day after a miss, when a broken chain makes quitting feel rational. Lally's research found one missed opportunity did not materially disrupt habit formation. The app should treat a miss as data, not a verdict.

## 3. The loop — cue, cadence, flame

*17.6s · starts at 32.4s*

Ember reduces the day to one loop: a named cue, a cadence that matches the rhythm, one tap, a lit flame. The run is the hero. The next slot is visible, so progress has a direction. Everything persists on the device. No account, no sync, no server.

## 4. Any rhythm — one ladder

*29.8s · starts at 50.7s*

Ember supports three rhythms: daily, specific weekdays, or a weekly target. Daily and fixed-day runs count scheduled days. A weekly quota counts weeks that hit the target. Both feed the same four proof rungs: three, seven, fourteen, or thirty scheduled days; or one, two, four, or eight weeks. You start with one slot. The limit is the product: few promises, kept. Slots cannot be bought, and archiving keeps the evidence that earned them.

## 5. The engine — evidence-informed mechanics

*25.1s · starts at 81.2s*

Four mechanisms do the work. Implementation intentions: naming the cue, with a medium-to-large effect in a ninety-four-test meta-analysis. Loss aversion: the flame is real, and at-risk copy waits until eight in the evening. Goal gradient: a real bar toward the next slot. And recovery: seven sessions earn a shield that covers the previous missed period, because one miss is not the habit.

## 6. The ethics line — pressure, not deception

*19.2s · starts at 107.0s*

This is where dark patterns usually enter. Ember uses pressure, not deception. No confirm-shaming, no fake urgency, no invented social proof, no countdowns, no hidden costs. The Settings screen lists every lever and its guardrail. If a user could read the mechanic and still choose it, it stays.

## 7. Timed rituals — honest about the platform

*18.2s · starts at 126.9s*

Timed rituals are honest about the platform. A closed serverless web app cannot schedule a local notification. While Ember is open, a minute tick can send one. When it is closed, one tap exports a recurring calendar alarm. No permission theatre, no fake push, no promise the web cannot keep.

## 8. Install and offline — a tab is not an app

*13.2s · starts at 145.9s*

Ember installs from Safari on iPhone and Chrome on Android, launches standalone, and loads offline from a service worker. Data stays in local storage. Export is the backup, because iOS can evict site data.

## 9. The build — pure core, tested edge

*24.6s · starts at 159.8s*

The build is ClojureScript, Reagent and Tailwind's dark theme. The domain is pure and tested: forty-one tests, one hundred and sixty-eight assertions. The browser run covered three cadences, current-week logging, persistence, shield repair, import and export, offline launch, keyboard focus, and three-hundred-and-twenty-pixel layouts. The deck and audio are generated from the same repository.

## 10. Close — one small flame

*3.9s · starts at 185.0s*

Ember. One small flame. Start with one. Keep it lit.

## Narration sources

The claims are cited on the slides and in `RESEARCH.md`. The short version: Lally et al. (2010) on 18–254 days and one missed opportunity; Gollwitzer & Sheeran (2006) on implementation intentions, d = 0.65 across 94 tests; Silverman & Barasch (2023) on intact versus broken streaks; Kivetz et al. (2006) on the goal gradient; Kahneman & Tversky (1979) on loss aversion; Gray et al. (2018) and Mathur et al. (2019) on dark patterns; WebKit (2023) on iOS web push.
