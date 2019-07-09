/*
 * Copyright 2019 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.sample.helloterminal.terminalworkflow

import com.googlecode.lanterna.TerminalPosition.TOP_LEFT_CORNER
import com.googlecode.lanterna.screen.Screen.RefreshType.COMPLETE
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.squareup.workflow.Workflow
import com.squareup.workflow.asWorker
import com.squareup.workflow.launchWorkflowIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

/**
 * Hosts [Workflow]s that:
 *  - gets information about the terminal configuration as input
 *  - renders the text to display on the terminal
 *  - finishes by emitting an exit code that should be passed to [kotlin.system.exitProcess].
 *
 * @param ioDispatcher Defaults to [Dispatchers.IO] and is used to listen for key events using
 * blocking APIs.
 */
class TerminalWorkflowRunner(
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

  private val screen = DefaultTerminalFactory().createScreen()

  /**
   * Runs [workflow] until it emits an [ExitCode] and then returns it.
   */
  @UseExperimental(ExperimentalCoroutinesApi::class)
  // Some methods on screen are synchronized, which Kotlin detects as blocking and warns us about
  // when invoking from coroutines. This entire function is blocking however, so we don't care.
  @Suppress("BlockingMethodInNonBlockingContext")
  fun run(workflow: TerminalWorkflow): ExitCode = runBlocking {
    val keyStrokes = screen.keyStrokes()
        // Collect on the IO dispatcher because it will do blocking IO.
        .flowOn(ioDispatcher)
        // We have to share the flow otherwise keystrokes will get fanned out to each collector if
        // multiple workflows are observing them.
        .share()
    val resizes = screen.terminal.size

    // Hide the cursor.
    screen.cursorPosition = null

    screen.startScreen()
    try {
      return@runBlocking runTerminalWorkflow(workflow, screen, keyStrokes, resizes)
    } finally {
      screen.stopScreen()
    }
  }
}

@Suppress("BlockingMethodInNonBlockingContext")
@UseExperimental(FlowPreview::class, ExperimentalCoroutinesApi::class)
private suspend fun runTerminalWorkflow(
  workflow: TerminalWorkflow,
  screen: TerminalScreen,
  keyStrokes: Flow<KeyStroke>,
  resizes: Flow<TerminalSize>
): ExitCode = coroutineScope {
  val keyStrokesWorker = keyStrokes.asWorker()
  val inputs = resizes
      .map { size ->
        screen.doResizeIfNecessary()
            // If the terminal was resized since the last iteration, we need to notify the workflow.
            ?.toSize()
            ?: size
      }
      .distinctUntilChanged()
      .map { TerminalInput(it, keyStrokesWorker) }

  // Use the result as the parent Job of the runtime coroutine so it gets cancelled automatically
  // if there's an error.
  val result = launchWorkflowIn(this, workflow, inputs) { renderingsAndSnapshots, outputs ->
    renderingsAndSnapshots.map { it.rendering }
        .onEach { rendering ->
          screen.clear()
          screen.newTextGraphics()
              .apply {
                foregroundColor = rendering.textColor.toTextColor()
                backgroundColor = rendering.backgroundColor.toTextColor()
                rendering.text.lineSequence()
                    .forEachIndexed { index, line ->
                      putString(TOP_LEFT_CORNER.withRelativeRow(index), line)
                    }
              }

          screen.refresh(COMPLETE)
        }
        .launchIn(this)

    return@launchWorkflowIn async { outputs.first() }
  }

  val exitCode = result.await()
  // If we don't cancel the workflow runtime explicitly, coroutineScope will hang waiting for it to
  // finish.
  coroutineContext.cancelChildren(
      CancellationException("TerminalWorkflowRunner completed with exit code $exitCode")
  )
  return@coroutineScope exitCode
}