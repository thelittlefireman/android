/*
 * Nextcloud application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.jobs;

import android.accounts.Account;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.evernote.android.job.Job;
import com.evernote.android.job.util.support.PersistableBundleCompat;
import com.google.gson.Gson;
import com.owncloud.android.R;
import com.owncloud.android.authentication.AccountUtils;
import com.owncloud.android.datamodel.DecryptedPushMessage;
import com.owncloud.android.datamodel.SignatureVerification;
import com.owncloud.android.lib.common.OwnCloudAccount;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientManagerFactory;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.lib.resources.notifications.DeleteNotificationRemoteOperation;
import com.owncloud.android.lib.resources.notifications.GetNotificationRemoteOperation;
import com.owncloud.android.lib.resources.notifications.models.Action;
import com.owncloud.android.lib.resources.notifications.models.Notification;
import com.owncloud.android.ui.activity.NotificationsActivity;
import com.owncloud.android.ui.notifications.NotificationUtils;
import com.owncloud.android.utils.PushUtils;
import com.owncloud.android.utils.ThemeUtils;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class NotificationJob extends Job {
    public static final String TAG = "NotificationJob";

    public static final String KEY_NOTIFICATION_ACCOUNT = "KEY_NOTIFICATION_ACCOUNT";
    public static final String KEY_NOTIFICATION_SUBJECT = "subject";
    public static final String KEY_NOTIFICATION_SIGNATURE = "signature";
    private static final String KEY_NOTIFICATION_ACTION_LINK = "KEY_NOTIFICATION_ACTION_LINK";
    private static final String KEY_NOTIFICATION_ACTION_TYPE = "KEY_NOTIFICATION_ACTION_TYPE";
    private static final String PUSH_NOTIFICATION_ID = "PUSH_NOTIFICATION_ID";
    private static final String NUMERIC_NOTIFICATION_ID = "NUMERIC_NOTIFICATION_ID";
    public static final String APP_SPREED = "spreed";

    private SecureRandom randomId = new SecureRandom();
    private Context context;

    @NonNull
    @Override
    protected Result onRunJob(@NonNull Params params) {
        context = getContext();
        PersistableBundleCompat persistableBundleCompat = getParams().getExtras();
        String subject = persistableBundleCompat.getString(KEY_NOTIFICATION_SUBJECT, "");
        String signature = persistableBundleCompat.getString(KEY_NOTIFICATION_SIGNATURE, "");

        if (!TextUtils.isEmpty(subject) && !TextUtils.isEmpty(signature)) {
            try {
                byte[] base64DecodedSubject = Base64.decode(subject, Base64.DEFAULT);
                byte[] base64DecodedSignature = Base64.decode(signature, Base64.DEFAULT);
                PrivateKey privateKey = (PrivateKey) PushUtils.readKeyFromFile(false);

                try {
                    SignatureVerification signatureVerification = PushUtils.verifySignature(context,
                                                                                            base64DecodedSignature,
                                                                                            base64DecodedSubject);

                    if (signatureVerification != null && signatureVerification.isSignatureValid()) {
                        Cipher cipher = Cipher.getInstance("RSA/None/PKCS1Padding");
                        cipher.init(Cipher.DECRYPT_MODE, privateKey);
                        byte[] decryptedSubject = cipher.doFinal(base64DecodedSubject);

                        Gson gson = new Gson();
                        DecryptedPushMessage decryptedPushMessage = gson.fromJson(new String(decryptedSubject),
                                                                                  DecryptedPushMessage.class);

                        // We ignore Spreed messages for now
                        if (!APP_SPREED.equals(decryptedPushMessage.getApp())) {
                            fetchCompleteNotification(signatureVerification.getAccount(), decryptedPushMessage);
                        }
                    }
                } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e1) {
                    Log.d(TAG, "Error decrypting message " + e1.getClass().getName()
                        + " " + e1.getLocalizedMessage());
                }
            } catch (Exception exception) {
                Log.d(TAG, "Something went very wrong" + exception.getLocalizedMessage());
            }
        }
        return Result.SUCCESS;
    }

    private void sendNotification(Notification notification, Account account) {
        Intent intent = new Intent(context, NotificationsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(KEY_NOTIFICATION_ACCOUNT, account.name);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);
        int pushNotificationId = randomId.nextInt();

        NotificationCompat.Builder notificationBuilder =
            new NotificationCompat.Builder(context, NotificationUtils.NOTIFICATION_CHANNEL_PUSH)
                .setSmallIcon(R.drawable.notification_icon)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.notification_icon))
                .setColor(ThemeUtils.primaryColor(account, false, context))
                .setShowWhen(true)
                .setSubText(account.name)
                .setContentTitle(notification.getSubject())
                .setContentText(notification.getMessage())
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(pendingIntent);

        // Remove
        if (notification.getActions().isEmpty()) {
            Intent disableDetection = new Intent(context, NotificationJob.NotificationReceiver.class);
            disableDetection.putExtra(NUMERIC_NOTIFICATION_ID, notification.getNotificationId());
            disableDetection.putExtra(PUSH_NOTIFICATION_ID, pushNotificationId);
            disableDetection.putExtra(KEY_NOTIFICATION_ACCOUNT, account.name);

            PendingIntent disableIntent = PendingIntent.getBroadcast(context, pushNotificationId, disableDetection,
                                                                     PendingIntent.FLAG_CANCEL_CURRENT);

            notificationBuilder.addAction(new NotificationCompat.Action(R.drawable.ic_close,
                                                                        context.getString(R.string.remove_push_notification), disableIntent));
        } else {
            // Actions
            for (Action action : notification.getActions()) {
                Intent actionIntent = new Intent(context, NotificationJob.NotificationReceiver.class);
                actionIntent.putExtra(NUMERIC_NOTIFICATION_ID, notification.getNotificationId());
                actionIntent.putExtra(PUSH_NOTIFICATION_ID, pushNotificationId);
                actionIntent.putExtra(KEY_NOTIFICATION_ACCOUNT, account.name);
                actionIntent.putExtra(KEY_NOTIFICATION_ACTION_LINK, action.link);
                actionIntent.putExtra(KEY_NOTIFICATION_ACTION_TYPE, action.type);


                PendingIntent actionPendingIntent = PendingIntent.getBroadcast(context, randomId.nextInt(),
                                                                               actionIntent,
                                                                               PendingIntent.FLAG_CANCEL_CURRENT);

                int icon;
                if (action.primary) {
                    icon = R.drawable.ic_check_circle;
                } else {
                    icon = R.drawable.ic_check_circle_outline;
                }

                notificationBuilder.addAction(new NotificationCompat.Action(icon, action.label, actionPendingIntent));
            }
        }

        notificationBuilder.setPublicVersion(
            new NotificationCompat.Builder(context, NotificationUtils.NOTIFICATION_CHANNEL_PUSH)
                .setSmallIcon(R.drawable.notification_icon)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.notification_icon))
                .setColor(ThemeUtils.primaryColor(account, false, context))
                .setShowWhen(true)
                .setSubText(account.name)
                .setContentTitle(context.getString(R.string.new_notification))
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent).build());

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(pushNotificationId, notificationBuilder.build());
    }

    private void fetchCompleteNotification(Account account, DecryptedPushMessage decryptedPushMessage) {
        Account currentAccount = AccountUtils.getOwnCloudAccountByName(context, account.name);

        if (currentAccount == null) {
            Log_OC.e(this, "Account may not be null");
            return;
        }

        try {
            OwnCloudAccount ocAccount = new OwnCloudAccount(currentAccount, context);
            OwnCloudClient client = OwnCloudClientManagerFactory.getDefaultSingleton()
                .getClientFor(ocAccount, context);
            client.setOwnCloudVersion(AccountUtils.getServerVersion(currentAccount));

            RemoteOperationResult result = new GetNotificationRemoteOperation(decryptedPushMessage.nid)
                .execute(client);

            if (result.isSuccess()) {
                Notification notification = result.getNotificationData().get(0);
                sendNotification(notification, account);
            }

        } catch (Exception e) {
            Log_OC.e(this, "Error creating account", e);
        }
    }

    public static class NotificationReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int numericNotificationId = intent.getIntExtra(NUMERIC_NOTIFICATION_ID, 0);
            int pushNotificationId = intent.getIntExtra(PUSH_NOTIFICATION_ID, 0);
            String accountName = intent.getStringExtra(NotificationJob.KEY_NOTIFICATION_ACCOUNT);

            if (numericNotificationId != 0) {
                new Thread(() -> {
                    try {
                        Account currentAccount = AccountUtils.getOwnCloudAccountByName(context, accountName);

                        if (currentAccount == null) {
                            Log_OC.e(this, "Account may not be null");
                            return;
                        }

                        OwnCloudAccount ocAccount = new OwnCloudAccount(currentAccount, context);
                        OwnCloudClient client = OwnCloudClientManagerFactory.getDefaultSingleton()
                            .getClientFor(ocAccount, context);
                        client.setOwnCloudVersion(AccountUtils.getServerVersion(currentAccount));

                        String actionType = intent.getStringExtra(KEY_NOTIFICATION_ACTION_TYPE);
                        String actionLink = intent.getStringExtra(KEY_NOTIFICATION_ACTION_LINK);

                        boolean success;
                        if (!TextUtils.isEmpty(actionType) && !TextUtils.isEmpty(actionLink)) {
                            success = executeAction(actionType, actionLink, client) == HttpStatus.SC_OK;
                        } else {
                            success = new DeleteNotificationRemoteOperation(numericNotificationId)
                                .execute(client).isSuccess();
                        }

                        if (success) {
                            cancel(context, pushNotificationId);
                        }
                    } catch (com.owncloud.android.lib.common.accounts.AccountUtils.AccountNotFoundException |
                        IOException | OperationCanceledException | AuthenticatorException e) {
                        Log_OC.e(TAG, "Error initializing client", e);
                    }
                }).start();
            }
        }

        @SuppressFBWarnings(value = "HTTP_PARAMETER_POLLUTION",
            justification = "link and type are from server and expected to be safe")
        private int executeAction(String actionType, String actionLink, OwnCloudClient client) {
            HttpMethod method;

            switch (actionType) {
                case "GET":
                    method = new GetMethod(actionLink);
                    break;

                case "POST":
                    method = new PostMethod(actionLink);
                    break;

                case "DELETE":
                    method = new DeleteMethod(actionLink);
                    break;

                case "PUT":
                    method = new PutMethod(actionLink);
                    break;

                default:
                    // do nothing
                    return 0;
            }

            method.setRequestHeader(RemoteOperation.OCS_API_HEADER, RemoteOperation.OCS_API_HEADER_VALUE);

            try {
                return client.executeMethod(method);
            } catch (IOException e) {
                Log_OC.e(TAG, "Execution of notification action failed: " + e);
            }
            return 0;
        }

        private void cancel(Context context, int notificationId) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(
                Activity.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                notificationManager.cancel(notificationId);
            }
        }
    }
}
