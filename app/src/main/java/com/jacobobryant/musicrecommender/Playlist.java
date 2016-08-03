package com.jacobobryant.musicrecommender;

import java.util.LinkedList;
import java.util.List;

public class Playlist {
    public enum Type {LOCAL, SPOTIFY};
    private List<String> tracks;
    private String name;
    private Type type;

    public static List<List<String>> toList(List<Playlist> playlists) {
        List<List<String>> list = new LinkedList<>();
        for (Playlist plist : playlists) {
            list.add(plist.tracks);
        }
        return list;
    }

    public Playlist(List<String> tracks, String name, Type type) {
        this.tracks = tracks;
        this.name = name;
        this.type = type;
    }

    public List<String> getTracks() {
        return tracks;
    }

    public void setTracks(List<String> tracks) {
        this.tracks = tracks;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }
}
