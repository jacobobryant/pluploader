package com.jacobobryant.musicrecommender;

import java.util.*;

public class Recommendations {
    private String playlistName;
    private List<Map<String, Object>> recList;

    public Recommendations(List<Map<String, Object>> recList) {
        this.recList = recList;
    }

    public String getPlaylistName() {
        return playlistName;
    }

    public void setPlaylistName(String playlistName) {
        this.playlistName = playlistName;
    }

    public List<Map<String, Object>> getRecList() {
        return recList;
    }

    public void setRecList(List<Map<String, Object>> recList) {
        this.recList = recList;
    }
}
