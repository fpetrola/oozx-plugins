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

package com.fpetrola.oozx.mappings;

import com.fpetrola.emulation.helpers.machine.MachineTypes;
import com.fpetrola.oozx.Speccy;
import com.fpetrola.oozx.config.RomFiles;
import com.fpetrola.oozx.formats.Bindings;
import com.fpetrola.oozx.formats.Bytes;
import com.fpetrola.oozx.formats.DeclaredFormat;
import com.fpetrola.oozx.formats.Encoding;
import com.fpetrola.oozx.formats.Field;
import com.fpetrola.oozx.formats.Fixed;
import com.fpetrola.oozx.formats.Layout;
import com.fpetrola.oozx.formats.Mapping;
import com.fpetrola.oozx.formats.SnapshotFile;
import com.fpetrola.oozx.snapshots.SzxFormat;
import com.fpetrola.oozx.speccy.devices.ay.AyPeripheral;
import com.fpetrola.oozx.speccy.devices.ulaplus.UlaPlusPeripheral;
import com.fpetrola.oozx.speccy.machine.RomNotLoadedException;
import com.fpetrola.oozx.speccy.machine.Spectrum;

import java.util.Arrays;
import java.util.List;

/**
 * What an SZX carries beyond the machine it names: the AY in its block AY, the ULAplus in PLTT, a ROM of the machine's
 * own in ROM, and what a session kept around the machine.
 */
public final class SzxMapping implements Mapping {

  static final Fixed AY = Fixed.of("AY\0\0", 18);
  static final Fixed PLTT = Fixed.of("PLTT", 66);
  static final Fixed ROM = Fixed.of("ROM\0", 6);

  static final Layout<AyPeripheral> AY_TABLE = Layout.<AyPeripheral>of()
      .bytes(AY, 2, 16, AyFields.REGISTERS).u8(AY, 1, AyFields.SELECTED);
  static final Layout<UlaPlusPeripheral> ULAPLUS = Layout.<UlaPlusPeripheral>of().encoded(PLTT, 0, 66, new Palette());

  /**
   * ROM: what the machine's ROM chips hold, written only where it is not what the build makes it with - another set,
   * a file of somebody's, something written over it - and as long as libspectrum reads that machine's. Read, it goes
   * into the chips: until it is switched on again, the machine runs on what it was running.
   */
  static final Layout<Spectrum> CUSTOM_ROM = Layout.<Spectrum>of()
      .sizedRest(ROM, 0, 0, 2, Field.of("rom", Spectrum::rom, Spectrum::rom))
      .writtenWhen(machine -> machine.nearestModel() != null && machine.rom().length == romLength(machine.nearestModel())
          && !isTheBuilds(machine));

  /** How long libspectrum reads the ROM of each machine, -1 for the ones whose ROM it refuses. */
  static int romLength(MachineTypes machine) {
    return switch (machine) {
      case SPECTRUM16K, SPECTRUM48K, TC2048 -> 0x4000;
      case SPECTRUM128K, SPECTRUMPLUS2, PENTAGON128, SPECTRUMSE -> 0x8000;
      case SPECTRUMPLUS2A, SPECTRUMPLUS3, SPECTRUMPLUS3E, SCORPION, PENTAGON512, PENTAGON1024 -> 0x10000;
      case TC2068, TS2068 -> 0x6000;
      default -> -1;
    };
  }

  /** Whether its chips hold what they would with nobody's choice: a build whose own ROM is not here says no. */
  private static boolean isTheBuilds(Spectrum machine) {
    try {
      return Arrays.equals(machine.rom(), machine.romOf(new RomFiles()));
    } catch (RomNotLoadedException notHere) {
      return false;
    }
  }

  @Override
  public Class<? extends DeclaredFormat> format() {
    return SzxFormat.class;
  }

  @Override
  public Bindings bindings() {
    return Bindings.of().on(AyPeripheral.class, AY_TABLE).on(UlaPlusPeripheral.class, ULAPLUS).on(Spectrum.class, CUSTOM_ROM);
  }

  @Override
  public List<Fixed> blocks() {
    return List.of(AY, PLTT, ROM);
  }

  /**
   * Before the machine is walked: what a session kept that has to be in place first ({@link
   * SzxSession#plug}), and the ULAplus, which is in the machine if the file has its block and not
   * if it does not, as the old loader did.
   */
  @Override
  public void plug(SnapshotFile file, Speccy speccy) {
    byte[] block = file.carried().get(SzxSession.BLOCK);
    if (block != null) SzxSession.plug(block, speccy, file);
    if (speccy.peripheralRegistry.find(UlaPlusPeripheral.class) instanceof UlaPlusPeripheral chip) chip.fitted(file.has(PLTT));
  }

  /** PLTT: whether the palette is on, the register last named, and the sixty-four colours as written. */
  static final class Palette implements Encoding<UlaPlusPeripheral> {
    public void read(Bytes bytes, UlaPlusPeripheral chip, SnapshotFile file) {
      int[] colours = new int[64];
      for (int colour = 0; colour < 64; colour++) colours[colour] = bytes.u8(2 + colour);
      chip.asItWas(colours, bytes.u8(0) == 1);
    }

    public void write(UlaPlusPeripheral chip, Bytes bytes, SnapshotFile file) {
      bytes.u8(0, chip.inUse() ? 1 : 0).u8(1, chip.named());
      for (int colour = 0; colour < 64; colour++) bytes.u8(2 + colour, chip.register(colour));
    }

    public List<Object> fields() {
      return List.of();
    }
  }
}
