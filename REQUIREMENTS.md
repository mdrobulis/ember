# Ember — requirements

**Purpose.** Help one person keep small recurring habits by turning the run into
something they want to protect, without making a second habit impossible to
start.

**Who.** One person, one phone, no account. The failure mode this designs
against is **losing momentum after one missed period**, not forgetting what the
habit is.

**Device.** A phone browser first; installable to the iPhone Home Screen or an
Android launcher. It must work with no signal after the first load.

## 1. The core loop

> Open → see what is due today and what is already done → log the smallest
> version that counts → it stays logged after a reload → close.

The loop must be reachable in one tap from the installed icon and one tap on the
ritual card. There is no sign-in, paywall or permission prompt before the first
value is delivered.

## 2. Cadence

A ritual has one of three cadences:

| Cadence | Completion unit | Due rule |
|---|---|---|
| **Daily** | Day | Every day |
| **Fixed days** | Scheduled day | Only the selected weekdays |
| **Weekly quota** | Week | N real sessions by the deadline |

A weekly quota may have an optional allowed-day window. With no window, any day
counts. With a window, only sessions inside those weekdays count, and the target
cannot exceed the window size.

Off days are neutral. They do not extend or break a streak.

## 3. Rituals and slots

A ritual has a name, creation date, completions, repairs, frozen weeks, a
schedule, a cue, an optional time, an optional reason, and an archived flag.

| Rule | Requirement |
|---|---|
| Active limit | 1 slot at the start; at most 5 |
| Unlock | Best real run of 3/7/14/30 scheduled days, or 1/2/4/8 weeks |
| Permanence | Once earned, a slot is never removed by a break, archive, edit or delete |
| Delete | Deleting a habit keeps the earned-slot floor; only erasing all data resets it |
| Archive | Reversible, frees the slot, keeps all history |
| Reorder | Oldest first |
| Quantity | Done or not done; no partial credit |

## 4. Streak rules

- **Daily:** the unbroken chain of completed or repaired days ending today, or
  ending yesterday when today is not lit yet.
- **Fixed days:** the equivalent chain counted only on scheduled days. Off days
  are skipped; a missed scheduled day breaks it.
- **Weekly quota:** the chain of weeks that met the target, plus frozen weeks.
  The current week is `2/3` until the deadline and does not break the chain
  while it is still in progress.
- A shield can cover the previous missed scheduled day (daily/fixed) or freeze
  the previous quota week.
- Repairs and frozen weeks do not count as real sessions and do not unlock
  slots.
- Best runs and lifetime totals never reset. The weekly chapter resets Monday.

## 5. Current-week logging

- The current week can be corrected from the card's **Log a past day** sheet.
- Only allowed days at or before today can be checked. Future days are locked.
- Previous weeks cannot be edited; shields preserve the visible chain instead.
- A quota's deadline is the last allowed day, or Sunday when the window is open.

## 6. Shields

- One shield is earned per seven real sessions, banked to a maximum of three.
- Spending one covers the previous missed period: the last scheduled day for
  daily/fixed habits, the previous week for a quota.
- The repair action is offered only when the balance is positive and the
  previous period is uncovered.
- If the habit is archived, no repair is offered.

## 7. Reminders

- A ritual can have one optional time in `HH:MM`.
- While the app is open, a minute check may show a system notification through
  the service worker.
- If notification permission is unavailable, the same reminder becomes an
  in-app alert.
- A reminder fires at most once per ritual per day, even across reloads.
- Reminders respect cadence: scheduled days for fixed habits; allowed days for a
  quota with a window, otherwise the quota deadline.
- A closed serverless web app cannot schedule a local notification. The app must
  offer a recurring `.ics` calendar export with an alarm for every timed ritual.
- The app must state the closed-app limitation in plain language; it must not
  promise push notifications it cannot send.

## 8. Install and offline

- The app must be a valid PWA: manifest, icons, service worker, standalone
  display, theme colour, start URL and scope.
- It must be installable on iPhone (Safari → Share → Add to Home Screen) and
  Android (Chrome install prompt or menu).
- Settings must show install steps for iPhone, Android and desktop.
- When Chrome fires `beforeinstallprompt`, the install sheet must offer the
  native prompt.
- The app shell must load from the service worker with the network disabled.
- There are no backend API calls and no third-party requests. Fonts and icons
  are self-hosted; the service worker only re-fetches same-origin shell assets
  when online.

## 9. Data and privacy

- All data lives in `localStorage` under `habits/state/v1`; no server, no
  analytics, no third-party script.
- JSON export preserves namespaced keys; import replaces the whole list only
  after confirmation.
- Imports accept the older namespace-less `"list"` shape, and habits without a
  schedule become daily.
- Unreadable saved data is copied aside before the app starts fresh.
- Clearing all data requires an explicit second step and reloads to onboarding.
- The app must say that iOS can evict site data and that export is the backup.

## 10. Visual and interaction requirements

- Tailwind CSS, class-based dark mode, dark by default.
- A visible, animated flame; a run number in its unit; a next-slot progress bar.
- Rest days and quota progress are visible without looking like failures.
- Celebration is multi-channel (motion, colour, a live-region message) but the
  same state is always available as text.
- All controls are native `<button>`, `<a>`, `<input>`, `<label>`, `<select>`.
- Every input has a visible label; every modal traps focus, closes on Escape,
  and returns focus to its trigger.
- Body text meets 4.5:1; targets are at least 24×24, routinely 44×44.
- At 320 px there is no horizontal scroll and no control becomes unreachable.
- `prefers-reduced-motion` suppresses animation.
- Disabled controls explain why, via adjacent text and `aria-describedby`.

## 11. Non-goals

No accounts, sync, social features, leaderboards, payments, subscriptions,
analytics, third-party runtime scripts, AI coaching, health claims, or a server.

## 12. Acceptance checks

| # | Given | When | Then |
|---|---|---|---|
| 1 | no data | I finish onboarding | one ritual exists with a cadence, cue and optional time |
| 2 | one active ritual and no first proof | I try to add another | the add button is disabled and explains the unlock rule |
| 3 | a 3-day scheduled run | I open Today | a second slot is available |
| 4 | today unlit, previous scheduled day lit | I open Today | the live run is intact |
| 5 | a missed scheduled day, one shield banked | I tap Repair | the day is repaired, the run recovers, the shield is spent |
| 6 | a weekly quota at `2/3` | I log one more session | the card shows `3/3` and the week is complete |
| 7 | a timed, unlit ritual | its scheduled minute arrives while Ember is open | one alert appears and does not repeat after reload |
| 8 | a timed ritual | I tap Add all to calendar | an `.ics` file with the correct daily/weekly rule downloads |
| 9 | a fixed-day habit on an off day | I open Today | it shows Rest day and cannot be marked failed |
| 10 | a current-week quota | I open **Log a past day** | allowed past days can be corrected; future days cannot |
| 11 | a 30-day scheduled run or 8-week run | I open Progress | five slots are available; no purchase UI exists |
| 12 | a 320 px viewport | I open all screens | no horizontal scroll |
| 13 | the installed app with no network | I launch it | Today renders and a log persists |
| 14 | a JSON export | I import it | confirmation is required before replacement |
| 15 | a modal open | I press Escape | it closes and focus returns to the trigger |

## 13. Decisions log

| Date | Decision | Why |
|---|---|---|
| 2026-10-24 | `localStorage`, no backend | one person, one phone, no server to keep alive |
| 2026-10-24 | Import replaces after confirmation | simpler than merging; confirmation prevents the loss |
| 2026-10-27 | One starting slot, max five | the constraint is the product; it forces few promises |
| 2026-10-27 | Slots from real evidence only | success unlocks capacity; repairs cannot farm it |
| 2026-10-27 | Shields earned by real sessions | a landing net, not a purchased streak |
| 2026-10-27 | Dark-only Tailwind theme | one palette kept contrast-correct; the product is a night-fire |
| 2026-10-27 | In-app + calendar reminders | honest about the serverless scheduling limit |
| 2026-10-27 | No deceptive dark patterns | UI invariants outrank the brief; pressure is transparent and documented |
| 2026-10-27 | Namespace-safe JSON | `clj->js` would silently flatten `habits/list` to `list` |
| 2026-10-27 | Generic cadence layer | daily-only streak logic misrepresents fixed-day and quota habits |
| 2026-10-27 | Weeks are Monday–Sunday, local | one boundary, independent of timezone storage |
| 2026-10-27 | Normalise old habits to daily | migration never rewrites history |
| 2026-10-27 | Current week editable, past weeks not | logging mistakes are human; rewriting old evidence is not the fix |

## 14. Open questions

1. Is the 3/7/14/30 scheduled-day ladder too slow or too fast in real use?
2. Should a fixed-day habit allow a make-up session, or only a shield?
3. Should a quota's deadline be configurable, rather than the last allowed day?
4. Is the fixed 20:00 at-risk hour right, or should it follow the ritual's time?
5. Would a weekly review prompt help, and what should it ask?
6. Do enough people want a light theme to justify a second palette?

## 15. Next three things

1. A one-screen weekly review after Sunday.
2. A “what changed” note when a schedule edit alters the current week.
3. A local “export is stale” nudge after 30 days.
