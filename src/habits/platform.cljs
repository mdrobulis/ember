(ns habits.platform
  "Browser capabilities at the edge. Every function degrades to a value the UI
  can explain; none of them pretends a feature is available when it is not.

  Local scheduling is deliberately absent. A closed serverless web app cannot
  fire a notification later; the app uses a minute tick while open and an
  iCalendar export for OS-level alarms when closed."
  (:require))

(defn standalone?
  "True when the app is running from the Home Screen or an installed window."
  []
  (or (and (exists? js/window)
           (fn? js/window.matchMedia)
           (.-matches (.matchMedia js/window "(display-mode: standalone)")))
      (and (exists? js/navigator)
           (true? (.-standalone js/navigator)))))

(defn ios?
  []
  (and (exists? js/navigator)
       (boolean (re-find #"iPhone|iPad|iPod" (or (.-userAgent js/navigator) "")))))

(defn notifications-supported?
  []
  (and (exists? js/Notification)
       (exists? js/navigator.serviceWorker)))

(defn notification-permission
  "`granted`, `denied`, `default`, or `unsupported`."
  []
  (if (exists? js/Notification)
    (or (.-permission js/Notification) "default")
    "unsupported"))

(defn request-notification-permission!
  "Ask for permission from a direct user gesture, as browsers require. Calls
  `on-result` with the resulting permission string."
  [on-result]
  (if-not (notifications-supported?)
    (on-result "unsupported")
    (try
      (-> (js/Notification.requestPermission)
          (.then (fn [permission] (on-result (str permission))))
          (.catch (fn [_] (on-result "default"))))
      (catch :default _
        (on-result "unsupported")))))

(defn- show-via-service-worker!
  [title options]
  (if (and (exists? js/navigator.serviceWorker)
           (.-serviceWorker js/navigator))
    (-> (.ready js/navigator.serviceWorker)
        (.then (fn [registration]
                 (.showNotification registration title (clj->js options)))))
    (throw (js/Error. "No service worker"))))

(defn show-notification!
  "Show a local, system-level notification through the service worker. Returns a
  promise; the caller provides the in-app fallback when permission is absent.
  On mobile, `new Notification` is not reliable, so the service worker is the
  first and only path there."
  [title body tag]
  (if (and (notifications-supported?)
           (= "granted" (notification-permission)))
    (let [options {:body body
                   :tag tag
                   :icon "./icons/icon-192.png"
                   :badge "./icons/icon-192.png"
                   :data {:url "./"}}]
      (try
        (show-via-service-worker! title options)
        (catch :default _
          (try
            (js/Notification. title (clj->js options))
            (catch :default _
              nil)))))
    nil))

(defn test-notification!
  "A single test notification, so permission can be checked immediately."
  [on-result]
  (if (and (notifications-supported?)
           (= "granted" (notification-permission)))
    (if-let [request (show-notification! "Ember is listening"
                                         "This is what a timed ritual reminder looks like."
                                         "ember-test")]
      (-> request
          (.then (fn [_] (on-result true)))
          (.catch (fn [_] (on-result false))))
      (on-result false))
    (on-result false)))

(defn vibrate!
  "A tiny haptic on the platforms that support it. Silently absent elsewhere."
  []
  (when (and (exists? js/navigator)
             (fn? (.-vibrate js/navigator))
             (or (not (exists? js/navigator.userActivation))
                 (.-isActive js/navigator.userActivation)))
    (try
      (.vibrate js/navigator 18)
      (catch :default _ nil))))

(defn add-before-install-listener!
  "Capture Chrome's install event. Returns nil when the event does not arrive;
  the caller then shows the platform's manual instructions."
  [on-available]
  (when (exists? js/window)
    (.addEventListener
     js/window "beforeinstallprompt"
     (fn [event]
       (.preventDefault event)
       (on-available event)))
    nil))

(defn prompt-install!
  "Open Chrome's install prompt. Returns a promise resolving to whether the user
  accepted."
  [event]
  (if (and event (fn? (.-prompt event)))
    (-> (.prompt event)
        (.then (fn [] (-> (.userChoice event)
                          (.then (fn [choice] (= "accepted" (.-outcome choice))))))))
    (js/Promise.resolve false)))
