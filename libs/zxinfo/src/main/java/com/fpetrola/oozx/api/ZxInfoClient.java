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

package com.fpetrola.oozx.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.annotations.jaxrs.PathParam;

import java.util.List;

@Path("/v3")
@Produces(MediaType.APPLICATION_JSON)
public interface ZxInfoClient {

    /** ZXInfo's own service, and this project's, which answers the same questions from ZXDB (tools/zxinfo-worker). */
    String ZXINFO = "https://api.zxinfo.dk", OURS = "https://zxinfo.zxinfo-worker.workers.dev";

    /** JSON detail level accepted by /search and /games/{id}. The API defaults to "compact". */
    String MODE_TINY = "tiny";
    String MODE_COMPACT = "compact";
    String MODE_FULL = "full";

    /**
     * General search. Only "query" is required; every other parameter narrows the result down.
     * Pass null to leave a filter out - RESTEasy omits null query params.
     */
    @GET
    @Path("/search")
    SearchResponse searchGames(
        @QueryParam("query") String query,
        @QueryParam("size") @DefaultValue("10") int size,
        @QueryParam("offset") @DefaultValue("0") String offset,
        @QueryParam("mode") @DefaultValue(MODE_COMPACT) String mode,
        @QueryParam("titlesonly") Boolean titlesOnly,
        @QueryParam("sort") String sort,
        @QueryParam("contenttype") String contentType,
        @QueryParam("year") Integer year,
        @QueryParam("language") String language,
        @QueryParam("genretype") String genreType,
        @QueryParam("genresubtype") String genreSubType,
        @QueryParam("machinetype") String machineType,
        @QueryParam("controls") String controls,
        @QueryParam("multiplayermode") String multiplayerMode,
        @QueryParam("multiplayertype") String multiplayerType,
        @QueryParam("originalpublication") String originalPublication,
        @QueryParam("availability") String availability,
        @QueryParam("tosectype") String tosecType,
        @QueryParam("group") String group,
        @QueryParam("groupname") String groupName
    );

    @GET
    @Path("/games/{id}")
    GameResponse getGameDetails(@PathParam("id") String id, @QueryParam("mode") String mode);

    /** The values the search filters accept, with how many entries each one has. */
    @GET
    @Path("/metadata/")
    Metadata getMetadata();

    /** Suggestions across titles, publishers and authors, for type-as-you-go fields. */
    @GET
    @Path("/suggest/{term}")
    List<Suggestion> getSuggestions(@PathParam("term") String term);

    @GET
    @Path("/suggest/author/{term}")
    List<Suggestion> getSuggestionsAuthor(@PathParam("term") String term);

    @GET
    @Path("/suggest/publisher/{term}")
    List<Suggestion> getSuggestionsPublisher(@PathParam("term") String term);

    /**
     * Looks up the entry a file belongs to, by MD5 (32 chars) or SHA512 (128 chars)
     * of the tape/disk image itself. Answers 404 when nothing matches.
     */
    @GET
    @Path("/filecheck/{hash}")
    FileCheckResult getFileByHash(@PathParam("hash") String hash);

    /**
     * Asks the service at that address. Without a timeout a request nobody answers waits for ever (a catalogue run once
     * sat four hours in one read), and the archive answers 301 when it moves, which has to be followed.
     */
    static <T> T at(String address, java.util.function.Function<ZxInfoClient, T> call) {
        jakarta.ws.rs.client.Client client = ((org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder) jakarta.ws.rs.client.ClientBuilder.newBuilder())
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .setFollowRedirects(true)
            .build();
        try {
            return call.apply(((org.jboss.resteasy.client.jaxrs.ResteasyWebTarget) client.target(address)).proxy(ZxInfoClient.class));
        } finally {
            client.close();
        }
    }

    /** Where ZXDB's own paths are actually served from: ZXInfo's screens, and Spectrum Computing for the rest. */
    static String served(String path) {
        // ZXDB lists some files by their whole address already - the RZX Archive's recordings at
        // archive.org - and putting a host in front of one makes a link to nowhere.
        if (path.startsWith("http")) {
            return path;
        }
        if (path.startsWith("/zxscreens")) {
            return "https://zxinfo.dk/media" + path;
        }
        return "https://spectrumcomputing.co.uk" + path;
    }
}
