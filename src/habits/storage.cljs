(ns habits.storage
  "The only namespace that touches the browser's storage or the file system.
  Functions are thin, effectful, and named with `!`; the data they move is a
  plain map owned by `habits.model`.

  Two failures matter on iOS: Safari can evict `localStorage` under storage
  pressure, and private browsing can refuse writes. Both are reported to the
  caller as a message. Nothing is swallowed, and a save that cannot be parsed is
  copied aside before the app starts fresh, so the only copy is never overwritten."
  (:require [habits.model :as model]))

(def ^:private storage-key "habits/state/v1")
(def ^:private notified-key "habits/notified/v1")
(def ^:private corrupt-prefix "habits/state/corrupt/")

(defn- json-ready
  "Convert Clojure data to a JSON-ready value without losing keyword
  namespaces. `clj->js` drops the namespace from map keys, which silently turns
  `:habits/list` into `list`; that is a data-loss bug, not a formatting choice."
  [value]
  (cond
    (map? value)
    (into {} (map (fn [[key item]]
                    [(if (keyword? key) (subs (str key) 1) (str key))
                     (json-ready item)])
                  value))

    (set? value)
    (mapv json-ready value)

    (sequential? value)
    (mapv json-ready value)

    (keyword? value)
    (subs (str value) 1)

    :else value))

(defn- json-string
  [value & [spacing]]
  (js/JSON.stringify (clj->js (json-ready value)) nil spacing))

(defn- local-storage
  "The `localStorage` object, or nil where the platform has none."
  []
  (when (exists? js/localStorage) js/localStorage))

(defn- preserve-corrupt!
  "Copy an unreadable save to a side key so a later write cannot destroy it.
  Best effort: if this fails too, the original is still there under the main key."
  [store]
  (try
    (let [raw (.getItem store storage-key)
          key (str corrupt-prefix (.toISOString (js/Date.)))]
      (.setItem store key raw)
      (js/console.warn "Unreadable saved habits moved to" key))
    (catch :default e
      (js/console.error "Could not preserve unreadable saved habits:" e))))

(defn load!
  "Read the saved state into a partial state map, or nil when nothing usable is
  saved. Unreadable data is preserved by `preserve-corrupt!` before nil is
  returned."
  []
  (when-let [store (local-storage)]
    (try
      (when-let [raw (.getItem store storage-key)]
        (let [data (js->clj (js/JSON.parse raw) :keywordize-keys true)
              list (or (:habits/list data) (:list data))]
          (when-not (sequential? list)
            (throw (ex-info "Saved habits have no list." {:habits.error/type :habits/import-invalid})))
          (let [floor (:app/earned-slots data)]
            (cond-> {:habits/list (model/parse-habits list)}
              (and (integer? floor) (<= 1 floor 5))
              (assoc :app/earned-slots floor)))))
      (catch :default e
        (preserve-corrupt! store)
        (js/console.error "Could not read saved habits:" e)
        nil))))

(defn save!
  "Persist the habit list. Returns nil on success, or a message on failure.
  Only `:habits/list` is stored; view state such as :app/error is not."
  [state]
  (if-let [store (local-storage)]
    (try
      (.setItem store storage-key
                (json-string (select-keys state [:habits/list :app/earned-slots])))
      nil
      (catch :default e
        (str "Could not save on this device ("
             (or (.-name e) "storage error")
             "). Export a copy before closing.")))
    "This browser has no local storage, so nothing is saved. Export your data."))

(defn load-notified!
  "The reminder keys already fired today, so a reload does not repeat them.
  Returns a set. A different date is discarded rather than carried forward."
  [iso-day]
  (when-let [store (local-storage)]
    (try
      (when-let [raw (.getItem store notified-key)]
        (let [saved (js->clj (js/JSON.parse raw))
              date (get saved "date")
              entries (get saved "keys")]
          (if (= date iso-day) (set (or entries [])) #{})))
      (catch :default _
        #{}))))

(defn save-notified!
  "Store only today's fired reminder keys. Capped so the key cannot grow without
  bound across a long session."
  [iso-day keys]
  (when-let [store (local-storage)]
    (try
      (.setItem store notified-key
                (js/JSON.stringify #js {:date iso-day
                                        :keys (clj->js (vec (take-last 80 (sort keys))))}))
      (catch :default _
        nil))))

(defn download-text!
  "Offer `text` to the user as a file. Used for JSON and iCalendar exports."
  [text filename mime]
  (let [blob (js/Blob. #js [text] #js {:type mime})
        url  (js/URL.createObjectURL blob)
        link (.createElement js/document "a")]
    (set! (.-href link) url)
    (set! (.-download link) filename)
    (.appendChild (.-body js/document) link)
    (.click link)
    (.remove link)
    (js/setTimeout #(js/URL.revokeObjectURL url) 1000)))

(defn download!
  "Offer `data` to the user as a JSON file named `filename`."
  [data filename]
  (download-text! (json-string data 2)
                  filename
                  "application/json"))

(defn download-ics!
  "Offer the generated iCalendar document to the user."
  [text filename]
  (download-text! text filename "text/calendar;charset=utf-8"))

(defn read-file!
  "Read a user-chosen `file` as text. Calls `on-text` with the contents, or
  `on-error` with a message."
  [^js file on-text on-error]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader) (fn [_] (on-text (.-result reader))))
    (set! (.-onerror reader) (fn [_] (on-error "Could not read that file.")))
    (.readAsText reader file)))

(defn parse-export
  "Parse the text of an exported file. Returns `{:ok data}` or `{:error message}`.
  The shape is validated by `habits.model/parse-habits`, not here."
  [text]
  (try
    {:ok (js->clj (js/JSON.parse text) :keywordize-keys true)}
    (catch :default _
      {:error "That file is not valid JSON."})))

(defn clear-all!
  "Remove every Ember key from this device. The caller confirms first because
  this is the one operation that cannot be undone from inside the app."
  []
  (when-let [store (local-storage)]
    (doseq [key [storage-key notified-key]]
      (.removeItem store key))))
