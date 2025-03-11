# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Agent skills

### Issue tracker

Issues and specs live as GitHub issues in `cd-water/ticketbox`, driven by the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Frontend

任何前端开发（`frontend/web` 与 `frontend/admin`）必须先调用并使用以下两个 skill：

- `impeccable:impeccable`（plugin `impeccable@impeccable`）——设计、审查、打磨
- `taste-skill:design-taste-frontend`（plugin `taste-skill@taste-skill`）——设计品味与反模板约束

架构与页面清单见 `docs/design.md` 第 8 节。
