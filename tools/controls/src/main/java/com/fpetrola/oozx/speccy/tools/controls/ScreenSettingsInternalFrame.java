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
package com.fpetrola.oozx.speccy.tools.controls;

import com.fpetrola.oozx.speccy.config.OOZxConfiguration;
import com.fpetrola.oozx.speccy.devices.Desk;
import com.fpetrola.oozx.speccy.screen.ScreenProfile;
import com.fpetrola.oozx.speccy.screen.ScreenSettings;

import javax.swing.JInternalFrame;
import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A window on one machine's screen settings, open while the game is played: what it shows is
 * {@link ScreenSettingsPanel}, which is also what the Video tab of the settings shows. The window
 * is the frame around it and nothing else.
 */
public class ScreenSettingsInternalFrame extends JInternalFrame {

  public ScreenSettingsInternalFrame(String machineName, ScreenSettings settings) {
    super("Screen - " + machineName, true, true, true, true);
    OOZxConfiguration config = Desk.theOne().configuration();
    setLayout(new BorderLayout());
    add(new ScreenSettingsPanel(settings, kept -> {
      config.setScreenDefaults(new LinkedHashMap<>(kept));
      config.save();
    }, () -> {
      Map<String, Map<String, String>> kept = new LinkedHashMap<>();
      for (ScreenProfile profile : ScreenSettings.getKeptProfiles()) {
        kept.put(profile.name(), new LinkedHashMap<>(profile.values()));
      }
      config.setKeptScreenProfiles(kept);
      config.save();
    }), BorderLayout.CENTER);
    setSize(430, 560);
  }
}
