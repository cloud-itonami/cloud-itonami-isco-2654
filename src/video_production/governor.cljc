(ns video-production.governor
  "VideoProductionGovernor — the independent rights/consent/traceability
  layer for the ISCO-08 2654 independent video director/producer actor.
  Wired as its own `:govern` node in `video-production.actor`'s StateGraph,
  downstream of `:advise` — the Advisor has no notion of production
  provenance or consent/rights clearance, so this MUST be a separate system
  able to reject a proposal (itonami actor pattern, per ADR-2607011000 /
  CLAUDE.md Actors section).

  The timeline sanity check is NOT bespoke: it builds the actual render
  plan with `douga.ffmpeg/build-render-plan` from the kotoba-lang `douga`
  craft lib (ADR-2607023000) — an assembly whose plan has zero renderable
  segments goes to a human instead of silently committing.

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. production provenance — the request's production must be registered.
    2. no-actuation          — proposal :effect must be :propose.
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    3. :publish without subject consent AND location/rights clearance on
       the registered production.
    4. :assemble whose douga render plan has zero segments.
    5. low confidence (< `confidence-floor`)."
  (:require [douga.ffmpeg :as ffmpeg]
            [video-production.store :as store]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} production-record]
  (cond-> []
    (nil? production-record)
    (conj {:rule :no-production
           :detail (str "未登録 production " (:production-id request))})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a `store`
  implementing `video-production.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool
    :escalations [...]}`."
  [request _context proposal store]
  (let [production-record (store/production store (:production-id request))
        hard (hard-violations {:request request :proposal proposal} production-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        clearance? (and (= :publish (:op proposal))
                        production-record
                        (not (and (:subjects-consented? production-record)
                                  (:location-cleared? production-record))))
        empty-plan? (and (= :assemble (:op proposal))
                         (empty? (:segments (ffmpeg/build-render-plan
                                             (:timeline request)))))
        escalations (cond-> []
                      clearance? (conj {:rule :rights-clearance
                                        :detail "公開には subject consent + location/rights clearance の人間承認が必要"})
                      empty-plan? (conj {:rule :empty-render-plan
                                         :detail "douga render plan に segment が無い（timeline を人間が確認）"})
                      low? (conj {:rule :low-confidence :detail conf}))]
    {:ok? (and (not hard?) (empty? escalations))
     :violations hard
     :escalations escalations
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (boolean (seq escalations)))}))
