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

import dev.crystal.plugins.api.Offers;

import org.pushingpixels.radiance.theming.api.RadianceThemingCortex;

import java.util.List;

/**
 * Radiance dice que pieles tiene y se las pone solo: ponerle el look por clase lo deja sin piel,
 * y todo lo que pinte despues falla por eso.
 */
@Offers("Change the look: Radiance")
public class RadianceLooks extends FromThisPlugin {

  public String family() {
    return "Radiance";
  }

  public List<String> names() {
    return RadianceThemingCortex.GlobalScope.getAllSkins().values().stream()
        .map(skin -> skin.getDisplayName()).toList();
  }

  public void wear(String name) throws Exception {
    RadianceThemingCortex.GlobalScope.getAllSkins().values().stream()
        .filter(skin -> skin.getDisplayName().equals(name)).findFirst()
        .ifPresent(skin -> {
          try {
            withOurClasses(() -> RadianceThemingCortex.GlobalScope.setSkin(skin.getClassName()));
          } catch (Exception couldNot) {
            throw new IllegalStateException(couldNot);
          }
        });
  }
}
