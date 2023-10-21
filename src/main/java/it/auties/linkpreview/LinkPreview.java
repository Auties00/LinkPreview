package it.auties.linkpreview;

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
@SuppressWarnings("unused")
public final class LinkPreview {
    private static final Pattern URL_REGEX = Pattern.compile("(https?://)?([\\w.-]+)(\\.\\w{2,})+(?::(\\d+))?([/\\w.?=-]*)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
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
    public static List<LinkPreviewMatch> createPreviews(String text) {
        return createPreviewsAsync(text).join();
    }

    /**
     * Creates a preview from a piece of text.
     * All links will be scanned.
     *
     * @param text the text to scan
     * @return a non-null list
     */
    public static CompletableFuture<List<LinkPreviewMatch>> createPreviewsAsync(String text) {
        Objects.requireNonNull(text);
        try {
            var results = URL_REGEX.matcher(text)
                    .results()
                    .map(MatchResult::group)
                    .map(matched -> createPreviewAsync(URI.create(matched))
                            .thenApply(optional -> optional.map(value -> new LinkPreviewMatch(matched, value))))
                    .toList();
            return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> results.stream()
                            .map(CompletableFuture::join)
                            .flatMap(Optional::stream)
                            .toList());
        }catch (Throwable throwable) {
            return CompletableFuture.completedFuture(List.of());
        }
    }


    /**
     * Creates a preview from a piece of text.
     * Only the first link will be scanned.
     *
     * @param text the text to scan
     * @return a non-null list
     */
    public static Optional<LinkPreviewMatch> createPreview(String text) {
        return createPreviewAsync(text).join();
    }


    /**
     * Creates a preview from a piece of text.
     * Only the first link will be scanned.
     *
     * @param text the text to scan
     * @return a non-null list
     */
    public static CompletableFuture<Optional<LinkPreviewMatch>> createPreviewAsync(String text) {
        Objects.requireNonNull(text);
      try {
          var matched = URL_REGEX.matcher(text)
                  .results()
                  .map(MatchResult::group)
                  .findFirst();
          if (matched.isEmpty()) {
              return CompletableFuture.completedFuture(Optional.empty());
          }

          return createPreviewAsync(URI.create(matched.get()))
                  .thenApply(optional -> optional.map(value -> new LinkPreviewMatch(matched.get(), value)));
      }catch (Throwable throwable) {
          return CompletableFuture.completedFuture(Optional.empty());
      }
    }

    /**
     * Creates a preview from an uri synchronously
     *
     * @param uri the uri to use
     * @return a non-null optional wrapping the preview result
     */
    public static Optional<LinkPreviewResult> createPreview(URI uri) {
        return createPreviewAsync(uri).join();
    }

    /**
     * Creates a preview from an uri asynchronously
     *
     * @param uri the uri to use
     * @return a non-null completable future wrapping an optional wrapping the preview result
     */
    public static CompletableFuture<Optional<LinkPreviewResult>> createPreviewAsync(URI uri) {
        return createPreviewAsync(CLIENT, uri);
    }

    /**
     * Creates a preview from an uri synchronously
     *
     * @param client the client to use
     * @param uri    the uri to use
     * @return a non-null optional wrapping the preview result
     */
    public static Optional<LinkPreviewResult> createPreview(HttpClient client, URI uri) {
        return createPreviewAsync(client, uri).join();
    }

    /**
     * Creates a preview from an uri asynchronously
     *
     * @param client the client to use
     * @param uri    the uri to use
     * @return a non-null completable future wrapping an optional wrapping the preview result
     */
    public static CompletableFuture<Optional<LinkPreviewResult>> createPreviewAsync(HttpClient client, URI uri) {
        Objects.requireNonNull(client);
        Objects.requireNonNull(uri);
        try {
            var request = createDefaultRequest(uri, true);
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApplyAsync(LinkPreview::handleResponse);
        } catch (Throwable throwable) {
            if(uri.getScheme() != null) {
                throw throwable;
            }

           try {
               var request = createDefaultRequest(uri, false);
               return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                       .thenApplyAsync(LinkPreview::handleResponse);
           }catch (Throwable ignored) {
               return CompletableFuture.completedFuture(Optional.empty());
           }
        }
    }

    private static HttpRequest createDefaultRequest(URI uri, boolean secure) {
        return HttpRequest.newBuilder()
                .uri(uri.getScheme() == null ? URI.create("%s://%s".formatted(secure ? "https" : "http", uri)) : uri)
                .GET()
                .header("User-Agent", "Googlebot")
                .build();
    }

    private static Optional<LinkPreviewResult> handleResponse(HttpResponse<String> response) {
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

    private static Optional<String> getTitleFallback(Document document) {
        return document.getElementsByTag("title")
                .stream()
                .findFirst()
                .map(Element::text);
    }

    private static Set<URI> getFavIcons(Document document, URI uri) {
        var results = Stream.of("@rel=\"icon\"", "@rel=\"shortcut icon\"", "@rel=\"apple-touch-icon\"")
                .map(selector -> document.selectXpath("//link[%s]".formatted(selector)))
                .flatMap(Collection::stream)
                .map(src -> src.attr("href"))
                .map(uri::resolve)
                .collect(Collectors.toUnmodifiableSet());
        return results.isEmpty() ? Set.of(uri.resolve("/favicon.ico")) : results;
    }

    private static Set<LinkPreviewMedia> getImages(Document document, URI uri) {
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

    private static int tryParseUnsignedInt(String input) {
        try {
            if (input == null) {
                return -1;
            }

            return Integer.parseUnsignedInt(input);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static Set<LinkPreviewMedia> getVideos(Document document) {
        var videos = Stream.of(getElements(document, "og:video:secure_url"), getElements(document, "og:video:url"))
                .flatMap(Collection::stream)
                .map(entry -> entry.attr("content"))
                .distinct()
                .map(URI::create)
                .collect(Collectors.toUnmodifiableSet());
        var widths = getElements(document, "og:video:width")
                .iterator();
        var heights = getElements(document, "og:video:height")
                .iterator();
        return createMedias(videos.iterator(), widths, heights);
    }

    private static Set<LinkPreviewMedia> createMedias(Iterator<URI> videoIterator, Iterator<Element> widths, Iterator<Element> heights) {
        var results = new HashSet<LinkPreviewMedia>();
        while (videoIterator.hasNext()) {
            var hasData = widths.hasNext() && heights.hasNext();
            var width = !hasData ? -1 : tryParseUnsignedInt(widths.next().attr("content"));
            var height = !hasData ? -1 : tryParseUnsignedInt(heights.next().attr("content"));
            results.add(new LinkPreviewMedia(videoIterator.next(), width, height));
        }

        return Collections.unmodifiableSet(results);
    }

    private static Optional<String> getElementContent(Document document, String type) {
        return getElements(document, type, "property")
                .stream()
                .findFirst()
                .or(() -> getElements(document, type, "name").stream().findFirst())
                .map(entry -> entry.attr("content"));
    }

    private static Elements getElements(Document document, String type) {
        var elements = getElements(document, type, "property");
        return elements.isEmpty() ? getElements(document, type, "name") : elements;
    }

    private static Elements getElements(Document document, String type, String attribute) {
        return document.selectXpath("//meta[@%s=\"%s\"]".formatted(attribute, type));
    }
}
