/**
 * Nextcloud Android client application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2016 Andy Scherzinger
 * Copyright (C) 2016 Nextcloud
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.owncloud.android.ui.dialog.parcel;

import android.os.Parcel;
import android.os.Parcelable;

import com.owncloud.android.datamodel.SyncedFolderItem;

/**
 * Parceble for SyncedFolder objects to transport them from/to dialog fragments.
 */
public class SyncedFolderParcelable implements Parcelable {
    private String mLocalPath;
    private String mRemotePath;
    private Boolean mWifiOnly;
    private Boolean mChargingOnly;
    private boolean mEnabled;
    private Boolean mSubfolderByDate;
    private Integer mUploadAction;
    private long mId;
    private String mAccount;

    public SyncedFolderParcelable() {
    }

    public SyncedFolderParcelable(SyncedFolderItem syncedFolderItem) {
        mId = syncedFolderItem.getId();
        mLocalPath = syncedFolderItem.getLocalPath();
        mRemotePath = syncedFolderItem.getRemotePath();
        mWifiOnly = syncedFolderItem.getWifiOnly();
        mChargingOnly = syncedFolderItem.getChargingOnly();
        mEnabled = syncedFolderItem.isEnabled();
        mSubfolderByDate = syncedFolderItem.getSubfolderByDate();
        mAccount = syncedFolderItem.getAccount();
        mUploadAction = syncedFolderItem.getUploadAction();
    }

    public SyncedFolderParcelable(Parcel read) {
        mId = read.readLong();
        mLocalPath = read.readString();
        mRemotePath = read.readString();
        mWifiOnly = read.readInt()!= 0;
        mChargingOnly = read.readInt() != 0;
        mEnabled = read.readInt() != 0;
        mSubfolderByDate = read.readInt() != 0;
        mAccount = read.readString();
        mUploadAction = read.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mId);
        dest.writeString(mLocalPath);
        dest.writeString(mRemotePath);
        dest.writeInt(mWifiOnly ? 1 : 0);
        dest.writeInt(mChargingOnly ? 1 : 0);
        dest.writeInt(mEnabled ? 1 : 0);
        dest.writeInt(mSubfolderByDate ? 1 : 0);
        dest.writeString(mAccount);
        dest.writeInt(mUploadAction);
    }

    public static final Creator<SyncedFolderParcelable> CREATOR =
            new Creator<SyncedFolderParcelable>() {

                @Override
                public SyncedFolderParcelable createFromParcel(Parcel source) {
                    return new SyncedFolderParcelable(source);
                }

                @Override
                public SyncedFolderParcelable[] newArray(int size) {
                    return new SyncedFolderParcelable[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    public String getLocalPath() {
        return mLocalPath;
    }

    public void setLocalPath(String mLocalPath) {
        this.mLocalPath = mLocalPath;
    }

    public String getRemotePath() {
        return mRemotePath;
    }

    public void setRemotePath(String mRemotePath) {
        this.mRemotePath = mRemotePath;
    }

    public Boolean getWifiOnly() {
        return mWifiOnly;
    }

    public void setWifiOnly(Boolean mWifiOnly) {
        this.mWifiOnly = mWifiOnly;
    }

    public Boolean getChargingOnly() {
        return mChargingOnly;
    }

    public void setChargingOnly(Boolean mChargingOnly) {
        this.mChargingOnly = mChargingOnly;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(boolean mEnabled) {
        this.mEnabled = mEnabled;
    }

    public Boolean getSubfolderByDate() {
        return mSubfolderByDate;
    }

    public void setSubfolderByDate(Boolean mSubfolderByDate) {
        this.mSubfolderByDate = mSubfolderByDate;
    }

    public Integer getUploadAction() {
        return mUploadAction;
    }

    public void setUploadAction(Integer mUploadAction) {
        this.mUploadAction = mUploadAction;
    }

    public long getId() {
        return mId;
    }

    public void setId(long mId) {
        this.mId = mId;
    }

    public String getAccount() {
        return mAccount;
    }

    public void setAccount(String mAccount) {
        this.mAccount = mAccount;
    }
}
