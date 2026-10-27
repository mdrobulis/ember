(ns habits.icons
  "Inline SVG icons. No icon font, no runtime network request, and every icon is
  decorative unless a caller gives it a title.")

(def ^:private parts
  {:flame [[:path {:d "M12 22c4.1 0 7-2.7 7-6.6 0-2.7-1.6-4.8-3.2-6.5-.4 1.5-1.2 2.5-2.1 3.1.2-3.9-1.5-7-4.2-9.5.1 3.3-1.3 5.3-2.5 7.1C5.8 11.3 5 12.9 5 15.4 5 19.3 7.9 22 12 22Z"
                    :fill "currentColor"}]]
   :check [[:path {:d "m4.5 12.5 4.5 4.5L19.5 6.5" :stroke-linecap "round" :stroke-linejoin "round"}]]
   :plus [[:path {:d "M12 5v14M5 12h14" :stroke-linecap "round"}]]
   :chart [[:path {:d "M4 20V10M10 20V4M16 20v-7M22 20H2" :stroke-linecap "round"}]]
   :settings [[:path {:d "M4 7h9M17 7h3M4 17h3M11 17h9" :stroke-linecap "round"}]
              [:circle {:cx "15" :cy "7" :r "2.2"}] [:circle {:cx "9" :cy "17" :r "2.2"}]]
   :bell [[:path {:d "M18 9a6 6 0 1 0-12 0c0 6-2 7-2 7h16s-2-1-2-7" :stroke-linecap "round" :stroke-linejoin "round"}]
          [:path {:d "M10 20a2.4 2.4 0 0 0 4 0" :stroke-linecap "round"}]]
   :shield [[:path {:d "M12 3 5 6v5c0 4.4 2.9 8.2 7 10 4.1-1.8 7-5.6 7-10V6l-7-3Z" :stroke-linejoin "round"}]
            [:path {:d "m9 12 2 2 4-4" :stroke-linecap "round" :stroke-linejoin "round"}]]
   :lock [[:rect {:x "4" :y "10" :width "16" :height "11" :rx "2.5"}]
          [:path {:d "M8 10V7a4 4 0 0 1 8 0v3" :stroke-linecap "round"}]]
   :x [[:path {:d "m6 6 12 12M18 6 6 18" :stroke-linecap "round"}]]
   :more [[:circle {:cx "5" :cy "12" :r "1" :fill "currentColor" :stroke "none"}]
          [:circle {:cx "12" :cy "12" :r "1" :fill "currentColor" :stroke "none"}]
          [:circle {:cx "19" :cy "12" :r "1" :fill "currentColor" :stroke "none"}]]
   :edit [[:path {:d "M4 20h4l10.5-10.5a2.8 2.8 0 0 0-4-4L4 16v4Z" :stroke-linejoin "round"}]
          [:path {:d "m13.5 6.5 4 4" :stroke-linecap "round"}]]
   :archive [[:rect {:x "3" :y "4" :width "18" :height "5" :rx "1.5"}]
             [:path {:d "M5 9v9a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V9M10 13h4" :stroke-linecap "round"}]]
   :trash [[:path {:d "M4 7h16M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2M7 7l1 13h8l1-13M10 11v5M14 11v5" :stroke-linecap "round" :stroke-linejoin "round"}]]
   :download [[:path {:d "M12 3v12M7 10l5 5 5-5M5 21h14" :stroke-linecap "round" :stroke-linejoin "round"}]]
   :upload [[:path {:d "M12 21V9M7 14l5-5 5 5M5 3h14" :stroke-linecap "round" :stroke-linejoin "round"}]]
   :share [[:path {:d "M12 15V3M8 7l4-4 4 4" :stroke-linecap "round" :stroke-linejoin "round"}]
           [:path {:d "M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7" :stroke-linecap "round"}]]
   :calendar [[:rect {:x "3" :y "5" :width "18" :height "16" :rx "2.5"}]
              [:path {:d "M8 3v4M16 3v4M3 10h18" :stroke-linecap "round"}]]
   :sparkles [[:path {:d "m12 3 1.4 4.1L17.5 8.5l-4.1 1.4L12 14l-1.4-4.1-4.1-1.4 4.1-1.4L12 3ZM19 14l.8 2.2 2.2.8-2.2.8L19 20l-.8-2.2-2.2-.8 2.2-.8L19 14ZM5 14l.7 1.8 1.8.7-1.8.7L5 19l-.7-1.8-1.8-.7 1.8-.7L5 14Z" :stroke-linejoin "round"}]]
   :chevron [[:path {:d "m9 5 7 7-7 7" :stroke-linecap "round" :stroke-linejoin "round"}]]
   :arrow-up-right [[:path {:d "M7 17 17 7M8 7h9v9" :stroke-linecap "round" :stroke-linejoin "round"}]]
   :info [[:circle {:cx "12" :cy "12" :r "9"}]
          [:path {:d "M12 11v5M12 8h.01" :stroke-linecap "round"}]]
   :clock [[:circle {:cx "12" :cy "12" :r "9"}]
           [:path {:d "M12 7v5l3.5 2" :stroke-linecap "round" :stroke-linejoin "round"}]]
   :phone [[:rect {:x "6" :y "2.5" :width "12" :height "19" :rx "3"}]
           [:path {:d "M10 18.5h4" :stroke-linecap "round"}]]
   :globe [[:circle {:cx "12" :cy "12" :r "9"}]
           [:path {:d "M3 12h18M12 3c2.5 2.4 3.8 5.5 3.8 9S14.5 18.6 12 21c-2.5-2.4-3.8-5.5-3.8-9S9.5 5.4 12 3Z"}]
           [:path {:d "M3 12h18"}]]
   :refresh [[:path {:d "M20 7v5h-5M4 17v-5h5" :stroke-linecap "round" :stroke-linejoin "round"}]
             [:path {:d "M18.5 9A7.5 7.5 0 0 0 5.4 7.2M5.5 15A7.5 7.5 0 0 0 18.6 16.8" :stroke-linecap "round"}]]
   :target [[:circle {:cx "12" :cy "12" :r "9"}]
            [:circle {:cx "12" :cy "12" :r "5"}]
            [:circle {:cx "12" :cy "12" :r "1.4" :fill "currentColor"}]]
   :zap [[:path {:d "M13 2 4.5 13H11l-1 9 8.5-11H12l1-9Z" :stroke-linejoin "round"}]]
   :leaf [[:path {:d "M20 4C11 4 4 8 4 15c0 3 2 5 5 5 7 0 11-7 11-16Z" :stroke-linejoin "round"}]
          [:path {:d "M4 20c3-5 7-8 12-10" :stroke-linecap "round"}]]})

(defn icon
  "An inline icon. `opts` may carry `:class`, `:aria-hidden`, or a title."
  ([name] (icon name {}))
  ([name opts]
   (let [class (str "h-5 w-5 " (or (:class opts) ""))]
     [:svg (merge {:viewBox "0 0 24 24"
                   :fill "none"
                   :stroke "currentColor"
                   :stroke-width 1.8
                   :aria-hidden "true"
                   :focusable "false"
                   :class class}
                  (dissoc opts :class))
      (for [part (get parts (keyword name))]
        ^{:key (str name "-" (pr-str part))} part)])))

(defn logo
  "The Ember mark: a solid flame inside a warm rounded tile."
  [{:keys [class]}]
  [:span {:class (str "relative inline-flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-2xl border border-ember-300/25 bg-gradient-to-br from-ember-300 via-ember-500 to-ember-700 shadow-glow " (or class ""))
          :aria-hidden "true"}
   [:span {:class "absolute inset-0 bg-[radial-gradient(circle_at_35%_25%,rgba(255,255,255,0.45),transparent_55%)]"}]
   [:svg {:viewBox "0 0 24 24" :class "relative h-6 w-6 text-ink-950" :aria-hidden "true"}
    [:path {:d "M12 22c4.1 0 7-2.7 7-6.6 0-2.7-1.6-4.8-3.2-6.5-.4 1.5-1.2 2.5-2.1 3.1.2-3.9-1.5-7-4.2-9.5.1 3.3-1.3 5.3-2.5 7.1C5.8 11.3 5 12.9 5 15.4 5 19.3 7.9 22 12 22Z"
           :fill "currentColor"
           :transform "translate(0,0.2) scale(0.92) translate(1,0.9)"}]]])
