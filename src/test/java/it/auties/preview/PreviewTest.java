package it.auties.preview;

import it.auties.linkpreview.LinkPreview;

public class PreviewTest {
    public static void main(String[] args) {
        System.out.println(LinkPreview.createPreview("Hello: https://www.youtube.com/watch?v=W9NjLyZBgL0"));
        System.out.println(LinkPreview.createPreview("https://it.wikipedia.org/wiki/Vulcano"));
    }
}
