package it.auties.linkpreview;

/**
 * A model representing a link preview
 *
 * @param text   the matched text
 * @param result the result
 */
public record LinkPreviewMatch(String text, LinkPreviewResult result) {

}
