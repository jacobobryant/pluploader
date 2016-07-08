package com.jacobobryant.playlistuploader;

import java.util.*;

public class Recommendations {
    private int playlistId;
    private List<Map<String, Object>> recList;

    public Recommendations(List<Map<String, Object>> recList) {
        this.recList = recList;
    }

    public int getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(int playlistId) {
        this.playlistId = playlistId;
    }

    public List<Map<String, Object>> getRecList() {
        return recList;
    }

    public void setRecList(List<Map<String, Object>> recList) {
        this.recList = recList;
    }
}
