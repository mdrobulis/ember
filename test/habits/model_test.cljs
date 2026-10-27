(ns habits.model-test
  "Tests for the pure domain. No DOM, no storage, no clock: every date is passed
  in, which is the point of keeping `habits.model` pure."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.string :as str]
            [habits.model :as model]))

(defn- habit
  "A habit with the given completed days and sensible defaults."
  [name & days]
  {:habit/id          (str "id-" name)
   :habit/name        name
   :habit/created-at  "2026-01-01"
   :habit/completions (set days)
   :habit/repairs     #{}
   :habit/cue         ""
   :habit/reminder    nil
   :habit/why         ""
   :habit/archived?   false
   :habit/schedule    {:schedule/type :daily}
   :habit/frozen-weeks #{}})

(defn- fixed
  [name days & completions]
  (assoc (apply habit name completions)
         :habit/schedule {:schedule/type :fixed-days :schedule/days (set days)}))

(defn- quota
  [name target allowed & completions]
  (assoc (apply habit name completions)
         :habit/schedule (cond-> {:schedule/type :weekly-quota
                                  :schedule/target target}
                           (seq allowed) (assoc :schedule/days (set allowed)))))

(defn- state-of
  [& habits]
  {:habits/list (vec habits)
   :app/today   "2026-10-24"
   :app/error   nil})

(deftest add-habit-trims-the-name
  (let [state (model/add-habit {:habits/list []}
                               {:habit/id "a" :habit/name "  Read  "
                                :habit/created-at "2026-10-24"})]
    (is (= ["Read"] (mapv :habit/name (:habits/list state))))
    (is (= #{} (:habit/completions (first (:habits/list state)))))
    (is (= #{} (:habit/repairs (first (:habits/list state)))))
    (is (nil? (:app/error state)))))

(deftest add-habit-rejects-a-blank-name
  (let [state (model/add-habit {:habits/list []}
                               {:habit/id "a" :habit/name "   "
                                :habit/created-at "2026-10-24"})]
    (is (empty? (:habits/list state)))
    (is (string? (:app/error state)))))

(deftest add-habit-stops-at-the-slot-limit
  (let [one   (habit "Read")
        two   (habit "Walk")
        state (state-of one two)]
    (testing "one starting slot holds one active habit"
      (is (= 1 (model/unlocked-slots (:habits/list state))))
      (is (false? (model/can-add-habit? state))))
    (testing "a failed add is explained, not silent"
      (let [next-state (model/add-habit state
                                        {:habit/id "c" :habit/name "Stretch"
                                         :habit/created-at "2026-10-24"})]
        (is (= 2 (count (:habits/list next-state))))
        (is (re-find #"slot" (:app/error next-state)))))))

(deftest slots-unlock-on-real-best-streaks
  (testing "three days opens the second slot"
    (is (= 2 (model/unlocked-slots
              (:habits/list (state-of (habit "Read"
                                            "2026-10-01" "2026-10-02" "2026-10-03")))))))
  (testing "seven, fourteen and thirty unlock the rest"
    (is (= 3 (model/unlocked-slots
              (:habits/list (state-of (habit "Read"
                                            "2026-01-01" "2026-01-02" "2026-01-03"
                                            "2026-01-04" "2026-01-05" "2026-01-06"
                                            "2026-01-07"))))))
    (is (= 4 (model/unlocked-slots
              (:habits/list (state-of (habit "Read"
                                            "2026-01-01" "2026-01-02" "2026-01-03"
                                            "2026-01-04" "2026-01-05" "2026-01-06"
                                            "2026-01-07" "2026-01-08" "2026-01-09"
                                            "2026-01-10" "2026-01-11" "2026-01-12"
                                            "2026-01-13" "2026-01-14"))))))
    (is (= 5 (model/unlocked-slots
              (:habits/list (state-of (apply habit "Read"
                                                       (map #(str "2026-03-"
                                                                  (if (< % 10) (str "0" %) %))
                                                            (range 1 31)))))))))
  (testing "the ladder never exceeds five"
    (is (= 5 (model/unlocked-slots
              (:habits/list (state-of (apply habit "Read"
                                                       (map #(str "2026-03-"
                                                                  (if (< % 10) (str "0" %) %))
                                                            (range 1 31))))))))))

(deftest earned-slots-survive-lost-evidence
  (let [state (-> {:habits/list [(habit "Read")]}
                  model/ensure-earned-slots
                  (assoc :app/earned-slots 4))]
    (is (= 4 (model/state-unlocked-slots state)))
    (is (true? (model/can-add-habit? state)))
    (is (= 5 (:slot (model/next-slot (:habits/list state) 4))))
    (is (= 30 (:at (model/next-slot (:habits/list state) 4))))
    (is (= 4 (:habits/earned-slots
              (model/export-data state "2026-10-24"))))))

(deftest next-slot-reports-real-distance
  (let [slot (model/next-slot (:habits/list
                               (state-of (habit "Read" "2026-10-01" "2026-10-02"))))]
    (is (= 2 (:slot slot)))
    (is (= 3 (:at slot)))
    (is (= 1 (:remaining slot)))
    (is (= 2 (:best slot))))
  (is (nil? (model/next-slot
             (:habits/list (state-of (apply habit "Read"
                                                      (map #(str "2026-03-"
                                                                 (if (< % 10) (str "0" %) %))
                                                           (range 1 31)))))))))

(deftest archived-habits-free-slots-but-keep-evidence
  (let [one (habit "Read" "2026-10-01" "2026-10-02" "2026-10-03")
        two (assoc (habit "Walk") :habit/id "id-Walk")
        state (state-of one two)
        archived (model/set-archived state "id-Walk" true)]
    (is (false? (model/can-add-habit? state)))
    (is (true? (model/can-add-habit? archived)))
    (is (= 1 (count (model/archived-habits archived))))
    (is (= 2 (model/unlocked-slots (:habits/list archived))))
    (is (= 1 (count (model/active-habits archived))))))

(deftest toggle-completion-adds-then-removes-a-day
  (let [state {:habits/list [(habit "Read")]}
        on    (model/toggle-completion state "id-Read" "2026-10-24")
        off   (model/toggle-completion on "id-Read" "2026-10-24")]
    (is (model/done-on? (first (:habits/list on)) "2026-10-24"))
    (is (not (model/done-on? (first (:habits/list off)) "2026-10-24")))))

(deftest toggle-completion-ignores-an-unknown-habit
  (let [state {:habits/list [(habit "Read")]}]
    (is (= state (model/toggle-completion state "id-nope" "2026-10-24")))))

(deftest streaks
  (testing "counts back through today"
    (is (= 3 (model/current-streak
              (habit "Read" "2026-10-22" "2026-10-23" "2026-10-24")
              "2026-10-24"))))

  (testing "survives a day that is not ticked yet"
    (is (= 2 (model/current-streak
              (habit "Read" "2026-10-22" "2026-10-23")
              "2026-10-24"))))

  (testing "a missed day breaks it"
    (is (= 1 (model/current-streak
              (habit "Read" "2026-10-19" "2026-10-24")
              "2026-10-24"))))

  (testing "is zero with no completions"
    (is (= 0 (model/current-streak (habit "Read") "2026-10-24")))))

(deftest a-repaired-day-bridges-the-visible-streak
  (let [read (habit "Read"
                    "2026-10-16" "2026-10-17" "2026-10-18" "2026-10-19"
                    "2026-10-20" "2026-10-21" "2026-10-22")
        state (state-of read)
        fixed (model/repair-day state "id-Read" "2026-10-24")
        read' (first (:habits/list fixed))]
    (is (= 0 (model/current-streak read "2026-10-24")))
    (is (= 1 (model/shield-balance read)))
    (is (true? (model/can-repair? read "2026-10-24")))
    (is (contains? (:habit/repairs read') "2026-10-23"))
    (is (= 8 (model/current-streak read' "2026-10-24")))
    (is (zero? (model/shield-balance read')))
    (testing "a repair is not a completion"
      (is (not (model/done-on? read' "2026-10-23")))
      (is (= 7 (model/total-done read'))))))

(deftest a-repair-only-reaches-yesterday-and-needs-a-shield
  (let [read (habit "Read"
                    "2026-10-16" "2026-10-17" "2026-10-18" "2026-10-19"
                    "2026-10-20" "2026-10-21" "2026-10-22")
        no-shield (assoc read :habit/repairs #{"2026-01-01"})
        state (state-of read)]
    (is (false? (model/can-repair? no-shield "2026-10-24")))
    (is (= state (model/repair-day state "id-Read" "2026-10-23")))
    (is (false? (model/can-repair? (assoc read :habit/archived? true) "2026-10-24")))))

(deftest longest-streak-finds-the-best-run
  (is (= 3 (model/longest-streak
            (habit "Read" "2026-01-01" "2026-01-02" "2026-01-03"
                   "2026-01-05" "2026-01-06"))))
  (is (= 0 (model/longest-streak (habit "Read"))))
  (is (= 1 (model/longest-streak (habit "Read" "2026-01-01")))))

(deftest longest-chain-includes-repairs
  (is (= 4 (model/longest-chain
            (assoc (habit "Read" "2026-01-01" "2026-01-02" "2026-01-04")
                   :habit/repairs #{"2026-01-03"})))))

(deftest weekly-proof-unlocks-slots
  (let [one (quota "Train" 1 nil "2026-10-26")
        four (quota "Train" 1 nil
                    "2026-10-05" "2026-10-12" "2026-10-19" "2026-10-26")
        mixed [(quota "Train" 1 nil "2026-10-26")
               (habit "Read" "2026-10-24" "2026-10-25" "2026-10-26")]]
    (is (= 1 (model/proof-points one)))
    (is (= 2 (model/unlocked-slots [one])))
    (is (= 3 (:slot (model/next-slot [one]))))
    (is (= 2 (:at (model/next-slot [one]))))
    (is (= :week (:unit (model/next-slot [one]))))
    (is (= 3 (model/proof-points four)))
    (is (= 4 (model/unlocked-slots [four])))
    (is (= 5 (:slot (model/next-slot [four]))))
    (is (= 8 (:at (model/next-slot [four]))))
    (is (= 2 (model/unlocked-slots mixed)))))

(deftest weekly-completion-rate-uses-completed-weeks
  (let [gym (assoc (quota "Train" 1 nil "2026-10-05" "2026-10-12")
                   :habit/created-at "2026-10-05")]
    (is (= 67 (model/completion-rate gym "2026-10-30" 30)))))

(deftest fixed-can-repair-the-previous-scheduled-day
  (let [read (fixed "Read" [1 3 5] "2026-10-28") ; Wednesday, missing
        with-shield (assoc read :habit/completions
                           #{"2026-10-05" "2026-10-12" "2026-10-19"
                             "2026-10-20" "2026-10-21" "2026-10-23"
                             "2026-10-26"})]
    (is (false? (model/can-repair? read "2026-10-27")))
    (is (true? (model/can-repair? with-shield "2026-10-29")))
    (is (true? (model/can-repair? with-shield "2026-10-30")))
    (let [fixed-state (model/repair-day (state-of with-shield) "id-Read" "2026-10-29")
          read' (first (:habits/list fixed-state))]
      (is (contains? (:habit/repairs read') "2026-10-28")))))

(deftest completion-rate-measures-from-creation
  (testing "a habit created today is not punished for days before it existed"
    (is (= 100 (model/completion-rate
                (assoc (habit "Read" "2026-10-24") :habit/created-at "2026-10-24")
                "2026-10-24" 30))))
  (testing "a repair is not a completion"
    (let [read (assoc (habit "Read" "2026-10-15" "2026-10-16" "2026-10-17"
                             "2026-10-18" "2026-10-19" "2026-10-20")
                      :habit/created-at "2026-10-15"
                      :habit/repairs #{"2026-10-21"})]
      (is (= 60 (model/completion-rate read "2026-10-24" 10))))))

(deftest iso-dates
  (is (model/iso-date? "2026-10-24"))
  (is (model/iso-date? "2028-02-29"))
  (is (not (model/iso-date? "2026-02-30")))
  (is (not (model/iso-date? "24/10/2026")))
  (is (not (model/iso-date? nil))))

(deftest times
  (is (= 510 (model/parse-time "08:30")))
  (is (= 510 (model/parse-time "8:30")))
  (is (nil? (model/parse-time "24:00")))
  (is (nil? (model/parse-time "8:3")))
  (is (nil? (model/parse-time nil)))
  (is (= "08:30" (model/format-time 510)))
  (is (= "23:55" (model/format-time 1435))))

(deftest shift-days-crosses-boundaries
  (is (= "2026-11-01" (model/shift-days "2026-10-31" 1)))
  (is (= "2027-01-01" (model/shift-days "2026-12-31" 1)))
  (is (= "2026-02-28" (model/shift-days "2026-03-01" -1)))
  (is (= "2028-02-29" (model/shift-days "2028-02-28" 1)))
  (testing "a daylight-saving change does not move the day"
    (is (= "2026-10-25" (model/shift-days "2026-10-24" 1)))
    (is (= "2026-10-26" (model/shift-days "2026-10-25" 1)))))

(deftest reminder-timing
  (let [read (assoc (habit "Read") :habit/reminder "09:55")]
    (testing "fires within the thirty-minute window"
      (is (= 1 (count (model/due-reminders [read] "2026-10-24" 600 #{})))))
    (testing "does not repeat once notified"
      (is (empty? (model/due-reminders [read] "2026-10-24" 601
                                       #{(model/reminder-key read "2026-10-24")}))))
    (testing "does not fire for a lit habit"
      (is (empty? (model/due-reminders [(assoc read :habit/completions #{"2026-10-24"})]
                                       "2026-10-24" 600 #{}))))
    (testing "does not fire from hours ago"
      (is (empty? (model/due-reminders [read] "2026-10-24" 900 #{}))))
    (testing "the next reminder is later today and not lit"
      (is (= "09:55" (get-in (model/next-reminder [read] "2026-10-24" 480)
                             [:habit :habit/reminder]))))
    (testing "at-risk copy only after 20:00"
      (is (false? (model/at-risk? read "2026-10-24" 1199)))
      (is (true? (model/at-risk? read "2026-10-24" 1200))))))

(deftest calendar-export-has-a-daily-alarm
  (let [read (assoc (habit "Read, 10 pages") :habit/reminder "08:30")
        ics (model/calendar-ics [read] "2026-10-24" 600)]
    (is (string? ics))
    (is (str/includes? ics "BEGIN:VCALENDAR"))
    (is (str/includes? ics "RRULE:FREQ=DAILY"))
    (is (str/includes? ics "TRIGGER:-PT0M"))
    (is (str/includes? ics "Read\\, 10 pages")))
  (is (nil? (model/calendar-ics [(habit "Read")] "2026-10-24" 600))))

(deftest edit-habit-keeps-the-history
  (let [state (state-of (habit "Read" "2026-10-24"))
        edited (model/edit-habit state "id-Read"
                                 {:habit/name "Read 20 pages"
                                  :habit/cue "After coffee"
                                  :habit/why "Think clearly"
                                  :habit/reminder "08:30"})
        read (first (:habits/list edited))]
    (is (= "Read 20 pages" (:habit/name read)))
    (is (= "After coffee" (:habit/cue read)))
    (is (= "08:30" (:habit/reminder read)))
    (is (= #{"2026-10-24"} (:habit/completions read))))
  (testing "a blank rename is refused"
    (let [state (state-of (habit "Read"))
          edited (model/edit-habit state "id-Read" {:habit/name "  "})]
      (is (string? (:app/error edited)))
      (is (= "Read" (:habit/name (first (:habits/list edited))))))))

(deftest export-and-import-round-trip
  (let [state    {:habits/list [(habit "Read" "2026-10-24")]}
        exported (model/export-data state "2026-10-24")
        imported (model/import-data {:habits/list []} exported)]
    (is (= (model/parse-habits (:habits/list state))
           (:habits/list imported)))))

(deftest import-rejects-a-file-that-is-not-a-habit-export
  (is (thrown? js/Error (model/import-data {:habits/list []} {})))
  (is (thrown? js/Error
               (model/import-data {:habits/list []}
                                  {:habits/list [{:habit/name "No id"}]}))))

(deftest import-keeps-an-earned-slot-floor
  (let [state (model/import-data {:habits/list []}
                                 {:habits/list [(habit "Read")]
                                  :habits/earned-slots 4})]
    (is (= 4 (:app/earned-slots state)))
    (is (= 4 (model/state-unlocked-slots state)))))

(deftest legacy-list-key-still-imports
  (let [state (model/import-data {:habits/list []}
                                 {:version 1
                                  :list [{:habit/id "a" :habit/name "Read"}]})]
    (is (= ["Read"] (mapv :habit/name (:habits/list state))))))

(deftest import-ignores-unusable-completion-dates
  (let [state (model/import-data
               {:habits/list []}
               {:habits/list [{:habit/id "a" :habit/name "Read"
                               :habit/completions ["2026-10-24" "nope" nil 42]}]})]
    (is (= #{"2026-10-24"} (:habit/completions (first (:habits/list state)))))))

(deftest deleting-asks-then-removes
  (let [state   {:habits/list [(habit "Read")]}
        asked   (model/apply-event state [:ask-delete "id-Read"])
        kept    (model/apply-event asked [:cancel-delete])
        deleted (model/apply-event asked [:delete "id-Read"])]
    (is (= "id-Read" (:app/pending-delete asked)))
    (is (nil? (:app/pending-delete kept)))
    (is (empty? (:habits/list deleted)))))


(deftest fixed-days-skip-off-days
  (let [read (fixed "Read" [1 3 5]
                    "2026-10-26" "2026-10-28" "2026-10-30")]
    (is (true? (model/due-day? read "2026-10-26")))
    (is (false? (model/due-day? read "2026-10-27")))
    (is (= 3 (model/current-streak read "2026-10-30")))
    (is (= 3 (model/current-streak read "2026-10-31")))
    (testing "a missed scheduled day breaks the chain"
      (is (= 1 (model/current-streak
                (fixed "Read" [1 3 5] "2026-10-26" "2026-10-30")
                "2026-10-30"))))
    (testing "a completion on an off day is not evidence"
      (let [odd (fixed "Read" [1 3 5] "2026-10-26" "2026-10-27" "2026-10-28")]
        (is (= 2 (model/longest-streak odd)))
        (is (= 2 (model/current-streak odd "2026-10-28")))))))

(deftest weekly-quota-counts-and-respects-a-window
  (testing "three sessions complete the week"
    (let [gym (quota "Train" 3 nil
                     "2026-10-26" "2026-10-28" "2026-10-30")]
      (is (= 3 (model/quota-week-count gym "2026-10-26")))
      (is (true? (model/week-complete? gym "2026-10-26")))
      (is (= 1 (model/current-streak gym "2026-10-30")))
      (is (= 1 (model/longest-streak gym)))))
  (testing "a session outside the allowed window does not count"
    (let [weekday (quota "Train" 3 #{1 2 3 4 5}
                         "2026-10-26" "2026-10-27" "2026-10-31")]
      (is (= 2 (model/quota-week-count weekday "2026-10-26")))
      (is (false? (model/week-complete? weekday "2026-10-26"))))))

(deftest weekly-quota-streak-survives-an-in-progress-week
  (let [gym (quota "Train" 3 nil
                   "2026-10-19" "2026-10-20" "2026-10-21"
                   "2026-10-26" "2026-10-28")]
    (is (= 1 (model/current-streak gym "2026-10-30")))
    (testing "completing the current week extends it"
      (let [done (assoc gym :habit/completions
                        (conj (:habit/completions gym) "2026-10-30"))]
        (is (= 2 (model/current-streak done "2026-10-30")))))
    (testing "the best streak only counts complete weeks"
      (is (= 1 (model/longest-streak gym))))))

(deftest quota-shields-freeze-the-previous-week
  (let [gym (quota "Train" 3 nil
                   "2026-10-12" "2026-10-13"
                   "2026-10-19" "2026-10-20"
                   "2026-10-26" "2026-10-28" "2026-10-30")
        state (state-of gym)
        fixed (model/repair-day state "id-Train" "2026-10-30")
        gym' (first (:habits/list fixed))]
    (is (true? (model/can-repair? gym "2026-10-30")))
    (is (contains? (:habit/frozen-weeks gym') "2026-10-19"))
    (is (true? (model/week-frozen? gym' "2026-10-19")))
    (is (= 1 (model/shields-spent gym')))
    (testing "the previous incomplete week is bridged, not completed"
      (is (= 2 (model/current-streak gym' "2026-10-30")))
      (is (= 7 (model/total-done gym'))))))

(deftest cadence-aware-at-risk-and-availability
  (let [fixed-read (fixed "Read" [1 3 5] "2026-10-26")
        gym (quota "Train" 3 nil "2026-10-26")]
    (is (false? (model/at-risk? fixed-read "2026-10-27" 1300)))
    (is (true? (model/at-risk? fixed-read "2026-10-28" 1300)))
    (is (false? (model/available-today? fixed-read "2026-10-27")))
    (is (true? (model/at-risk? gym "2026-11-01" 1300)))
    (is (false? (model/at-risk? gym "2026-10-31" 1300)))
    (is (true? (model/available-today? gym "2026-11-01")))
    (is (true? (model/available-today? gym "2026-10-26")))))

(deftest cadence-aware-reminders
  (let [fixed-read (assoc (fixed "Read" [1 3 5]) :habit/reminder "08:00")
        gym (assoc (quota "Train" 3 nil) :habit/reminder "08:00")
        gym-window (assoc (quota "Train" 3 #{1 2 3 4 5}) :habit/reminder "08:00")]
    (is (empty? (model/due-reminders [fixed-read] "2026-10-27" 500 #{})))
    (is (= 1 (count (model/due-reminders [fixed-read] "2026-10-28" 500 #{}))))
    (is (empty? (model/due-reminders [gym] "2026-10-30" 500 #{})))
    (is (= 1 (count (model/due-reminders [gym] "2026-11-01" 500 #{}))))
    (is (= 1 (count (model/due-reminders [gym-window] "2026-10-28" 500 #{}))))))

(deftest cadence-aware-heatmap
  (let [read (fixed "Read" [1 3 5] "2026-10-26")
        cells (model/habit-heatmap read "2026-10-28")
        tue (first (filter #(= "2026-10-27" (:day %)) cells))
        mon (first (filter #(= "2026-10-26" (:day %)) cells))
        gym (quota "Train" 3 #{1 2 3 4 5})
        gym-cells (model/habit-heatmap gym "2026-10-28")
        sat (first (filter #(= "2026-10-24" (:day %)) gym-cells))
        wed (first (filter #(= "2026-10-28" (:day %)) gym-cells))]
    (is (true? (:rest? tue)))
    (is (false? (:missed? tue)))
    (is (true? (:done mon)))
    (is (false? (:missed? mon)))
    (is (true? (:rest? sat)))
    (is (false? (:rest? wed)))))

(deftest cadence-aware-calendar
  (let [fixed-read (assoc (fixed "Read" [1 3 5]) :habit/reminder "08:00")
        gym (assoc (quota "Train" 3 #{1 2 3 4 5}) :habit/reminder "18:00")
        daily (assoc (habit "Daily") :habit/reminder "07:00")]
    (is (str/includes? (model/calendar-ics [fixed-read] "2026-10-27" 600)
                       "RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR"))
    (is (str/includes? (model/calendar-ics [gym] "2026-10-27" 600)
                       "RRULE:FREQ=WEEKLY;BYDAY=FR"))
    (is (str/includes? (model/calendar-ics [daily] "2026-10-27" 600)
                       "RRULE:FREQ=DAILY"))))

(deftest schedule-normalisation
  (is (= {:schedule/type :daily} (model/normalize-schedule nil)))
  (is (= {:schedule/type :daily} (model/normalize-schedule {:schedule/type 42})))
  (is (= {:schedule/type :fixed-days :schedule/days #{1 3 5}}
         (model/normalize-schedule {:schedule/type "fixed-days"
                                    :schedule/days [1 3 5 9]})))
  (is (= {:schedule/type :weekly-quota :schedule/target 3}
         (model/normalize-schedule {:schedule/type "weekly-quota"
                                    :schedule/target 3})))
  (testing "a target cannot exceed the allowed window"
    (is (= 2 (:schedule/target
              (model/normalize-schedule {:schedule/type :weekly-quota
                                         :schedule/target 5
                                         :schedule/days #{6 7}}))))))

(deftest schedule-migrates-on-read
  (let [state (model/import-data
               {:habits/list []}
               {:habits/list [{:habit/id "a" :habit/name "Old"
                               :habit/completions ["2026-10-26"]}]})
        old (first (:habits/list state))
        gym (first (:habits/list
                    (model/import-data
                     {:habits/list []}
                     {:habits/list [{:habit/id "b" :habit/name "New"
                                     :habit/schedule {:schedule/type "weekly-quota"
                                                      :schedule/target 3}
                                     :habit/reminder "08:00"}]})))]
    (is (= {:schedule/type :daily} (:habit/schedule old)))
    (is (= :weekly-quota (model/schedule-type gym)))
    (is (= "08:00" (:habit/reminder gym)))))

(deftest unknown-events-do-not-change-state
  (let [warn (.-warn js/console)]
    (set! (.-warn js/console) (fn [& _] nil))
    (try
      (is (= {:habits/list []} (model/apply-event {:habits/list []} [:bogus 1])))
      (finally
        (set! (.-warn js/console) warn)))))
