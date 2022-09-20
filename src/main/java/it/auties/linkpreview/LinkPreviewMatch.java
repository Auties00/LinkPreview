package it.auties.linkpreview;

import lombok.NonNull;

/**
 * A model representing a link preview
 *
 * @param text   the matched text
 * @param result the result
 */
public record LinkPreviewMatch(@NonNull String text, @NonNull LinkPreviewResult result) {

}
