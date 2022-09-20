package it.auties.linkpreview;

import lombok.NonNull;

import java.net.URI;
import java.util.Objects;

public record LinkPreviewMedia(@NonNull URI uri, int width, int height) {
    public LinkPreviewMedia(@NonNull URI uri){
        this(uri, -1, -1);
    }

    public boolean hasDimensions(){
        return width >= 0 && height >= 0;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LinkPreviewMedia that
                && Objects.equals(uri(), that.uri());
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri());
    }
}
