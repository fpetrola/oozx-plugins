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
import dev.plugdesk.desk.Widgets;

import com.fpetrola.oozx.speccy.modules.input.Input;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;

/**
 * The µSpeech on the desk: a meter for what it says and, expanded, whether it is in, the allophone a program
 * last wrote and the intonation, its volume, which ROMs it runs on, and a line to have it say something.
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
  private final JLabel loaded = new JLabel();
  private final JTextField say = new JTextField(14);
  private final JCheckBox raised = new JCheckBox("raised");
  /** How what is written in Say reaches the µSpeech: spelled from a language, or as written in its own spelling. */
  private final JComboBox<String> language = new JComboBox<>(new String[]{"English", "Castellano", "as written"});
  private final java.util.List<SoundSpelling> spellings = java.util.List.of(SoundSpelling.english(), SoundSpelling.castilian());
  private final JComboBox<String> allophones = new JComboBox<>(ALLOPHONES);
  private final JCheckBox high = new JCheckBox("high");
  private final JButton sayAllophone = new JButton("Say");
  private final JCheckBox speakKeys = new JCheckBox("as they are typed", true);

  public UspeechFrame() {
    super("Currah µSpeech", UspeechPeripheral.class, UspeechPeripheral.FULL_SCALE);
    roms.addActionListener(e -> {
      chooseRoms("The µSpeech's ROM (2K)", "The SP0256-AL2's ROM (2K)");
      showRoms();
    });
    row("ROMs", beside(loaded, roms));
    say.setToolTipText("What to say: Enter types LET s$=\"...\" into the machine for the µSpeech's ROM to speak;"
        + " (brackets) mark its sounds, as in hel(oo)");
    // L in the 48K's keyword mode is LET, so the line is typed the way a person types it there.
    say.addActionListener(e -> Input.of(machine()).type("ls$=\"" + spoken(say.getText()) + "\"\n"));
    raised.setToolTipText("Upper-case letters are how the µSpeech raises the pitch of a sound, as in hE(ll)(oO)");
    language.setToolTipText("The language Say is written in, spelled by its sounds for the µSpeech; as written,"
        + " it goes as it is, in the µSpeech's own spelling such as he(ll)(oo)");
    row("Say", beside(say, beside(language, raised)));
    allophones.setToolTipText("One of the SP0256-AL2's 64 sounds, said straight by the chip, the way a program writes it");
    sayAllophone.addActionListener(e -> {
      int which = allophones.getSelectedIndex();
      boolean pitch = high.isSelected();
      machine().loop.later(() -> device().sayAllophone(which, pitch));
    });
    row("Allophone", beside(allophones, beside(high, sayAllophone)));
    speakKeys.setToolTipText("The µSpeech's own setting, LET keys=1 or 0: whether it says every key typed;"
        + " it starts on with its ROM, and this types the line that changes it");
    speakKeys.addActionListener(e -> Input.of(machine()).type("lkeys=" + (speakKeys.isSelected() ? 1 : 0) + "\n"));
    row("Speak keys", speakKeys);
    plugged(device());
  }

  @Override
  protected void plugged(UspeechPeripheral device) {
    super.plugged(device);
    if (roms != null) {
      roms.setEnabled(device != null);
      say.setEnabled(device != null);
      speakKeys.setEnabled(device != null);
      allophones.setEnabled(device != null);
      high.setEnabled(device != null);
      sayAllophone.setEnabled(device != null);
      raised.setEnabled(device != null);
      language.setEnabled(device != null);
      showRoms();
    }
  }

  /** The text as the µSpeech takes it: spelled by its sounds from the language chosen, and in capitals when raised. */
  private String spoken(String text) {
    int chosen = language.getSelectedIndex();
    String bare = chosen < spellings.size() ? spellings.get(chosen).of(text) : text.replace("\"", "");
    return raised.isSelected() ? bare.toUpperCase() : bare;
  }

  private static JPanel beside(javax.swing.JComponent main, javax.swing.JComponent after) {
    JPanel row = new JPanel(new BorderLayout(6, 0));
    row.add(main, BorderLayout.CENTER);
    row.add(after, BorderLayout.EAST);
    return row;
  }

  /** The files it runs on, by name; the whole path is in the tooltip. */
  private void showRoms() {
    java.util.List<String> files = device() == null ? java.util.List.of() : machine().roms.running(device());
    loaded.setText(files.isEmpty() ? "none chosen" : String.join(", ",
        files.stream().map(path -> new java.io.File(path).getName()).toList()));
    loaded.setToolTipText(files.isEmpty() ? null : String.join("\n", files));
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
    return "Show what it is saying, its volume and ROMs, or just the meter";
  }
}
