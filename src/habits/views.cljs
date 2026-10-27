(ns habits.views
  "Root view: brand, navigation, onboarding, the three screens, and the shared
  modal layer. Every state transition is delegated to `dispatch`; nothing here
  decides what a habit is."
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [habits.icons :as icons]
            [habits.model :as model]
            [habits.platform :as platform]
            [habits.progress :as progress]
            [habits.settings :as settings]
            [habits.today :as today]
            [habits.ui :as ui]))

(defn- go!
  [dispatch screen]
  (dispatch [:screen screen])
  (when (and (exists? js/window) (not= (.-hash js/window.location) (str "#" (name screen))))
    (set! (.-hash js/window.location) (name screen)))
  (when (exists? js/window)
    (js/window.scrollTo #js {:top 0 :behavior "smooth"})))

(defn- brand
  [{:app/keys [standalone?]} dispatch open-install]
  [:header {:class "mb-5 flex items-center justify-between gap-3 pt-1"}
   [:a {:href "#today"
        :class "group flex min-h-11 items-center gap-2.5 rounded-2xl focus-visible:outline-none"
        :on-click (fn [e] (.preventDefault e) (go! dispatch :today))}
    [icons/logo {:class "h-10 w-10 transition duration-300 group-hover:rotate-3 group-hover:scale-105"}]
    [:div
     [:p {:class "display text-[1.15rem] leading-none tracking-[0.12em] text-white"} "EMBER"]
     [:p {:class "mt-1 font-display text-[0.5625rem] font-medium uppercase tracking-[0.24em] text-slate-400"}
      "Keep the fire alive"]]]
   (when-not standalone?
     [:button {:type "button"
               :class "btn btn-ghost min-h-9 px-3 text-xs"
               :on-click open-install}
      [icons/icon "download" {:class "h-3.5 w-3.5"}]
      "Install"])])

(defn- bottom-nav
  [screen dispatch]
  [:nav {:class "bottom-nav" :aria-label "Primary"}
   [:div {:class "flex gap-1"}
    (for [[key icon label] [[:today "flame" "Today"]
                            [:progress "chart" "Progress"]
                            [:settings "settings" "Settings"]]]
      ^{:key key}
      [:button {:type "button"
                :class "nav-item"
                :aria-current (when (= key screen) "page")
                :on-click #(go! dispatch key)}
       [icons/icon icon {:class "h-5 w-5"}]
       [:span label]])]])

(defn- error-banner
  [{:app/keys [error]} dispatch]
  (when error
    [:div {:class "mb-4 flex items-start gap-3 rounded-2xl border border-rose/25 bg-rose/[0.08] p-3.5"
           :role "alert"}
     [:span {:class "mt-0.5 text-rose"} [icons/icon "info" {:class "h-4 w-4"}]]
     [:p {:class "min-w-0 flex-1 text-sm leading-relaxed text-rose"} error]
     [ui/icon-button {:icon "x" :label "Dismiss error" :class "h-8 w-8"
                      :on-click #(dispatch [:clear-error])}]]))

(defn- field
  [{:keys [id label hint type value on-change placeholder autofocus required? min max step]}]
  [:div
   [:label {:for id :class "field-label"} label
    (when required? [:span {:class "ml-1 text-ember-300" :aria-hidden "true"} "*"])]
   [:input {:id id
            :name id
            :type (or type "text")
            :class "field"
            :value (or value "")
            :placeholder placeholder
            :autofocus autofocus
            :required required?
            :min min
            :max max
            :step step
            :autocomplete "off"
            :on-change #(on-change (.-value (.-target %)))}]
   (when hint [:p {:class "field-hint"} hint])])

(defn- habit-form
  [{:keys [habit on-save on-archive on-delete on-close add?]}]
  (let [draft (r/atom {:name     (or (:habit/name habit) "")
                       :cue      (or (:habit/cue habit) "")
                       :why      (or (:habit/why habit) "")
                       :reminder (or (:habit/reminder habit) "")})
        error (r/atom nil)
        update! (fn [key value] (swap! draft assoc key value))]
    (fn []
      (let [{:keys [name cue why reminder]} @draft]
        [:form {:on-submit
                (fn [event]
                  (.preventDefault event)
                  (if (str/blank? (str/trim name))
                    (reset! error "Give the ritual a name — even a tiny one.")
                    (on-save {:habit/name     (str/trim name)
                              :habit/cue      (str/trim cue)
                              :habit/why      (str/trim why)
                              :habit/reminder (when (seq reminder) reminder)})))}
         [:div {:class "space-y-4"}
          [field {:id "habit-name" :label "The ritual" :required? true
                  :value name :autofocus true
                  :placeholder "Read 10 pages"
                  :on-change #(update! :name %)}]
          [field {:id "habit-cue" :label "The cue"
                  :value cue
                  :placeholder "After morning coffee"
                  :hint "Implementation intention: name the moment that comes before the action."
                  :on-change #(update! :cue %)}]
          [:div
           [:label {:for "habit-reminder" :class "field-label"} "A time, if it helps"]
           [:div {:class "flex gap-2"}
            [:input {:id "habit-reminder" :name "habit-reminder" :type "time"
                     :class "field flex-1"
                     :value reminder
                     :on-change #(update! :reminder (.-value (.-target %)))}]
            (when (seq reminder)
              [:button {:type "button" :class "btn btn-quiet min-h-12 px-3 text-xs"
                        :on-click #(update! :reminder "")} "Clear"])]
           [:p {:class "field-hint"}
            "Notifications fire while Ember is open. For a closed app, export the daily calendar alarm in Settings."]]
          [field {:id "habit-why" :label "Why it matters"
                  :value why
                  :placeholder "So my mornings start with my own mind."
                  :hint "One line. It appears on the card when you need the reminder."
                  :on-change #(update! :why %)}]
          (when @error
            [:p {:class "rounded-xl border border-rose/25 bg-rose/[0.08] px-3 py-2 text-sm text-rose" :role "alert"}
             @error])
          (when (seq (str/trim cue))
            [:div {:class "rounded-2xl border border-white/[0.06] bg-white/[0.025] p-3.5"}
             [:p {:class "eyebrow mb-1"} "The intention"]
             [:p {:class "text-sm leading-relaxed text-slate-300"}
              "I will " [:strong {:class "text-white"} (str/trim name)]
              " " (str/trim cue)
              (when (seq reminder) (str " at " (today/time-label reminder)))
              "."]])
          [:div {:class "flex flex-col-reverse gap-2 pt-1 sm:flex-row sm:justify-end"}
           [:button {:type "button" :class "btn btn-ghost" :on-click on-close} "Cancel"]
           [:button {:type "submit" :class "btn btn-primary"}
            [icons/icon (if add? "flame" "check") {:class "h-4 w-4"}]
            (if add? "Light this flame" "Save ritual")]]]
         (when (and habit (not add?))
           [:div {:class "mt-6 border-t border-white/[0.07] pt-4"}
            [:p {:class "eyebrow mb-2"} "Manage"]
            [:div {:class "flex flex-wrap items-center justify-between gap-2"}
             [:button {:type "button" :class "btn btn-quiet min-h-10 px-3 text-xs"
                       :on-click on-archive}
              [icons/icon "archive" {:class "h-4 w-4"}] "Archive"]
             [:button {:type "button" :class "btn btn-danger min-h-10 px-3 text-xs"
                       :on-click on-delete}
              [icons/icon "trash" {:class "h-4 w-4"}] "Delete forever"]]])]))))

(defn- onboarding
  [{:app/keys [error] :as state} dispatch]
  (let [step (r/atom 1)
        draft (r/atom {:name "" :cue "" :why "" :reminder ""})
        local-error (r/atom nil)
        update! (fn [key value] (swap! draft assoc key value))
        finish! (fn []
                  (let [{:keys [name cue why reminder]} @draft]
                    (cond
                      (str/blank? (str/trim name))
                      (reset! local-error "A name is enough to begin.")

                      (str/blank? (str/trim cue))
                      (reset! local-error "Name the moment before the ritual. That is the anchor.")

                      :else
                      (dispatch [:add {:habit/name     (str/trim name)
                                       :habit/cue      (str/trim cue)
                                       :habit/why      (str/trim why)
                                       :habit/reminder (when (seq reminder) reminder)}]))))]
    (fn []
      (let [{:keys [name cue why reminder]} @draft]
        [:main {:class "page-shell flex min-h-[100svh] flex-col justify-center" :id "main"}
         [:div {:class "mx-auto w-full max-w-md animate-slide-up"}
          [:div {:class "mb-7 flex flex-col items-center text-center"}
           [icons/logo {:class "h-14 w-14"}]
           [:p {:class "mt-4 font-display text-sm font-semibold tracking-[0.26em] text-white"} "EMBER"]
           [:p {:class "mt-1 text-sm text-slate-400"} "Keep the fire alive."]]
          [:div {:class "panel overflow-hidden"}
           [:div {:class "hero-aurora border-b border-white/[0.06] px-5 py-5 sm:px-6"}
            [:div {:class "flex items-center justify-between gap-3"}
             [:div
              [:p {:class "eyebrow"} (str "Step " @step " of 2")]
              [:h1 {:class "display mt-1 text-2xl text-white"}
               (if (= @step 1) "One small ritual." "Anchor it to a moment.")]]
             [:div {:class "flex gap-1.5" :aria-hidden "true"}
              (for [i [1 2]]
                ^{:key i}
                [:span {:class (str "h-1.5 rounded-full transition-all "
                                    (if (>= @step i) "w-7 bg-ember-400" "w-3 bg-white/15"))}])]]
            [:p {:class "mt-2 text-sm leading-relaxed text-slate-400"}
             (if (= @step 1)
               "Pick the smallest daily action you would still call a win on a bad day."
               "Name the moment before it. This is the part the research says matters most.")]]
           [:div {:class "p-5 sm:p-6"}
            (if (= @step 1)
              [:form {:on-submit (fn [event]
                                   (.preventDefault event)
                                   (if (str/blank? (str/trim name))
                                     (reset! local-error "A name is enough to begin.")
                                     (do (reset! local-error nil) (reset! step 2))))}
               [:div {:class "space-y-4"}
                [field {:id "onboard-name" :label "The ritual" :required? true
                        :value name :autofocus true
                        :placeholder "Read 10 pages"
                        :on-change #(update! :name %)}]
                [:div {:class "rounded-2xl border border-ember-400/15 bg-ember-500/[0.06] p-3.5"}
                 [:p {:class "text-xs leading-relaxed text-ember-100/80"}
                  "One flame to start. A 3-day best unlocks a second slot, then 7, 14 and 30 days. The limit is the point: few promises, kept."]]
                (when @local-error
                  [:p {:class "text-sm text-rose" :role "alert"} @local-error])
                [:button {:type "submit" :class "btn btn-primary w-full"}
                 "Next" [icons/icon "chevron" {:class "h-4 w-4"}]]]]
              [:form {:on-submit (fn [event] (.preventDefault event) (finish!))}
               [:div {:class "space-y-4"}
                [field {:id "onboard-cue" :label "The cue" :required? true
                        :value cue :autofocus true
                        :placeholder "After morning coffee"
                        :hint "The moment that will trigger the action."
                        :on-change #(update! :cue %)}]
                [:div
                 [:label {:for "onboard-reminder" :class "field-label"} "A time, if it helps"]
                 [:input {:id "onboard-reminder" :type "time"
                          :class "field" :value reminder
                          :on-change #(update! :reminder (.-value (.-target %)))}]
                 [:p {:class "field-hint"} "Optional. Ember will nudge while open, or you can export a daily calendar alarm."]]
                [field {:id "onboard-why" :label "Why it matters"
                        :value why
                        :placeholder "So my mornings start with my own mind."
                        :hint "One line, shown on the hard days."
                        :on-change #(update! :why %)}]
                [:div {:class "rounded-2xl border border-white/[0.06] bg-white/[0.025] p-3.5"}
                 [:p {:class "eyebrow mb-1"} "The intention"]
                 [:p {:class "text-sm leading-relaxed text-slate-300"}
                  "I will " [:strong {:class "text-white"} name]
                  " " cue (when (seq reminder) (str " at " (today/time-label reminder))) "."]]
                (when @local-error
                  [:p {:class "text-sm text-rose" :role "alert"} @local-error])
                (when error
                  [:p {:class "text-sm text-rose" :role "alert"} error])
                [:div {:class "flex gap-2"}
                 [:button {:type "button" :class "btn btn-ghost flex-1"
                           :on-click #(do (reset! step 1) (reset! local-error nil))} "Back"]
                 [:button {:type "submit" :class "btn btn-primary flex-[1.4]"}
                  [icons/icon "flame" {:class "h-4 w-4"}] "Light my first flame"]]]])]]
          [:p {:class "mt-5 text-center text-xs leading-relaxed text-slate-400"}
           "No account. No server. Your data stays in this browser."]]]))))

(defn- install-modal
  [{:app/keys [install-event standalone?] :as state} on-close dispatch]
  [:div
   (when standalone?
     [:div {:class "mb-4 rounded-2xl border border-spark/20 bg-spark/[0.07] p-3.5"}
      [:p {:class "text-sm font-medium text-spark"} "You are already running as an app."]
      [:p {:class "mt-1 text-xs text-slate-400"} "The steps below are for a different device or browser."]])
   [:div {:class "space-y-4"}
    [ui/bullet {:icon "phone" :tone :warn
                :title "iPhone or iPad — Safari"
                :body "Tap Share (the square with an arrow), then Add to Home Screen, then Add. Open Ember from the new icon."}]
    [ui/bullet {:icon "phone"
                :title "Android — Chrome"
                :body "Open the three-dot menu and choose Install app (or Add to Home screen). If a prompt is available, the button below is faster."}]
    [ui/bullet {:icon "globe" :tone :cool
                :title "Desktop — Chrome or Edge"
                :body "Click the install icon in the address bar, or the three-dot menu → Install Ember."}]]
   (when install-event
     [:button {:type "button" :class "btn btn-primary mt-5 w-full"
               :on-click #(-> (platform/prompt-install! install-event)
                              (.then (fn [accepted?]
                                       (dispatch [:toast (if accepted?
                                                           {:kind :success :message "Ember is installed. The campfire is yours."}
                                                           {:kind :default :message "Install cancelled. You can try again from Settings."} )])
                                       (when accepted? (on-close)))))}
      [icons/icon "download" {:class "h-4 w-4"}] "Use the browser install prompt"])
   [:div {:class "mt-5 rounded-2xl border border-white/[0.06] bg-white/[0.02] p-3.5"}
    [:p {:class "text-xs font-medium text-slate-300"} "Why the extra step?"]
    [:p {:class "mt-1 text-[0.6875rem] leading-relaxed text-slate-400"}
     "A web app needs HTTPS (or localhost) and a browser that supports installation. On iOS, notifications also require the Home Screen version. The app works in a normal tab, but installation is what makes it feel like an app."]]])

(defn- delete-modal
  [{:app/keys [pending-delete] :habits/keys [list]} dispatch]
  (when-let [habit (first (filter #(= pending-delete (:habit/id %)) list))]
    [ui/modal
     {:on-close #(dispatch [:cancel-delete])
      :title (str "Delete " (:habit/name habit) "?")
      :subtitle "This also deletes its history. Archive is reversible; this is not."
      :content
      [:div
       [:div {:class "rounded-2xl border border-rose/20 bg-rose/[0.06] p-3.5"}
        [:p {:class "text-sm leading-relaxed text-slate-300"}
         (str "You will lose " (model/total-done habit) " recorded day"
              (when (not= 1 (model/total-done habit)) "s")
              " and a best streak of " (model/longest-streak habit) " days.")]
        [:p {:class "mt-1.5 text-xs leading-relaxed text-slate-400"}
         "If this ritual holds your best streak, deleting it can also close a flame slot. Archive keeps both."]]
       [:div {:class "mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end"}
        [:button {:type "button" :class "btn btn-ghost"
                  :on-click #(dispatch [:cancel-delete])} "Keep it"]
        [:button {:type "button" :class "btn btn-ghost"
                  :on-click #(dispatch [:archive (:habit/id habit)])} "Archive instead"]
        [:button {:type "button" :class "btn btn-danger"
                  :on-click #(do (dispatch [:delete (:habit/id habit)])
                                 (dispatch [:toast {:kind :success :message "Ritual deleted."}]))}
         "Delete forever"]]]}]))

(defn app
  "Root component. `state` is a Reagent atom holding the map described in
  `habits.model`; `dispatch` applies one event to it."
  [state dispatch]
  (let [modal (r/atom nil)
        open-add (fn []
                   (if (model/can-add-habit? @state)
                     (reset! modal {:type :add})
                     (dispatch [:error "Every flame slot is full. Archive a ritual or grow a best streak to unlock another."])))
        open-editor (fn [id] (reset! modal {:type :edit :id id}))
        open-install (fn [] (reset! modal {:type :install}))
        close (fn [] (reset! modal nil))
        request-permission (fn []
                             (platform/request-notification-permission!
                              (fn [permission]
                                (dispatch [:notification-permission permission])
                                (dispatch [:toast (if (= permission "granted")
                                                    {:kind :success :message "Notifications allowed. Timed rituals can now reach you while Ember is open."}
                                                    {:kind :default :message "Notification permission was not granted. The calendar export still works."})]))))
        test-notification (fn []
                            (platform/test-notification!
                             (fn [ok?]
                               (dispatch [:toast (if ok?
                                                   {:kind :success :message "Test sent. That is the reminder you will get."}
                                                   {:kind :error :message "The test did not send. Check notification permission in your browser settings."})]))))]
    (fn []
      (let [current @state
            habits (:habits/list current)
            screen (or (:app/screen current) :today)]
        [:div
         (if (empty? habits)
           [onboarding current dispatch]
           [:div {:class "min-h-[100svh]"}
            [:main {:class "page-shell" :id "main"}
             [brand current dispatch open-install]
             [error-banner current dispatch]
             (case screen
               :today [today/today-screen current dispatch open-add open-editor]
               :progress [progress/progress-screen current]
               :settings [settings/settings-screen state dispatch open-install
                          request-permission test-notification]
               [today/today-screen current dispatch open-add open-editor])]
            [bottom-nav screen dispatch]])
         (when (:app/celebrating current)
          [:div {:class "pointer-events-none fixed inset-x-0 top-24 z-40 h-0"}
           [ui/confetti]])
         [ui/toast (:app/toast current)]
         (when (= :add (:type @modal))
          [ui/modal
           {:on-close close
            :title "New ritual"
            :subtitle "One slot, one clear intention."
            :content
            [habit-form
             {:add? true
              :on-close close
              :on-save (fn [habit]
                         (dispatch [:add habit])
                         (close))
              :on-delete nil
               :on-archive nil}]}])
         (when (= :edit (:type @modal))
          (when-let [habit (first (filter #(= (:id @modal) (:habit/id %)) habits))]
            [ui/modal
             {:on-close close
              :title "Edit ritual"
              :subtitle "Change the plan, not the proof."
              :content
              [habit-form
               {:habit habit
                :add? false
                :on-close close
                :on-save (fn [changes]
                           (dispatch [:edit (:habit/id habit) changes])
                           (close))
                :on-archive (fn []
                              (dispatch [:archive (:habit/id habit)])
                              (close)
                              (dispatch [:toast {:kind :success :message "Archived. Restore it any time from Settings."}]))
                :on-delete (fn []
                             (dispatch [:ask-delete (:habit/id habit)])
                             (close))}]}]))
         (when (= :install (:type @modal))
          [ui/modal
           {:on-close close
            :title "Put Ember on your Home Screen"
            :subtitle "A tab is a website. The icon is an app."
             :content [install-modal current close dispatch]}])
         [delete-modal current dispatch]]))))
