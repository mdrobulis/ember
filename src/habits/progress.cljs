(ns habits.progress
  "The Progress screen: the honest numbers, a weekly column chart, and a
  five-week heatmap per ritual."
  (:require [clojure.string :as str]
            [habits.icons :as icons]
            [habits.model :as model]
            [habits.today :as today]
            [habits.ui :as ui]))

(defn- weekday-initial
  [iso-day]
  (if-let [[year month day] (model/parts iso-day)]
    (let [d (js/Date. (js/Date.UTC year (dec month) day 12))
          names ["S" "M" "T" "W" "T" "F" "S"]]
      (names (.getUTCDay d)))
    "·"))

(defn- weekly-chart
  [habits today-iso]
  (let [counts (model/weekly-counts habits today-iso)
        top (max 1 (apply max (map :count counts)))]
    [:section {:class "panel mt-4 p-5"}
     [ui/section-heading {:eyebrow "The current chapter" :title "This week"}]
     [:p {:class "mb-4 text-xs leading-relaxed text-slate-400"}
      "Monday starts a new chapter. A bad week costs a week, never your lifetime total."]
     [:div {:class "flex items-end justify-between gap-2 sm:gap-3" :role "img"
            :aria-label (str "Completions this week: "
                             (str/join ", " (map #(str (:count %) " on " (today/short-date (:day %))) counts)))}
      (for [{:keys [day count]} counts]
        (let [future? (pos? (compare day today-iso))
              current? (= day today-iso)
              height (if (zero? count) 4 (max 8 (* 100 (/ count top))))]
          ^{:key day}
          [:div {:class "flex min-w-0 flex-1 flex-col items-center gap-1.5"}
           [:span {:class (str "font-display text-[0.6875rem] font-semibold tabular "
                               (if current? "text-ember-300" "text-slate-400"))}
            count]
           [:div {:class "flex h-28 w-full items-end justify-center"}
            [:div {:class (str "w-full max-w-[2rem] rounded-t-lg transition-all duration-700 "
                               (cond
                                 future? "border border-dashed border-white/10 bg-transparent"
                                 (pos? count) "bg-gradient-to-t from-ember-600 via-ember-500 to-ember-300"
                                 :else "bg-white/[0.055]"))
                   :style {:height (str height "%")}}]]
           [:span {:class (str "font-display text-[0.625rem] font-semibold "
                               (if current? "text-ember-300" "text-slate-400"))}
            (weekday-initial day)]]))]
     [:p {:class "mt-3 text-[0.6875rem] leading-relaxed text-slate-400"}
      "Counts real sessions across active rituals. Repaired periods are shields, not sessions."]]))

(defn- heatmap
  [habit today-iso]
  (let [cells (model/habit-heatmap habit today-iso)
        complete (count (filter :done cells))
        repaired (count (filter :repaired cells))]
    [:div {:class "panel-soft p-3.5"}
     [:div {:class "flex items-start justify-between gap-3"}
      [:div {:class "min-w-0"}
       [:h3 {:class "truncate font-display text-sm font-semibold text-white"} (:habit/name habit)]
       [:p {:class "mt-0.5 text-[0.6875rem] text-slate-400"}
        (str complete "/35 done"
             (when (pos? repaired) (str " · " repaired " repaired")))]]
      [:span {:class "chip shrink-0 border-ember-400/20 bg-ember-500/10 text-ember-200"}
       (str (model/current-streak habit today-iso) (ui/unit-short (model/streak-unit habit))) ]]
     [:div {:class "mt-3 grid grid-cols-7 gap-1" :aria-hidden "true"}
      (for [{:keys [day done repaired rest? missed?]} cells]
        ^{:key day}
        [:span {:class (str "heat-cell "
                            (cond repaired "heat-cell--repaired"
                                  done "heat-cell--done"
                                  rest? "heat-cell--rest"
                                  missed? "heat-cell--missed"
                                  :else ""))
                :title (str day (cond repaired " · repaired" done " · done" rest? " · rest" missed? " · missed" :else " · open"))}])]
     [:div {:class "mt-2 flex flex-wrap items-center gap-3 text-[0.625rem] text-slate-400"}
      [:span {:class "flex items-center gap-1"}
       [:span {:class "h-2.5 w-2.5 rounded-[0.2rem] bg-ember-400"}] "done"]
      [:span {:class "flex items-center gap-1"}
       [:span {:class "h-2.5 w-2.5 rounded-[0.2rem] bg-frost/40"}] "shield"]
      [:span {:class "flex items-center gap-1"}
       [:span {:class "h-2.5 w-2.5 rounded-[0.2rem] border border-white/10 bg-white/[0.03]"}] "rest"]
      [:span {:class "flex items-center gap-1"}
       [:span {:class "h-2.5 w-2.5 rounded-[0.2rem] border border-rose/25 bg-rose/15"}] "missed"]
      [:span "35 days"]]]))

(defn progress-screen
  "The Progress screen: honest totals, a weekly column chart and heatmaps."
  [{:habits/keys [list] :app/keys [today] :as state}]
  (let [habits (model/sort-habits (model/active-habits state))
        rates (map #(model/completion-rate % today 30) habits)
        avg-rate (if (seq rates) (js/Math.round (/ (reduce + rates) (count rates))) 0)
        best (model/global-best-habit list)
        best-value (if best (str (:best best) (ui/unit-short (:unit best))) "—")
        best-sub (if best
                   (str (:habit/name (:habit best)) " · " (ui/schedule-label (:habit best)))
                   "No run yet")
        shows (model/total-shows (model/active-habits state))
        shields (reduce + 0 (map model/shield-balance habits))]
    [:div {:class "animate-slide-up"}
     [:header {:class "mb-5 mt-2"}
      [:p {:class "eyebrow"} "Evidence, not vibes"]
      [:h1 {:class "display mt-1 text-3xl text-white"} "Progress"]
      [:p {:class "mt-1 text-sm text-slate-400"}
       "The streak is the headline. These are the footnotes that make it true."]]
     (if (empty? habits)
       [ui/empty-state
        {:title "Nothing to measure yet."
         :body "Light one flame on Today. The numbers will follow the tap."}]
       [:<>
        [:div {:class "grid grid-cols-2 gap-2.5 sm:grid-cols-4"}
         [ui/stat {:label "Best run" :value best-value
                   :sub best-sub :icon "target" :tone :warn}]
         [ui/stat {:label "Rate (30d)" :value (str avg-rate "%")
                   :sub "Due periods, active rituals" :icon "chart"}]
         [ui/stat {:label "Sessions" :value (str shows)
                   :sub "Real sessions, all time" :icon "check"}]
         [ui/stat {:label "Shields" :value (str shields)
                   :sub "Banked and ready" :icon "shield"}]]
        [weekly-chart habits today]
        [:section {:class "mt-6"}
         [ui/section-heading {:eyebrow "Five weeks at a glance" :title "Heatmaps"}]
         [:p {:class "mb-3 text-xs leading-relaxed text-slate-400"}
          "Bright means done. Cyan means a shield held the line. A faint cell is a rest day or an open day."]
         [:div {:class "grid gap-2.5 sm:grid-cols-2"}
          (for [habit habits]
            ^{:key (:habit/id habit)}
            [heatmap habit today])]]
        [:section {:class "mt-6"}
         [ui/section-heading {:eyebrow "The ledger" :title "Per ritual"}]
         [:div {:class "panel overflow-hidden"}
          [:table {:class "w-full border-collapse text-left text-sm"}
           [:caption {:class "sr-only"} "Streak and completion numbers per active ritual"]
           [:thead
            [:tr {:class "border-b border-white/[0.07] text-[0.625rem] uppercase tracking-[0.14em] text-slate-400"}
             [:th {:class "px-2 py-2.5 font-medium sm:px-3" :scope "col"} "Ritual"]
             [:th {:class "px-1.5 py-2.5 text-right font-medium" :scope "col"} "Live"]
             [:th {:class "px-1.5 py-2.5 text-right font-medium" :scope "col"} "Best"]
             [:th {:class "px-2 py-2.5 text-right font-medium" :scope "col"} "30d"]
             [:th {:class "hidden px-2 py-2.5 text-right font-medium sm:table-cell sm:px-3" :scope "col"} "Shields"]]]
           [:tbody
            (for [habit habits]
              ^{:key (:habit/id habit)}
              [:tr {:class "border-b border-white/[0.05] last:border-0"}
               [:th {:class "max-w-[8rem] truncate px-2 py-3 font-medium text-slate-200 sm:max-w-[12rem] sm:px-3" :scope "row"}
                (:habit/name habit)]
               [:td {:class "px-1.5 py-3 text-right tabular text-ember-300"}
                (str (model/current-streak habit today) (ui/unit-short (model/streak-unit habit)))]
               [:td {:class "px-1.5 py-3 text-right tabular text-slate-300"}
                (str (model/longest-streak habit) (ui/unit-short (model/streak-unit habit)))]
               [:td {:class "px-2 py-3 text-right tabular text-slate-300"}
                (str (model/completion-rate habit today 30) "%")]
               [:td {:class "hidden px-2 py-3 text-right tabular text-frost sm:table-cell sm:px-3"}
                (model/shield-balance habit)]])]]]
         [:p {:class "mt-2 text-[0.625rem] text-slate-400"}
          "Live and best are shown in each ritual's cadence unit. A shield protects the visible streak but never counts as a session."]]
        [:div {:class "mt-6 rounded-2xl border border-white/[0.06] bg-white/[0.02] p-4"}
         [:p {:class "display text-sm text-white"} "Why the week resets"]
         [:p {:class "mt-1 text-xs leading-relaxed text-slate-400"}
          "A fresh-start landmark makes it easier to resume after a bad week. The chapter resets on Monday; the best streak and lifetime shows never do."]]])]))
