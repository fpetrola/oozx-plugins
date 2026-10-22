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

package com.fpetrola.oozx.speccy.devices.uspeech;

import com.fpetrola.oozx.speccy.machine.RomNotLoadedException;
import com.fpetrola.oozx.speccy.machine.Roms;
import com.fpetrola.oozx.speccy.machine.SpectrumMachine;
import com.fpetrola.oozx.speccy.modules.memory.MappedMemory;
import com.fpetrola.oozx.speccy.modules.memory.MemoryBus;
import com.fpetrola.oozx.speccy.modules.memory.Registers;
import com.fpetrola.oozx.speccy.modules.memory.Rom;
import com.fpetrola.oozx.speccy.modules.sound.Dac;
import com.fpetrola.oozx.speccy.modules.sound.DacDevice;
import com.fpetrola.oozx.speccy.modules.sound.Sound;
import com.fpetrola.oozx.speccy.peripherals.PluggablePeripheral;
import com.fpetrola.oozx.speccy.ports.BusAnswer;
import com.fpetrola.oozx.speccy.ports.DefaultPortHandler;
import com.fpetrola.oozx.speccy.ports.Wired;
import com.fpetrola.z80.cpu.Z80Clock;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.List;

/**
 * Currah's µSpeech: an SP0256-AL2 that says the allophone a program writes to 0x1000, and 2K of
 * ROM that covers the 48K's twice. Every access to 0x0038 flips it in or out - the processor
 * fetching there for the interrupt, a read or a write of that address, or the port of that
 * number - and whatever the access reads is what is there once it has flipped. That is how its
 * ROM takes the interrupt every frame and hands it on to the machine's.
 * <p>
 * To see the accesses to an address it does not answer while it is out, it is plugged over the
 * first two K anyway and lets what it covers answer. In, the rest of its sixteen K reads 0xff
 * but for 0x1000, where bit 0 says the chip has an allophone still waiting; a write to 0x3000 or
 * 0x3001 changes the crystal, which is its intonation.
 */
@Singleton
public class UspeechPeripheral extends PluggablePeripheral implements DacDevice {

  public static final int ROM_SIZE = 0x800;
  /** The loudest the chip gets, for a meter's top. */
  public static final double FULL_SCALE = 32768;
  private static final int FLIP = 0x0038, PLAY = 0x1000, INTONATION = 0x3000;
  public static final int NORMAL = 3050000, HIGH = 3260000;

  private final MemoryBus memory;
  private final Sound sound;
  private final Z80Clock clock;
  private final Roms roms;
  private final Rom rom = new Rom(ROM_SIZE);
  private final Registers bottom = new Registers(0x4000) {
    public int read(int address) {
      return UspeechPeripheral.this.read(address);
    }

    public void write(int address, byte value) {
      UspeechPeripheral.this.write(address, value);
    }
  };
  private final MappedMemory watching = new MappedMemory(0x0000, bottom, 0, ROM_SIZE);
  private final MappedMemory covering = new MappedMemory(0x0000, bottom);
  private SpectrumMachine on;
  private Sp0256 chip;
  private Dac dac;
  private int volume = 100;
  private boolean available;
  private boolean paged;
  private int crystal = NORMAL;
  private int allophone = -1;

  @Inject
  public UspeechPeripheral(MemoryBus memory, Sound sound, Z80Clock clock, Roms roms) {
    super(List.of());
    this.memory = memory;
    this.sound = sound;
    this.clock = clock;
    this.roms = roms;
    ports(Wired.at(0xffff, FLIP, new DefaultPortHandler(true, true) {
      public BusAnswer read(int port) {
        if (available) flip();
        return BusAnswer.NONE;
      }

      public void write(int port, byte value) {
        if (available) flip();
      }
    }), Wired.at(0xffff, PLAY, new DefaultPortHandler(true, true) {
      public BusAnswer read(int port) {
        return paged ? BusAnswer.of(busy()) : BusAnswer.NONE;
      }

      public void write(int port, byte value) {
        if (paged) play(value);
      }
    }), Wired.at(0xffff, INTONATION, new DefaultPortHandler(false, true) {
      public void write(int port, byte value) {
        if (paged) intonation(NORMAL);
      }
    }), Wired.at(0xffff, INTONATION + 1, new DefaultPortHandler(false, true) {
      public void write(int port, byte value) {
        if (paged) intonation(HIGH);
      }
    }));
  }

  /** The 48K's interrupt is what it rides on: not a 128, not the Amstrad machines, not a clone. */
  @Override
  public boolean fitsOn(SpectrumMachine machine) {
    return !machine.pagesThrough7ffd() && !machine.fullyDecodesPorts();
  }

  /** Its ROM goes over the machine's, so it takes effect at a reset. */
  @Override
  public boolean hasHardReset() {
    return true;
  }

  @Override
  public void activate(SpectrumMachine machine) {
    on = machine;
    dac = sound.add(new Speaker(volume));
  }

  @Override
  public void deactivate() {
    unplug();
    if (dac != null) {
      sound.remove(dac);
      dac = null;
    }
    on = null;
  }

  @Override
  public void machineWasReset(boolean hard) {
    unplug();
    try {
      rom.fill(roms.of(this, 0, ROM_SIZE));
      chip = new Sp0256(roms.of(this, 1, ROM_SIZE), (tstates, level) -> {
        if (dac != null) dac.write(tstates, level);
      });
    } catch (RomNotLoadedException missing) {
      return;
    }
    crystal = NORMAL;
    chip.clock(crystal, on.getTimings().processorSpeed());
    available = true;
    memory.plug(watching);
  }

  /** True once both its ROMs are loaded; without them it is not over anything. */
  public boolean isAvailable() {
    return available;
  }

  public boolean isPaged() {
    return paged;
  }

  /** Its ROM in or out, flipped as its port flips it: only while it has its ROMs. */
  public void pagedIn(boolean in) {
    if (available && paged != in) flip();
  }

  /** The last allophone a program wrote, or -1 since the reset. */
  public int allophone() {
    return allophone;
  }

  public boolean speaksHigh() {
    return crystal == HIGH;
  }

  @Override
  public Dac dac() {
    return dac;
  }

  @Override
  public int volume() {
    return volume;
  }

  @Override
  public void setVolume(int percent) {
    volume = percent;
    if (dac != null) {
      sound.remove(dac);
      dac = sound.add(new Speaker(percent));
    }
  }

  private int read(int address) {
    if (address == FLIP) flip();
    if (!paged) return memory.peekBeneath(watching, address);
    if (address < PLAY) return rom.read(address & ROM_SIZE - 1);
    return address < 0x2000 ? busy() : 0xff;
  }

  private void write(int address, byte value) {
    if (paged && (address & 0xf000) == PLAY) {
      play(value);
    } else if (paged && (address & 0xf000) == INTONATION) {
      intonation((address & 1) == 0 ? NORMAL : HIGH);
    } else if (address == FLIP) {
      flip();
    } else if (!paged) {
      memory.pokeBeneath(watching, address, value);
    }
  }

  private void flip() {
    paged = !paged;
    memory.unplug(paged ? watching : covering);
    memory.plug(paged ? covering : watching);
  }

  private void unplug() {
    memory.unplug(watching, covering);
    available = false;
    paged = false;
    chip = null;
    allophone = -1;
  }

  private int busy() {
    return chip.busy(clock.getTStates()) ? 1 : 0;
  }

  private void play(byte value) {
    allophone = value & 0x3f;
    chip.play(clock.getTStates(), allophone);
  }

  private void intonation(int hz) {
    crystal = hz;
    chip.changeClock(clock.getTStates(), hz, on.getTimings().processorSpeed());
  }

  /** The chip's samples, in the mix; what it said in a frame is made when the frame ends. */
  private final class Speaker extends Dac {
    Speaker(int volume) {
      super(sound, volume);
    }

    @Override
    public void endFrame(int frameTstates) {
      if (chip != null) chip.endFrame(frameTstates);
      super.endFrame(frameTstates);
    }
  }
}
