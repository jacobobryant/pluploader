package com.jacobobryant.musicrecommender;

import java.util.LinkedList;
import java.util.List;

public class Playlist {
    private List<String> tracks;
    private int id;

    public Playlist(List<String> tracks, int id) {
        this.tracks = tracks;
        this.id = id;
    }

    public static List<List<String>> toList(List<Playlist> playlists) {
        List<List<String>> list = new LinkedList<>();
        for (Playlist plist : playlists) {
            list.add(plist.tracks);
        }
        return list;
    }

    public List<String> getTracks() {
        return tracks;
    }

    public void setTracks(List<String> tracks) {
        this.tracks = tracks;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
