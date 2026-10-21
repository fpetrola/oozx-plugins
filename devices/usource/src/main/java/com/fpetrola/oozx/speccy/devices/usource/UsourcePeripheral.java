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

package com.fpetrola.oozx.speccy.devices.usource;

import com.fpetrola.oozx.speccy.machine.RomNotLoadedException;
import com.fpetrola.oozx.speccy.machine.Roms;
import com.fpetrola.oozx.speccy.machine.SpectrumMachine;
import com.fpetrola.oozx.speccy.modules.memory.MappedMemory;
import com.fpetrola.oozx.speccy.modules.memory.MemoryBus;
import com.fpetrola.oozx.speccy.modules.memory.Rom;
import com.fpetrola.oozx.speccy.modules.z80.Cpu;
import com.fpetrola.oozx.speccy.modules.z80.PcTraps;
import com.fpetrola.oozx.speccy.peripherals.PluggablePeripheral;
import com.fpetrola.oozx.speccy.ports.BusAnswer;
import com.fpetrola.oozx.speccy.ports.DefaultPortHandler;
import com.fpetrola.oozx.speccy.ports.Wired;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.List;

/**
 * Currah's µSource: an assembler and a Forth in 8K of ROM that covers the machine's own twice,
 * at 0x0000 and again at 0x2000. Anything at 0x2bae flips it in or out: reading or writing that
 * port, or the processor going there to fetch, which is how RANDOMIZE USR 11182 brings it in.
 * It starts out, and no snapshot format keeps whether it is in.
 */
@Singleton
public class UsourcePeripheral extends PluggablePeripheral {

  public static final int ROM_SIZE = 0x2000;
  private static final int FLIP = 0x2bae;

  private final MemoryBus memory;
  private final Cpu cpu;
  private final Roms roms;
  private final Rom rom = new Rom(ROM_SIZE);
  private final MappedMemory[] held = {new MappedMemory(0x0000, rom), new MappedMemory(0x2000, rom)};
  private PcTraps.Watch reaching;
  private boolean available;
  private boolean paged;

  @Inject
  public UsourcePeripheral(MemoryBus memory, Cpu cpu, Roms roms) {
    super(List.of());
    this.memory = memory;
    this.cpu = cpu;
    this.roms = roms;
    ports(Wired.at(0xffff, FLIP, new DefaultPortHandler(true, true) {
      public BusAnswer read(int port) {
        flip();
        return BusAnswer.NONE;
      }

      public void write(int port, byte value) {
        flip();
      }
    }));
  }

  /** A 48K or a 128: not the Amstrad machines, not a clone. */
  @Override
  public boolean fitsOn(SpectrumMachine machine) {
    return !machine.pagesThrough1ffd() && !machine.fullyDecodesPorts();
  }

  /** Its ROM goes over the machine's, so it takes effect at a reset. */
  @Override
  public boolean hasHardReset() {
    return true;
  }

  @Override
  public void deactivate() {
    unpage();
    disarm();
    available = false;
  }

  @Override
  public void machineWasReset(boolean hard) {
    deactivate();
    try {
      rom.fill(roms.of(this, ROM_SIZE));
    } catch (RomNotLoadedException missing) {
      return;
    }
    available = true;
    reaching = cpu.beforeFetch().watch(FLIP, pc -> flip());
  }

  /** True once its ROM is loaded; without one nothing flips. */
  public boolean isAvailable() {
    return available;
  }

  public boolean isPaged() {
    return paged;
  }

  private void flip() {
    if (!available) {
      return;
    }
    if (paged) {
      unpage();
    } else {
      paged = true;
      memory.plug(held);
    }
  }

  private void unpage() {
    paged = false;
    memory.unplug(held);
  }

  private void disarm() {
    if (reaching != null) {
      reaching.off();
      reaching = null;
    }
  }
}
