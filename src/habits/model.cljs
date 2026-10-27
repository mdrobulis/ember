(ns habits.model
  "Pure domain logic for a habit's cadence, periods, streaks, slots, shields and
  reminders.

  A habit carries a `:habit/schedule` map:

    {:schedule/type :daily}
    {:schedule/type :fixed-days :schedule/days #{1 3 5}}
    {:schedule/type :weekly-quota :schedule/target 3
     :schedule/days #{1 2 3 4 5}}          ; optional allowed-day window

  `:schedule/type` values are keywords in Clojure and strings in JSON; the
  boundary normaliser accepts both. Missing or unusable schedules normalise to
  daily, which is the migration path for the original app.

  Dates are `YYYY-MM-DD` strings in the device's local calendar. Every function
  here is pure: ids, dates and the clock are passed in by the caller."
  (:require [clojure.string :as str]))

(def ^:private iso-pattern #"^(\d{4})-(\d{2})-(\d{2})$")
(def ^:private time-pattern #"^([01]?\d|2[0-3]):([0-5]\d)$")

(def ^:private day-ms
  ;; Date arithmetic runs at 12:00 UTC so a daylight-saving shift cannot move
  ;; the calendar day by one.
  86400000)

(def ^:private minutes-in-day 1440)
(def ^:private max-slots 5)
(def ^:private shield-cap 3)

(def ^:private proof-thresholds
  "How a habit earns one of the four consistency points. Capacity is global, so
  the two units map onto the same four rungs instead of comparing a week with a
  day."
  {:day  [3 7 14 30]
   :week [1 2 4 8]})

(def ^:private slot-titles
  {2 "Room for two"
   3 "A week of proof"
   4 "Two weeks deep"
   5 "A month of fire"})

(defn- pad2 [n]
  (if (< n 10) (str "0" n) (str n)))

(defn- utc-iso [^js d]
  (str (.getUTCFullYear d)
       "-" (pad2 (inc (.getUTCMonth d)))
       "-" (pad2 (.getUTCDate d))))

(defn- int-value [value]
  (cond
    (number? value) (js/Math.floor value)
    (string? value) (let [n (js/parseInt value 10)]
                      (when-not (js/isNaN n) n))
    :else nil))

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
  "Minutes since midnight for an `HH:MM` string, or nil when it is not valid."
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

(defn iso-weekday
  "ISO weekday: Monday = 1 … Sunday = 7."
  [iso-day]
  (let [[year month day] (parts iso-day)
        dow (.getUTCDay (js/Date. (js/Date.UTC year (dec month) day 12)))]
    (if (zero? dow) 7 dow)))

(defn week-start
  "The Monday of the local week containing `iso-day`."
  [iso-day]
  (shift-days iso-day (- (day-offset iso-day))))

(defn week-end
  "The Sunday of the local week containing `iso-day`."
  [iso-day]
  (shift-days (week-start iso-day) 6))

(defn week-days
  "The seven ISO dates of the Monday–Sunday week containing `iso-day`."
  [iso-day]
  (let [monday (week-start iso-day)]
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

;; ---------------------------------------------------------------------------
;; Schedule

(defn normalize-schedule
  "Validate a schedule. Missing or unusable data becomes daily, which is the
  migration path for the original daily-only app."
  [schedule]
  (let [schedule (or schedule {})
        raw-type (:schedule/type schedule)
        type     (if (or (keyword? raw-type) (string? raw-type))
                   (keyword (name raw-type))
                   :daily)
        days     (->> (:schedule/days schedule)
                      (keep int-value)
                      (filter #(<= 1 % 7))
                      set)
        days     (when (seq days) days)]
    (case type
      :fixed-days
      (if days
        {:schedule/type :fixed-days :schedule/days days}
        {:schedule/type :daily})

      :weekly-quota
      (let [cap    (if days (count days) 7)
            target (max 1 (min cap (or (int-value (:schedule/target schedule)) 3)))]
        (cond-> {:schedule/type :weekly-quota :schedule/target target}
          days (assoc :schedule/days days)))

      {:schedule/type :daily})))

(defn schedule-of
  [habit]
  (normalize-schedule (:habit/schedule habit)))

(defn schedule-type
  [habit]
  (:schedule/type (schedule-of habit)))

(defn schedule-days
  "The days that matter: the fixed weekdays, or the allowed-day window of a
  quota. Nil means every day is allowed."
  [habit]
  (:schedule/days (schedule-of habit)))

(defn schedule-target
  [habit]
  (:schedule/target (schedule-of habit)))

(defn daily-schedule? [habit] (= :daily (schedule-type habit)))
(defn fixed-days? [habit] (= :fixed-days (schedule-type habit)))
(defn weekly-quota? [habit] (= :weekly-quota (schedule-type habit)))

(defn due-day?
  "True when a daily or fixed-day habit is scheduled on `iso-day`. A weekly
  quota has no single due day; use `quota-allowed-day?` or week functions."
  [habit iso-day]
  (case (schedule-type habit)
    :fixed-days (contains? (schedule-days habit) (iso-weekday iso-day))
    :weekly-quota false
    true))

(defn quota-allowed-day?
  "True when a weekly quota allows a session on `iso-day`. No day window means
  any day is allowed."
  [habit iso-day]
  (let [days (schedule-days habit)]
    (or (nil? days) (contains? days (iso-weekday iso-day)))))

(defn quota-deadline-weekday
  "The last weekday on which a weekly quota can be completed."
  [habit]
  (if-let [days (schedule-days habit)]
    (apply max days)
    7))

(defn quota-deadline-day
  "The date of this week's quota deadline."
  [habit iso-day]
  (let [deadline (quota-deadline-weekday habit)]
    (first (filter #(= deadline (iso-weekday %)) (week-days iso-day)))))

;; ---------------------------------------------------------------------------
;; Completion and weekly periods

(defn done-on?
  "True when `habit` has a real completion recorded for `iso-day`."
  [{:habit/keys [completions]} iso-day]
  (contains? completions iso-day))

(defn repaired-on?
  "True when `iso-day` was covered by a spent shield."
  [{:habit/keys [repairs]} iso-day]
  (contains? repairs iso-day))

(defn chain-day?
  "A day that counts in a daily or fixed-day streak."
  [habit iso-day]
  (or (done-on? habit iso-day) (repaired-on? habit iso-day)))

(defn quota-week-count
  "Real completions inside the week beginning `week-start`, respecting an
  allowed-day window. Repairs and frozen weeks are not sessions."
  [habit week-start]
  (let [allowed (schedule-days habit)]
    (count (filter (fn [day]
                     (and (done-on? habit day)
                          (or (nil? allowed)
                              (contains? allowed (iso-weekday day)))))
                   (week-days week-start)))))

(defn week-complete?
  "True when a quota week met its target with real sessions."
  [habit week-start]
  (and (weekly-quota? habit)
       (>= (quota-week-count habit week-start)
           (schedule-target habit))))

(defn week-frozen?
  "True when a previous quota week was covered by a shield."
  [{:habit/keys [frozen-weeks]} week-start]
  (contains? (or frozen-weeks #{}) week-start))

(defn week-chain?
  "A quota week that counts in the visible streak."
  [habit week-start]
  (or (week-complete? habit week-start)
      (week-frozen? habit week-start)))

(defn available-today?
  "True when the habit has an action available today: a scheduled day for daily
  and fixed cadences, or an allowed day with an incomplete quota."
  [habit iso-day]
  (case (schedule-type habit)
    :weekly-quota (and (quota-allowed-day? habit iso-day)
                       (not (week-complete? habit iso-day)))
    (due-day? habit iso-day)))

(defn- previous-due-day
  [habit iso-day]
  (loop [offset 1]
    (let [candidate (shift-days iso-day (- offset))]
      (if (due-day? habit candidate)
        candidate
        (recur (inc offset))))))

(defn- next-due-day
  [habit iso-day]
  (loop [offset 1]
    (let [candidate (shift-days iso-day offset)]
      (if (due-day? habit candidate)
        candidate
        (recur (inc offset))))))

(defn- due-day-streak
  [habit iso-day]
  (let [today-chain? (and (due-day? habit iso-day)
                          (chain-day? habit iso-day))
        start (if today-chain?
                iso-day
                (previous-due-day habit iso-day))]
    (loop [day start
           run 0]
      (if (chain-day? habit day)
        (recur (previous-due-day habit day) (inc run))
        run))))

(defn- quota-streak
  [habit iso-day]
  (let [current (week-start iso-day)
        start (if (week-chain? habit current)
                current
                (shift-days current -7))]
    (loop [week start
           run 0]
      (if (week-chain? habit week)
        (recur (shift-days week -7) (inc run))
        run))))

(defn current-streak
  "The visible streak in the habit's unit: scheduled days for daily/fixed
  habits, successful weeks for weekly quotas. A current period that is not yet
  overdue does not break the streak."
  [habit iso-day]
  (case (schedule-type habit)
    :weekly-quota (quota-streak habit iso-day)
    (due-day-streak habit iso-day)))

(defn streak-unit
  "`:week` for a weekly quota, `:day` otherwise."
  [habit]
  (if (weekly-quota? habit) :week :day))

(defn- day-longest-run
  [habit days]
  (let [days (->> days (filter #(due-day? habit %)) sort)]
    (loop [remaining days
           previous nil
           run 0
           best 0]
      (if-let [day (first remaining)]
        (let [next-run (if (and previous (= day (next-due-day habit previous)))
                         (inc run)
                         1)]
          (recur (rest remaining) day next-run (max best next-run)))
        best))))

(defn- quota-longest-run
  [habit end-day chain?]
  (if-not (iso-date? end-day)
    0
    (let [created (or (:habit/created-at habit) end-day)
          start (week-start created)
          end (week-start end-day)]
      (loop [week start
             run 0
             best 0]
        (if (pos? (compare week end))
          best
          (if (chain? habit week)
            (let [next-run (inc run)]
              (recur (shift-days week 7) next-run (max best next-run)))
            (if (= week end)
              best
              (recur (shift-days week 7) 0 best))))))))

(defn- latest-activity-day
  [habit]
  (let [dates (concat (:habit/completions habit)
                      (:habit/repairs habit)
                      (:habit/frozen-weeks habit))]
    (when (seq dates) (last (sort dates)))))

(defn longest-streak
  "The best run of real evidence in the habit's unit. Repairs and frozen weeks
  are excluded: they protect a visible streak but are not evidence."
  [habit]
  (case (schedule-type habit)
    :weekly-quota (quota-longest-run habit
                                     (or (latest-activity-day habit)
                                         (:habit/created-at habit))
                                     week-complete?)
    (day-longest-run habit (:habit/completions habit))))

(defn longest-chain
  "The best visible run, including repaired days or frozen weeks."
  [habit]
  (case (schedule-type habit)
    :weekly-quota (quota-longest-run habit
                                     (or (latest-activity-day habit)
                                         (:habit/created-at habit))
                                     week-chain?)
    (day-longest-run habit (into (or (:habit/completions habit) #{})
                                (or (:habit/repairs habit) #{})))))

;; ---------------------------------------------------------------------------
;; Consistency points, slots and shields

(defn proof-points
  "How many of the four consistency points a habit's best run has earned."
  [habit]
  (let [unit (streak-unit habit)
        best (longest-streak habit)]
    (count (filter #(<= % best) (get proof-thresholds unit)))))

(defn habit-proof
  "The best run with its unit, thresholds and earned points."
  [habit]
  (let [unit (streak-unit habit)
        best (longest-streak habit)]
    {:habit  habit
     :unit   unit
     :best   best
     :points (count (filter #(<= % best) (get proof-thresholds unit)))
     :thresholds (get proof-thresholds unit)}))

(defn global-best-habit
  "The habit holding the strongest proof, or nil."
  [habits]
  (when (seq habits)
    (last (sort-by (juxt :points :best) (map habit-proof habits)))))

(defn best-live-habit
  "The habit with the strongest live run, or nil."
  [habits iso-day]
  (when (seq habits)
    (last (sort-by (juxt :points :best)
                   (->> habits
                        (map (fn [habit]
                               (let [proof (habit-proof habit)]
                                 (assoc proof
                                        :best (current-streak habit iso-day)
                                        :habit habit))))
                        (filter #(pos? (:best %))))))))

(defn unlocked-slots
  "Slots earned: one to begin, plus one for each consistency point. `floor`
  carries already-earned slots forward."
  ([habits]
   (unlocked-slots habits 1))
  ([habits floor]
   (let [points (reduce max 0 (map proof-points habits))]
     (min max-slots
          (max (or floor 1) (inc points))))))

(defn state-unlocked-slots
  "The slot count carried by a full state map."
  [state]
  (unlocked-slots (:habits/list state) (:app/earned-slots state)))

(defn ensure-earned-slots
  "Promote the persisted earned-slot floor to at least the current evidence."
  [state]
  (let [floor (min max-slots
                   (max (or (:app/earned-slots state) 1)
                        (unlocked-slots (:habits/list state))))]
    (assoc state :app/earned-slots floor)))

(defn active-count [state]
  (count (active-habits state)))

(defn can-add-habit?
  "Slots limit active habits, not archived history."
  [state]
  (< (active-count state) (state-unlocked-slots state)))

(defn next-slot
  "The next locked slot, with the habit that is closest to the next proof.
  `floor` prevents a lost-evidence milestone from reappearing."
  ([habits]
   (next-slot habits 1))
  ([habits floor]
   (let [proof   (reduce max 0 (map proof-points habits))
         floor   (min max-slots (max 1 (or floor 1)))
         slots   (min max-slots (max floor (inc proof)))
         next-at-index (dec slots)]
     (when (< slots max-slots)
       (let [candidates
             (keep (fn [habit]
                     (let [unit (streak-unit habit)
                           thresholds (get proof-thresholds unit)
                           at (nth thresholds next-at-index nil)
                           best (longest-streak habit)]
                       (when at
                         {:habit habit
                          :unit unit
                          :best best
                          :at at
                          :progress (max 0 (min 1 (/ best at)))})))
                   habits)
             chosen (when (seq candidates)
                      (apply max-key :progress candidates))
             at     (or (:at chosen) 3)
             best   (or (:best chosen) 0)
             unit   (or (:unit chosen) :day)]
         {:slot       (inc slots)
          :title      (get slot-titles (inc slots))
          :habit      (:habit chosen)
          :habit-name (get-in chosen [:habit :habit/name])
          :unit       unit
          :best       best
          :at         at
          :remaining  (max 0 (- at best))
          :progress   (or (:progress chosen) 0)})))))

(defn completion-rate
  "Completion rate over the last `window` days, measured in due periods:
  scheduled days for daily/fixed habits, completed weeks for quotas. Repairs and
  frozen weeks are excluded."
  [habit iso-day window]
  (case (schedule-type habit)
    :weekly-quota
    (let [start (max (or (:habit/created-at habit) (shift-days iso-day (dec window)))
                     (shift-days iso-day (dec window)))
          start-week (week-start start)
          current (week-start iso-day)
          weeks (loop [week start-week
                       acc []]
                  (if (pos? (compare week current))
                    acc
                    (recur (shift-days week 7) (conj acc week))))
          weeks (if (week-complete? habit current)
                  weeks
                  (remove #(= % current) weeks))
          complete (count (filter #(week-complete? habit %) weeks))]
      (if (seq weeks)
        (js/Math.round (* 100 (/ complete (count weeks))))
        0))

    (let [start (max (or (:habit/created-at habit) (shift-days iso-day (dec window)))
                     (shift-days iso-day (dec window)))
          days (->> (last-days iso-day window)
                    (filter #(<= (compare start %) 0))
                    (filter #(due-day? habit %)))
          done (count (filter #(done-on? habit %) days))]
      (if (seq days)
        (js/Math.round (* 100 (/ done (count days))))
        0))))

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
  "The last 35 days for one habit, oldest first, with a cadence-aware state."
  [habit iso-day]
  (let [quota? (weekly-quota? habit)]
    (mapv (fn [day]
            (let [future? (pos? (compare day iso-day))
                  done? (done-on? habit day)
                  repaired? (repaired-on? habit day)
                  due? (due-day? habit day)
                  rest? (or (and (fixed-days? habit) (not due?))
                            (and quota? (not (quota-allowed-day? habit day))))]
              {:day day
               :done done?
               :repaired repaired?
               :future? future?
               :rest? rest?
               :missed? (and (not quota?)
                             (not future?)
                             due?
                             (not done?)
                             (not repaired?))}))
          (last-days iso-day 35))))

(defn at-risk?
  "Pressure appears only on a real deadline after 20:00: a scheduled day for
  daily/fixed habits, or a quota's deadline day while the target is unmet."
  [habit iso-day now-minute]
  (and (not (:habit/archived? habit))
       (>= now-minute 1200)
       (case (schedule-type habit)
         :weekly-quota
         (and (= (iso-weekday iso-day) (quota-deadline-weekday habit))
              (not (week-complete? habit iso-day)))

         (and (due-day? habit iso-day)
              (not (done-on? habit iso-day))))))

(defn greeting
  [minute-of-day]
  (cond
    (< minute-of-day 300) "Still up?"
    (< minute-of-day 720) "Good morning"
    (< minute-of-day 1020) "Good afternoon"
    (< minute-of-day 1260) "Good evening"
    :else "Late, but not too late"))

;; ---------------------------------------------------------------------------
;; Reminders

(defn reminder-key
  [habit iso-day]
  (str (:habit/id habit) "|" iso-day "|" (:habit/reminder habit)))

(defn- reminder-day?
  "A timed ritual only speaks on a day when an action could matter."
  [habit iso-day]
  (case (schedule-type habit)
    :weekly-quota
    (let [days (schedule-days habit)]
      (if days
        (contains? days (iso-weekday iso-day))
        (= (iso-weekday iso-day) (quota-deadline-weekday habit))))

    (due-day? habit iso-day)))

(defn- reminder-complete?
  [habit iso-day]
  (if (weekly-quota? habit)
    (week-complete? habit iso-day)
    (done-on? habit iso-day)))

(defn due-reminders
  "Active, timed habits whose reminder fell within the last 30 minutes on a day
  when an action could matter."
  [habits iso-day now-minute notified]
  (->> habits
       (filter #(not (:habit/archived? %)))
       (keep (fn [habit]
               (when-let [minute (parse-time (:habit/reminder habit))]
                 (when (and (reminder-day? habit iso-day)
                            (not (reminder-complete? habit iso-day))
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
                 (when (and (reminder-day? habit iso-day)
                            (not (reminder-complete? habit iso-day))
                            (> minute now-minute))
                   {:habit habit :minute minute}))))
       (sort-by :minute)
       first))

;; ---------------------------------------------------------------------------
;; Shields

(defn shields-earned
  "One shield for every seven real completions, banked up to three."
  [{:habit/keys [completions]}]
  (min shield-cap (quot (count (or completions #{})) 7)))

(defn shields-spent
  [{:habit/keys [repairs frozen-weeks]}]
  (+ (count (or repairs #{}))
     (count (or frozen-weeks #{}))))

(defn shield-balance
  "Shields available to spend."
  [habit]
  (max 0 (- (shields-earned habit) (shields-spent habit))))

(defn can-repair?
  "True when a shield can protect the previous missed period: the previous
  scheduled day for a daily/fixed habit, or last week for a quota."
  [habit iso-day]
  (let [created (:habit/created-at habit)]
    (and (not (:habit/archived? habit))
         (pos? (shield-balance habit))
         (case (schedule-type habit)
           :weekly-quota
           (let [previous (shift-days (week-start iso-day) -7)
                 created-week (when created (week-start created))]
             (and (or (nil? created-week)
                      (<= (compare created-week previous) 0))
                  (not (week-chain? habit previous))))

           (let [previous (previous-due-day habit iso-day)]
             (and (or (nil? created)
                      (<= (compare created previous) 0))
                  (not (chain-day? habit previous))))))))

(defn repair-day
  "Spend a shield on the previous scheduled day or last week. Returns the state
  unchanged when the rule does not allow it."
  [state id iso-day]
  (update state :habits/list
          (fn [habits]
            (mapv (fn [habit]
                    (if (and (= id (:habit/id habit))
                             (can-repair? habit iso-day))
                      (if (weekly-quota? habit)
                        (update habit :habit/frozen-weeks (fnil conj #{})
                                (shift-days (week-start iso-day) -7))
                        (update habit :habit/repairs (fnil conj #{})
                                (previous-due-day habit iso-day)))
                      habit))
                  habits))))

;; ---------------------------------------------------------------------------
;; Export and import

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

(defn- read-dates
  [values]
  (into #{} (filter iso-date?) values))

(defn- read-habit
  "Validate and normalise one habit read from a file or storage. Unknown keys
  are kept, so a file written by a newer version still imports."
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
           :habit/reminder    (when-let [minute (parse-time (:habit/reminder candidate))]
                                (format-time minute))
           :habit/schedule    (normalize-schedule (:habit/schedule candidate))
           :habit/completions (read-dates (:habit/completions candidate))
           :habit/repairs     (read-dates (:habit/repairs candidate))
           :habit/frozen-weeks (read-dates (:habit/frozen-weeks candidate))
           :habit/archived?   (true? (:habit/archived? candidate)))))

(defn parse-habits
  "Validate a sequence of habit maps. Returns a vector; throws `ex-info` when the
  shape is not usable."
  [habits]
  (when-not (sequential? habits)
    (throw (ex-info "That file is not a habit export."
                    {:habits.error/type :habits/import-invalid
                     :habits/value      (type habits)})))
  (mapv read-habit habits (range)))

(defn add-habit
  "Append `habit`, which must carry an id and a name. Defaults the optional
  fields and normalises the schedule."
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
                      (assoc :habit/schedule (normalize-schedule (:habit/schedule habit)))
                      (update :habit/completions #(or % #{}))
                      (update :habit/repairs #(or % #{}))
                      (update :habit/frozen-weeks #(or % #{}))
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
  "Record or clear a real completion for habit `id` on `iso-day`."
  [state id iso-day]
  (update-habit state id
                (fn [habit]
                  (update habit :habit/completions
                          (fn [completions]
                            (if (contains? completions iso-day)
                              (disj completions iso-day)
                              (conj completions iso-day)))))))

(defn edit-habit
  "Edit fields the user owns. History and slot evidence are not editable."
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
                        (format-time minute)))

               (contains? changes :habit/schedule)
               (assoc :habit/schedule (normalize-schedule (:habit/schedule changes))))))
          (dissoc :app/error)))))

(defn set-archived
  "Archive or restore a habit. Archiving frees a slot; history stays."
  [state id archived?]
  (-> (update-habit state id #(assoc % :habit/archived? (boolean archived?)))
      (dissoc :app/pending-delete)))

(defn ask-to-delete [state id]
  (assoc state :app/pending-delete id))

(defn cancel-delete [state]
  (dissoc state :app/pending-delete))

(defn delete-habit
  "Remove habit `id` and its history. Destructive and irreversible."
  [state id]
  (-> state
      (update :habits/list (fn [habits] (filterv #(not= id (:habit/id %)) habits)))
      (dissoc :app/pending-delete)))

(defn import-data
  "Replace the habit list with `data`. Throws when the file is not usable."
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
  "Apply one UI event to `state`."
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

(defn- escape-ics [text]
  (-> (str (or text ""))
      (str/replace "\\" "\\\\")
      (str/replace "\n" "\\n")
      (str/replace "," "\\,")
      (str/replace ";" "\\;")))

(defn- ics-date [iso-day]
  (str/replace iso-day "-" ""))

(defn- ics-stamp [iso-day]
  (str (ics-date iso-day) "T120000Z"))

(def ^:private weekday-codes
  {1 "MO" 2 "TU" 3 "WE" 4 "TH" 5 "FR" 6 "SA" 7 "SU"})

(defn- next-occurrence
  [habit iso-day now-minute]
  (let [minute (parse-time (:habit/reminder habit))
        future-today? (> minute now-minute)]
    (case (schedule-type habit)
      :fixed-days
      (loop [day iso-day]
        (if (and (due-day? habit day)
                 (or (not= day iso-day) future-today?))
          day
          (recur (shift-days day 1))))

      :weekly-quota
      (loop [day iso-day]
        (if (and (= (iso-weekday day) (quota-deadline-weekday habit))
                 (or (not= day iso-day) future-today?))
          day
          (recur (shift-days day 1))))

      (if future-today? iso-day (shift-days iso-day 1)))))

(defn- event-lines
  [habit iso-day now-minute]
  (let [minute (parse-time (:habit/reminder habit))]
    (when minute
      (let [start-day (next-occurrence habit iso-day now-minute)
            [end-day end-minute] (shift-minutes start-day minute 15)
            type (schedule-type habit)
            cue (:habit/cue habit)
            check-in? (= :weekly-quota type)
            description (str (if check-in?
                               "Weekly check-in for "
                               "Ember ritual: ")
                             (:habit/name habit)
                             (when-not (str/blank? cue) (str "\nCue: " cue))
                             "\nOpen Ember and log what you did.")
            rrule (case type
                    :fixed-days
                    (str "RRULE:FREQ=WEEKLY;BYDAY="
                         (str/join "," (map weekday-codes (sort (schedule-days habit)))))

                    :weekly-quota
                    (str "RRULE:FREQ=WEEKLY;BYDAY="
                         (weekday-codes (quota-deadline-weekday habit)))

                    "RRULE:FREQ=DAILY")]
        [(str "BEGIN:VEVENT")
         (str "UID:" (:habit/id habit) "@ember.local")
         (str "DTSTAMP:" (ics-stamp iso-day))
         (str "DTSTART:" (ics-date start-day) "T" (str/replace (format-time minute) ":" "") "00")
         (str "DTEND:" (ics-date end-day) "T" (str/replace (format-time end-minute) ":" "") "00")
         rrule
         (str "SUMMARY:" (escape-ics (str "Ember: " (:habit/name habit))))
         (str "DESCRIPTION:" (escape-ics description))
         "BEGIN:VALARM"
         "TRIGGER:-PT0M"
         "ACTION:DISPLAY"
         (str "DESCRIPTION:" (escape-ics
                              (str (if check-in? "Weekly check-in: " "Time for ")
                                   (:habit/name habit))))
         "END:VALARM"
         "END:VEVENT"]))))

(defn calendar-ics
  "A complete iCalendar document with one alarm per timed, active habit. Fixed
  days and quotas repeat weekly; daily habits repeat daily."
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
                         "X-WR-CALDESC:Scheduled alarms for Ember rituals"]
                        events
                        ["END:VCALENDAR"])))))
