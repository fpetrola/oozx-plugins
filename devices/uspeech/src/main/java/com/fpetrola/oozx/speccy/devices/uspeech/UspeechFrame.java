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

import com.fpetrola.oozx.speccy.devices.DacFrame;
import com.fpetrola.oozx.speccy.modules.sound.Dac;
import com.fpetrola.oozx.speccy.windows.Widgets;

import javax.swing.JButton;

/**
 * The µSpeech on the desk: a meter for what it says and its volume, its two ROMs, and under the
 * meter whether it is in, the allophone a program last wrote, and the intonation.
 */
public class UspeechFrame extends DacFrame<UspeechPeripheral> {

  /** The SP0256-AL2's allophones by number, as Fuse names them. */
  private static final String[] ALLOPHONES = {
      "PA1", "PA2", "PA3", "PA4", "PA5", "/OY/", "/AY/", "/EH/", "/KK3/", "/PP/", "/JH/", "/NN1/", "/IH/",
      "/TT2/", "/RR1/", "/AX/", "/MM/", "/TT1/", "/DH1/", "/IY/", "/EY/", "/DD1/", "/UW1/", "/AO/", "/AA/",
      "/YY2/", "/AE/", "/HH1/", "/BB1/", "/TH/", "/UH/", "/UW2/", "/AW/", "/DD2/", "/GG3/", "/VV/", "/GG1/",
      "/SH/", "/ZH/", "/RR2/", "/FF/", "/KK2/", "/KK1/", "/ZZ/", "/NG/", "/LL/", "/WW/", "/XR/", "/WH/",
      "/YY1/", "/CH/", "/ER1/", "/ER2/", "/OW/", "/DH2/", "/SS/", "/NN2/", "/HH2/", "/OR/", "/AR/", "/YR/",
      "/GG2/", "/EL/", "/BB2/"};

  private final JButton roms = Widgets.iconButton("rom.svg", "ROMs...",
      "Choose the µSpeech's ROM and the SP0256-AL2's, which are not something this emulator can ship");

  public UspeechFrame() {
    super("Currah µSpeech", UspeechPeripheral.class, UspeechPeripheral.FULL_SCALE);
    roms.addActionListener(e -> chooseRoms("The µSpeech's ROM (2K)", "The SP0256-AL2's ROM (2K)"));
    controls.add(roms);
    plugged(device());
  }

  @Override
  protected void plugged(UspeechPeripheral device) {
    super.plugged(device);
    if (roms != null) {
      roms.setEnabled(device != null);
    }
  }

  @Override
  protected String said(UspeechPeripheral device, Dac dac) {
    if (!device.isAvailable()) {
      return "no ROM: " + String.join(", ", machine().roms.missingFor(device));
    }
    return (device.isPaged() ? "in" : "out") + " - "
        + (device.allophone() < 0 ? "nothing said yet" : ALLOPHONES[device.allophone()]) + " - "
        + (device.speaksHigh() ? "high" : "normal") + " intonation";
  }

  @Override
  protected String expandTip() {
    return "Show what it is saying, or just the meter";
  }
}
