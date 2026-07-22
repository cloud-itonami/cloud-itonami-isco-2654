(ns video-production.store
  "SSoT for the ISCO-08 2654 independent video director/producer
  sole-proprietor actor. Store is a protocol injected into the
  `video-production.actor` StateGraph — `MemStore` is the default,
  deterministic, zero-dep backend; a Datomic/kotoba-server-backed
  implementation can be swapped in without touching the actor or governor
  (itonami actor pattern, per ADR-2607011000 / CLAUDE.md Actors section).

  Domain:

    production — a registered production (:production/id, rights clearance
                 flags :subjects-consented? / :location-cleared?)
    record     — a committed operating record under a production (assemble,
                 publish) — written ONLY via commit-record!, never mutated
                 in place
    ledger     — an append-only audit trail of every proposal/verdict/
                 disposition, regardless of outcome (commit or hold)

  Two backends implement the same `Store` protocol so the backend is a
  swap, not a rewrite:

    - `MemStore`     — atom of EDN. The deterministic default for
                       dev/tests/demo (no deps).
    - `DatomicStore` — backed by `langchain.db`, a Datomic-API-compatible
                       EAV store (swappable to a kotoba-server pod in
                       production). production/record/ledger entries
                       carry free-form fields (records even embed a
                       douga render plan), so each is stored as an
                       EDN-blob payload via `langchain-store.core`
                       (`ls/enc`/`ls/dec*`), not a hand-rolled codec
                       (ADR-2607141600).

  Both pass the same contract (test/video_production/store_contract_test.clj)."
  (:require [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (production [s production-id])
  (records-of [s production-id])
  (ledger [s])
  (register-production! [s production])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (production [_ production-id] (get-in @a [:productions production-id]))
  (records-of [_ production-id]
    (filter #(= production-id (:production-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-production! [s production]
    (swap! a assoc-in [:productions (:production/id production)] production) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:productions {} :records [] :ledger []} seed)))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  (ls/identity-schema [:production/id :record/seq :ledger/seq]))

(defn- blob-lookup
  "Look up the EDN-blob payload for the entity uniquely identified by
  `id-attr`/`id` and stored under `payload-attr`."
  [conn id-attr payload-attr id]
  (ls/dec* (d/q {:find '[?p .] :in '[$ ?id]
                 :where [['?e id-attr '?id] ['?e payload-attr '?p]]}
               (d/db conn) id)))

(defrecord DatomicStore [conn]
  Store
  (production [_ production-id] (blob-lookup conn :production/id :production/payload production-id))
  (records-of [_ production-id]
    (filter #(= production-id (:production-id %)) (ls/read-stream conn :record/seq :record/payload)))
  (ledger [_] (ls/read-stream conn :ledger/seq :ledger/payload))
  (register-production! [s production]
    (d/transact! conn [{:production/id (:production/id production) :production/payload (ls/enc production)}]) s)
  (commit-record! [s record]
    (ls/append-blob! conn :record/seq :record/payload (count (ls/read-stream conn :record/seq :record/payload)) record) s)
  (append-ledger! [s fact]
    (ls/append-blob! conn :ledger/seq :ledger/payload (count (ls/read-stream conn :ledger/seq :ledger/payload)) fact) s))

(defn datomic-store
  ([] (datomic-store {}))
  ([seed]
   (let [s (->DatomicStore (d/create-conn schema))]
     (doseq [[_ production] (:productions seed)] (register-production! s production))
     (doseq [record (:records seed)] (commit-record! s record))
     (doseq [fact (:ledger seed)] (append-ledger! s fact))
     s)))
