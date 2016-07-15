package com.jacobobryant.playlistuploader;

public class MyCoolProvider { }

//public class Provider extends ContentProvider {
//    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
//
//    static {
//        //sUriMatcher.addURI("com.example.app.provider", "table3", 1);
//        //sUriMatcher.addURI("com.example.app.provider", "table3/#", 2);
//    }
//
//    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
//                        String sortOrder) {
//        switch (sUriMatcher.match(uri)) {
//            case 1:
//                if (TextUtils.isEmpty(sortOrder)) sortOrder = "_ID ASC";
//                break;
//            case 2:
//                selection = selection + "_ID = " + uri.getLastPathSegment();
//                break;
//            default:
//        }
//        // call the code to actually do the query
//        return null;
//    }
//}