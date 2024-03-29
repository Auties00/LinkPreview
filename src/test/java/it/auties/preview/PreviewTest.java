package it.auties.preview;

import it.auties.linkpreview.LinkPreview;

public class PreviewTest {
    public static void main(String[] args) {
        System.out.println(LinkPreview.createPreview("https://it.wikipedia.org/wiki/Vulcano"));
        System.out.println(LinkPreview.createPreview("https://music.youtube.com/watch?v=RdmrS0jOIbg&cbrd=1"));
        System.out.println(LinkPreview.createPreviews("This is a string containing links: google.com"));
        System.out.println(LinkPreview.createPreviews("This is a string containing links: google.com/abc"));
    }
}
