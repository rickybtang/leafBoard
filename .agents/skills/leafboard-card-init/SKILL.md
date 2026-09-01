---
name: leafboard-card-init
description: Initialize and design a custom LeafBoard metric or status card from a confirmed data source. Use when the user wants to add, configure, sketch, or recommend a LeafBoard card before implementation; start from source capabilities, then collect the title, primary metric, compact details, and large-card extensions. Do not use for unrelated dashboards or for changing an already-set local card layout only.
license: MIT
---

# LeafBoard Card Init

Produce a source-backed card specification that is ready for implementation. Do not fabricate fields to fill the layout, and do not treat design approval as permission to publish or deploy unless the user also asks for that work.

## Source gate

Confirm the concrete data source before asking the card-layout questions. Identify:

- Source name, owner, and whether it is a local database, structured API, CLI, application service, or file.
- Read method, authentication boundary, and safe refresh frequency.
- Available metrics, their raw types and units, timestamps, aggregation windows, null behavior, and failure behavior.
- Data that is unavailable or would require reading secrets, browser Cookies, uncontrolled DOM, transcript bodies, or other sensitive content.

Use read-only inspection when the source is locally accessible. Prefer structured sources over page scraping. If the source or its authorization is not confirmed, report the gap and do not invent a completed card.

### Stable producer path is mandatory

A card source is confirmed only after a stable, non-browser read path has been identified and read successfully. Acceptable paths are an official structured API or CLI with least-privilege read access, an installed application's independently persisted structured session, or a documented local database/file contract. Record the owner, exact read method, authentication boundary, safe refresh frequency, and the observed field/null/failure behavior.

Browser pages may be used once for read-only discovery or visual verification, but never as a Producer path. Do not scrape their DOM, automate periodic page refreshes, reuse browser Cookies/local storage, or treat an already logged-in page as authorization for a background card.

If no stable non-browser path is available, report `BLOCKED: no stable non-browser read path`. Do not mark the source or design ready, and do not produce a final card field contract that implies it can be implemented. Do not ask for or create broad credentials merely to remove this block; first identify the provider-supported read-only authorization option.

When operating in a LeafBoard workspace, first follow its `AGENTS.md` and `docs/README.md`. Treat `protocol/schemas/card.schema.json` and `protocol/protocol.md` as authoritative; then read the relevant requirements, architecture, integration, development, and security documents.

## Recommend before asking

Summarize the source as a compact capability table, then recommend a default card. Rank candidates using:

1. Glance frequency: information the user is likely to check most often.
2. Decision relevance: a value that changes what the user does.
3. Freshness: whether the source updates often enough for the intended slot.
4. Stability and fit: a bounded numeric value or confirmed short text that will render reliably.
5. Non-duplication: details should add context rather than restate the primary metric.

Prefer the highest-frequency stable value as the primary metric. Use compact rows for immediate context. Use the two large-only rows for aggregates, trends, breakdowns, or structured pairs that benefit from more space. Explain unavailable recommendations instead of silently substituting data.

## Show the three wireframes

Show these sketches with the recommended fields filled in before collecting the final choices.

```text
small · 1×1
┌──────────────────┐
│ 标题             │
│                  │
│      主指标      │
├──────────────────┤
│ 辅助 1       值  │
│ 辅助 2       值  │
│ 采集时间     时间 │
└──────────────────┘

medium · 2×1
┌────────────────────────────────────┐
│ 标题                               │
│ 主标签  │ 辅助 1              值  │
│ 主指标  │ 辅助 2              值  │
│         │ 采集时间            时间 │
└────────────────────────────────────┘

large · 2×2
┌────────────────────────────────────┐
│ 标题                               │
│ 主标签                             │
│ 主指标                             │
├────────────────────────────────────┤
│ 辅助 1                       值     │
│ 辅助 2                       值     │
│ 大卡扩展 1            值 1   值 2  │
│ 大卡扩展 2            值 1   值 2  │
│ 采集时间                    时间    │
└────────────────────────────────────┘
```

## Collect the configuration

Let the user accept the whole recommendation or override individual slots. Ask for:

1. Card title.
2. Primary metric.
3. The three compact key-value rows. The first two are configurable; the third is always `采集时间`, so show it as reserved rather than asking the user to replace it.
4. The two large-only rows. Each may contain one structured value or two structured values when the source supports a meaningful pair.

For every configurable field, resolve the source column or formula, label, raw type, format, unit, time window, and empty-value behavior. Ask only questions that change the business result. Do not ask the user to restate facts already established from the source.

## Build the field contract

Use one field array, not three separate layouts. For the standard one-primary plus five-detail card, preserve this exact order:

```text
primary
compact-detail-1
compact-detail-2
large-detail-1
large-detail-2
collected-at
```

This order keeps `采集时间` last after `minSize` filtering:

- Small and medium: primary + compact detail 1 + compact detail 2 + collected-at.
- Large: primary + compact detail 1 + compact detail 2 + large detail 1 + large detail 2 + collected-at.

Set the first three fields and `collected-at` to `minSize=small`. Set both large extensions to `minSize=large`. Use `role=primary` exactly once; use `role=detail` for the five detail rows.

`采集时间` is a fixed generated field:

- `key=last-refresh`
- `label=采集时间`
- `format=datetime`
- `role=detail`
- `minSize=small`
- value is the Producer collection time as timezone-aware ISO 8601

Do not confuse collection time with an event time returned by the source. If the event time matters, it needs its own field and cannot displace collection time from the last row.

## Protocol constraints

- Use only `schemaVersion: "1.0"` unless the workspace authority says otherwise.
- Title length is 1–24 characters. `metric` and `status` have exactly one primary field.
- Primary label is at most 8 Unicode characters. A text primary value is 1–12 Unicode characters and must have a confirmed upper bound.
- Preserve raw semantic types. Formats are `text`, `number`, `percent`, `money`, `datetime`, `duration`, and `boolean`.
- `duration` is a non-negative number of seconds with `unit=s`; the Reader formats hours, minutes, and seconds.
- Money uses an ISO 4217 currency unit.
- A two-value row is `role=detail`, `minSize=large`, and label length at most 8. Its first value may be short text or a structured `number`, `percent`, `money`, or `duration`; its `secondary` value is numeric and uses `number`, `percent`, `money`, or `duration`.
- Never concatenate two values into display text when the dual-value structure can represent them.
- The Producer may suggest `preferredSize`, but layout remains a device-local preference.
- A source read failure preserves the last valid card. Do not publish zeroes or empty values as a substitute for failed reads.
- Increment a card revision when its values or presentation fields change; do not use a timestamp as the revision.

Derived values must use an explicit, auditable formula. Prefer one source-side aggregation per refresh when several windows come from the same records. For averages, use the correct weighted aggregate unless the business definition explicitly calls for averaging per-record rates.

## Deliver the initialized specification

Return:

- Confirmed source and safe read path.
- Available/unavailable metric matrix.
- Recommended mapping and short rationale.
- Three populated wireframes.
- Final ordered field table with keys, labels, roles, formats, units, `minSize`, formulas, and null behavior.
- Remaining business decisions, if any.
- Clear status: design ready, implementation requested, or blocked by source/authorization.

If the user requests implementation, then update every protocol layer required by the workspace: protocol text, Schema, examples, Producer model/validator, Reader parser/renderer, tests, and current-state documentation. Validate with real source data and keep automated, cloud-chain, device, and user acceptance statuses distinct.
