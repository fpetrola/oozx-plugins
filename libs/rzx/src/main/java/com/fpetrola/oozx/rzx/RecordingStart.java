/*
 * Copyright (c) 2023-2026 Fernando Damian Petrola
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.fpetrola.oozx.rzx;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.speccy.modules.snapshot.Snapshots;
import com.fpetrola.z80.cpu.MemorySetter;
import com.fpetrola.z80.cpu.State;
import com.fpetrola.z80.ide.rzx.RzxFile;

/**
 * The state a recording starts from, for a processor that replays it WITHOUT the machine around it: an analysis
 * that runs the recording one instruction at a time and watches every byte, with no screen, no sound and no
 * clock of its own.
 * <p>
 * It is read the way a session reads it, through the machine's own snapshot path -- the recording carries its start
 * as a snapshot, z80, szx or sna by its extension -- so that what a bare processor starts from is exactly what the
 * emulator starts from. A machine is built only to read it and let go of; what is kept is what a processor needs:
 * the 64K it sees, ROM included, and the processor's own state, registers, pins and latches.
 */
public final class RecordingStart {
  private final int[] memory;
  private final State processor;

  private RecordingStart(int[] memory, State processor) {
    this.memory = memory;
    this.processor = processor;
  }

  public static RecordingStart of(RzxFile recording) {
    if (recording.getSnapshotBlock() == null || recording.getSnapshotBlock().getSnapshotData() == null)
      throw new IllegalArgumentException("the recording carries no snapshot, so there is no state to start it from");
    Speccy speccy = Speccy.create();
    try {
      speccy.init();
      Snapshots.of(speccy).load(RzxSession.snapshotFileOf(recording).toAbsolutePath().toString());
      State loaded = speccy.cpu.getOoz80().getState();
      int[] memory = new int[0x10000];
      for (int address = 0; address < memory.length; address++)
        memory[address] = loaded.getMemory().read(address) & 0xff;
      return new RecordingStart(memory, loaded);
    } finally {
      speccy.end();
    }
  }

  /**
   * A bare processor at the start of the recording: its state, and the 64K written into its memory the way a
   * snapshot always was -- with the write listeners off and the clock at zero ({@link MemorySetter}).
   */
  public void startOn(State processor) {
    applyTo(processor);
    byte[] bytes = new byte[memory.length];
    for (int address = 0; address < memory.length; address++)
      bytes[address] = (byte) memory[address];
    new MemorySetter(processor.getMemory(), bytes, processor).setData(bytes);
  }

  /** The 64K the processor sees when the recording starts, ROM included; a copy. */
  public int[] memory() {
    return memory.clone();
  }

  /**
   * Puts the processor's state at the start -- registers, pins and latches -- into {@code processor}. Its run state
   * follows the halt, as {@link State#setHalted} sets it, and not the machine that read the start, which never ran.
   */
  public void applyTo(State processor) {
    processor.takeFrom(this.processor);
    processor.setHalted(processor.isHalted());
  }
}
