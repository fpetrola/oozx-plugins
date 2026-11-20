package com.fpetrola.oozx.api;

import dev.plugdesk.desk.KnowsAbout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What ZXInfo knows of an entry, a game or the real thing a plugin emulates, by its ZXDB id ({@code zxdb:1000221}): asked
 * of its API, answered from the copy of ZXDB that ships when it cannot be reached.
 */
public class KnownToZxInfo implements KnowsAbout {

  private static final String KNOWN = "zxdb:";
  private static final Set<String> PICTURES = Set.of("Hardware picture", "Hardware advertisement scan", "Game map", "Inlay - Front");
  private static final Set<String> READING = Set.of("Hardware technical documentation", "Instructions", "Scanned instructions");
  private static final int NAMED = 3;

  @Override
  public About about(String known) {
    if (known == null || !known.startsWith(KNOWN)) return null;
    String id = known.substring(KNOWN.length());
    GameEntry entry = Catalogues.game(String.format("%07d", Integer.parseInt(id)));
    if (entry == null) return null;
    String made = entry.publishers == null ? null : entry.publishers.stream()
        .map(publisher -> publisher.country == null ? publisher.name : publisher.name + " (" + publisher.country + ")")
        .collect(Collectors.joining(", "));
    String year = entry.originalYearOfRelease == null ? null : String.valueOf(entry.originalYearOfRelease);
    List<String> prices = prices(entry);
    Map<String, String> facts = new LinkedHashMap<>();
    put(facts, "What it is", entry.genreSubType);
    put(facts, "Made by", made);
    put(facts, "By", entry.authors == null ? null : entry.authors.stream().map(author -> author.name).filter(Objects::nonNull)
        .distinct().collect(Collectors.joining(", ")));
    put(facts, "Runs on", entry.machineType);
    put(facts, "Players", entry.numberOfPlayers == null || entry.numberOfPlayers < 2 ? null : String.valueOf(entry.numberOfPlayers));
    put(facts, "Score", entry.score == null || entry.score.score == null ? null
        : entry.score.score + " from " + entry.score.votes + " votes");
    put(facts, "Released", year);
    put(facts, "Cost", prices.isEmpty() ? null : String.join(", ", prices));
    put(facts, "Also known as", entry.alsoKnownAs);
    List<String> needing = titles(entry.requiredByHardware);
    if (!needing.isEmpty()) put(facts, "Needed by", needing.size() + (needing.size() == 1 ? " program: " : " programs, such as ")
        + String.join(", ", needing.subList(0, Math.min(NAMED, needing.size()))));
    if (entry.magazineReferences != null && !entry.magazineReferences.isEmpty())
      put(facts, "In magazines", entry.magazineReferences.size() + " mentions, from " + entry.magazineReferences.stream()
          .map(reference -> reference.dateYear).filter(Objects::nonNull).min(Integer::compare).map(String::valueOf).orElse("?"));
    String maker = entry.publishers == null || entry.publishers.isEmpty() ? null : entry.publishers.getFirst().name;
    String then = Stream.of(maker, year, prices.isEmpty() ? null : prices.getFirst())
        .filter(Objects::nonNull).filter(said -> !said.isEmpty()).collect(Collectors.joining("  ·  "));
    return new About(then.isEmpty() ? entry.title : then, plain(entry.hardwareBlurb), plain(entry.remarks),
        downloads(entry, PICTURES).values().stream().filter(KnownToZxInfo::isPicture).toList(), facts, links(entry, id));
  }

  private static void put(Map<String, String> facts, String what, String is) {
    if (is != null && !is.isBlank()) facts.put(what, is);
  }

  private static List<String> prices(GameEntry entry) {
    Stream<GameEntry.Price> each = entry.releases == null || entry.releases.isEmpty()
        ? Stream.ofNullable(entry.originalPrice) : entry.releases.stream().map(release -> release.releasePrice);
    return each.filter(Objects::nonNull).filter(price -> price.amount != null).map(price -> {
      String currency = price.currency == null ? "" : price.currency;
      return price.prefix == null || price.prefix != 0 ? currency + price.amount : price.amount + " " + currency;
    }).distinct().toList();
  }

  @SuppressWarnings("unchecked")
  private static List<String> titles(List<Object> entries) {
    return entries == null ? List.of() : entries.stream().filter(Map.class::isInstance)
        .map(one -> ((Map<String, Object>) one).get("title")).filter(Objects::nonNull).map(String::valueOf).toList();
  }

  /** Where to read more: the catalogue's own pages, the sites it links, and its documents, by what each is. */
  private static Map<String, String> links(GameEntry entry, String id) {
    Map<String, String> links = new LinkedHashMap<>();
    links.put("ZXInfo", "https://zxinfo.dk/details/" + id);
    links.put("Spectrum Computing", "https://spectrumcomputing.co.uk/entry/" + id);
    if (entry.relatedLinks != null) entry.relatedLinks.forEach(link -> links.putIfAbsent(link.siteName, link.url));
    if (entry.relatedSites != null) entry.relatedSites.forEach(link -> links.putIfAbsent(link.siteName, link.url));
    downloads(entry, READING).forEach(links::putIfAbsent);
    return links;
  }

  /** The files of those kinds, each under its name, at the address it is served from. */
  private static Map<String, String> downloads(GameEntry entry, Set<String> kinds) {
    Map<String, String> found = new LinkedHashMap<>();
    if (entry.additionalDownloads != null) entry.additionalDownloads.stream()
        .filter(download -> kinds.contains(download.type) && download.path != null)
        .forEach(download -> found.put(download.path.substring(download.path.lastIndexOf('/') + 1),
            ZxInfoApiHandler.mediaUrl(download.path)));
    return found;
  }

  private static boolean isPicture(String address) {
    return address.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif)$");
  }

  /** ZXDB writes a mention of another entry as {Title|Publisher|id}: a person reads the title. */
  private static String plain(String said) {
    return said == null || said.isBlank() ? null : said.replaceAll("\\{([^|}]*)\\|[^}]*}", "$1").strip();
  }
}
