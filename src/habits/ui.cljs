(ns habits.ui
  "Small visual primitives shared by the screens. Every state is encoded in
  text as well as colour, and every interactive element is a native control."
  (:require [reagent.core :as r]
            [habits.icons :as icons]))

(def ^:private pieces
  (mapv (fn [i]
          {:left (+ 6 (* i 5.3))
           :delay (mod i 7)
           :color (nth ["#fbbf24" "#f97316" "#a3e635" "#67e8f9" "#fde68a"] (mod i 5))
           :tone (mod i 3)})
        (range 18)))

(defn confetti
  "A short, decorative burst of paper. The live region carries the state, so the
  confetti itself is hidden from assistive technology."
  []
  [:div {:class "pointer-events-none absolute inset-x-0 top-0 z-20 h-0 overflow-visible"
         :aria-hidden "true"}
   (for [piece pieces]
     ^{:key (pr-str piece)}
     [:span {:class (str "confetti-piece confetti-piece--" (:tone piece))
             :style {:left (str (:left piece) "%")
                     :backgroundColor (:color piece)
                     :animationDelay (str (* (:delay piece) 24) "ms")}}])])

(defn progress-ring
  "A real progress ring. `value` is 0..1 and the text inside is always the
  authoritative value."
  [{:keys [value size label sublabel]}]
  (let [size (or size 104)
        radius (- (/ size 2) 8)
        circumference (* 2 js/Math.PI radius)
        clamped (max 0 (min 1 (or value 0)))]
    [:div {:class "relative inline-flex shrink-0 items-center justify-center"
           :style {:width (str size "px") :height (str size "px")}}
     [:svg {:viewBox (str "0 0 " size " " size)
            :class "h-full w-full -rotate-90"
            :aria-hidden "true"}
      [:circle {:cx (/ size 2) :cy (/ size 2) :r radius
                :fill "none" :stroke "rgba(255,255,255,.08)" :stroke-width "7"}]
      [:circle {:cx (/ size 2) :cy (/ size 2) :r radius
                :fill "none" :stroke "url(#ring-gradient)" :stroke-width "7"
                :stroke-linecap "round"
                :stroke-dasharray (str circumference)
                :stroke-dashoffset (str (* circumference (- 1 clamped)))}]
      [:defs
       [:linearGradient {:id "ring-gradient" :x1 "0" :y1 "0" :x2 "1" :y2 "1"}
        [:stop {:offset "0%" :stop-color "#fcd34d"}]
        [:stop {:offset "100%" :stop-color "#f97316"}]]]]
     [:div {:class "absolute inset-0 flex flex-col items-center justify-center"}
      [:span {:class "font-display text-2xl font-semibold leading-none tabular text-white"}
       (or label "")]
      (when sublabel
        [:span {:class "mt-1 font-display text-[0.625rem] font-medium uppercase tracking-[0.16em] text-slate-400"}
         sublabel])]]))

(defn chip
  "A chip. Children can be passed as a second argument, the idiomatic Reagent
  form, or under `:children`."
  [{:keys [icon children tone class]} & args]
  (let [body (if (seq args) (into [:<>] args) children)]
    [:span {:class (str "chip "
                        (case tone
                          :warn "border-ember-400/25 bg-ember-500/10 text-ember-200"
                          :danger "border-rose/25 bg-rose/10 text-rose"
                          :good "border-spark/25 bg-spark/10 text-spark"
                          :cool "border-frost/25 bg-frost/10 text-frost"
                          (or class "")))}
     (when icon [icons/icon icon {:class "h-3.5 w-3.5"}])
     body]))

(defn stat
  "A labelled number with an optional sub-label and icon."
  [{:keys [label value sub tone icon]}]
  [:div {:class "panel-soft p-3.5"}
   [:div {:class "flex items-start justify-between gap-2"}
    [:p {:class "eyebrow"} label]
    (when icon [icons/icon icon {:class "h-4 w-4 text-slate-400"}])]
   [:p {:class (str "mt-2 stat-value " (if (= tone :warn) "text-ember-300" "text-white"))}
    value]
   (when sub [:p {:class "mt-1 text-xs leading-snug text-slate-400"} sub])])

(defn icon-button
  "A square icon-only button with a required accessible label."
  [{:keys [icon label on-click class disabled?]}]
  [:button {:type "button"
            :class (str "icon-btn " (or class ""))
            :aria-label label
            :title label
            :disabled disabled?
            :on-click on-click}
   [icons/icon icon {:class "h-5 w-5"}]])

(defn- modal-key-down
  "Escape closes; Tab cycles within the sheet. The trap is intentionally small
  and only claims Tab when focus is at an edge."
  [on-close event]
  (let [key (.-key event)]
    (cond
      (= key "Escape")
      (do (.preventDefault event) (on-close))

      (= key "Tab")
      (let [node (.-currentTarget event)
            focusables (vec (array-seq
                             (.querySelectorAll
                              node
                              "button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex]:not([tabindex='-1'])")))]
        (when (seq focusables)
          (let [first-el (first focusables)
                last-el (last focusables)
                active (.-activeElement js/document)]
            (cond
              (and (.-shiftKey event) (= active first-el))
              (do (.preventDefault event) (.focus last-el))

              (and (not (.-shiftKey event)) (= active last-el))
              (do (.preventDefault event) (.focus first-el)))))))))

(def modal
  "A native-feeling dialog with Escape, a focus trap and a visible close button.
  Implemented as a class component so the document-level listeners are removed
  on unmount. The body is passed as `:content` so the component type is stable."
  (r/create-class
   {:component-did-mount
    (fn [this]
      (aset this "lastFocused" (.-activeElement js/document))
      (aset this "bodyOverflow" (.-overflow (.-style js/document.body)))
      (let [node (.querySelector js/document ".modal-sheet")]
        (set! (.-overflow (.-style js/document.body)) "hidden")
        (js/setTimeout
         (fn []
           (when (and node (.-isConnected node))
             (when-let [target (or (.querySelector node "input:not([type='hidden']), select, textarea, [autofocus]")
                                   (first (array-seq (.querySelectorAll node "button, input, select, textarea, a[href], [tabindex]:not([tabindex='-1'])"))))]
               (.focus target))))
         30)))

    :component-will-unmount
    (fn [this]
      (set! (.-overflow (.-style js/document.body)) (or (aget this "bodyOverflow") ""))
      (when-let [previous (aget this "lastFocused")]
        (when (and previous (.-isConnected previous))
          (.focus previous))))

    :reagent-render
    (fn [{:keys [on-close title subtitle content]}]
      [:div {:class "modal-backdrop"
             :on-click (fn [event]
                         (when (= (.-target event) (.-currentTarget event))
                           (on-close)))}
       [:div {:class "modal-sheet"
              :role "dialog"
              :aria-modal "true"
              :aria-labelledby "modal-title"
              :on-key-down #(modal-key-down on-close %)}
        [:div {:class "mb-5 flex items-start justify-between gap-4"}
         [:div
          [:h2 {:id "modal-title" :class "display text-2xl text-white"} title]
          (when subtitle [:p {:class "mt-1 text-sm text-slate-400"} subtitle])]
         [icon-button {:icon "x" :label "Close" :on-click on-close}]]
        content]])}))

(defn toast
  "A transient, polite live-region message."
  [{:keys [kind message]}]
  (when message
    [:div {:class "pointer-events-none fixed inset-x-0 bottom-24 z-50 flex justify-center px-4 safe-bottom"
           :role "status"
           :aria-live "polite"}
     [:div {:class (str "pointer-events-auto flex max-w-sm items-center gap-3 rounded-2xl border px-4 py-3 text-sm shadow-panel backdrop-blur-xl animate-pop-in "
                        (case kind
                          :error "border-rose/30 bg-ink-900/95 text-rose"
                          :unlock "border-ember-400/35 bg-ink-900/95 text-ember-100"
                          "border-spark/25 bg-ink-900/95 text-slate-100"))}
      [icons/icon (case kind :error "info" :unlock "sparkles" "check")
       {:class "h-5 w-5 shrink-0"}]
      [:span message]]]))

(defn section-heading
  "A screen section heading with an optional eyebrow and action."
  [{:keys [eyebrow title action]}]
  [:div {:class "mb-3 flex items-end justify-between gap-3"}
   [:div
    (when eyebrow [:p {:class "eyebrow mb-1"} eyebrow])
    [:h2 {:class "display text-xl text-white"} title]]
   action])

(defn empty-state
  "A designed empty state: a title, a plain explanation, and an optional action."
  [{:keys [title body action]}]
  [:div {:class "panel flex flex-col items-center px-6 py-10 text-center"}
   [:div {:class "mb-4 flex h-16 w-16 items-center justify-center rounded-3xl border border-ember-400/20 bg-ember-500/10 text-ember-300"}
    [icons/icon "flame" {:class "h-8 w-8 animate-flicker"}]]
   [:h2 {:class "display text-2xl text-white"} title]
   [:p {:class "mt-2 max-w-sm text-sm leading-relaxed text-slate-400"} body]
   (when action [:div {:class "mt-5"} action])])

(defn bullet
  "A small icon, title and explanation row used by install and transparency copy."
  [{:keys [icon tone title body]}]
  [:div {:class "flex gap-3"}
   [:span {:class (str "mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border "
                       (case tone
                         :warn "border-ember-400/20 bg-ember-500/10 text-ember-300"
                         :cool "border-frost/20 bg-frost/10 text-frost"
                         :good "border-spark/20 bg-spark/10 text-spark"
                         "border-white/10 bg-white/[0.04] text-slate-300"))}
    [icons/icon icon {:class "h-4 w-4"}]]
   [:div
    [:p {:class "font-display text-sm font-semibold text-white"} title]
    [:p {:class "mt-0.5 text-xs leading-relaxed text-slate-400"} body]]])
