package com.nextcloud.android.sso;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.nextcloud.android.sso.aidl.IInputStreamService;
import com.nextcloud.android.sso.aidl.NextcloudRequest;
import com.nextcloud.android.sso.aidl.ParcelFileDescriptorUtil;
import com.owncloud.android.authentication.AccountUtils;
import com.owncloud.android.db.PreferenceManager;
import com.owncloud.android.lib.common.OwnCloudAccount;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientManagerFactory;

import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 *  Nextcloud SingleSignOn
 *
 *  @author David Luhmer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * More information here: https://github.com/abeluck/android-streams-ipc
 *
 */


public class InputStreamBinder extends IInputStreamService.Stub {

    private final static String TAG = "InputStreamBinder";
    private Context context;
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";
    private static final String CHARSET_UTF8 = "UTF-8";

    private ArrayList<String> validPackages = new ArrayList<>(Arrays.asList(
            "de.luhmer.owncloudnewsreader"
    ));

    public InputStreamBinder(Context context) {
        this.context = context;
    }

    private NameValuePair[] convertMapToNVP(Map<String, String> map) {
        NameValuePair[] nvp = new NameValuePair[map.size()];
        int i = 0;
        for (String key : map.keySet()) {
            nvp[i] = new NameValuePair(key, map.get(key));
            i++;
        }
        return nvp;
    }

    public ParcelFileDescriptor performNextcloudRequest(ParcelFileDescriptor input) {
        // read the input
        final InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(input);

        Exception exception = null;
        InputStream httpStream = new InputStream() {
            @Override
            public int read() {
                return 0;
            }
        };
        try {
            // Start request and catch exceptions
            NextcloudRequest request = deserializeObjectAndCloseStream(is);
            httpStream = processRequest(request);
        } catch (Exception e) {
            e.printStackTrace();
            exception = e;
        }

        try {
            // Write exception to the stream followed by the actual network stream
            InputStream exceptionStream = serializeObjectToInputStream(exception);
            InputStream resultStream = new java.io.SequenceInputStream(exceptionStream, httpStream);
            return ParcelFileDescriptorUtil.pipeFrom(resultStream, thread -> Log.d(TAG, "Done sending result"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private <T extends Serializable> ByteArrayInputStream serializeObjectToInputStream(T obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.flush();
        oos.close();
        return new ByteArrayInputStream(baos.toByteArray());
    }

    private <T extends Serializable> T deserializeObjectAndCloseStream(InputStream is) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(is);
        T result = (T) ois.readObject();
        is.close();
        ois.close();
        return result;
    }


    private InputStream processRequest(final NextcloudRequest request) throws Exception {
        Account account = AccountUtils.getOwnCloudAccountByName(context, request.accountName); // TODO handle case that account is not found!
        if(account == null) {
            throw new IllegalStateException("CE_2"); // Custom Exception 2 (Account not found)
        }
        OwnCloudAccount ocAccount = new OwnCloudAccount(account, context);
        OwnCloudClient client = OwnCloudClientManagerFactory.getDefaultSingleton().getClientFor(ocAccount, context);

        // Validate token & package name
        if (!isValid(request)) {
            throw new IllegalStateException("CE_1"); // Custom Exception 1 (Invalid token or package name)
        }

        // Validate URL
        if(!request.url.startsWith("/")) {
            throw new IllegalStateException("URL need to start with a /");
        }

        request.url = client.getBaseUri() + request.url;
        HttpMethodBase method;

        switch (request.method) {
            case "GET":
                method = new GetMethod(request.url);
                break;

            case "POST":
                method = new PostMethod(request.url);
                if (request.requestBody != null) {
                    StringRequestEntity requestEntity = new StringRequestEntity(
                            request.requestBody,
                            CONTENT_TYPE_APPLICATION_JSON,
                            CHARSET_UTF8);
                    ((PostMethod) method).setRequestEntity(requestEntity);
                }
                break;

            case "PUT":
                method = new PutMethod(request.url);
                if (request.requestBody != null) {
                    StringRequestEntity requestEntity = new StringRequestEntity(
                            request.requestBody,
                            CONTENT_TYPE_APPLICATION_JSON,
                            CHARSET_UTF8);
                    ((PutMethod) method).setRequestEntity(requestEntity);
                }
                break;

            case "DELETE":
                method = new DeleteMethod(request.url);
                break;

            default:
                throw new Exception("Unexpected type!!");

        }

        method.setQueryString(convertMapToNVP(request.parameter));
        method.addRequestHeader("OCS-APIREQUEST", "true");

        int status = client.executeMethod(method);
        if (status == 200) {
            return method.getResponseBodyAsStream();
        } else {
            throw new Exception("Request returned code: " + status);
        }
    }

    private boolean isValid(NextcloudRequest request) {
        if(request.packageName == null) {
            String callingPackageName = context.getPackageManager().getNameForUid(Binder.getCallingUid());
            request.packageName = callingPackageName;
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String storedToken = sharedPreferences.getString(request.packageName, "");
        return validPackages.contains(request.packageName) && request.token.equals(storedToken);
    }
}
