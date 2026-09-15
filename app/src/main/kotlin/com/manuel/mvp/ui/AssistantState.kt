package com.manuel.mvp.ui

/**
 * The assistant's visible UI state (FR-001), rendered by [MainScreen]. Deliberately a distinct
 * type from `com.manuel.mvp.pipeline.PipelineState` (T018) -- see `t019-main-screen-test-spec.md`'s
 * and `t020-main-screen-activity-spec.md`'s Clarifications -- so the `ui` package stays decoupled
 * from `pipeline` internals; `MainActivity` maps one to the other.
 */
sealed class AssistantState {
    object Disarmed : AssistantState()
    object Armed : AssistantState()
    object Listening : AssistantState()
    object Processing : AssistantState()
    object Responding : AssistantState()
    data class Error(val message: String) : AssistantState()
}
