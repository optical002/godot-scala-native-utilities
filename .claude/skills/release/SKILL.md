---
name: release
description: Cut a release of the utilities modules — bump the version, update changelogs, tag for JitPack. Use when releasing, tagging a version, bumping to the next dev version, or when checking whether the repo's version pins match its git tags.
---

# Releasing

All modules share **one** plain-semver git tag. JitPack builds it (`jitpack.yml` runs
`cd utilities && sbt publishM2`) and exports `VERSION=<tag>`, which every version site
reads.

## Pre-flight: check for drift

Do this first. This repo has silently shipped releases where the tag moved but no file
did:

```
git tag -l
grep -rn "VERSION" utilities/build.sbt
grep -rn "0\.[0-9]*\.[0-9]*" scala/build.sbt scala/project/plugins.sbt
```

Every tag should sit on a commit whose `utilities/build.sbt` carries that plain version,
and each module changelog should have a matching dated section. Known history to
reconcile: **`0.1.3` was never tagged**, and `0.1.4` and `0.1.5` both point at the same
ordinary feature commit (`02211e5`) with no version bump and no changelog entries.

## The version sites (6 across 4 files)

A release must move all of them together:

| File | Site |
|---|---|
| `utilities/build.sbt` | `bindingVersion` fallback |
| `utilities/build.sbt` | `version :=` fallback |
| `scala/build.sbt` | 3 library dependency pins |
| `scala/project/plugins.sbt` | the binding sbt plugin |

Plus the prose: 5 × `utilities/modules/*/CHANGELOG.md` and the root `CHANGELOG.md`.

Find them all with:

```
grep -rn "<current-version>" utilities/build.sbt scala/build.sbt scala/project/plugins.sbt
```

## Ordering constraint

Because JitPack pins the binding to the **same** tag it is building, the binding must be
released under that tag *before* this repo is tagged with it. If the binding is not
available at that version, the JitPack build of this repo will fail to resolve.

## The two-commit ritual

Modelled on `bdcef6b` (release) then `c322681` (bump), which are worth reading with
`git show`.

**Commit 1 — release `X.Y.Z`:**
1. Set all 6 version sites to the plain release version.
2. In each of the 5 module changelogs, retitle `## [Unreleased]` to `## [X.Y.Z] — <date>`
   and make sure it lists real changes (not `_No changes yet._`). A module with no
   changes gets an explicit "no functional changes" note — that is the existing
   convention.
3. Commit, then tag `X.Y.Z` and push the tag. JitPack builds it.

**Commit 2 — open the next dev version:**
1. Bump all 6 sites to the next version (bare, e.g. `0.1.7`).
2. Add a fresh stanza to each module changelog:

   ```
   ## [Unreleased]

   _No changes yet._
   ```
3. Commit.

Between releases, the pinned version names the release being worked toward and is not on
JitPack yet, so it resolves from a local `publishLocal` of the binding.

## Verify

```
cd utilities && sbt -J-Xmx4G test
```

then confirm the tag resolves on JitPack before relying on it downstream.
