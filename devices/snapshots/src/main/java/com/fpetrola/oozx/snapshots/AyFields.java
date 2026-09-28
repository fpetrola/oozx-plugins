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

package com.fpetrola.oozx.snapshots;

import com.fpetrola.oozx.formats.Field;
import com.fpetrola.oozx.speccy.devices.ay.AyPeripheral;

/** How to reach what the AY has, through its own API: for every format of this module that carries it. */
final class AyFields {

  static final Field<AyPeripheral, Integer> SELECTED = Field.of("ay.selected", AyPeripheral::selected, AyPeripheral::select);

  /** The sixteen registers, set one by one as writes to the chip. */
  static final Field<AyPeripheral, byte[]> REGISTERS = Field.of("ay.registers", ay -> {
    byte[] registers = new byte[16];
    for (int register = 0; register < 16; register++) registers[register] = (byte) ay.register(register);
    return registers;
  }, (ay, registers) -> {
    for (int register = 0; register < 16; register++) ay.register(register, registers[register] & 0xff);
  });

  private AyFields() {
  }
}
