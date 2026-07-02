# cloud-itonami-isco-2654

Open Occupation Blueprint for **ISCO-08 2654**: Film, Stage and Related
Directors and Producers (映像作家 / 動画クリエイター).

This repository designs a forkable OSS business for an independent video
director / producer: a video production practice where the studio keeps its
own timelines, footage rights and delivery records instead of renting a closed
production-management SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a camera/gimbal robot performs physical camera
moves, lighting-rig adjustment and set handling under an actor that proposes
actions and an independent **Video Production Governor** that gates them. The
governor never dispatches hardware itself; `:high`/`:safety-critical` actions
(such as publishing footage without subject consent / rights clearance, or
overriding a location-safety constraint) require human sign-off.

A live sample of the operator console (robotics safety console, shared
template) is rendered in
[docs/samples/operator-console.html](docs/samples/operator-console.html) —
pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
production brief + timeline + footage/voice/bgm assets
        |
        v
Production Advisor -> Video Production Governor -> shoot/assemble/deliver,
        |                                          or human sign-off
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, publish
footage without rights clearance, or suppress an operating record.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `2654`). Required capabilities:

- :robotics
- :identity
- :forms
- :dmn
- :bpmn
- :audit-ledger

Craft library (public, kotoba-lang):
[`douga`](https://github.com/kotoba-lang/douga) — pure timeline→ffmpeg
render-plan builder and command builders. The private reference implementation
is gftdcojp's `ai-gftd-dougaka` actor (ADR-2607023000: コードは kotoba-lang、
職能は cloud-itonami-isco、商売は gftdcojp).

## Reference actor (`:maturity :implemented`)

Full itonami Actor pattern (like
[`cloud-itonami-isco-6130`](https://github.com/cloud-itonami/cloud-itonami-isco-6130) /
[`-2652`](https://github.com/cloud-itonami/cloud-itonami-isco-2652)): a real
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph` with Advisor and Governor as distinct nodes and human-in-the-loop
interrupt/resume. The governor's timeline sanity check builds the **actual
ffmpeg render plan** via
[`douga.ffmpeg`](https://github.com/kotoba-lang/douga) (kotoba-lang craft
lib, ADR-2607023000), and assemble commits carry the built plan so what was
approved is exactly what renders.

- HARD → `:hold`: unregistered production, non-`:propose` effect.
- ESCALATE → `:request-approval` (human-signed): publish without subject
  consent + location/rights clearance, assemble whose render plan has zero
  segments, low confidence.

```bash
clojure -M:test
```

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
