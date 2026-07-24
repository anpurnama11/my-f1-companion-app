---
id: 23
title: "Default predictive back behavior for v1"
type: grilling
status: closed
blocked_by: []
owner: "pi"
---

## Question

Does v1 need a custom `PredictiveBackHandler`, screen-specific predictive-back animations, or a custom animation shape?

## Answer

No. F1app v1 uses the default Android and Navigation 3 predictive-back behavior. The app does not add a custom `PredictiveBackHandler` or screen-specific predictive-back animations.

The existing `android:enableOnBackInvokedCallback="true"` manifest setting remains enabled. Custom predictive-back motion is outside the v1 destination and should return only as a fresh effort if the default behavior proves inadequate.
