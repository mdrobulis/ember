(ns habits.model
  "Pure domain logic: dates, streaks, slots, shields, reminders and state
  transitions.

  The state is a plain map, and it crosses namespaces, so every key carries its
  namespace:

    {:habits/list        [{:habit/id \"...\"                 ; uuid string
                           :habit/name \"Read 10 pages\"
                           :habit/created-at \"2026-10-24\"
                           :habit/completions #{\"2026-10-24\" ...}
                           :habit/repairs #{}
                           :habit/cue \"After morning coffee\"
                           :habit/reminder \"08:30\"         ; or nil
                           :habit/why \"Think clearly\"
                           :habit/archived? false}]
     :app/today          \"2026-10-24\"                    ; the day being edited
     :app/now-minutes    510                              ; device clock, 0..1439
     :app/error          \"Give the habit a name.\"        ; present only on failure
     :app/pending-delete \"...\"}                          ; habit id awaiting confirmation

  Dates are `YYYY-MM-DD` strings in the device's local calendar. In that format
  lexicographic order is chronological order, which the streak functions rely
  on. Every function here is pure: ids and dates are passed in by the caller,
  never generated. That is what makes the rules testable without a browser."
  (:require [clojure.string :as str]))

(def ^:private iso-pattern #"^(\d{4})-(\d{2})-(\d{2})$")
(def ^:private time-pattern #"^([01]?\d|2[0-3]):([0-5]\d)$")

(def ^:private day-ms
  ;; Date arithmetic runs at 12:00 UTC so a daylight-saving shift cannot move
  ;; the calendar day by one.
  86400000)

(def ^:private minutes-in-day 1440)

(def ^:private max-slots 5)

(def ^:private slot-milestones
  "Each entry is the best actual streak that unlocks the next slot."
  [{:slot 2 :at 3  :title "Room for two"}
   {:slot 3 :at 7  :title "A week of proof"}
   {:slot 4 :at 14 :title "Two weeks deep"}
   {:slot 5 :at 30 :title "A month of fire"}])

(def ^:private shield-cap 3)

(defn- pad2 [n]
  (if (< n 10) (str "0" n) (str n)))

(defn- utc-iso [^js d]
  (str (.getUTCFullYear d)
       "-" (pad2 (inc (.getUTCMonth d)))
       "-" (pad2 (.getUTCDate d))))

(defn parts
  "The `[year month day]` numbers of an ISO date string, or nil when `s` is not
  a real calendar date in `YYYY-MM-DD` form."
  [s]
  (when (string? s)
    (when-let [[_ y m d] (re-matches iso-pattern s)]
      (let [year  (js/parseInt y 10)
            month (js/parseInt m 10)
            day   (js/parseInt d 10)
            ms    (js/Date.UTC year (dec month) day 12)]
        (when (= s (utc-iso (js/Date. ms)))
          [year month day])))))

(defn iso-date?
  "True when `s` is a real `YYYY-MM-DD` date."
  [s]
  (some? (parts s)))

(defn today
  "The device's local calendar date as `YYYY-MM-DD`."
  []
  (let [now (js/Date.)]
    (str (.getFullYear now)
         "-" (pad2 (inc (.getMonth now)))
         "-" (pad2 (.getDate now)))))

(defn now-minutes
  "Minutes since local midnight, 0..1439."
  []
  (let [now (js/Date.)]
    (+ (* 60 (.getHours now)) (.getMinutes now))))

(defn shift-days
  "The ISO date `n` days after `iso-day`; negative `n` moves backwards."
  [iso-day n]
  (if-let [[year month day] (parts iso-day)]
    (utc-iso (js/Date. (+ (.getTime (js/Date. (js/Date.UTC year (dec month) day 12)))
                          (* n day-ms))))
    (throw (ex-info "Not an ISO date." {:habits.error/type :habits/not-a-date
                                        :habits/date      iso-day}))))

(defn parse-time
  "Minutes since midnight for an `HH:MM` string, or nil when it is not a valid
  24-hour time. `8:30` is accepted and normalised by `format-time`."
  [s]
  (when (and (string? s) (re-matches time-pattern s))
    (let [[_ h m] (re-matches time-pattern s)]
      (+ (* 60 (js/parseInt h 10)) (js/parseInt m 10)))))

(defn format-time
  "`HH:MM` from minutes since midnight, or nil for an unusable number."
  [minutes]
  (when (and (number? minutes) (<= 0 minutes) (< minutes minutes-in-day))
    (str (pad2 (quot minutes 60)) ":" (pad2 (mod minutes 60)))))

(defn shift-minutes
  "`[iso-day minutes]` moved forward by `n` minutes, rolling the date if needed."
  [iso-day minutes n]
  (let [total (+ minutes (or n 0))
        days  (js/Math.floor (/ total minutes-in-day))]
    [(shift-days iso-day days) (mod total minutes-in-day)]))

(defn- day-offset
  "Days to subtract to reach Monday, for a date. Sunday is 6."
  [iso-day]
  (let [[year month day] (parts iso-day)
        dow (.getUTCDay (js/Date. (js/Date.UTC year (dec month) day 12)))]
    (mod (+ dow 6) 7)))

(defn week-days
  "The seven ISO dates of the Monday–Sunday week containing `iso-day`."
  [iso-day]
  (let [monday (shift-days iso-day (- (day-offset iso-day)))]
    (mapv #(shift-days monday %) (range 7))))

(defn last-days
  "The `n` most recent days ending on `iso-day`, oldest first."
  [iso-day n]
  (mapv (fn [offset] (shift-days iso-day (- offset)))
        (range (dec n) -1 -1)))

(defn sort-habits
  "Habits oldest first. Habits without a creation date sort first."
  [habits]
  (sort-by (comp str :habit/created-at) habits))

(defn active-habits [state]
  (filterv (complement :habit/archived?) (:habits/list state)))

(defn archived-habits [state]
  (filterv :habit/archived? (:habits/list state)))

(defn done-on?
  "True when `habit` has a real completion recorded for `iso-day`."
  [{:habit/keys [completions]} iso-day]
  (contains? completions iso-day))

(defn repaired-on?
  "True when `iso-day` was covered by a spent shield."
  [{:habit/keys [repairs]} iso-day]
  (contains? repairs iso-day))

(defn chain-day?
  "A day that counts in the visible streak: either really done or repaired."
  [habit iso-day]
  (or (done-on? habit iso-day) (repaired-on? habit iso-day)))

(defn current-streak
  "Unbroken run of chain days ending today — or ending yesterday when today is
  not ticked yet, so a streak is not lost until a day is actually missed."
  [{:habit/keys [completions repairs]} iso-day]
  (let [habit {:habit/completions completions
               :habit/repairs     (or repairs #{})}]
    (loop [day (if (chain-day? habit iso-day) iso-day (shift-days iso-day -1))
           run 0]
      (if (chain-day? habit day)
        (recur (shift-days day -1) (inc run))
        run))))

(defn- longest-run
  [days]
  (loop [days (sort days)
         prev nil
         run  0
         best 0]
    (if-let [day (first days)]
      (let [run' (if (and prev (= day (shift-days prev 1))) (inc run) 1)]
        (recur (rest days) day run' (max best run')))
      best)))

(defn longest-streak
  "The longest run of real completions, ever. Repairs do not count: a shield
  protects a visible streak but does not manufacture evidence."
  [{:habit/keys [completions]}]
  (longest-run completions))

(defn longest-chain
  "The longest run of completions plus repaired days, used for narrative only."
  [{:habit/keys [completions repairs]}]
  (longest-run (into (or completions #{}) (or repairs #{}))))

(defn shields-earned
  "One shield for every seven real completions, banked up to three."
  [{:habit/keys [completions]}]
  (min shield-cap (quot (count (or completions #{})) 7)))

(defn shields-spent
  [{:habit/keys [repairs]}]
  (count (or repairs #{})))

(defn shield-balance
  "Shields available to spend. Repairs are the only thing that spends them."
  [habit]
  (max 0 (- (shields-earned habit) (shields-spent habit))))

(defn can-repair?
  "True when `habit` can spend a shield to cover yesterday. Only yesterday is
  repairable: the point is a landing net, not a time machine."
  [habit iso-day]
  (let [yesterday (shift-days iso-day -1)]
    (and (not (:habit/archived? habit))
         (pos? (shield-balance habit))
         (not (chain-day? habit yesterday)))))

(defn repair-day
  "Cover yesterday with a shield. Returns state unchanged when the rule does not
  allow it; the view explains why rather than silently failing."
  [state id iso-day]
  (let [yesterday (shift-days iso-day -1)]
    (update state :habits/list
            (fn [habits]
              (mapv (fn [habit]
                      (if (and (= id (:habit/id habit))
                               (can-repair? habit iso-day))
                        (update habit :habit/repairs (fnil conj #{}) yesterday)
                        habit))
                    habits)))))

(defn global-best-streak
  "The best real streak across every habit, archived ones included. This is the
  evidence that unlocks slots and it never goes down."
  [habits]
  (reduce max 0 (map longest-streak habits)))

(defn unlocked-slots
  "Slots earned so far: one to begin, plus one for each best-streak milestone.
  `floor` carries already-earned slots forward, so undoing a completion or
  deleting old evidence never takes capacity away."
  ([habits]
   (unlocked-slots habits 1))
  ([habits floor]
   (min max-slots
        (max (or floor 1)
             (inc (count (filter #(<= (:at %) (global-best-streak habits))
                                 slot-milestones)))))))

(defn state-unlocked-slots
  "The slot count carried by a full state map."
  [state]
  (unlocked-slots (:habits/list state) (:app/earned-slots state)))

(defn ensure-earned-slots
  "Promote the persisted earned-slot floor to at least the current evidence.
  Called at the effectful edge after every event; pure functions treat a missing
  floor as one."
  [state]
  (let [floor (min max-slots
                   (max (or (:app/earned-slots state) 1)
                        (unlocked-slots (:habits/list state))))]
    (assoc state :app/earned-slots floor)))

(defn active-count [state]
  (count (active-habits state)))

(defn can-add-habit?
  "Slots limit active habits, not archived history. Once earned, a slot is
  permanent for the lifetime of this dataset."
  [state]
  (< (active-count state) (state-unlocked-slots state)))

(defn next-slot
  "The next locked slot, with the real progress toward it, or nil at the top.
  An earned-slot `floor` keeps the ladder from showing a milestone that has
  already been banked, even if the evidence was later undone or deleted."
  ([habits]
   (next-slot habits 1))
  ([habits floor]
   (let [floor (min max-slots (max 1 (or floor 1)))
         banked (or (some #(when (= (:slot %) floor) (:at %)) slot-milestones) 0)
         best (max (global-best-streak habits) banked)]
     (when-let [slot (first (filter #(< best (:at %)) slot-milestones))]
       (let [previous (or (:at (last (filter #(<= (:at %) best) slot-milestones))) 0)
             span     (- (:at slot) previous)
             done     (- best previous)]
         (assoc slot
                :best best
                :previous previous
                :remaining (- (:at slot) best)
                :progress (if (pos? span) (max 0 (min 1 (/ done span))) 0)))))))

(defn completion-rate
  "Completed days in the last `window` days as a percentage. Repairs are not
  completions. A habit created inside the window is measured from its creation
  day, so a new habit is not punished for days before it existed."
  [habit iso-day window]
  (let [start (max (or (:habit/created-at habit) (shift-days iso-day (dec window)))
                   (shift-days iso-day (dec window)))
        days  (filter #(<= (compare start %) 0) (last-days iso-day window))
        done  (count (filter #(done-on? habit %) days))]
    (if (seq days) (js/Math.round (* 100 (/ done (count days)))) 0)))

(defn total-done
  [{:habit/keys [completions]}]
  (count (or completions #{})))

(defn total-shows
  "All real completions across the list. Repairs are excluded."
  [habits]
  (reduce + 0 (map total-done habits)))

(defn weekly-counts
  "Counts of real completions per day for the Monday–Sunday week containing
  `iso-day`. Future days are zero and are not distinguished here."
  [habits iso-day]
  (mapv (fn [day]
          {:day day
           :count (count (filter #(done-on? % day) habits))})
        (week-days iso-day)))

(defn habit-heatmap
  "The last 35 days for one habit, oldest first, each with its state."
  [habit iso-day]
  (mapv (fn [day]
          {:day  day
           :done (done-on? habit day)
           :repaired (repaired-on? habit day)})
        (last-days iso-day 35)))

(defn at-risk?
  "A habit is at risk only after 20:00 on a day it is not lit. The cut-off is
  a real clock, not a manufactured urgency."
  [habit iso-day now-minute]
  (and (not (:habit/archived? habit))
       (not (done-on? habit iso-day))
       (>= now-minute 1200)))

(defn greeting
  [minute-of-day]
  (cond
    (< minute-of-day 300) "Still up?"
    (< minute-of-day 720) "Good morning"
    (< minute-of-day 1020) "Good afternoon"
    (< minute-of-day 1260) "Good evening"
    :else "Late, but not too late"))

(defn reminder-key
  [habit iso-day]
  (str (:habit/id habit) "|" iso-day "|" (:habit/reminder habit)))

(defn due-reminders
  "Active, timed, still-unlit habits whose reminder fell within the last 30
  minutes and has not already fired today. Returns a vector of
  `{:habit ... :minute ... :key ...}`.

  The 30-minute window means opening the app shortly after the time still
  surfaces the reminder, without firing one from hours ago."
  [habits iso-day now-minute notified]
  (->> habits
       (filter #(not (:habit/archived? %)))
       (keep (fn [habit]
               (when-let [minute (parse-time (:habit/reminder habit))]
                 (when (and (not (done-on? habit iso-day))
                            (<= minute now-minute)
                            (< (- now-minute minute) 30)
                            (not (contains? notified (reminder-key habit iso-day))))
                   {:habit habit :minute minute :key (reminder-key habit iso-day)}))))
       vec))

(defn next-reminder
  "The next timed reminder later today, or nil."
  [habits iso-day now-minute]
  (->> habits
       (filter #(not (:habit/archived? %)))
       (keep (fn [habit]
               (when-let [minute (parse-time (:habit/reminder habit))]
                 (when (and (not (done-on? habit iso-day))
                            (> minute now-minute))
                   {:habit habit :minute minute}))))
       (sort-by :minute)
       first))

(def ^:private export-version 1)

(defn export-data
  "A serialisable snapshot of the habits, stamped with `iso-now`."
  [state iso-now]
  {:habits/version        export-version
   :habits/exported-at    iso-now
   :habits/earned-slots   (or (:app/earned-slots state) 1)
   :habits/list           (:habits/list state)})

(defn- clean-text
  [value max-length]
  (let [text (str/trim (str (or value "")))]
    (if (> (count text) max-length)
      (subs text 0 max-length)
      text)))

(defn- read-repairs
  [candidate]
  (into #{} (filter iso-date?) (:habit/repairs candidate)))

(defn- read-habit
  "Validate and normalise one habit read from a file or from storage. Throws
  `ex-info` when it has no usable id or name. Unknown keys are kept, so a file
  written by a newer version still imports."
  [candidate index]
  (let [{:habit/keys [id name]} candidate]
    (when-not (and (string? id) (not (str/blank? id)))
      (throw (ex-info "A habit in that file has no id."
                      {:habits.error/type :habits/import-invalid
                       :habits/index      index})))
    (when-not (and (string? name) (not (str/blank? name)))
      (throw (ex-info "A habit in that file has no name."
                      {:habits.error/type :habits/import-invalid
                       :habits/index      index})))
    (assoc candidate
           :habit/name        (str/trim name)
           :habit/cue         (clean-text (:habit/cue candidate) 80)
           :habit/why         (clean-text (:habit/why candidate) 120)
           :habit/reminder    (when (parse-time (:habit/reminder candidate))
                                (format-time (parse-time (:habit/reminder candidate))))
           :habit/completions (into #{} (filter iso-date?) (:habit/completions candidate))
           :habit/repairs     (read-repairs candidate)
           :habit/archived?   (true? (:habit/archived? candidate)))))

(defn parse-habits
  "Validate a sequence of habit maps, as read from an exported file or from
  storage. Returns a vector. Throws `ex-info` when the shape is not usable."
  [habits]
  (when-not (sequential? habits)
    (throw (ex-info "That file is not a habit export."
                    {:habits.error/type :habits/import-invalid
                     :habits/value      (type habits)})))
  (mapv read-habit habits (range)))

(defn add-habit
  "Append `habit`, which must already carry :habit/id and :habit/name. Defaults
  the optional implementation-intention fields and the two day sets.

  A blank name sets :app/error. A full slot ladder sets a different error: the
  limit is stated, never silently ignored."
  [state habit]
  (let [trimmed (str/trim (str (:habit/name habit)))]
    (cond
      (str/blank? trimmed)
      (assoc state :app/error "Give the habit a name.")

      (not (can-add-habit? state))
      (assoc state :app/error
             (str "All " (state-unlocked-slots state)
                  " flame slots are full. Archive a habit, or grow a streak to unlock another."))

      :else
      (-> state
          (update :habits/list (fnil conj [])
                  (-> habit
                      (assoc :habit/name trimmed)
                      (update :habit/completions #(or % #{}))
                      (update :habit/repairs #(or % #{}))
                      (update :habit/cue clean-text 80)
                      (update :habit/why clean-text 120)
                      (update :habit/reminder
                              (fn [value]
                                (when-let [minute (parse-time value)]
                                  (format-time minute))))
                      (update :habit/archived? boolean)))
          (dissoc :app/error)))))

(defn- update-habit
  [state id f]
  (update state :habits/list
          (fn [habits]
            (mapv (fn [habit]
                    (if (= id (:habit/id habit)) (f habit) habit))
                  habits))))

(defn toggle-completion
  "Record or clear today's real completion for habit `id`."
  [state id iso-day]
  (update-habit state id
                (fn [habit]
                  (update habit :habit/completions
                          (fn [completions]
                            (if (contains? completions iso-day)
                              (disj completions iso-day)
                              (conj completions iso-day)))))))

(defn edit-habit
  "Edit the implementation intention and display fields. Only the keys the user
  can change are accepted; id, history and slot evidence are not editable."
  [state id changes]
  (let [name (some-> (:habit/name changes) str/trim)]
    (if (and (contains? changes :habit/name) (str/blank? name))
      (assoc state :app/error "A habit needs a name.")
      (-> (update-habit
           state id
           (fn [habit]
             (cond-> habit
               (contains? changes :habit/name)
               (assoc :habit/name name)

               (contains? changes :habit/cue)
               (assoc :habit/cue (clean-text (:habit/cue changes) 80))

               (contains? changes :habit/why)
               (assoc :habit/why (clean-text (:habit/why changes) 120))

               (contains? changes :habit/reminder)
               (assoc :habit/reminder
                      (when-let [minute (parse-time (:habit/reminder changes))]
                        (format-time minute))))))
          (dissoc :app/error)))))

(defn set-archived
  "Archive or restore a habit. Archiving frees a slot; history and earned slots
  stay. Reversible without a confirmation dialog."
  [state id archived?]
  (-> (update-habit state id #(assoc % :habit/archived? (boolean archived?)))
      (dissoc :app/pending-delete)))

(defn ask-to-delete
  "Mark habit `id` as awaiting delete confirmation."
  [state id]
  (assoc state :app/pending-delete id))

(defn cancel-delete
  "Drop a pending delete confirmation."
  [state]
  (dissoc state :app/pending-delete))

(defn delete-habit
  "Remove habit `id` and its history. Destructive and irreversible, so the
  caller asks first with `ask-to-delete`. Deleting a habit also gives back its
  slot if it was active."
  [state id]
  (-> state
      (update :habits/list (fn [habits] (filterv #(not= id (:habit/id %)) habits)))
      (dissoc :app/pending-delete)))

(defn import-data
  "Replace the habit list with `data`, the parsed contents of an exported file.
  Throws `ex-info` when the file is not a habit export. Replacing is
  destructive, so the view asks before calling this."
  [state data]
  (let [list (or (:habits/list data) (:list data))
        habits (parse-habits list)
        floor (min max-slots
                   (max (or (:habits/earned-slots data) 1)
                        (unlocked-slots habits)))]
    (-> state
        (assoc :habits/list habits :app/earned-slots floor)
        (dissoc :app/error :app/pending-import))))

(defn apply-event
  "Apply one UI event to `state`, e.g. `[:add habit]` or `[:toggle id day]`.

  The one impure thing here is the warning for an unknown event: it can only be
  a programming mistake, and ignoring it silently would hide the bug."
  [state [event & args]]
  (case event
    :add           (apply add-habit state args)
    :toggle        (apply toggle-completion state args)
    :repair        (apply repair-day state args)
    :edit          (apply edit-habit state args)
    :archive       (set-archived state (first args) true)
    :unarchive     (set-archived state (first args) false)
    :ask-delete    (apply ask-to-delete state args)
    :cancel-delete (cancel-delete state)
    :delete        (apply delete-habit state args)
    :import        (apply import-data state args)
    :error         (assoc state :app/error (first args))
    :clear-error   (dissoc state :app/error)
    :screen        (assoc state :app/screen (first args))
    :toast         (assoc state :app/toast (first args))
    :clear-toast   (dissoc state :app/toast)
    :notified      (update state :app/notified (fnil conj #{}) (first args))
    :notification-permission (assoc state :app/notification-permission (first args))
    :update-clock  (assoc state :app/today (first args) :app/now-minutes (second args))
    (do (.warn js/console "Unknown event:" (pr-str event))
        state)))

;; ---------------------------------------------------------------------------
;; Calendar export
;;
;; A closed serverless PWA cannot schedule an OS notification for later. A
;; daily calendar event with an alarm can, so it is the reliable fallback for
;; timed habits.

(defn- escape-ics
  [text]
  (-> (str (or text ""))
      (str/replace "\\" "\\\\")
      (str/replace "\n" "\\n")
      (str/replace "," "\\,")
      (str/replace ";" "\\;")))

(defn- ics-date
  [iso-day]
  (str/replace iso-day "-" ""))

(defn- ics-stamp
  [iso-day]
  (str (ics-date iso-day) "T120000Z"))

(defn- event-lines
  [habit iso-day now-minute]
  (let [minute (parse-time (:habit/reminder habit))]
    (when minute
      (let [[day _] (shift-minutes iso-day minute 0)
            start-day (if (> minute now-minute) day (shift-days day 1))
            [end-day end-minute] (shift-minutes start-day minute 15)
            cue (:habit/cue habit)
            description (str "Ember ritual: " (:habit/name habit)
                             (when-not (str/blank? cue) (str "\nCue: " cue))
                             "\nOpen Ember and light the flame.")]
        [(str "BEGIN:VEVENT")
         (str "UID:" (:habit/id habit) "@ember.local")
         (str "DTSTAMP:" (ics-stamp iso-day))
         (str "DTSTART:" (ics-date start-day) "T" (str/replace (format-time minute) ":" "") "00")
         (str "DTEND:" (ics-date end-day) "T" (str/replace (format-time end-minute) ":" "") "00")
         "RRULE:FREQ=DAILY"
         (str "SUMMARY:" (escape-ics (str "Ember: " (:habit/name habit))))
         (str "DESCRIPTION:" (escape-ics description))
         "BEGIN:VALARM"
         "TRIGGER:-PT0M"
         "ACTION:DISPLAY"
         (str "DESCRIPTION:" (escape-ics (str "Time for " (:habit/name habit))))
         "END:VALARM"
         "END:VEVENT"]))))

(defn calendar-ics
  "A complete iCalendar document with one daily alarm per timed, active habit.
  Floating local times are used, so the phone's calendar follows its own
  timezone. Returns nil when there are no timed habits."
  [habits iso-day now-minute]
  (let [events (->> habits
                    (remove :habit/archived?)
                    (keep #(event-lines % iso-day now-minute))
                    (mapcat identity))]
    (when (seq events)
      (str/join "\r\n"
                (concat ["BEGIN:VCALENDAR"
                         "VERSION:2.0"
                         "PRODID:-//Ember//Habit reminders//EN"
                         "CALSCALE:GREGORIAN"
                         "METHOD:PUBLISH"
                         "X-WR-CALNAME:Ember reminders"
                         "X-WR-CALDESC:Daily alarms for timed Ember rituals"]
                        events
                        ["END:VCALENDAR"])))))
