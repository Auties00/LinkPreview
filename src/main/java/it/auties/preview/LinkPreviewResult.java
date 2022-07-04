package it.auties.preview;

import lombok.NonNull;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * A model representing a link preview
 *
 * @param uri             the uri of the website
 * @param title           the title of the website
 * @param siteName        the name of the website
 * @param siteDescription the description of the website
 * @param mediaType       the type of media of the website
 * @param images          the images of the website
 * @param videos          the videos of the website
 * @param favIcons        the fav icons of the website
 */
public record LinkPreviewResult(@NonNull URI uri, @NonNull String title, @NonNull String siteName,
                                @NonNull String siteDescription, @NonNull String mediaType,
                                @NonNull Set<URI> images, @NonNull Set<URI> videos, @NonNull Set<URI> favIcons) {

}
