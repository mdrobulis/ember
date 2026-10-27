# Ember — requirements

**Purpose.** Help one person keep a small daily habit by turning the streak into
something they want to protect, without making a second habit impossible to
start.

**Who.** One person, one phone, no account. The failure mode this designs
against is **losing momentum after one missed day**, not forgetting what the
habit is.

**Device.** A phone browser first; installable to the iPhone Home Screen or an
Android launcher. It must work with no signal after the first load.

## 1. The core loop

> Open → see today's flames and the live streak → tap the smallest version that
> counts → it stays lit after a reload → close.

The entire loop must be reachable in one tap from the installed icon and one tap
on the ritual card. There is no sign-in, onboarding quiz, paywall or permission
prompt before the first value is delivered.

## 2. Rituals and slots

A ritual has a name, creation date, a set of completed days, a set of repaired
days, a cue, an optional time, an optional reason, and an archived flag.

| Rule | Requirement |
|---|---|
| Active limit | 1 slot at the start; at most 5 |
| Unlock | Best **real** streak of 3, 7, 14 and 30 days unlocks slots 2–5 |
| Permanence | Once earned, a slot is never removed by a break, archive, edit or delete |
| Delete | Deleting a habit keeps the earned-slot floor; only erasing all data resets it |
| Archive | Reversible, frees the slot, keeps all history |
| Reorder | Oldest first |
| Schedule | Daily; no weekday exceptions in this version |
| Quantity | Done or not done; no partial credit |

## 3. Streak rules

- A streak is the unbroken chain of completed **or repaired** days ending today,
  or ending yesterday when today is not lit yet.
- A missed day ends the visible streak.
- A shield can repair **yesterday only**. It never repairs today or the future.
- Repairs do **not** count as completions and do **not** unlock slots.
- The best streak is calculated from real completions only.
- Best streak and lifetime totals never reset. A weekly chapter resets Monday.

## 4. Shields

- One shield is earned per seven real completions, banked to a maximum of three.
- Spending one covers yesterday and reduces the balance by one.
- The repair action is offered only when the balance is positive and yesterday
  is uncovered.
- If the habit is archived, no repair is offered.

## 5. Reminders

- A ritual can have one optional daily time in `HH:MM`.
- While the app is open, a minute check may show a system notification through
  the service worker.
- If notification permission is unavailable, the same reminder becomes an
  in-app alert.
- A reminder fires at most once per ritual per day, even across reloads.
- A closed serverless web app cannot schedule a local notification. The app must
  offer a daily `.ics` calendar export with an alarm for every timed ritual.
- The app must state the closed-app limitation in plain language; it must not
  promise push notifications it cannot send.

## 6. Install and offline

- The app must be a valid PWA: manifest, icons, service worker, standalone
  display, theme colour, start URL and scope.
- It must be installable on iPhone (Safari → Share → Add to Home Screen) and
  Android (Chrome install prompt or menu).
- Settings must show install steps for iPhone, Android and desktop.
- When Chrome fires `beforeinstallprompt`, the install sheet must offer the
  native prompt.
- The app shell must load from the service worker with the network disabled.
- There are no backend API calls and no third-party requests. Fonts and icons are self-hosted. The service worker only re-fetches same-origin shell assets when online.

## 7. Data and privacy

- All data lives in `localStorage` under `habits/state/v1`; no server, no
  analytics, no third-party script.
- JSON export preserves namespaced keys; import replaces the whole list only
  after confirmation.
- Imports accept the older namespace-less `"list"` shape as well as the current
  `"habits/list"` shape.
- Unreadable saved data is copied aside before the app starts fresh.
- Clearing all data requires an explicit second step and reloads to onboarding.
- The app must say that iOS can evict site data and that export is the backup.

## 8. Visual and interaction requirements

- Tailwind CSS, class-based dark mode, dark by default.
- A visible, animated flame; a streak number; a next-slot progress bar.
- Celebration is multi-channel (motion, colour, a live-region message) but the
  same state is always available as text.
- All controls are native `<button>`, `<a>`, `<input>`, `<label>`.
- Every input has a visible label; every modal traps focus, closes on Escape,
  and returns focus to its trigger.
- Body text meets 4.5:1; targets are at least 24×24, routinely 44×44.
- At 320 px there is no horizontal scroll and no control becomes unreachable.
- `prefers-reduced-motion` suppresses animation.
- Disabled controls explain why, via adjacent text and `aria-describedby`.

## 9. Non-goals

No accounts, sync, social features, leaderboards, payments, subscriptions,
analytics, third-party runtime scripts, AI coaching, health claims, or a server.

## 10. Acceptance checks

| # | Given | When | Then |
|---|---|---|---|
| 1 | no data | I finish onboarding | one ritual exists with a cue and an optional time |
| 2 | one active ritual and no 3-day best | I try to add another | the add button is disabled and explains the unlock rule |
| 3 | a 3-day best | I open Today | a second slot is available |
| 4 | today unlit, yesterday lit | I open Today | the live streak is intact |
| 5 | yesterday missed, one shield banked | I tap Repair | yesterday is repaired, the streak recovers, the shield is spent |
| 6 | a timed, unlit ritual | its minute arrives while Ember is open | one alert/toast appears and does not repeat after reload |
| 7 | a timed ritual | I tap Add all to calendar | an `.ics` file with a daily alarm downloads |
| 8 | a lit ritual | I reload | the completion and streak are unchanged |
| 9 | a 30-day best | I open Progress | five slots are available; no purchase UI exists |
| 10 | a 320 px viewport | I open all screens | no horizontal scroll |
| 11 | the installed app with no network | I launch it | Today renders and a tap persists |
| 12 | a JSON export | I import it | confirmation is required before replacement |
| 13 | a modal open | I press Escape | it closes and focus returns to the trigger |

## 11. Decisions log

| Date | Decision | Why |
|---|---|---|
| 2026-10-24 | `localStorage`, no backend | one person, one phone, no server to keep alive |
| 2026-10-24 | Import replaces after confirmation | simpler than merging; confirmation prevents the loss |
| 2026-10-27 | One starting slot, max five | the constraint is the product; it forces few promises |
| 2026-10-27 | Slots from best real streak | success unlocks capacity; repairs cannot farm it |
| 2026-10-27 | Shields earned by real completions | a landing net, not a purchased streak |
| 2026-10-27 | Dark-only Tailwind theme | one palette kept contrast-correct; the product is a night-fire |
| 2026-10-27 | In-app + calendar reminders | honest about the serverless scheduling limit |
| 2026-10-27 | No deceptive dark patterns | UI invariants outrank the brief; pressure is transparent and documented |
| 2026-10-27 | Namespace-safe JSON | `clj->js` would silently flatten `habits/list` to `list` |

## 12. Open questions

1. Is the 3/7/14/30 ladder too slow or too fast in real use?
2. Should a shield repair any of the last three days instead of only yesterday?
3. Should there be a weekly review prompt, and what would it ask?
4. Is the fixed 20:00 at-risk hour right, or should it follow the ritual's time?
5. Would a monthly history export reminder be worth the interruption?
6. Do enough people want a light theme to justify a second palette?

## 13. Next three things

1. A one-screen weekly review after Sunday.
2. Optional per-ritual schedule (weekdays) with an honest streak definition.
3. A local “export is stale” nudge after 30 days.
