package com.jacobobryant.playlistuploader;

import java.io.Serializable;

public class Metadata implements Serializable {
    public String artist;
    public String album;
    public String track;

    public Metadata(String artist, String album, String track) {
        this.artist = artist;
        this.album = album;
        this.track = track;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Metadata metadata = (Metadata) o;

        if (artist != null ? !artist.equals(metadata.artist) : metadata.artist != null)
            return false;
        if (album != null ? !album.equals(metadata.album) : metadata.album != null)
            return false;
        return track != null ? track.equals(metadata.track) : metadata.track == null;

    }

    @Override
    public int hashCode() {
        int result = artist != null ? artist.hashCode() : 0;
        result = 31 * result + (album != null ? album.hashCode() : 0);
        result = 31 * result + (track != null ? track.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return artist + ';' + album + ';' + track;
    }
}
