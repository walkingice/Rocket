package org.mozilla.focus.persistence;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;

@Entity(tableName = "tabs")
public class TabModel {

    @Ignore
    public TabModel(String id, String parentId) {
        this(id, parentId, "", "");
    }

    public TabModel(String id, String parentId, String title, String url) {
        this.id = id;
        this.parentId = parentId;
        this.title = title;
        this.url = url;
    }

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "tab_id")
    private String id;

    @ColumnInfo(name = "tab_parent_id")
    private String parentId;

    @ColumnInfo(name = "tab_title")
    private String title;

    @ColumnInfo(name = "tab_url")
    private String url;

    /**
     * Thumbnail bitmap for tab previewing.
     */
    @Ignore
    private Bitmap thumbnail;

    /**
     * Favicon bitmap for tab tray item.
     */
    @Ignore
    private Bitmap favicon;

    /**
     * ViewState for this Tab. Usually to fill by WebView.saveViewState(Bundle)
     * Set it as @Ignore to avoid storing this field into database.
     * It will be serialized to a file and save the uri path into webViewStateUri field.
     */
    @Ignore
    private Bundle webViewState;

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Bitmap getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(Bitmap thumbnail) {
        this.thumbnail = thumbnail;
    }

    public Bitmap getFavicon() {
        return favicon;
    }

    public void setFavicon(Bitmap favicon) {
        this.favicon = favicon;
    }

    public Bundle getWebViewState() {
        return webViewState;
    }

    public void setWebViewState(Bundle webViewState) {
        this.webViewState = webViewState;
    }

    public boolean isValid() {
        final boolean hasId = !TextUtils.isEmpty(this.getId());
        final boolean hasUrl = !TextUtils.isEmpty(this.getUrl());

        return hasId && hasUrl;
    }

    @Override
    public String toString() {
        return "TabModel{" +
                "id='" + id + '\'' +
                ", parentId='" + parentId + '\'' +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", thumbnail=" + thumbnail +
                ", favicon=" + favicon +
                ", webViewState=" + webViewState +
                '}';
    }
}
