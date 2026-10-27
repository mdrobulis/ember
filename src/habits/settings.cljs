(ns habits.settings
  "Settings: install, honest notification limits, data ownership, archive, and
  the plain-language account of how the product persuades."
  (:require [reagent.core :as r]
            [habits.icons :as icons]
            [habits.model :as model]
            [habits.platform :as platform]
            [habits.storage :as storage]
            [habits.today :as today]
            [habits.ui :as ui]))

(defn- permission-copy
  [permission]
  (case permission
    "granted" "Allowed on this device"
    "denied" "Blocked in browser settings"
    "unsupported" "Not available in this browser"
    "Not asked yet"))

(defn- permission-tone
  [permission]
  (case permission
    "granted" :good
    "denied" :danger
    :warn))

(defn- install-card
  [{:app/keys [standalone?] :as state} open-install]
  [:section {:class "panel p-5"}
   [:div {:class "flex items-start gap-3"}
    [:span {:class "flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-ember-400/20 bg-ember-500/10 text-ember-300"}
     [icons/icon "phone" {:class "h-5 w-5"}]]
    [:div {:class "min-w-0"}
     [:div {:class "flex flex-wrap items-center gap-2"}
      [:h2 {:class "display text-lg text-white"} "Install Ember"]
      (when standalone? [ui/chip {:tone :good} "Installed"])]
     [:p {:class "mt-1 text-sm leading-relaxed text-slate-400"}
      (if standalone?
        "Running from your Home Screen. It opens full-screen and works offline after the first load."
        "Add Ember to your Home Screen. It opens like an app, keeps your data on this device, and works with no signal after the first load.")]
     (when-not standalone?
       [:button {:type "button" :class "btn btn-primary mt-3 min-h-11 px-4 text-sm"
                 :on-click open-install}
        [icons/icon "download" {:class "h-4 w-4"}]
        "Show me how"])]]])

(defn- notifications-card
  [{:app/keys [notification-permission] :as state} dispatch request-permission test-notification]
  (let [timed (filter #(seq (:habit/reminder %)) (model/active-habits state))]
    [:section {:class "panel p-5"}
     [ui/section-heading
      {:eyebrow "Timed rituals" :title "Notifications"
       :action [ui/chip {:tone (permission-tone notification-permission)}
                (permission-copy notification-permission)]}]
     [:p {:class "text-sm leading-relaxed text-slate-400"}
      "While Ember is open, a timed ritual can send a system notification. A closed serverless app cannot schedule one for later; that is a platform limit, not a setting."]
     [:div {:class "mt-4 flex flex-wrap gap-2"}
      (case notification-permission
        "granted"
        [:button {:type "button" :class "btn btn-ghost min-h-11 px-4 text-sm"
                  :on-click test-notification}
         [icons/icon "bell" {:class "h-4 w-4"}] "Send a test"]

        "denied"
        [:div {:class "w-full rounded-2xl border border-rose/20 bg-rose/[0.06] p-3.5"}
         [:p {:class "text-sm font-medium text-rose"} "Notifications are blocked"]
         [:p {:class "mt-1 text-xs leading-relaxed text-slate-400"}
          "Allow notifications for this site in your browser or system settings, then reload Ember. The calendar alarm below does not need permission."]]

        "unsupported"
        [:p {:class "text-sm text-slate-400"}
         "This browser cannot show web notifications. The calendar export below still works."]

        [:button {:type "button" :class "btn btn-primary min-h-11 px-4 text-sm"
                  :on-click request-permission}
         [icons/icon "bell" {:class "h-4 w-4"}] "Enable notifications"])]
     (when (and (platform/ios?) (not (:app/standalone? state)))
       [:p {:class "mt-3 rounded-xl border border-ember-400/15 bg-ember-500/[0.06] p-3 text-xs leading-relaxed text-ember-200/80"}
        "On iPhone, notifications require the app to be added to the Home Screen first. Install steps are above."])
     [:div {:class "mt-5 rounded-2xl border border-white/[0.07] bg-ink-950/45 p-3.5"}
      [:div {:class "flex items-start justify-between gap-3"}
       [:div
        [:p {:class "font-display text-sm font-semibold text-white"} "Reliable when closed: calendar"]
        [:p {:class "mt-1 text-xs leading-relaxed text-slate-400"}
         "A daily calendar event with an alarm is the one OS-level reminder a serverless app can create. One tap adds every timed ritual."]]
       [icons/icon "calendar" {:class "h-5 w-5 shrink-0 text-frost"}]]
      (if (seq timed)
        [:<>
         [:ul {:class "mt-3 space-y-1.5"}
          (for [habit timed]
            ^{:key (:habit/id habit)}
            [:li {:class "flex items-center justify-between gap-2 text-xs"}
             [:span {:class "truncate text-slate-300"} (:habit/name habit)]
             [:span {:class "shrink-0 font-display tabular text-slate-400"}
              (today/time-label (:habit/reminder habit))]])]
         [:button {:type "button"
                   :class "btn btn-ghost mt-3 min-h-11 w-full px-4 text-sm"
                   :on-click #(let [ics (model/calendar-ics (model/active-habits state)
                                                            (:app/today state)
                                                            (:app/now-minutes state))]
                                (if ics
                                  (storage/download-ics! ics "ember-reminders.ics")
                                  (dispatch [:error "No timed ritual to add. Set a time when you edit a habit."])))}
          [icons/icon "calendar" {:class "h-4 w-4"}] "Add all to calendar"]
         [:p {:class "mt-2 text-[0.6875rem] leading-relaxed text-slate-400"}
          "This downloads an .ics file. Open it to add a repeating daily alarm to your calendar."]]
        [:p {:class "mt-3 text-xs text-slate-400"}
         "No timed rituals yet. Open a ritual, choose Edit, and set a time."])]]))

(defn- import-panel
  [state dispatch]
  (let [pending (r/atom nil)
        on-file (fn [event]
                  (let [input (.-target event)
                        file (.item (.-files input) 0)]
                    (set! (.-value input) "")
                    (when file
                      (storage/read-file!
                       file
                       (fn [text]
                         (let [{:keys [ok error]} (storage/parse-export text)]
                           (if error
                             (dispatch [:error error])
                             (try
                               (let [habits (model/parse-habits (or (:habits/list ok) (:list ok)))]
                                 (reset! pending {:data ok :count (count habits)}))
                               (catch :default e
                                 (dispatch [:error (.-message e)]))))))
                       #(dispatch [:error %])))))]
    (fn []
      [:<>
       [:div {:class "mt-4 flex flex-wrap gap-2"}
        [:button {:type "button" :class "btn btn-ghost min-h-11 px-4 text-sm"
                  :on-click #(do (storage/download! (model/export-data @state (model/today))
                                                    (str "ember-" (model/today) ".json"))
                                 (dispatch [:toast {:kind :success
                                                    :message "Export downloaded. Keep it somewhere that survives the phone."}]))}
         [icons/icon "download" {:class "h-4 w-4"}] "Export JSON"]
        [:label {:for "import-file" :class "btn btn-ghost min-h-11 cursor-pointer px-4 text-sm"}
         [icons/icon "upload" {:class "h-4 w-4"}] "Import JSON"]
        [:input {:id "import-file" :type "file"
                 :class "field cursor-pointer text-xs file:mr-3 file:rounded-xl file:border-0 file:bg-white/10 file:px-3 file:py-2 file:font-display file:text-xs file:font-semibold file:text-slate-200 hover:file:bg-white/15"
                 :accept "application/json,.json"
                 :aria-label "Choose an Ember JSON export to import"
                 :on-change on-file}]]
       (when-let [{:keys [data count]} @pending]
         [:div {:class "mt-3 rounded-2xl border border-rose/25 bg-rose/[0.06] p-3.5" :role "status"}
          [:p {:class "text-sm font-medium text-rose"}
           (str "Replace everything with " count " ritual" (when (not= 1 count) "s") "?")]
          [:p {:class "mt-1 text-xs leading-relaxed text-slate-400"}
           "Import replaces the whole list. It is not a merge, and it cannot be undone from inside Ember."]
          [:div {:class "mt-3 flex gap-2"}
           [:button {:type "button" :class "btn btn-danger min-h-10 px-3 text-sm"
                     :on-click #(do (dispatch [:import data])
                                    (reset! pending nil))}
            "Replace"]
           [:button {:type "button" :class "btn btn-ghost min-h-10 px-3 text-sm"
                     :on-click #(reset! pending nil)} "Cancel"]]])])))

(defn- data-card
  [state dispatch]
  [:section {:class "panel p-5"}
   [ui/section-heading {:eyebrow "Yours, locally" :title "Your data"}]
   [:p {:class "text-sm leading-relaxed text-slate-400"}
    "No account, no server, no analytics. Everything lives in this browser's local storage. iOS can evict it under storage pressure, so an export is the only real backup."]
   [import-panel state dispatch]
   [:p {:class "mt-3 text-[0.6875rem] leading-relaxed text-slate-400"}
    "Export format is additive JSON. A file from a newer version is kept, not tightened."]])

(defn- archive-card
  [state dispatch]
  (let [archived (model/archived-habits state)]
    [:section {:class "panel p-5"}
     [ui/section-heading
      {:eyebrow "Out of the way, not gone"
       :title (str "Archive (" (count archived) ")")}]
     (if (seq archived)
       [:ul {:class "space-y-2"}
        (for [habit archived]
          ^{:key (:habit/id habit)}
          [:li {:class "flex items-center justify-between gap-3 rounded-2xl border border-white/[0.06] bg-white/[0.02] p-3"}
           [:div {:class "min-w-0"}
            [:p {:class "truncate font-display text-sm font-medium text-slate-200"} (:habit/name habit)]
            [:p {:class "text-[0.6875rem] text-slate-400"}
             (str (model/total-done habit) " shows · best " (model/longest-streak habit) "d")]]
           [:div {:class "flex shrink-0 gap-1"}
            [:button {:type "button" :class "btn btn-quiet min-h-9 px-2.5 text-xs"
                      :on-click #(dispatch [:unarchive (:habit/id habit)])} "Restore"]
            [ui/icon-button {:icon "trash" :label (str "Delete " (:habit/name habit))
                             :class "h-9 w-9"
                             :on-click #(dispatch [:ask-delete (:habit/id habit)])}]]])]
       [:p {:class "text-sm text-slate-400"}
        "Archived rituals keep their history, free their slot, and can come back. Nothing is deleted by archiving."])]))

(defn- clear-card
  []
  (let [confirm? (r/atom false)]
    (fn []
      [:section {:class "rounded-3xl border border-rose/15 bg-rose/[0.025] p-5"}
       [:h2 {:class "display text-lg text-rose"} "Erase this device"]
       [:p {:class "mt-1 text-sm leading-relaxed text-slate-400"}
        "Delete every ritual, completion, shield and preference from this browser. Export first if you might want any of it."]
       [:button {:type "button" :class "btn btn-danger mt-3 min-h-11 px-4 text-sm"
                 :on-click #(reset! confirm? true)}
        [icons/icon "trash" {:class "h-4 w-4"}] "Erase all data"]
       (when @confirm?
         [ui/modal
          {:on-close #(reset! confirm? false)
           :title "Erase everything?"
           :subtitle "This cannot be undone from inside Ember."
           :content
           [:div
            [:p {:class "text-sm leading-relaxed text-slate-300"}
             "Every ritual and every day of history will be removed from this browser. There is no account and no cloud copy."]
            [:div {:class "mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end"}
             [:button {:type "button" :class "btn btn-ghost"
                       :on-click #(reset! confirm? false)} "Keep my data"]
             [:button {:type "button" :class "btn btn-danger"
                       :on-click #(do (storage/clear-all!)
                                      (.reload js/location))}
              "Yes, erase this device"]]]}])])))

(defn- transparency-card
  []
  [:section {:class "panel p-5"}
   [ui/section-heading {:eyebrow "The design contract" :title "How Ember persuades"}]
   [:p {:class "text-sm leading-relaxed text-slate-400"}
    "The app uses pressure deliberately, and writes it down. If a mechanic only works because you cannot see it, it does not belong here."]
   [:div {:class "mt-4 space-y-3"}
    [ui/bullet {:icon "flame" :tone :warn
                :title "Loss aversion, with a curfew"
                :body "The flame matters. At-risk language appears only after 20:00, only when the habit is really incomplete."}]
    [ui/bullet {:icon "lock" :tone :warn
                :title "Scarcity that is earned"
                :body "One slot to begin; 3, 7, 14 and 30-day best streaks unlock the rest. A slot can never be bought or taken away."}]
    [ui/bullet {:icon "shield" :tone :cool
                :title "A landing net, not a time machine"
                :body "Seven completions earn a shield. It can repair yesterday, never today or the future, and it never unlocks a slot."}]
    [ui/bullet {:icon "refresh" :tone :good
                :title "The weekly fresh start"
                :body "The chapter resets on Monday. Lifetime shows and best streaks never do. A bad week costs a week."}]
    [ui/bullet {:icon "info"
                :title "No fake urgency, shame, or hidden costs"
                :body "No countdown timers, no confirm-shaming, no pre-ticked consent, no purchases, no social proof invented to look busy."}]]
   [:details {:class "mt-4 rounded-2xl border border-white/[0.06] bg-white/[0.02] p-3.5"}
    [:summary {:class "cursor-pointer font-display text-sm font-medium text-slate-300"}
     "The research behind these choices"]
    [:div {:class "mt-3 space-y-2 text-xs leading-relaxed text-slate-400"}
     [:p "Implementation intentions: 94 tests, medium-to-large effect (d = 0.65). "
      [:a {:class "text-ember-300 underline decoration-ember-300/30 underline-offset-2"
           :href "https://doi.org/10.1016/S0065-2601(06)38002-1"
           :target "_blank" :rel "noopener noreferrer"} "Gollwitzer & Sheeran, 2006"]]
     [:p "Habit formation varied from 18 to 254 days; one missed day did not materially disrupt it. "
      [:a {:class "text-ember-300 underline decoration-ember-300/30 underline-offset-2"
           :href "https://doi.org/10.1002/ejsp.674"
           :target "_blank" :rel "noopener noreferrer"} "Lally et al., 2010"]]
     [:p "Intact logged streaks increased later engagement; repair attenuated the damage of a break. "
      [:a {:class "text-ember-300 underline decoration-ember-300/30 underline-offset-2"
           :href "https://doi.org/10.1093/jcr/ucac029"
           :target "_blank" :rel "noopener noreferrer"} "Silverman & Barasch, 2023"]]
     [:p "The full brief, including rejected dark patterns and PWA limits, is in "
      [:code {:class "text-slate-300"} "RESEARCH.md"] " in the project."]]]])

(defn- limits-card
  []
  [:section {:class "panel p-5"}
   [ui/section-heading {:eyebrow "Honest limits" :title "What Ember cannot do"}]
   [:ul {:class "space-y-2.5 text-sm leading-relaxed text-slate-400"}
    [:li [:strong {:class "text-slate-200"} "No sync."] " Two phones are two separate datasets."]
    [:li [:strong {:class "text-slate-200"} "No server."] " A closed app cannot schedule a local notification. Use the calendar export."]
    [:li [:strong {:class "text-slate-200"} "No guarantee against eviction."] " iOS can clear site data under pressure. Export."]
    [:li [:strong {:class "text-slate-200"} "No social anything."] " No accounts, leaderboards, or sharing."]]
   [:p {:class "mt-4 text-[0.6875rem] text-slate-400"} "Ember 2.0 · local-first · no tracking"]])

(defn settings-screen
  "Settings, with install, reminders, data ownership and the design contract."
  [state dispatch open-install request-permission test-notification]
  (let [current @state]
    [:div {:class "animate-slide-up space-y-4"}
     [:header {:class "mb-2 mt-2"}
      [:p {:class "eyebrow"} "Your phone, your fire"]
      [:h1 {:class "display mt-1 text-3xl text-white"} "Settings"]
      [:p {:class "mt-1 text-sm text-slate-400"} "Install it, time it, back it up, or take it all away."]]
     [install-card current open-install]
     [notifications-card current dispatch request-permission test-notification]
     [data-card state dispatch]
     [archive-card current dispatch]
     [transparency-card]
     [limits-card]
     [clear-card]]))
