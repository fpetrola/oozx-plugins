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

package model.tests.media;

import com.fpetrola.oozx.rzx.RecordingStart;
import com.fpetrola.oozx.rzx.RzxSession;
import com.fpetrola.z80.cpu.MockedIO;
import com.fpetrola.z80.cpu.State;
import com.fpetrola.z80.ide.rzx.RzxParser;
import com.fpetrola.z80.minizx.emulation.MockedMemory;
import com.fpetrola.z80.registers.RegisterName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The state a recording starts from, handed to a processor that runs without the machine around it -- the way an
 * analysis replays a recording, one instruction at a time, watching every byte. It has to be exactly the state a
 * session starts the machine from, read through the same snapshot path.
 */
class RecordingStartTest {
  private static File recording() {
    try {
      return model.harness.TestFiles.testFile("/rzx/jsw-full.rzx");
    } catch (Exception e) {
      throw new IllegalStateException("the jsw-full.rzx test resource is missing", e);
    }
  }

  @Test
  void a_bare_processor_starts_where_the_session_starts_the_machine() {
    RecordingStart start = RecordingStart.of(new RzxParser().parseFile(recording().getAbsolutePath()));
    State machine = RzxSession.open(recording()).getSpeccy().cpu.getOoz80().getState();

    int[] seen = new int[0x10000];
    for (int address = 0; address < seen.length; address++)
      seen[address] = machine.getMemory().read(address) & 0xff;
    assertArrayEquals(seen, start.memory(), "the 64K the processor sees, ROM included");

    State bare = new State(new MockedIO(), new MockedMemory(true));
    start.applyTo(bare);
    for (RegisterName name : RegisterName.values())
      assertEquals(machine.getRegister(name).read(), bare.getRegister(name).read(), name.name());
    assertEquals(machine.getInterruptionMode(), bare.getInterruptionMode());
    assertEquals(machine.isIff1(), bare.isIff1());
    assertEquals(machine.isIff2(), bare.isIff2());
    assertEquals(machine.isHalted(), bare.isHalted());
  }

  /**
   * The run state follows the halt, as State.setHalted sets it, and not the machine that read the start, which never
   * ran: a processor not halted at the start is running.
   */
  @Test
  void the_run_state_follows_the_halt() {
    RecordingStart start = RecordingStart.of(new RzxParser().parseFile(recording().getAbsolutePath()));
    State bare = new State(new MockedIO(), new MockedMemory(true));

    start.applyTo(bare);

    assertEquals(bare.isHalted() ? State.RunState.STATE_STOPPED_NORMAL : State.RunState.STATE_RUNNING, bare.getRunState());
  }

  /** What a replay needs in one call: the 64K written into the processor's memory, and its state. */
  @Test
  void starting_a_processor_writes_its_memory_and_its_state() {
    RecordingStart start = RecordingStart.of(new RzxParser().parseFile(recording().getAbsolutePath()));
    State bare = new State(new MockedIO(), new MockedMemory(true));
    State expected = new State(new MockedIO(), new MockedMemory(true));
    start.applyTo(expected);

    start.startOn(bare);

    int[] seen = new int[0x10000];
    for (int address = 0; address < seen.length; address++)
      seen[address] = bare.getMemory().read(address) & 0xff;
    assertArrayEquals(start.memory(), seen);
    for (RegisterName name : RegisterName.values())
      assertEquals(expected.getRegister(name).read(), bare.getRegister(name).read(), name.name());
    assertEquals(0, bare.clock.getTStates(), "the clock at the start of the recording");
  }

  @Test
  void the_memory_handed_out_is_a_copy() {
    RecordingStart start = RecordingStart.of(new RzxParser().parseFile(recording().getAbsolutePath()));
    int[] handed = start.memory();
    handed[40000] ^= 0x55;

    assertNotEquals(handed[40000], start.memory()[40000], "changing the copy leaves the start as it was");
  }
}
