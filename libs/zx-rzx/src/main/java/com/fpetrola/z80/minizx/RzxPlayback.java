/*
 *
 *  * Copyright (c) 2023-2025 Fernando Damian Petrola
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.fpetrola.z80.minizx;

import com.fpetrola.z80.cpu.OOZ80;
import com.fpetrola.z80.cpu.State;
import com.fpetrola.z80.ide.rzx.InputRecordingBlock;
import com.fpetrola.z80.ide.rzx.RzxFile;
import java.util.List;

/**
 * Runs a recording against a processor: steps one instruction at a time, counts fetches, and
 * interrupts where the recording says a frame ended.
 * <p>
 * A recording is not measured in T-states but in fetches, so this drives the CPU itself rather
 * than letting a machine's own frame event decide when to interrupt. Feeding a recording into a
 * machine that keeps interrupting on its own clock desynchronises it within a frame or two.
 * <p>
 * The fetches are counted by the processor ({@link com.fpetrola.z80.cpu.Z80Cpu#runFetches}), which runs the whole
 * stretch up to the end of a frame in one loop: stepped from here one instruction at a time, the same recording
 * took between 22.6 and 24.2 seconds depending on how the JIT shaped this loop, and 20 run as one stretch.
 * <p>
 * It knows nothing about any particular machine: give it a processor, the port that plays the
 * recorded input back, and the recording.
 * <p>
 * One thing it does have to tell the machine, because it is running the processor in the
 * machine's place: when a frame ended and how long it was. A machine's own loop ends a frame on
 * its own count of T-states, and with that loop bypassed nobody would - the count grows without
 * bound until it runs off the end of the tables the ULA and the memory index by it. What is
 * recorded is where the frames ended, so those are the ends the machine is given.
 */
public class RzxPlayback {

  private final State state;
  private final RZXPlayerIO player;
  private final java.util.function.IntPredicate endOfFrame;
  private final List<InputRecordingBlock.Frame> frames;
  private final com.fpetrola.z80.cpu.Z80Clock clock;
  /** Told the length of every frame the recording ends, for the machine to end its own on. */
  private final java.util.function.IntConsumer frameEnded;
  /** Runs instructions through the machine, so that whatever it hangs on a fetch is heard, up to a number of fetches. */
  private final java.util.function.IntToLongFunction stretch;

  private int fetchCounter;
  private int frameIndex;
  private long instructions;

  public RzxPlayback(OOZ80 cpu, RZXPlayerIO player, RzxFile recording, java.util.function.IntConsumer frameEnded, java.util.function.IntToLongFunction stretch) {
    this.frameEnded = frameEnded;
    this.stretch = stretch;
    this.player = player;
    this.state = cpu.getState();
    this.frames = recording.getInputRecordingBlock().frames;

    player.setup(recording);
    player.setPc(state.getPc());
    // WHERE THE ACKNOWLEDGE FETCH BELONGS. Taking an interrupt is an M1 cycle like any other -
    // it bumps R - and a recorder counts it in the frame that ENDS, so the next frame's budget
    // starts after it. It only exists when the processor actually accepts, and that is what this
    // answers; a frame that runs with interrupts disabled has no acknowledge to account for.
    //
    // The player has always known how to do this and nothing outside the tests ever told it, so
    // every frame that took an interrupt ran one fetch long. One fetch is enough to carry a
    // port read across the boundary, and then the frame that lost it is one read short and the
    // next one read over, from where it drifts. Jet Set Willy is the one recording that never
    // showed it, because it runs with IFF1 at zero from beginning to end.
    // IFF1 alone answers it. An EI still pending only puts the interrupt off by the instruction
    // it shadows, and the line outlasts that one (playFrame), so a frame whose last instruction
    // is EI takes its interrupt too and its acknowledge is discounted like any other.
    player.setAcceptsInterrupt(state::isIff1);
    this.endOfFrame = player.getInterruptionCondition();
    this.clock = state.clock;
  }

  /**
   * Runs until the recording says the frame is over.
   *
   * @return false once every recorded frame has been played
   */
  public boolean playFrame() {
    if (isFinished()) {
      return false;
    }

    while (true) {
      run((int) (player.getFrameEnd() - fetchCounter));
      if (endOfFrame.test(fetchCounter)) break;
      run(1);
    }

    // The machine's frame ends here too, and lasted what the clock says it lasted: the interrupt
    // then falls where the recorded machine had it, at the top of the picture, and the frame
    // after it starts from there. Ended on the machine's own count instead, the interrupt walks
    // around inside the picture and takes every effect a game times from it along.
    frameEnded.accept(clock.getTStates());

    // The frame ends on the interrupt the recording expects, so it is taken here rather than
    // wherever the machine's own timing would have put it. After the boundary, as on a machine
    // that is not being driven: the cycles that acknowledge it belong to the frame it starts.
    // The line stays down past the instruction an EI shadows, as the ULA's 32 T-states do:
    // dropped after one instruction, a frame that ended on an EI lost its interrupt (Head over
    // Heels, frame 904, and every frame after it one port read short or over).
    state.setINTLine(true);
    while (state.isPendingEI())
      run(1);
    run(1);
    state.setINTLine(false);
    frameIndex++;
    return true;
  }

  /** Runs up to the given number of frames, stopping early at the end of the recording. */
  public int playFrames(int count) {
    int played = 0;
    while (played < count && playFrame()) {
      played++;
    }
    return played;
  }

  private void run(int fetches) {
    long ran = stretch.applyAsLong(fetches);
    fetchCounter += (int) ran;
    instructions += ran >>> 32;
  }

  /** Asked of the recording each time, since one being made grows while it is played. */
  public boolean isFinished() {
    return frameIndex >= frames.size();
  }

  /** Frames played so far. The player's own index should agree; they part company on a desync. */
  public int getFrameIndex() {
    return frameIndex;
  }

  public int getPlayerFrameIndex() {
    return player.getCurrentFrameIndex();
  }

  public int getFrameCount() {
    return frames.size();
  }

  public long getInstructions() {
    return instructions;
  }

  public int getFetchCounter() {
    return fetchCounter;
  }
}
