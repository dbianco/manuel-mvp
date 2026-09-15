package com.manuel.mvp.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests defining the exact behavioral contract that T020's real `MainScreen`
 * composable and `AssistantState` sealed type must satisfy: visible UI state per FR-001, and
 * accessible, distinctly-labelled control buttons (the company constitution's "interactive
 * elements are keyboard reachable and labelled" standard).
 *
 * `MainScreen` and `AssistantState` do not exist in the main source set yet -- they are T020's
 * job, not this task's (T019). This file will not compile until T020 adds them; that is expected
 * for this test-first split (T019 defines the contract via tests, T020 implements it), same
 * pattern as T005 -> T006, T007 -> T008, T009 -> T010, T012 -> T013, and T014 -> T015.
 *
 * `MainScreen` is deliberately stateless (`state: AssistantState` + two callbacks, no internal
 * `ViewModel`/`ConversationPipeline` reference) -- see `t019-main-screen-test-spec.md`'s
 * Clarifications -- which is what lets these tests render it directly with a fixed state, with no
 * real hardware, native engine, or TTS voice involved.
 *
 * NOT compiled or run in this sandbox: Jetpack Compose's UI/testing artifacts aren't resolvable
 * without a working Gradle sync (itself blocked by the standing no-Android-SDK constraint). This
 * file is written against the real, stable `androidx.compose.ui.test`/`.junit4` API from training
 * knowledge, reviewed by inspection, but unverified by any compiler here.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Scenario 1: Disarmed -- "Escuchar" enabled, "Dejar de escuchar" disabled. */
    @Test
    fun disarmed_escucharEnabled_dejarDeEscucharDisabled() {
        composeTestRule.setContent {
            MainScreen(state = AssistantState.Disarmed, onEscucharClick = {}, onDejarDeEscucharClick = {})
        }

        composeTestRule.onNodeWithText("Escuchar").assertIsEnabled()
        composeTestRule.onNodeWithText("Dejar de escuchar").assertIsNotEnabled()
    }

    /** Scenario 2: Armed -- "Escuchar" disabled, "Dejar de escuchar" enabled, status shows "armado". */
    @Test
    fun armed_dejarDeEscucharEnabled_escucharDisabled_statusShowsArmado() {
        composeTestRule.setContent {
            MainScreen(state = AssistantState.Armed, onEscucharClick = {}, onDejarDeEscucharClick = {})
        }

        composeTestRule.onNodeWithText("Escuchar").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Dejar de escuchar").assertIsEnabled()
        composeTestRule.onNode(hasText("armado", substring = true, ignoreCase = true)).assertExists()
    }

    /** Scenario 3: Listening -- status shows "escuchando". */
    @Test
    fun listening_statusShowsEscuchando() {
        composeTestRule.setContent {
            MainScreen(state = AssistantState.Listening, onEscucharClick = {}, onDejarDeEscucharClick = {})
        }

        composeTestRule.onNode(hasText("escuchando", substring = true, ignoreCase = true)).assertExists()
    }

    /** Scenario 4: Processing -- status shows "procesando". */
    @Test
    fun processing_statusShowsProcesando() {
        composeTestRule.setContent {
            MainScreen(state = AssistantState.Processing, onEscucharClick = {}, onDejarDeEscucharClick = {})
        }

        composeTestRule.onNode(hasText("procesando", substring = true, ignoreCase = true)).assertExists()
    }

    /** Scenario 5: Responding -- status shows "respondiendo". */
    @Test
    fun responding_statusShowsRespondiendo() {
        composeTestRule.setContent {
            MainScreen(state = AssistantState.Responding, onEscucharClick = {}, onDejarDeEscucharClick = {})
        }

        composeTestRule.onNode(hasText("respondiendo", substring = true, ignoreCase = true)).assertExists()
    }

    /** Scenario 6: Error -- status shows an error indication. */
    @Test
    fun error_statusShowsErrorIndication() {
        composeTestRule.setContent {
            MainScreen(
                state = AssistantState.Error("algo salió mal"),
                onEscucharClick = {},
                onDejarDeEscucharClick = {},
            )
        }

        composeTestRule.onNode(hasText("error", substring = true, ignoreCase = true)).assertExists()
    }

    /** Scenario 7: both buttons carry a non-blank, distinct contentDescription. */
    @Test
    fun bothButtons_haveDistinctNonBlankContentDescriptions() {
        composeTestRule.setContent {
            MainScreen(state = AssistantState.Disarmed, onEscucharClick = {}, onDejarDeEscucharClick = {})
        }

        composeTestRule.onNode(hasContentDescription("Escuchar")).assertExists()
        composeTestRule.onNode(hasContentDescription("Dejar de escuchar")).assertExists()
    }

    /** Scenario 8a: clicking "Escuchar" while enabled fires onEscucharClick. */
    @Test
    fun disarmed_clickingEscuchar_firesCallback() {
        var clicked = false

        composeTestRule.setContent {
            MainScreen(
                state = AssistantState.Disarmed,
                onEscucharClick = { clicked = true },
                onDejarDeEscucharClick = {},
            )
        }

        composeTestRule.onNodeWithText("Escuchar").assertHasClickAction().performClick()
        assert(clicked)
    }

    /** Scenario 8b: clicking "Dejar de escuchar" while enabled fires onDejarDeEscucharClick. */
    @Test
    fun armed_clickingDejarDeEscuchar_firesCallback() {
        var clicked = false

        composeTestRule.setContent {
            MainScreen(
                state = AssistantState.Armed,
                onEscucharClick = {},
                onDejarDeEscucharClick = { clicked = true },
            )
        }

        composeTestRule.onNodeWithText("Dejar de escuchar").assertHasClickAction().performClick()
        assert(clicked)
    }
}
