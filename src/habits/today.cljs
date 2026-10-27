(ns habits.today
  "The Today screen: the hero, the ritual cards, and the slot ladder."
  (:require [clojure.string :as str]
            [habits.icons :as icons]
            [habits.model :as model]
            [habits.ui :as ui]))

(def ^:private months
  ["January" "February" "March" "April" "May" "June"
   "July" "August" "September" "October" "November" "December"])

(def ^:private weekdays
  ["Sunday" "Monday" "Tuesday" "Wednesday" "Thursday" "Friday" "Saturday"])

(defn long-date
  "`Tuesday 27 October` from an ISO date."
  [iso-day]
  (if-let [[year month day] (model/parts iso-day)]
    (let [d (js/Date. (js/Date.UTC year (dec month) day 12))]
      (str (weekdays (.getUTCDay d)) " " day " " (months (dec month))))
    iso-day))

(defn short-date
  "`27 Oct` from an ISO date."
  [iso-day]
  (if-let [[_ month day] (model/parts iso-day)]
    (str day " " (subs (months (dec month)) 0 3))
    iso-day))

(defn time-label
  "A locale-formatted time from `HH:MM`."
  [hhmm]
  (when-let [minute (model/parse-time hhmm)]
    (let [h (quot minute 60)
          m (mod minute 60)
          d (js/Date. 2000 0 1 h m)]
      (.format (js/Intl.DateTimeFormat. "en-GB" #js {:hour "2-digit" :minute "2-digit"}) d))))

(defn- due-week-days
  [habit iso-day]
  (filterv #(model/due-day? habit %) (model/week-days iso-day)))

(defn- quota-info
  [habit iso-day]
  (let [week (model/week-start iso-day)
        target (model/schedule-target habit)
        progress (model/quota-week-count habit week)
        complete? (model/week-complete? habit week)]
    {:target target
     :progress progress
     :complete? complete?
     :deadline (model/quota-deadline-day habit iso-day)}))

(defn- rest-today?
  [habit iso-day]
  (case (model/schedule-type habit)
    :fixed-days (not (model/due-day? habit iso-day))
    :weekly-quota (not (model/quota-allowed-day? habit iso-day))
    false))

(defn- week-strip
  [habit iso-day]
  (let [days (model/week-days iso-day)]
    [:div {:class "flex items-center gap-1.5" :aria-hidden "true"}
     (for [day days]
       (let [done? (model/done-on? habit day)
             repaired? (model/repaired-on? habit day)
             rest? (or (and (model/fixed-days? habit)
                            (not (model/due-day? habit day)))
                       (and (model/weekly-quota? habit)
                            (not (model/quota-allowed-day? habit day))))
             today? (= day iso-day)
             future? (pos? (compare day iso-day))]
         ^{:key day}
         [:span {:class (str "flex h-7 w-7 items-center justify-center rounded-full border text-[0.625rem] font-semibold transition "
                             (cond
                               repaired? "border-frost/30 bg-frost/15 text-frost"
                               done? "border-ember-400/30 bg-ember-400 text-ink-950"
                               rest? "border-white/[0.04] bg-transparent text-slate-400"
                               today? "border-dashed border-ember-400/50 bg-transparent text-ember-300"
                               future? "border-white/[0.04] bg-transparent text-slate-500"
                               :else "border-white/[0.07] bg-white/[0.035] text-slate-400"))
                 :title (str day (cond repaired? " · shield" done? " · done" rest? " · rest" :else " · open"))}
          (cond repaired? "S" done? "✓" rest? "–" :else "·")]))]))

(defn- card-action
  [habit iso-day lit? rest? complete? dispatch]
  (let [name (:habit/name habit)
        quota? (model/weekly-quota? habit)
        action? (or lit?
                    (and (not rest?)
                         (model/available-today? habit iso-day)))
        label (str (if lit? "Undo " (if quota? "Log a session for " "Light ")) name
                   (if (and quota? (not lit?)) " today" " for today"))]
    (cond
      lit?
      [:button {:type "button"
                :class "mt-0.5 flex h-[3.25rem] w-[3.25rem] shrink-0 items-center justify-center rounded-[1.15rem] border border-spark/40 bg-spark text-ink-950 shadow-glow-lime transition duration-200"
                :aria-pressed "true"
                :aria-label label
                :title label
                :on-click #(dispatch [:toggle (:habit/id habit) iso-day])}
       [icons/icon "check" {:class "h-6 w-6"}]]

      complete?
      [:span {:class "mt-0.5 flex h-[3.25rem] w-[3.25rem] shrink-0 items-center justify-center rounded-[1.15rem] border border-spark/25 bg-spark/[0.08] text-spark"
              :role "img"
              :aria-label "Done for this period"}
       [icons/icon "check" {:class "h-5 w-5"}]]

      rest?
      [:span {:class "mt-0.5 flex h-[3.25rem] w-[3.25rem] shrink-0 items-center justify-center rounded-[1.15rem] border border-white/[0.07] bg-white/[0.02] text-slate-400"
              :role "img"
              :aria-label "Rest day"}
       [icons/icon "moon" {:class "h-5 w-5"}]]

      action?
      [:button {:type "button"
                :class "mt-0.5 flex h-[3.25rem] w-[3.25rem] shrink-0 items-center justify-center rounded-[1.15rem] border border-white/15 bg-white/[0.04] text-slate-400 transition duration-200 hover:border-ember-400/50 hover:bg-ember-500/10 hover:text-ember-300"
                :aria-pressed "false"
                :aria-label label
                :title label
                :on-click #(dispatch [:toggle (:habit/id habit) iso-day])}
       [icons/icon "flame" {:class "h-6 w-6"}]]

      :else
      [:span {:class "mt-0.5 flex h-[3.25rem] w-[3.25rem] shrink-0 items-center justify-center rounded-[1.15rem] border border-spark/20 bg-spark/[0.06] text-spark"
              :role "img"
              :aria-label "Done for this period"}
       [icons/icon "check" {:class "h-5 w-5"}]])))

(defn- habit-card
  [{:habit/keys [id name cue why reminder] :as habit}
   {:app/keys [today now-minutes]} dispatch open-editor open-log]
  (let [quota? (model/weekly-quota? habit)
        info (when quota? (quota-info habit today))
        streak (model/current-streak habit today)
        unit (model/streak-unit habit)
        shields (model/shield-balance habit)
        repair? (model/can-repair? habit today)
        lit? (model/done-on? habit today)
        rest? (rest-today? habit today)
        week-days (due-week-days habit today)
        done-week (count (filter #(model/done-on? habit %) week-days))
        complete? (:complete? info)
        active-card? (or lit? (and quota? complete?))
        status (cond
                 (and quota? complete?) "Week complete"
                 rest? "Rest day"
                 lit? "Lit today"
                 quota? (str (:progress info) "/" (:target info) " this week")
                 :else "Not lit yet")
        status-class (cond
                       (or lit? (and quota? complete?)) "text-spark"
                       rest? "text-slate-400"
                       :else "text-slate-300")]
    [:article {:class (str "habit-card heat-edge animate-pop-in " (when active-card? "habit-card--lit"))}
     [:div {:class "flex items-start gap-3"}
      [card-action habit today lit? rest? (and quota? complete?) dispatch]
      [:div {:class "min-w-0 flex-1"}
       [:div {:class "flex items-start justify-between gap-2"}
        [:div {:class "min-w-0"}
         [:h3 {:class (str "display break-words text-[1.15rem] leading-snug sm:text-xl " (if active-card? "text-white" "text-slate-100"))}
          name]
         [:p {:class "mt-1 text-sm leading-snug text-slate-400"}
          (if (str/blank? cue) "No cue yet — add one so the day knows when to start." cue)]]
        [ui/icon-button {:icon "edit" :label (str "Edit " name)
                         :on-click #(open-editor id)}]]
       [:div {:class "mt-3 flex flex-wrap items-center gap-1.5"}
        [ui/chip {:icon "calendar"} (ui/schedule-label habit)]
        [ui/chip {:icon "flame" :tone (when (pos? streak) :warn)}
         (str (ui/streak-label streak unit) " streak")]
        (when quota?
          [ui/chip {:icon "target" :tone (when complete? :good)}
           (str (:progress info) "/" (:target info) " this week")])
        (when reminder
          [ui/chip {:icon "clock"} (time-label reminder)])
        (when (pos? shields)
          [ui/chip {:icon "shield" :tone :cool}
           (str shields " shield" (when (not= 1 shields) "s"))])]
       (when-not (str/blank? why)
         [:p {:class "mt-2 border-l-2 border-white/10 pl-2 text-xs italic leading-relaxed text-slate-400"}
          (str "“" why "”")])]]
     [:div {:class "mt-4 flex flex-wrap items-end justify-between gap-x-4 gap-y-2 border-t border-white/[0.06] pt-3"}
      [:div
       [:p {:class "mb-1.5 font-display text-[0.625rem] font-medium uppercase tracking-[0.18em] text-slate-400"}
        (if quota?
          (str (:progress info) "/" (:target info) " sessions this week")
          (str done-week "/" (count week-days) " due this week"))]
       [week-strip habit today]]
      [:div {:class "text-right"}
       [:p {:class "font-display text-[0.625rem] font-medium uppercase tracking-[0.18em] text-slate-400"}
        (if (or lit? complete?) "Kept" "Status")]
       [:p {:class (str "mt-0.5 font-display text-sm font-semibold " status-class)}
        status]]]
     (when quota?
       [:button {:type "button"
                 :class "mt-3 inline-flex min-h-9 items-center gap-1.5 rounded-xl border border-white/10 bg-white/[0.03] px-3 py-1.5 font-display text-xs font-medium text-slate-300 transition hover:border-white/20 hover:text-white"
                 :on-click #(open-log id)}
        [icons/icon "calendar" {:class "h-3.5 w-3.5"}]
        "Log a past day"])
     (when repair?
       [:div {:class "mt-3 flex flex-wrap items-center justify-between gap-2 rounded-2xl border border-frost/15 bg-frost/[0.06] px-3 py-2.5"}
        [:div {:class "flex items-center gap-2 text-xs text-frost"}
         [icons/icon "shield" {:class "h-4 w-4 shrink-0"}]
         [:span (cond
                  quota? "Last week fell short. One shield can freeze it."
                  (model/fixed-days? habit) "The last scheduled day is uncovered. One shield can bridge it."
                  :else "Yesterday is uncovered. One shield can bridge it.")]]
        [:button {:type "button"
                  :class "min-h-9 rounded-xl border border-frost/25 bg-frost/10 px-3 py-1.5 font-display text-xs font-semibold text-frost transition hover:bg-frost/20"
                  :on-click #(dispatch [:repair id today])}
         (if quota? "Freeze last week" "Use 1 shield")]])
     (when (model/at-risk? habit today now-minutes)
       [:p {:class "mt-2 flex items-center gap-1.5 text-xs font-medium text-ember-300"}
        [icons/icon "clock" {:class "h-3.5 w-3.5"}]
        (if quota?
          (str "The week closes today. " (max 0 (- (:target info) (:progress info)))
               " session" (when (not= 1 (max 0 (- (:target info) (:progress info)))) "s") " to go.")
          "After 20:00, this flame is the one that needs you.")])]))

(defn- slot-dots
  [unlocked active]
  [:div {:class "flex items-center gap-1.5" :aria-hidden "true"}
   (for [i (range 5)]
     ^{:key i}
     [:span {:class (str "flex h-7 w-7 items-center justify-center rounded-full border text-[0.625rem] font-semibold "
                         (cond
                           (< i active) "border-ember-400/40 bg-ember-500/20 text-ember-200"
                           (< i unlocked) "border-ember-400/35 bg-ember-400 text-ink-950"
                           :else "border-white/10 bg-white/[0.025] text-slate-400"))}
      (if (< i unlocked) (inc i) "·")])])

(defn- slot-unit
  [next-slot]
  (if (= :week (:unit next-slot))
    ["week" "weeks"]
    (if (and (:habit next-slot) (model/fixed-days? (:habit next-slot)))
      ["scheduled day" "scheduled days"]
      ["day" "days"])))

(defn- slot-ladder
  [{:habits/keys [list] :app/keys [earned-slots]} dispatch open-add]
  (let [unlocked (model/unlocked-slots list earned-slots)
        active (model/active-count {:habits/list list})
        left (max 0 (- unlocked active))
        next-slot (model/next-slot list earned-slots)
        can-add? (pos? left)]
    [:section {:class "panel mt-4 overflow-hidden p-4"}
     [:div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
      [:div
       [:p {:class "eyebrow"} "Flame slots"]
       [:h2 {:class "display mt-1 text-lg text-white"}
        (cond
          (= unlocked 5) "All five flames unlocked"
          (pos? left) (str left " open slot" (when (not= left 1) "s"))
          :else "All slots are holding a flame")]
       [:p {:class "mt-1 max-w-md text-xs leading-relaxed text-slate-400"}
        "Each slot holds one active ritual. Slots are earned by your best streak and stay earned."]]
      [slot-dots unlocked active]]
     (if next-slot
       (let [[one many] (slot-unit next-slot)
             count-value (:best next-slot)
             target (:at next-slot)
             remaining (:remaining next-slot)]
         [:div {:class "mt-4"}
          [:div {:class "mb-1.5 flex items-center justify-between gap-3 text-xs"}
           [:span {:class "text-slate-400"}
            (str "Slot " (:slot next-slot) " at a " target "-" one " best")]
           [:span {:class "font-display font-semibold tabular text-ember-300"}
            (str count-value "/" target)]]
          [:div {:class "h-2 overflow-hidden rounded-full bg-white/[0.06]"}
           [:div {:class "h-full rounded-full bg-gradient-to-r from-ember-400 to-ember-300 transition-all duration-700"
                  :style {:width (str (* 100 (:progress next-slot)) "%")}}]]
          [:p {:class "mt-1.5 text-xs text-slate-400"}
           (if (= 1 remaining)
             (str "One more " one " at your best unlocks the next slot.")
             (str remaining " more " many " at your best unlock the next slot."))]])
       [:p {:class "mt-3 text-xs text-slate-400"}
        "Top of the ladder. The work is the reward now."])
     [:div {:class "mt-4 flex flex-wrap items-center gap-2"}
      [:button {:type "button"
                :class "btn btn-primary min-h-11 px-4 text-sm"
                :disabled (not can-add?)
                :aria-describedby (when-not can-add? "slot-limit-reason")
                :on-click open-add}
       [icons/icon "plus" {:class "h-4 w-4"}]
       "New ritual"]
      (when-not can-add?
        [:p {:id "slot-limit-reason" :class "text-xs leading-snug text-slate-400"}
         "Archive a ritual or grow a best streak to free a slot."])]]))

(defn- at-risk-banner
  [habits today now-minutes]
  (let [at-risk (filter #(model/at-risk? % today now-minutes) habits)]
    (when (seq at-risk)
      [:div {:class "mt-4 flex items-start gap-3 rounded-2xl border border-ember-400/20 bg-ember-500/[0.07] p-3.5"
             :role "status"}
       [:span {:class "mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-ember-500/15 text-ember-300"}
        [icons/icon "flame" {:class "h-4 w-4 animate-flicker"}]]
       [:div
        [:p {:class "font-display text-sm font-semibold text-ember-100"}
         (str (count at-risk) " deadline" (when (not= 1 (count at-risk)) "s") " close")]
        [:p {:class "mt-0.5 text-xs leading-relaxed text-ember-200/75"}
         "It is after 20:00. A tiny version still counts — the point is to return, not to perform."]]])))

(defn- hero
  [state]
  (let [{:habits/keys [list] :app/keys [today now-minutes]} state
        habits (model/active-habits state)
        today-habits (filterv #(or (model/done-on? % today)
                                   (model/available-today? % today))
                              habits)
        total (count today-habits)
        done (count (filter #(model/done-on? % today) today-habits))
        resting (count (filter #(rest-today? % today) habits))
        pct (if (pos? total) (/ done total) 0)
        live (model/best-live-habit habits today)
        all-lit? (and (pos? total) (= done total))
        next-reminder (model/next-reminder habits today now-minutes)
        next-slot (model/next-slot list (:app/earned-slots state))]
    [:section {:class "panel hero-aurora relative overflow-hidden p-5 sm:p-6"}
     [:div {:class "relative flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between"}
      [:div {:class "min-w-0"}
       [:div {:class "flex items-center gap-2"}
        [:span {:class "inline-flex h-2 w-2 rounded-full bg-spark shadow-[0_0_10px_2px_rgba(163,230,53,.55)]"}]
        [:p {:class "eyebrow"} (str (model/greeting now-minutes) " · " (long-date today))]]
       [:h1 {:class "display mt-2 max-w-md text-[2.15rem] leading-[1.02] text-white sm:text-[2.7rem]"}
        (cond
          (zero? (count habits)) "Start with one small thing."
          (zero? total) "Nothing due today."
          all-lit? "Every flame is lit."
          (zero? done) "One flame is all it takes."
          :else "Keep the fire alive.")]
       [:p {:class "mt-2 max-w-md text-sm leading-relaxed text-slate-400"}
        (cond
          (zero? (count habits)) "One ritual. One cue. One tap when it is done."
          (zero? total) "The chain is safe. Rest, or get ahead without pressure."
          all-lit? "The rest of today is yours. Come back when the next period opens."
          (zero? done) "Do the smallest version you can still call done. Momentum beats intensity."
          :else "The chain is already moving. Finish the next one small.")]
       [:div {:class "mt-4 flex flex-wrap items-center gap-2"}
        (when live
          [ui/chip {:icon "flame" :tone :warn}
           (str (:habit/name (:habit live)) " · " (ui/streak-label (:best live) (:unit live)) " live")])
        (when next-reminder
          [ui/chip {:icon "clock"}
           (str "Next: " (:habit/name (:habit next-reminder)) " at "
                (time-label (:habit/reminder (:habit next-reminder))))])
        (when next-slot
          [ui/chip {:icon "lock"}
           (str "Slot " (:slot next-slot) " in " (:remaining next-slot)
                (if (= :week (:unit next-slot)) "w" "d"))])
        (when (pos? resting)
          [ui/chip {:icon "clock"} (str resting " resting")])]]
      [:div {:class "flex shrink-0 items-center justify-between gap-4 sm:flex-col sm:items-end"}
       [ui/progress-ring {:value pct
                          :label (if (pos? total) (str done "/" total) "—")
                          :sublabel (if (pos? total) "due today" "rest day")}]
       [:div {:class "text-right sm:mt-1"}
        [:p {:class "font-display text-5xl font-semibold leading-none tabular text-white sm:text-4xl"}
         (str done)]
        [:p {:class "mt-1 text-xs text-slate-400"}
         (str "of " total " due")]]]]
     [at-risk-banner habits today now-minutes]]))

(defn today-screen
  "The Today screen. `state` is the current map; callbacks open the add and edit
  modals and dispatch events."
  [state dispatch open-add open-editor open-log]
  (let [habits (model/sort-habits (model/active-habits state))
        archived (model/archived-habits state)]
    [:div {:class "animate-slide-up"}
     [hero state]
     (when (seq habits)
       [:div {:class "mt-6 space-y-3"}
        (for [habit habits]
          ^{:key (:habit/id habit)}
          [habit-card habit state dispatch open-editor open-log])])
     (when-not (seq habits)
       [:div {:class "mt-6"}
        [ui/empty-state
         {:title (if (seq archived) "Your fire is resting." "No flames yet.")
          :body (if (seq archived)
                  "Every ritual is archived. Restore one from Settings, or start a new flame."
                  "Start with one small ritual. The first slot is yours; the next is earned.")
          :action [:button {:type "button" :class "btn btn-primary" :on-click open-add}
                   [icons/icon "plus" {:class "h-4 w-4"}]
                   "Start a ritual"]}]])
     [slot-ladder state dispatch open-add]
     [:p {:class "mt-5 text-center text-xs leading-relaxed text-slate-400"}
      "A missed period is data, not a verdict. The shield is there for the day life happens."]]))
