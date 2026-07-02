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
                 disposition, regardless of outcome (commit or hold)")

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
