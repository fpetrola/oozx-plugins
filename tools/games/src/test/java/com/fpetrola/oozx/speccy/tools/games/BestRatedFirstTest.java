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

package com.fpetrola.oozx.speccy.tools.games;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Best rated: the highest score first, the most voted of equals first, and the unrated after all, as they came. */
class BestRatedFirstTest {

  static GameSearchResult game(String title, Double score, Integer votes) {
    GameSearchResult result = new GameSearchResult(title, title, null, null, null, null);
    result.score = score;
    result.votes = votes;
    return result;
  }

  @Test
  void theHighestScoreFirstTheMostVotedOfEqualsAndTheUnratedLast() {
    List<GameSearchResult> found = new ArrayList<>(List.of(
        game("unrated one", null, null), game("seven, few", 7.0, 3), game("nine", 9.1, 40),
        game("seven, many", 7.0, 200), game("unrated two", null, null)));
    found.sort(GamesFrame.BY_RATING);
    assertEquals(List.of("nine", "seven, many", "seven, few", "unrated one", "unrated two"),
        found.stream().map(result -> result.title).toList());
  }
}
