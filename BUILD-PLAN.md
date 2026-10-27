# Ember — build plan

**Goal.** Turn the existing single-screen habit tracker into **Ember**, a
visually playful, installable, local-first habit app for one person, with a
streak-led loop, a small number of slots that unlock through success, honest
timed reminders, and a transparent engagement system.

**Non-goals.** Accounts, sync, social features, a backend, payments,
subscriptions, analytics, third-party runtime scripts, or web push that needs a
server.

**Product sentence.** Ember gives one small daily action a flame; the flame is
easy to keep while it is short, harder to lose when it is long, and one earned
shield can cover a bad day.

## 1. The loop

1. Onboard: name one ritual, write the cue ("After morning coffee"), optionally
   set a time and a one-line why.
2. Open Today: the hero shows how many flames are lit, the longest live streak,
   and the next slot.
3. Tap a card: it lights, confetti fires, the streak grows, and the next-slot
   bar moves.
4. Miss a day: the flame breaks. The app offers one earned shield to repair
   yesterday, not a purchase.
5. Progress: best streak, 30-day rate, five-week heatmap, weekly chapter.
6. Settings: install, notifications, calendar export, data export/import, and a
   plain-language account of the persuasive mechanics.

## 2. Slot ladder

| Slot | Unlock rule | Copy |
|---|---|---|
| 1 | Start | "One flame is enough to begin." |
| 2 | 3-day best streak | "You proved you can return. Room for another." |
| 3 | 7-day best streak | "A week. Your fire has a second shape." |
| 4 | 14-day best streak | "Two weeks of evidence." |
| 5 | 30-day best streak | "A month. This is who you are now." |

- Slots are based on the **best actual completion run ever**, so they cannot be
  farmed by repairs or by deleting a habit.
- Unlocked slots are permanent. Breaking a streak, editing history, archiving
  or deleting a habit never takes a slot away; only erasing all data resets the
  floor.
- There is no way to buy a slot, and no timer that creates one.
- Existing data with more than five habits is preserved; the limit applies only
  to adding new habits.

## 3. Psychology, with the guardrails attached

| Lever | Implementation | Guardrail |
|---|---|---|
| Implementation intentions | cue + optional time + why in onboarding | editable, never hidden |
| Loss aversion | flame, live streak, at-risk copy | only after 20:00 and only for real incomplete habits |
| Goal gradient | next-slot progress | real distance to a real threshold |
| Endowed progress | "Day 1 starts now" framing | no invented completions |
| Variable reward | rotating celebration line and confetti | no odds, no currency, no loot |
| Fresh start | weekly chapter resets Monday | totals and best streak never reset |
| Commitment / scarcity | one starting slot | earned, permanent, never purchasable |
| Recovery | shields, earned at 7 completions, cap 3 | repair only yesterday; repairs do not unlock slots |
| Friction | archive is reversible; delete needs confirmation | no confirm-shaming; the safe choice is the easy one |

The full evidence and the rejected patterns are in [RESEARCH.md](RESEARCH.md).

## 4. Screens and components

- **Today** — greeting, date, hero (lit/total, best live streak, next slot),
  at-risk banner, habit cards, locked-slot card, add action.
- **Habit card** — tap target, streak, cue, time, seven-day strip, shield count,
  repair action, edit menu.
- **Onboarding** — two-step implementation-intention form, progress indicator,
  no account.
- **Habit editor** — name, cue, time, why, archive; destructive delete isolated.
- **Progress** — best streak, total shows, 30-day rate, shields, weekly column
  chart, five-week heatmap per habit.
- **Settings** — install instructions for iPhone/Android/desktop, notification
  permission and test, calendar export, JSON export/import, data deletion,
  transparency panel, limits.
- **Install sheet** — iOS and Android steps, Android/desktop install prompt when
  available, standalone detection.

## 5. Technical shape

- **ClojureScript + Reagent + shadow-cljs** reused; no new runtime dependency.
- **Tailwind CSS 3.4** compiled into `public/css/app.css` with `darkMode:
  'class'`; the document starts in dark mode. Custom keyframes for flame flicker,
  card entry, confetti and the victory pulse live in the Tailwind layer.
- **Pure domain** in `habits.model`: dates, streaks, slots, shields, reminder
  due checks, calendar serialisation.
- **Effects isolated** in `habits.storage` (localStorage, files, downloads) and
  `habits.reminders` (permission, service-worker notifications, minute tick).
- **PWA**: manifest, icons, offline service worker, safe-area support, standalone
  display. No network after first load.
- **Tests**: `cljs.test` on Node for every pure rule; build with warnings as
  errors; browser smoke test at 390×844 and 320px width.

## 6. Execution order

1. Write research brief and this plan.
2. Extend the model and tests: slot ladder, shields, archive, reminder due,
   calendar, validation.
3. Add Tailwind toolchain, dark tokens, fonts, logo/icons.
4. Build the component library and the three screens.
5. Wire state, persistence, notifications, install prompt and service worker.
6. Build, test, exercise in headless Chromium at phone and desktop sizes, fix
   contrast/layout/focus issues, capture screenshots.
7. Write the presentation script, generate audio, build the deck and MP4.
8. Final check: offline load, install metadata, export/import round trip,
   notification permission path, keyboard path, docs.

## 7. Acceptance checks

| # | Given | When | Then |
|---|---|---|---|
| 1 | no saved data | I finish onboarding | one habit exists with cue, optional time, and Day 1 visible |
| 2 | a second habit and no 3-day best | I try to add | the add action is disabled with the unlock rule stated |
| 3 | a habit with a 3-day best | I return to Today | a second slot is unlocked and announced |
| 4 | an unticked today and a ticked yesterday | I open the app | the live streak is intact |
| 5 | a missed yesterday and one banked shield | I choose Repair | yesterday is marked as repaired, the streak is restored, the shield is spent |
| 6 | a timed habit and notification permission | the time arrives while the app is open | a system notification appears once |
| 7 | a timed habit | I tap Add to calendar | a daily `.ics` event with an alarm downloads |
| 8 | any state | I reload | the habit list and completions are unchanged |
| 9 | a saved 30-day best | I open Progress | five slots are available, no purchase prompt exists |
| 10 | 320px viewport | I open every screen | no horizontal scroll, controls remain reachable |
| 11 | no network after first load | I launch the installed app | Today renders and a tap persists |
| 12 | a JSON export | I import it | confirmation is required before replacement |

## 8. Delivered — 2026-10-27

- Ember shipped in `habits/`: onboarding, Today, Progress, Settings, slot
  ladder, shields, archive, JSON export/import, timed reminders, calendar
  export, install sheet, dark Tailwind design, offline service worker.
- Pure domain tests: 28 tests, 102 assertions, 0 failures.
- Browser exercise: onboarding, persistence across reload, shield repair,
  archive, slot limit, JSON round trip, `.ics` download, notifications
  fallback, keyboard focus/Escape, 320/390/768 px, offline reload.
- Research brief: `RESEARCH.md`. Requirements and decision log:
  `REQUIREMENTS.md`.
- Presentation: `presentation/index.html`, `presentation/ember-deck.pdf`,
  `presentation/ember-presentation.mp4`, 2:48 narration, transcript and
  timings.

Not verified: notification delivery on a physical iPhone or Android device,
installation on a real Home Screen, and localStorage eviction behaviour on a
device under storage pressure. Those require hardware, not a headless browser.
