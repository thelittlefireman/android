/*
 * Nextcloud Android client application
 *
 * @author masensio
 * @author Andy Scherzinger
 * @author Chris Narkiewicz <hello@ezaquarii.com>
 *
 * Copyright (C) 2015 ownCloud GmbH
 * Copyright (C) 2018 Andy Scherzinger
 * Copyright (C) 2020 Chris Narkiewicz <hello@ezaquarii.com>
 * Copyright (C) 2020 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.ui.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.owncloud.android.R;
import com.owncloud.android.databinding.FileDetailsSharePublicLinkItemBinding;
import com.owncloud.android.databinding.FileDetailsShareUserItemBinding;
import com.owncloud.android.lib.resources.shares.OCShare;
import com.owncloud.android.lib.resources.shares.ShareType;
import com.owncloud.android.utils.DisplayUtils;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Adapter to show a user/group/email/remote in Sharing list in file details view.
 */
public class ShareeListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
    implements DisplayUtils.AvatarGenerationListener {

    private ShareeListAdapterListener listener;
    private Context context;
    private List<OCShare> shares;
    private float avatarRadiusDimension;
    private String userId;

    public ShareeListAdapter(Context context,
                             List<OCShare> shares,
                             ShareeListAdapterListener listener,
                             String userId) {
        this.context = context;
        this.shares = shares;
        this.listener = listener;
        this.userId = userId;

        avatarRadiusDimension = context.getResources().getDimension(R.dimen.user_icon_radius);

        sort(this.shares);
    }

    @Override
    public int getItemViewType(int position) {
        return shares.get(position).getShareType().getValue();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (ShareType.fromValue(viewType)) {
            case PUBLIC_LINK:
            case EMAIL:
                return new PublicShareViewHolder(FileDetailsSharePublicLinkItemBinding.inflate(LayoutInflater.from(context),
                                                                                               parent,
                                                                                               false),
                                                 context);
            default:
                return new UserViewHolder(FileDetailsShareUserItemBinding.inflate(LayoutInflater.from(context),
                                                                                  parent,
                                                                                  false),
                                          context);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (shares == null || shares.size() <= position) {
            return;
        }

        final OCShare share = shares.get(position);

        if (holder instanceof PublicShareViewHolder) {
            PublicShareViewHolder publicShareViewHolder = (PublicShareViewHolder) holder;
            publicShareViewHolder.bind(share, listener);
        } else {
            UserViewHolder userViewHolder = (UserViewHolder) holder;
            userViewHolder.bind(share, listener, userId, avatarRadiusDimension);
        }
    }

    @Override
    public long getItemId(int position) {
        return shares.get(position).getId();
    }

    @Override
    public int getItemCount() {
        return shares.size();
    }

    public void addShares(List<OCShare> sharesToAdd) {
        shares.addAll(sharesToAdd);
        sort(shares);
        notifyDataSetChanged();
    }

    @Override
    public void avatarGenerated(Drawable avatarDrawable, Object callContext) {
        if (callContext instanceof ImageView) {
            ImageView iv = (ImageView) callContext;
            iv.setImageDrawable(avatarDrawable);
        }
    }

    @Override
    public boolean shouldCallGeneratedCallback(String tag, Object callContext) {
        if (callContext instanceof ImageView) {
            ImageView iv = (ImageView) callContext;
            return String.valueOf(iv.getTag()).equals(tag);
        }
        return false;
    }

    public void remove(OCShare share) {
        shares.remove(share);
        notifyDataSetChanged();
    }

    private void sort(List<OCShare> shares) {
        Collections.sort(shares, (o1, o2) -> {
            if (o1.getShareType() != o2.getShareType()) {
                return o1.getShareType().compareTo(o2.getShareType());
            }

            return o1.getSharedWithDisplayName().compareTo(o2.getSharedWithDisplayName());
        });
    }
}
