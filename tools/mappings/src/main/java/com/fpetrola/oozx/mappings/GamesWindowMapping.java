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

import com.fpetrola.oozx.api.KnowsTheGames;
import com.fpetrola.oozx.speccy.tools.games.GamesFrame;
import dev.crystal.plugins.api.OnlyWith;

import javax.swing.JInternalFrame;
import java.util.Map;

/** The game browser: what it is looking for, which comes back as a single search. */
@OnlyWith({"tool-games", "tool-catalogue"})
public final class GamesWindowMapping implements WindowInside {

  private static final String SEARCH = "search.";

  @Override
  public void keep(JInternalFrame window, Map<String, String> kept) {
    if (!(window instanceof GamesFrame games)) return;
    GamesFrame.Search search = games.search();
    kept.put(SEARCH + "words", search.words());
    kept.put(SEARCH + "where", search.where());
    kept.put(SEARCH + "order", search.order());
    KnowsTheGames.Slice slice = search.slice();
    if (slice.machine() != null) kept.put(SEARCH + "machine", slice.machine());
    if (slice.genre() != null) kept.put(SEARCH + "genre", slice.genre());
    if (slice.year() != null) kept.put(SEARCH + "year", String.valueOf(slice.year()));
    kept.put(SEARCH + "rzx", String.valueOf(search.rzx()));
    kept.put(SEARCH + "map", String.valueOf(search.map()));
    kept.put(SEARCH + "loadable", String.valueOf(search.loadable()));
    kept.put(SEARCH + "unknown", String.valueOf(search.unknown()));
    kept.put(SEARCH + "colour", String.valueOf(search.colour()));
  }

  @Override
  public void bringBack(JInternalFrame window, Map<String, String> kept) {
    if (!(window instanceof GamesFrame games) || !kept.containsKey(SEARCH + "words")) return;
    String year = kept.get(SEARCH + "year");
    games.search(new GamesFrame.Search(kept.get(SEARCH + "words"), kept.get(SEARCH + "where"), kept.get(SEARCH + "order"),
        new KnowsTheGames.Slice(year == null ? null : Integer.valueOf(year), kept.get(SEARCH + "genre"), kept.get(SEARCH + "machine")),
        on(kept, "rzx"), on(kept, "map"), on(kept, "loadable"), on(kept, "unknown"), on(kept, "colour")));
  }

  private static boolean on(Map<String, String> kept, String filter) {
    return Boolean.parseBoolean(kept.get(SEARCH + filter));
  }
}
