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


package com.fpetrola.oozx.speccy.machine;

import com.fpetrola.emulation.helpers.machine.MachineTypes;
import com.fpetrola.oozx.speccy.devices.ay.AyTimexPeripheral;
import com.fpetrola.oozx.speccy.devices.scld.ScldPeripheral;
import com.fpetrola.oozx.speccy.devices.scld.TimexMemoryPeripheral;
import com.fpetrola.oozx.speccy.modules.display.Display;
import com.fpetrola.oozx.speccy.modules.machine.Machine;
import com.fpetrola.oozx.speccy.modules.memory.MemoryBus;
import com.fpetrola.oozx.speccy.modules.memory.MemoryPart;
import com.fpetrola.oozx.speccy.modules.memory.Rom;
import com.fpetrola.oozx.speccy.modules.memory.SpectrumMemory;
import com.fpetrola.oozx.speccy.modules.scheduler.Scheduler;
import com.fpetrola.oozx.speccy.modules.sound.Sound;
import com.fpetrola.oozx.speccy.modules.timer.Timer;
import com.fpetrola.oozx.speccy.modules.z80.Cpu;
import com.fpetrola.oozx.speccy.peripherals.Peripheral;
import com.fpetrola.oozx.speccy.peripherals.PeripheralRegistry;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Set;

/**
 * The Timex with a sound chip and a cartridge slot: a TC2048 with an AY on two ports of its own,
 * eight K of ROM behind the slot, and nothing in the slot until somebody puts a cartridge there.
 */
@Singleton
public class Tc2068 extends Tc2048 {
  private final MemoryPart[] slot = new MemoryPart[8];
  private final MemoryPart[] behind = new MemoryPart[8];
  private DockCartridge cartridge;
  private boolean bringsHome;

  /**
   * Puts a cartridge in the dock, or takes it out when given none. The machine reads it at the
   * next reset, as it read the one it was switched on with, and only that reset fills the RAM the
   * cartridge has for the machine's own memory.
   */
  public void insert(DockCartridge cartridge) {
    this.cartridge = cartridge;
    bringsHome = cartridge != null;
  }

  public DockCartridge cartridge() {
    return cartridge;
  }

  @Inject
  public Tc2068(MemoryBus memory, SpectrumMemory banks, Display display, PeripheralRegistry peripherals, Machine.Unit unit, Roms roms, Scheduler scheduler, Cpu cpu, Timer timer, Sound sound) {
    super(memory, banks, display, peripherals, unit, roms, scheduler, cpu, timer, sound);
  }

  @Override
  public Set<Class<? extends Peripheral>> onBoard() {
    return Set.of(ScldPeripheral.class, TimexMemoryPeripheral.class, AyTimexPeripheral.class);
  }

  @Override
  public int reset() {
    int result = super.reset();
    Rom exrom = new Rom(0x2000, null);
    exrom.fill(roms.of(this, 1, 0x2000));
    MemoryPart[] inTheDock = bank(DockCartridge.DOCK), inTheExrom = bank(DockCartridge.EXROM), home = bank(DockCartridge.HOME);
    for (int chunk = 0; chunk < 8; chunk++) {
      slot[chunk] = inTheDock[chunk] != null ? inTheDock[chunk] : banks.absent();
      behind[chunk] = inTheExrom[chunk] != null ? inTheExrom[chunk] : exrom;
      if (bringsHome && home[chunk] != null) fill(chunk, home[chunk]);
    }
    bringsHome = false;
    TimexMemoryPeripheral slots = (TimexMemoryPeripheral) peripherals.find(TimexMemoryPeripheral.class);
    if (slots != null) slots.carrying(slot, behind);
    return result;
  }

  private MemoryPart[] bank(int id) {
    return cartridge == null ? new MemoryPart[8] : cartridge.bank(id);
  }

  /** The machine's own RAM in that eight K, from a page the cartridge brings for it. */
  private void fill(int chunk, MemoryPart page) {
    for (int at = 0; at < 0x2000; at++) {
      memory.poke(chunk * 0x2000 + at, (byte) page.read(at));
    }
  }

  @Override
  public String shortName() {
    return "TC2068";
  }

  @Override
  public MachineTypes snapshotModel() {
    return MachineTypes.TC2068;
  }

  @Override
  public String getName() {
    return "Timex TC2068";
  }
}
