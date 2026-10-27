# Ember — research brief

**Question.** What can a local-first, single-user habit app do that is genuinely
useful for building a recurring habit, and which persuasive mechanics are
worth the risk?

This is a design brief, not a literature review. Each finding is followed by the
decision it caused in Ember. Effect sizes are reported only where the source
reports them. Where the evidence is a company's A/B test rather than a
peer-reviewed study, it is labelled that way.

## 1. The findings that shaped the product

### 1.1 Habits are context-bound and slow — and one miss is not fatal

Lally et al. followed 96 people for 12 weeks as they repeated an eating,
drinking or activity behaviour in the same context. Automaticity increased on
an asymptotic curve. The time to reach 95% of a person's plateau ranged from
**18 to 254 days** (the often-quoted "66 days" is a median from the same
study, not a universal rule). Crucially: **missing one opportunity did not
materially affect the habit-formation process.** ([Lally et al., 2010,
*EJSP*](https://doi.org/10.1002/ejsp.674))

**Decision.** The app asks for a cue ("After morning coffee") and an optional
time. It does not pretend a streak is the habit. A shield can protect one
missed day, because the research says one miss is not the end of the habit.

### 1.2 "If situation Y, then I will do X" works

A meta-analysis of 94 independent tests found implementation intentions had a
**medium-to-large effect on goal attainment, d = 0.65**. The mechanism is that
the cue becomes highly accessible and the response becomes more automatic.
([Gollwitzer & Sheeran, 2006,
*Advances in Experimental Social Psychology*](https://doi.org/10.1016/S0065-2601(06)38002-1))

**Decision.** Onboarding is an implementation-intention form, not a name field.
Every habit stores a cue and shows it on the card. The commitment is editable,
so the user can correct a bad plan rather than abandon the app.

### 1.3 Visible streaks change behaviour, and repair matters

Across **seven studies**, Silverman and Barasch found that an intact logged
streak increased subsequent engagement relative to a broken one, independent of
actual past behaviour. The effect was stronger when the break was attributed to
the self, and **attenuated when consumers could repair the streak**.
([Silverman & Barasch, 2023, *Journal of Consumer Research*](https://doi.org/10.1093/jcr/ucac029))

**Decision.** The streak is the hero number. A broken chain is not a dead end:
the user can spend an earned shield to cover the previous missed scheduled day,
or freeze the previous quota week. Repairs do not count as completions and do
not unlock slots, so the mechanic protects a streak without fabricating success.

### 1.4 People accelerate near a goal

Kivetz, Urminsky and Zheng found that people visit more often as they approach
a reward, and that a 12-stamp card with two pre-existing "bonus" stamps was
completed faster than a 10-stamp card. The perceived progress itself changed
effort. ([Kivetz et al., 2006, *Journal of Marketing Research*](https://doi.org/10.1509/jmkr.43.1.39))

Nunes and Drèze independently documented the **endowed progress effect**:
artificial advancement increases effort. ([Nunes & Drèze, 2006, *JCR*](https://doi.org/10.1086/500480))

**Decision.** The slot ladder shows the *real* distance to the next slot, not a
fake one. We use the goal-gradient shape (a progress bar that accelerates near
the milestone) but not fake stamps. The honest version is still motivating, and
it survives the user checking the maths.

### 1.5 Loss aversion is real, and so is its dark side

Prospect theory established that losses loom larger than equivalent gains.
([Kahneman & Tversky, 1979,
*Econometrica*](https://doi.org/10.2307/1914185)). Duolingo's own A/B tests
report that a streak wager raised Day-7 retention by 14%, and that a "Weekend
Amulet" made learners 4% more likely to return a week later and 5% less likely
to lose their streak. Allowing **two** streak freezes instead of one was
followed by a 0.38% relative increase in daily active learners. These are
company tests, not peer-reviewed papers, but they are unusually concrete.
([Duolingo, 2017](https://blog.duolingo.com/how-streaks-keep-duolingo-learners-committed-to-their-language-goals/),
[Duolingo, 2022](https://blog.duolingo.com/how-duolingo-streak-builds-habit/))

The same 2022 post is candid that losing a streak can be *demotivating* and can
stop people starting at all. The design problem is therefore not "maximise
loss aversion"; it is "make the loss survivable enough to keep playing".

**Decision.** At-risk copy appears only after 20:00, only for habits that are
actually incomplete, and never invents a deadline. Shields are earned, capped,
and spent by an explicit action. No countdown timers, no "last chance", no
purchase.

### 1.6 A fresh start helps after a break

Across archival field studies, people were more likely to pursue goals after
temporal landmarks such as the start of a week, month or year. The proposed
mechanism is that the landmark closes a mental account and lets past
imperfections stay in the past. ([Dai, Milkman & Riis, 2014, *Management
Science*](https://doi.org/10.1287/mnsc.2014.1901))

**Decision.** The Progress screen has a "This week" chapter that resets every
Monday, while lifetime totals and best streaks never reset. A bad week costs a
week, not the identity.

### 1.7 Bundling a reward can help — and fades

In a field experiment, bundling tempting audiobooks with gym visits increased
gym visits by **51%** in the full-treatment group versus control; 61% of
participants later paid to keep the restriction. The effect declined over time.
([Milkman, Minson & Volpp, 2014, *Management Science*](https://doi.org/10.1287/mnsc.2013.1784))

**Decision.** The app asks for a one-line "why" and shows it on the card, but
does not build a reward economy. A fading reward would be another thing to
maintain; the cue and the streak are the durable loop.

### 1.8 Commitment devices work when the user chooses them

A commitment savings product in the Philippines — a restricted account with a
withdrawal lock — increased savings relative to a control. The point is not the
specific product; it is that **voluntary restriction** can beat pure willpower.
([Ashraf, Karlan & Yin, 2006, *QJE*](https://doi.org/10.1162/qjec.2006.121.2.635))

**Decision.** The app is deliberately constrained: one slot at the start,
unlocked by evidence. It is the user's own commitment, visible and reversible
by archiving a habit. Nothing is purchased or hidden.

## 2. What we deliberately did *not* build

"Dark patterns for the user's benefit" is a dangerous brief. Dark patterns are
usually defined as interface choices that **coerce, steer or deceive** users
into unintended decisions, often benefiting the service rather than the person.
A large-scale study of ~53,000 product pages found 1,818 dark-pattern instances
across 15 types. ([Mathur et al., 2019,
*CSCW*](https://doi.org/10.1145/3359183); [Gray et al., 2018,
*CHI*](https://doi.org/10.1145/3173574.3174108))

Ember uses *pressure*, not deception. The boundary is explicit in the app:

| Mechanic | Used? | How it stays honest |
|---|---|---|
| Scarcity (limited slots) | Yes | One slot at start; more earned by real best streaks; never purchasable |
| Loss aversion (flame, at-risk state) | Yes | Only after 20:00, only for incomplete habits, based on real data |
| Goal gradient (next-slot bar) | Yes | Real distance; no fake stamps |
| Endowed progress | Partly | The onboarding starts at "Day 1", not at a fabricated count |
| Variable reward | Yes, gently | Random non-monetary celebration copy; no loot boxes, no odds |
| Commitment device | Yes | Optional cue, time and "why"; archiving is reversible |
| Friction before quitting | Yes | Archive is reversible; permanent delete requires a second step |
| Countdown urgency | No | Deadlines are real times, never manufactured |
| Shame / confirm-shaming | No | "Start again" is the default after a miss |
| Fake social proof | No | No accounts, no invented users, no leaderboard |
| Hidden costs / roach motel | No | Free, local, exportable, deletable |

The product's ethical test: **if the user saw the mechanic written down, would
they still choose it?** The Settings screen includes a plain-language
"How Ember persuades" panel so the answer can be checked.

## 3. Notifications: what a no-server PWA can actually do

- The Notifications API requires a **secure context** and, in practice, a
  permission request triggered by a user gesture. On mobile browsers the
  reliable constructor is
  `ServiceWorkerRegistration.showNotification()`, not `new Notification()`.
  ([MDN, *Using the Notifications API*](https://developer.mozilla.org/en-US/docs/Web/API/Notifications_API/Using_the_Notifications_API))
- iOS and iPadOS 16.4+ support Web Push **only for web apps added to the Home
  Screen**, with permission requested after direct user interaction. Push
  requires a server with a push subscription. ([WebKit, *Web Push for Web Apps
  on iOS and iPadOS*](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/))
- There is no standards-based way for a closed, serverless web app to schedule a
  local notification for later. A service worker is not a cron daemon.

**Decision.** Ember implements two honest layers:

1. **While the app is open or installed and running**, a minute tick checks
   timed habits and shows a system notification via the service worker. If
   permission is unavailable, the same event becomes an in-app alert.
2. **When the app is closed**, a one-tap `.ics` download creates a recurring calendar
   event with an alarm for each timed habit. The phone's calendar is the
   scheduler; no server is involved.

The UI says this plainly instead of promising notifications it cannot deliver.
The calendar export is not a consolation prize — it is the reliable option on
iOS today.

## 4. Installability and data

- A web app is installable when it has a manifest, a service worker and is
  served over HTTPS (or localhost).
- iOS installs from Safari's Share → Add to Home Screen. Android/Chrome uses
  the install prompt or the browser menu.
- `localStorage` is the only store. It is private and requires no account, but
  iOS can evict it under storage pressure, so JSON export is the backup.
- No backend API calls, no analytics, no third-party scripts. Fonts and icons
  are self-hosted. The only outbound links are documentation citations.

## 5. Open questions and evidence gaps

1. The Duolingo numbers are company A/B tests, not independent replications.
   They are used as design evidence, not as proof of efficacy.
2. We have no outcome data for Ember itself. The slot ladder (3/7/14/30 days)
   is a product hypothesis, not a validated schedule.
3. The effect of celebration copy is unmeasured. It is included because it is
   low-cost and non-deceptive, not because a study says it works.
4. The shield economy has no empirical calibration. The cap of three banked
   shields and one shield per seven completions are judgement calls chosen to
   make repair possible without making the streak meaningless.
5. Notification delivery on a closed PWA remains platform-dependent. The
   calendar fallback is the only serverless route with an OS-level guarantee.

## References

- Ashraf, N., Karlan, D., & Yin, W. (2006). Tying Odysseus to the Mast.
  *Quarterly Journal of Economics*, 121(2). https://doi.org/10.1162/qjec.2006.121.2.635
- Dai, H., Milkman, K. L., & Riis, J. (2014). The Fresh Start Effect.
  *Management Science*, 60(10). https://doi.org/10.1287/mnsc.2014.1901
- Gollwitzer, P. M., & Sheeran, P. (2006). Implementation Intentions and Goal
  Achievement. *Advances in Experimental Social Psychology*, 38.
  https://doi.org/10.1016/S0065-2601(06)38002-1
- Gray, C. M., Kou, Y., Battles, B., Hoggatt, J., & Toombs, A. L. (2018). The
  Dark (Patterns) Side of UX Design. *CHI '18*.
  https://doi.org/10.1145/3173574.3174108
- Kahneman, D., & Tversky, A. (1979). Prospect Theory. *Econometrica*, 47(2).
  https://doi.org/10.2307/1914185
- Kivetz, R., Urminsky, O., & Zheng, Y. (2006). The Goal-Gradient Hypothesis
  Resurrected. *Journal of Marketing Research*, 43(1).
  https://doi.org/10.1509/jmkr.43.1.39
- Lally, P., van Jaarsveld, C. H. M., Potts, H. W. W., & Wardle, J. (2010). How
  are habits formed. *European Journal of Social Psychology*, 40(6).
  https://doi.org/10.1002/ejsp.674
- Mathur, A., Acar, G., Friedman, M. J., Lucherini, E., Mayer, J., Chetty, M.,
  & Narayanan, A. (2019). Dark Patterns at Scale. *CSCW*.
  https://doi.org/10.1145/3359183
- Milkman, K. L., Minson, J. A., & Volpp, K. G. M. (2014). Holding the Hunger
  Games Hostage at the Gym. *Management Science*, 60(2).
  https://doi.org/10.1287/mnsc.2013.1784
- Nunes, J. C., & Drèze, X. (2006). The Endowed Progress Effect. *Journal of
  Consumer Research*, 32(4). https://doi.org/10.1086/500480
- Silverman, J., & Barasch, A. (2023). On or Off Track. *Journal of Consumer
  Research*, 49(6). https://doi.org/10.1093/jcr/ucac029
- Duolingo. (2017). *How Streaks keep Duolingo learners committed*.
  https://blog.duolingo.com/how-streaks-keep-duolingo-learners-committed-to-their-language-goals/
- Duolingo. (2022). *The habit-building research behind your Duolingo streak*.
  https://blog.duolingo.com/how-duolingo-streak-builds-habit/
- WebKit. (2023). *Web Push for Web Apps on iOS and iPadOS*.
  https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/
- MDN. *Using the Notifications API*.
  https://developer.mozilla.org/en-US/docs/Web/API/Notifications_API/Using_the_Notifications_API
