# CAPSA-DI-HYGIENE-001 Report — Constructor Injection Convention

## Summary

Mechanical DI hygiene pass. Converted all applicable internal components from field injection to constructor injection with `private final` collaborators. Established the convention in `CLAUDE.md`.

## Components Converted

| Module | Component |
|---|---|
| capsa-users | `UserRepository` |
| capsa-users | `UserServiceImpl` |
| capsa-lists | `ListRepository` |
| capsa-lists | `ListServiceImpl` |
| capsa-items | `ItemRepository` |
| capsa-items | `ItemServiceImpl` |
| capsa-classification | `ClassificationMemoryEntryRepository` |
| capsa-capture | `CaptureRepository` |
| capsa-capture | `ClassificationAttemptRepository` |
| capsa-capture | `ClassificationResolutionRepository` |
| capsa-capture | `CaptureCreationService` |
| capsa-capture | `CaptureResolutionService` |
| capsa-capture | `CaptureServiceImpl` |

## Already Correct (No Change Needed)

| Module | Component | Reason |
|---|---|---|
| capsa-classification | `ClassificationServiceImpl` | Already used constructor injection |
| capsa-classification | `KnownClassificationStrategy` | Already used constructor injection |
| capsa-classification | `ClassificationPipeline` | Already used constructor injection (CDI + test constructors) |

## Deliberately Left Unchanged

All Jakarta REST resources (`ItemResource`, `CaptureResource`, `ListResource`, `UserResource`) — field injection style is intentional per task scope and established convention.

## One Fix Required

`ClassificationMemoryEntryRepository` had an existing unit test (`KnownClassificationStrategyTest`) that created anonymous subclasses via the implicit no-arg constructor. Adding constructor injection removed the no-arg constructor, breaking compilation. Added a `protected` no-arg constructor (sets `em = null`) to allow test subclassing. This is the standard pattern when a repository is subclassed in unit tests.

## CLAUDE.md Convention Added

Added a "Dependency Injection" section documenting the constructor injection convention, the `private final` rule, and the REST resource exemption.

## Test Result

```
Tests run: 42, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## No Behavioral Changes

No public APIs, REST contracts, CDI scopes, JPMS exports, or use-case behavior were modified. The refactor is purely mechanical.
