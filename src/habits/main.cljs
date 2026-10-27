(ns habits.main
  "Entry point: load, reduce events, persist, notify, mount.

  This is the effectful edge. It owns the clock, the uuid generator, the atom,
  notifications and the service worker; everything it calls is pure."
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [habits.model :as model]
            [habits.platform :as platform]
            [habits.storage :as storage]
            [habits.views :as views]))

(def ^:private celebration-lines
  ["Small counts. That is the whole trick."
   "The chain has one more link."
   "The version you did counts."
   "Fire kept."
   "Returning is the skill."])

(defn- new-habit-id
  "A fresh habit id. Generated here, never inside a `swap!` function."
  []
  (str (random-uuid)))

(defn- hash-screen
  []
  (case (str/replace (or (.-hash js/location) "") #"^#" "")
    "progress" :progress
    "settings" :settings
    :today))

(defn- find-habit
  [state id]
  (first (filter #(= id (:habit/id %)) (:habits/list state))))

(defn init
  "Boot the app. Called by the module generated from `shadow-cljs.edn`."
  []
  (let [day (model/today)
        toast-timer (atom nil)
        state (r/atom
               (model/ensure-earned-slots
                (merge {:habits/list []}
                       (storage/load!)
                       {:app/today day
                        :app/now-minutes (model/now-minutes)
                        :app/screen (hash-screen)
                        :app/notified (storage/load-notified! day)
                        :app/notification-permission (platform/notification-permission)
                        :app/standalone? (platform/standalone?)})))
        show-toast! (fn [kind message]
                      (swap! state assoc :app/toast {:kind kind :message message})
                      (when-let [timer @toast-timer]
                        (js/clearTimeout timer))
                      (reset! toast-timer
                              (js/setTimeout
                               #(swap! state dissoc :app/toast)
                               4600)))
        dispatch!
        (fn [event]
          (let [before @state
                event (if (= :add (first event))
                        ;; The form passes the intention fields; the edge owns
                        ;; identity and the clock.
                        [:add (assoc (second event)
                                     :habit/id (new-habit-id)
                                     :habit/created-at (model/today))]
                        event)
                _ (swap! state #(model/ensure-earned-slots (model/apply-event % event)))
                after @state]
            (cond
              (= :toggle (first event))
              (let [id (second event)
                    old-habit (find-habit before id)
                    new-habit (find-habit after id)
                    new-slots (:app/earned-slots after)
                    old-slots (:app/earned-slots before)]
                (when (and old-habit new-habit
                           (not (model/done-on? old-habit (:app/today after)))
                           (model/done-on? new-habit (:app/today after)))
                  (platform/vibrate!)
                  (swap! state assoc :app/celebrating id)
                  (js/setTimeout
                   #(when (= id (:app/celebrating @state))
                      (swap! state dissoc :app/celebrating))
                   1200)
                  (if (> new-slots old-slots)
                    (show-toast! :unlock
                                 (str "Slot " new-slots " unlocked. Your best streak earned it — no purchase, no timer."))
                    (show-toast! :success (rand-nth celebration-lines)))))

              (= :repair (first event))
              (let [id (second event)
                    old-habit (find-habit before id)
                    new-habit (find-habit after id)]
                (when (and old-habit new-habit
                           (< (model/shields-spent old-habit)
                              (model/shields-spent new-habit)))
                  (platform/vibrate!)
                  (show-toast! :success "Shield spent. Yesterday is bridged — not rewritten.")))

              (= :toast (first event))
              (let [{:keys [kind message]} (first (rest event))]
                (show-toast! kind message))

              (= :import (first event))
              (show-toast! :success "Rituals imported. Your old list was replaced."))

          (when (= :screen (first event))
            (let [screen (second event)
                  wanted (name screen)]
              (when (not= (.-hash js/location) (str "#" wanted))
                (set! (.-hash js/location) wanted))))))

        persist-when-habits-change!
        (fn []
          (add-watch state ::persist
                     (fn [_ _ old new]
                       (when (not= (:habits/list old) (:habits/list new))
                         (when-let [message (storage/save! new)]
                           (swap! state assoc :app/error message))))))

        check-reminders!
        (fn []
          (let [{:app/keys [today now-minutes notified]} @state
                due (model/due-reminders (model/active-habits @state)
                                         today now-minutes notified)]
            (doseq [{:keys [habit key]} due]
              (let [new-notified (conj notified key)
                    name (:habit/name habit)
                    cue (:habit/cue habit)]
                (swap! state assoc :app/notified new-notified)
                (storage/save-notified! today new-notified)
                (platform/vibrate!)
                (when-let [request (platform/show-notification!
                                    (str "Ember · " name)
                                    (str (when-not (str/blank? cue) (str cue ". "))
                                         "The flame is still dark. One small version counts.")
                                    key)]
                  (.catch request (fn [_] nil)))
                (show-toast! :default
                             (str "Time for " name ". "
                                  (when-not (str/blank? cue) (str cue ". "))
                                  "One small version counts."))))))

        start-clock!
        (fn []
          (js/setInterval
           (fn []
             (let [new-day (model/today)
                   minute (model/now-minutes)
                   old-day (:app/today @state)]
               (when (not= new-day old-day)
                 (swap! state assoc
                        :app/today new-day
                        :app/now-minutes minute
                        :app/notified #{})
                 (show-toast! :default "A new day. The slate is clean, not empty."))
               (when (not= minute (:app/now-minutes @state))
                 (swap! state assoc :app/now-minutes minute))
               (check-reminders!)))
           30000))

        register-service-worker!
        (fn []
          (when (exists? js/navigator.serviceWorker)
            (-> (.register js/navigator.serviceWorker "sw.js")
                (.catch (fn [error]
                          (.warn js/console "Service worker not registered:" error))))))]

    (persist-when-habits-change!)
    (rdom/render [views/app state dispatch!]
                 (.getElementById js/document "app"))
    (start-clock!)
    (check-reminders!)
    (register-service-worker!)

    (platform/add-before-install-listener!
     (fn [event]
       (swap! state assoc :app/install-event event)))

    (when (exists? js/window)
      (.addEventListener js/window "appinstalled"
                         (fn [_]
                           (swap! state assoc :app/standalone? true)
                           (show-toast! :success "Installed. Ember is on your Home Screen.")))
      (.addEventListener js/window "hashchange"
                         (fn [_]
                           (let [screen (hash-screen)]
                             (swap! state assoc :app/screen screen))))
      (.addEventListener js/window "focus"
                         (fn [_] (check-reminders!)))
      (when (exists? js/document)
        (.addEventListener js/document "visibilitychange"
                           (fn [_]
                             (when (= "visible" (.-visibilityState js/document))
                               (check-reminders!))))))))
