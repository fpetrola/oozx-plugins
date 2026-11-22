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
package com.fpetrola.oozx.speccy.devices.bettersound;

import com.fpetrola.oozx.speccy.devices.ula.UlaFullDecodePeripheral;
import com.fpetrola.oozx.speccy.devices.ula.UlaPeripheral;
import com.fpetrola.oozx.speccy.devices.ula.UlaPortHandler;
import com.fpetrola.oozx.speccy.machine.SpectrumMachine;
import com.fpetrola.oozx.speccy.modules.memory.MemoryBus;
import com.fpetrola.oozx.speccy.modules.sound.Sound;
import com.fpetrola.oozx.speccy.modules.z80.PortTraffic;
import com.fpetrola.oozx.speccy.modules.z80.Processors;
import com.fpetrola.oozx.speccy.peripherals.PeripheralRegistry;
import com.fpetrola.oozx.speccy.peripherals.PluggablePeripheral;
import com.fpetrola.oozx.speccy.screen.Knob;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.List;

/**
 * The speaker played better: while it is plugged in, the machine's own beeper is quiet and this plays the speaker
 * instead, its music and effects on an AY and the rest on a beeper of its own. Nothing on the machine's ports.
 */
@Singleton
public class BetterSoundPeripheral extends PluggablePeripheral {
  private final Sound sound;
  private final PortTraffic traffic;
  private final UlaPortHandler ula;
  private final PeripheralRegistry peripherals;
  private final BetterSound playing;
  private SpectrumMachine machine;

  @Inject
  public BetterSoundPeripheral(Sound sound, Processors traffic, UlaPortHandler ula, PeripheralRegistry peripherals,
                               MemoryBus memory) {
    super(List.of());
    this.sound = sound;
    this.traffic = traffic;
    this.ula = ula;
    this.peripherals = peripherals;
    playing = new BetterSound(sound, memory);
  }

  List<Knob> knobs() {
    return playing.knobs();
  }

  int musicNotes() {
    return playing.musicNotes();
  }

  /** Every machine has a speaker. */
  @Override
  public boolean fitsOn(SpectrumMachine machine) {
    return true;
  }

  @Override
  public void activate(SpectrumMachine machine) {
    this.machine = machine;
    ula.off();
    playing.takeOutputFrom(sound);
    sound.add(playing);
    traffic.whenWritten(playing);
  }

  /** The machine's beeper comes back, unless the machine is putting its ULA away too, as a change of model does. */
  @Override
  public void deactivate() {
    traffic.stopTellingAboutWrites(playing);
    sound.remove(playing);
    if (peripherals.isActive(UlaPeripheral.class) || peripherals.isActive(UlaFullDecodePeripheral.class))
      ula.on(machine);
  }
}
