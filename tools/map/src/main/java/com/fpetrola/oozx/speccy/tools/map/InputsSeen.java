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

package com.fpetrola.oozx.speccy.tools.map;

import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.speccy.modules.z80.PcTraps;
import com.fpetrola.z80.registers.RegisterName;

/**
 * The keys the game sees held, frame by frame: every IN it does, decoded. What the game is told
 * rather than what anybody presses, so a recording played back reads the same as a hand on the
 * keys. Bits 0 to 39 are the keyboard, five a half-row in the ULA's order; 40 to 44 the Kempston.
 */
public final class InputsSeen {
  public static final int KEMPSTON = 40;
  private static final int IN_A = 0xDB, PREFIX = 0xED;
  private static final RegisterName[] IN_R = {RegisterName.B, RegisterName.C, RegisterName.D, RegisterName.E,
      RegisterName.H, RegisterName.L, null, RegisterName.A};

  private final Speccy machine;
  private final PcTraps.Watch before, after;
  private int portOfInA;
  private long held;

  public InputsSeen(Speccy machine) {
    this.machine = machine;
    before = machine.cpu.beforeFetch().watch(0x0000, 0xffff, this::aboutToExecute);
    after = machine.cpu.afterInstruction().watch(0x0000, 0xffff, this::executed);
  }

  /** The keys seen held since the last time this was asked, and a clean slate for the next frame. */
  public long frame() {
    long keys = held;
    held = 0;
    return keys;
  }

  public void off() {
    before.off();
    after.off();
  }

  /** An IN A,(n) reads the port A names before it, which it then overwrites. */
  private void aboutToExecute(int pc) {
    if (peek(pc) == IN_A) portOfInA = register(RegisterName.A) << 8 | peek(pc + 1);
  }

  private void executed(int pc) {
    int opcode = peek(pc);
    if (opcode == IN_A) {
      saw(portOfInA, register(RegisterName.A));
    } else if (opcode == PREFIX && (peek(pc + 1) & 0xC7) == 0x40) {
      RegisterName into = IN_R[peek(pc + 1) >> 3 & 7];
      if (into != null) saw(register(RegisterName.BC), register(into));
    }
  }

  private void saw(int port, int value) {
    if ((port & 1) == 0) {
      for (int row = 0; row < 8; row++) {
        if ((port >> 8 + row & 1) != 0) continue;
        for (int bit = 0; bit < 5; bit++) if ((value >> bit & 1) == 0) held |= 1L << row * 5 + bit;
      }
    } else if ((port & 0xFF) == 0x1F) {
      held |= (value & 0x1F) << KEMPSTON;
    }
  }

  private int peek(int address) {
    return machine.memory.peek(address) & 0xff;
  }

  private int register(RegisterName name) {
    return machine.cpu.getOoz80().getState().getRegister(name).read();
  }
}
