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

/** Which games come first: the ones people know, and the ones many people rated well. */
class BestRatedFirstTest {

  static GameSearchResult game(String title, Double score, Integer votes) {
    GameSearchResult result = new GameSearchResult(title, title, null, null, null, null);
    result.score = score;
    result.votes = votes;
    return result;
  }

  static List<String> sorted(java.util.Comparator<GameSearchResult> by, GameSearchResult... games) {
    List<GameSearchResult> found = new ArrayList<>(List.of(games));
    found.sort(by);
    return found.stream().map(result -> result.title).toList();
  }

  @Test
  void mostKnownIsTheMostVotedWhateverItsScore() {
    assertEquals(List.of("manic miner", "obscure gem", "never voted"),
        sorted(GamesFrame.BY_VOTES, game("obscure gem", 9.8, 3), game("never voted", null, null), game("manic miner", 8.4, 412)));
  }

  @Test
  void aFewHighVotesDoNotBeatManyGoodOnes() {
    assertEquals(List.of("jet set willy", "obscure gem", "never voted"),
        sorted(GamesFrame.BY_RATING, game("obscure gem", 10.0, 3), game("never voted", null, null), game("jet set willy", 9.0, 400)));
  }

  @Test
  void ofTwoWellKnownTheBetterScoreWins() {
    assertEquals(List.of("head over heels", "chuckie egg"),
        sorted(GamesFrame.BY_RATING, game("chuckie egg", 8.0, 300), game("head over heels", 9.2, 250)));
  }
}
