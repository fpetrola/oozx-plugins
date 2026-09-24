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

package com.fpetrola.oozx.speccy.tools.looks;

import java.util.List;

public class FlatLafThemeLooks extends FromThisPlugin {

  public String family() {
    return "FlatLaf themes";
  }

  public List<String> names() {
    return spacedAll("Arc", "ArcDark", "ArcOrange", "ArcDarkOrange", "Carbon", "Cobalt2", "CyanLight",
        "DarkFlat", "DarkPurple", "Dracula", "Gray", "GradiantoDeepOcean", "GradiantoMidnightBlue",
        "GradiantoNatureGreen", "GruvboxDarkHard", "HiberbeeDark", "HighContrast", "LightFlat",
        "MaterialDesignDark", "Monocai", "MonokaiPro", "Nord", "OneDark", "SolarizedDark",
        "SolarizedLight", "Spacegray", "Vuesion", "XcodeDark");
  }

  public void wear(String name) throws Exception {
    byClass("com.formdev.flatlaf.intellijthemes.Flat%sIJTheme".formatted(unspaced(name)));
  }
}
