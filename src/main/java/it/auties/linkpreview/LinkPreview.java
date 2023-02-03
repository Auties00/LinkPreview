package it.auties.linkpreview;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class to generate preview links
 */
@UtilityClass
@SuppressWarnings("unused")
public class LinkPreview {
    private final Pattern URL_REGEX = Pattern.compile("(https?://)?([\\w.-]+)(\\.\\w{2,})+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    /**
     * Creates a preview from a piece of text.
     * All links will be scanned.
     *
     * @param text the text to scan
     * @return a non-null list
     */
    public List<LinkPreviewMatch> createPreviews(@NonNull String text) {
        try {
            return createPreviewStream(text)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    /**
     * Creates a preview from a piece of text.
     * Only the first link will be scanned.
     *
     * @param text the text to scan
     * @return a non-null list
     */
    public Optional<LinkPreviewMatch> createPreview(@NonNull String text) {
        try {
            return createPreviewStream(text)
                    .flatMap(Optional::stream)
                    .findFirst();
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static Stream<Optional<LinkPreviewMatch>> createPreviewStream(String text) {
        return URL_REGEX.matcher(text)
            .results()
            .map(MatchResult::group)
            .map(matched -> createPreview(URI.create(matched))
                .map(result -> new LinkPreviewMatch(matched, result)));
    }

    /**
     * Creates a preview from an uri synchronously
     *
     * @param uri the uri to use
     * @return a non-null optional wrapping the preview result
     */
    public Optional<LinkPreviewResult> createPreview(URI uri) {
        try {
            return createPreviewAsync(uri).join();
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    /**
     * Creates a preview from an uri asynchronously
     *
     * @param uri the uri to use
     * @return a non-null completable future wrapping an optional wrapping the preview result
     */
    public CompletableFuture<Optional<LinkPreviewResult>> createPreviewAsync(URI uri) {
        return createPreviewAsync(CLIENT, uri);
    }

    /**
     * Creates a preview from an uri synchronously
     *
     * @param client the client to use
     * @param uri    the uri to use
     * @return a non-null optional wrapping the preview result
     */
    public Optional<LinkPreviewResult> createPreview(HttpClient client, URI uri) {
        try {
            return createPreviewAsync(client, uri).join();
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    /**
     * Creates a preview from an uri asynchronously
     *
     * @param client the client to use
     * @param uri    the uri to use
     * @return a non-null completable future wrapping an optional wrapping the preview result
     */
    public CompletableFuture<Optional<LinkPreviewResult>> createPreviewAsync(@NonNull HttpClient client, @NonNull URI uri) {
        try {
            return createPreviewAsync(client, createDefaultRequest(uri, true));
        } catch (Throwable throwable) {
            return createPreviewAsync(client, createDefaultRequest(uri, false));
        }
    }

    private HttpRequest createDefaultRequest(URI uri, boolean secure) {
        return HttpRequest.newBuilder()
                .uri(uri.getScheme() == null ? URI.create("%s://%s".formatted(secure ? "https" : "http", uri)) : uri)
                .GET()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.0.0 Safari/537.36")
                .build();
    }

    /**
     * Creates a preview from an uri synchronously
     *
     * @param client  the client to use
     * @param request the request to use
     * @return a non-null optional wrapping the preview result
     */
    public Optional<LinkPreviewResult> createPreview(HttpClient client, HttpRequest request) {
        try {
            return createPreviewAsync(client, request).join();
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    /**
     * Creates a preview from an uri asynchronously
     *
     * @param client  the client to use
     * @param request the request to use
     * @return a non-null completable future wrapping an optional wrapping the preview result
     */
    public CompletableFuture<Optional<LinkPreviewResult>> createPreviewAsync(@NonNull HttpClient client, @NonNull HttpRequest request) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(LinkPreview::handleResponse);
    }

    private Optional<LinkPreviewResult> handleResponse(HttpResponse<String> response) {
        try {
            var contentType = response.headers().firstValue("Content-Type")
                    .map(type -> type.contains(";") ? type.split(";", 2)[0] : type)
                    .orElse(null);
            if (contentType == null || !contentType.equalsIgnoreCase("text/html")) {
                return Optional.empty();
            }

            var document = Jsoup.parse(response.body());
            var title = getElementContent(document, "og:title")
                    .or(() -> getTitleFallback(document))
                    .orElse("");
            var siteName = getElementContent(document, "og:site_name")
                    .orElse("");
            var siteDescription = getElementContent(document, "description")
                    .or(() -> getElementContent(document, "Description"))
                    .or(() -> getElementContent(document, "og:description"))
                    .orElse("");
            var mediaType = getElementContent(document, "medium")
                    .or(() -> getElementContent(document, "og:type"))
                    .orElse("website");
            var images = getImages(document, response.uri());
            var videos = getVideos(document);
            var favIcons = getFavIcons(document, response.uri());
            return Optional.of(new LinkPreviewResult(response.uri(), title, siteName, siteDescription, mediaType, images, videos, favIcons));
        } catch (Throwable throwable) {
            return Optional.empty();
        }
    }

    private Optional<String> getTitleFallback(Document document) {
        return document.getElementsByTag("title")
                .stream()
                .findFirst()
                .map(Element::text);
    }

    private Set<URI> getFavIcons(Document document, URI uri) {
        var results = Stream.of("@rel=\"icon\"", "@rel=\"shortcut icon\"", "@rel=\"apple-touch-icon\"")
                .map(selector -> document.selectXpath("//link[%s]".formatted(selector)))
                .flatMap(Collection::stream)
                .map(src -> src.attr("href"))
                .map(uri::resolve)
                .collect(Collectors.toUnmodifiableSet());
        return results.isEmpty() ? Set.of(uri.resolve("/favicon.ico")) : results;
    }

    private Set<LinkPreviewMedia> getImages(Document document, URI uri) {
        var images = getElements(document, "og:image")
                .stream()
                .map(src -> src.attr("content"))
                .map(uri::resolve)
                .collect(Collectors.toUnmodifiableSet());
        if (!images.isEmpty()) {
            var widths = getElements(document, "og:image:width")
                    .iterator();
            var heights = getElements(document, "og:image:height")
                    .iterator();
            return createMedias(images.iterator(), widths, heights);
        }

        var src = document.selectXpath("link[rel=image_src]");
        if (src.hasAttr("href")) {
            return Set.of(new LinkPreviewMedia(uri.resolve(src.attr("href"))));
        }

        var nodes = document.getElementsByTag("img");
        if (nodes.isEmpty()) {
            return Set.of();
        }

        return nodes.stream()
                .map(entry -> new LinkPreviewMedia(uri.resolve(entry.attr("src")),
                        tryParseUnsignedInt(entry.attr("width")),
                        tryParseUnsignedInt(entry.attr("height"))))
                .collect(Collectors.toUnmodifiableSet());
    }

    private int tryParseUnsignedInt(String input){
        try {
            if(input == null){
                return -1;
            }

            return Integer.parseUnsignedInt(input);
        }catch (NumberFormatException exception){
            return -1;
        }
    }

    private Set<LinkPreviewMedia> getVideos(Document document) {
        var videos = Stream.of(getElements(document, "og:video:secure_url"), getElements(document, "og:video:url"))
                .flatMap(Collection::stream)
                .map(entry -> entry.attr("content"))
                .distinct()
                .map(URI::create)
                .collect(Collectors.toUnmodifiableSet());
        if(videos.isEmpty()){
            return Set.of();
        }

        var widths = getElements(document, "og:video:width")
                .iterator();
        var heights = getElements(document, "og:video:height")
                .iterator();
        return createMedias(videos.iterator(), widths, heights);
    }

    private Set<LinkPreviewMedia> createMedias(Iterator<URI> videoIterator, Iterator<Element> widths, Iterator<Element> heights) {
        var results = new HashSet<LinkPreviewMedia>();
        while (videoIterator.hasNext()){
            var hasData = widths.hasNext() && heights.hasNext();
            var width = !hasData ? -1 : tryParseUnsignedInt(widths.next().attr("content"));
            var height = !hasData ? -1 : tryParseUnsignedInt(heights.next().attr("content"));
            results.add(new LinkPreviewMedia(videoIterator.next(), width, height));
        }

        return Collections.unmodifiableSet(results);
    }

    private Optional<String> getElementContent(Document document, String type) {
        return getElements(document, type, "property")
                .stream()
                .findFirst()
                .or(() -> getElements(document, type, "name").stream().findFirst())
                .map(entry -> entry.attr("content"));
    }

    private Elements getElements(Document document, String type) {
        var elements = getElements(document, type, "property");
        return elements.isEmpty() ? getElements(document, type, "name") : elements;
    }

    private Elements getElements(Document document, String type, String attribute) {
        return document.selectXpath("//meta[@%s=\"%s\"]".formatted(attribute, type));
    }
}
