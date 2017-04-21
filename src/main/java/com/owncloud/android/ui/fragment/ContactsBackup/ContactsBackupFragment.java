/**
 * Nextcloud Android client application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic
 * Copyright (C) 2017 Nextcloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.owncloud.android.ui.fragment.contactsbackup;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;

import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.support.PersistableBundleCompat;
import com.owncloud.android.R;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.db.PreferenceManager;
import com.owncloud.android.services.ContactsBackupJob;
import com.owncloud.android.ui.activity.ContactsPreferenceActivity;
import com.owncloud.android.ui.fragment.FileFragment;
import com.owncloud.android.utils.DisplayUtils;
import com.owncloud.android.utils.PermissionUtil;

import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.owncloud.android.ui.activity.ContactsPreferenceActivity.PREFERENCE_CONTACTS_AUTOMATIC_BACKUP;
import static com.owncloud.android.ui.activity.ContactsPreferenceActivity.PREFERENCE_CONTACTS_LAST_BACKUP;

public class ContactsBackupFragment extends FileFragment {
    public static final String TAG = ContactsBackupFragment.class.getSimpleName();

    private SharedPreferences sharedPreferences;

    @BindView(R.id.contacts_automatic_backup)
    private SwitchCompat backupSwitch;

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.contacts_backup_fragment, null);
        ButterKnife.bind(this, view);

        setHasOptionsMenu(true);

        final ContactsPreferenceActivity contactsPreferenceActivity = (ContactsPreferenceActivity) getActivity();

        contactsPreferenceActivity.getSupportActionBar().setTitle(R.string.actionbar_contacts);
        contactsPreferenceActivity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(contactsPreferenceActivity);

        backupSwitch.setChecked(sharedPreferences.getBoolean(PREFERENCE_CONTACTS_AUTOMATIC_BACKUP, false));

        backupSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked &&
                        checkAndAskForContactsReadPermission(PermissionUtil.PERMISSIONS_READ_CONTACTS_AUTOMATIC)) {
                    // store value
                    setAutomaticBackup(backupSwitch, true);

                    // enable daily job
                    contactsPreferenceActivity.startContactBackupJob(contactsPreferenceActivity.getAccount());
                } else {
                    setAutomaticBackup(backupSwitch, false);

                    // cancel pending jobs
                    contactsPreferenceActivity.cancelContactBackupJob(contactsPreferenceActivity);
                }
            }
        });

        // display last backup
        TextView lastBackup = (TextView) view.findViewById(R.id.contacts_last_backup_timestamp);
        Long lastBackupTimestamp = sharedPreferences.getLong(PREFERENCE_CONTACTS_LAST_BACKUP, -1);

        if (lastBackupTimestamp == -1) {
            lastBackup.setText(R.string.contacts_preference_backup_never);
        } else {
            lastBackup.setText(DisplayUtils.getRelativeTimestamp(contactsPreferenceActivity, lastBackupTimestamp));
        }

        return view;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final ContactsPreferenceActivity contactsPreferenceActivity = (ContactsPreferenceActivity) getActivity();

        boolean retval;
        switch (item.getItemId()) {
            case android.R.id.home:
                if (contactsPreferenceActivity.isDrawerOpen()) {
                    contactsPreferenceActivity.closeDrawer();
                } else {
                    contactsPreferenceActivity.openDrawer();
                }
                retval = true;
                break;

            default:
                retval = super.onOptionsItemSelected(item);
                break;
        }
        return retval;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionUtil.PERMISSIONS_READ_CONTACTS_AUTOMATIC) {
            for (int index = 0; index < permissions.length; index++) {
                if (Manifest.permission.READ_CONTACTS.equalsIgnoreCase(permissions[index])) {
                    if (grantResults[index] >= 0) {
                        setAutomaticBackup(backupSwitch, true);
                    } else {
                        setAutomaticBackup(backupSwitch, false);
                    }

                    break;
                }
            }
        }

        if (requestCode == PermissionUtil.PERMISSIONS_READ_CONTACTS_MANUALLY) {
            for (int index = 0; index < permissions.length; index++) {
                if (Manifest.permission.READ_CONTACTS.equalsIgnoreCase(permissions[index])) {
                    if (grantResults[index] >= 0) {
                        startContactsBackupJob();
                    }

                    break;
                }
            }
        }
    }

    @OnClick(R.id.contacts_backup_now)
    public void backupContacts() {
        if (checkAndAskForContactsReadPermission(PermissionUtil.PERMISSIONS_READ_CONTACTS_MANUALLY)) {
            startContactsBackupJob();
        }
    }

    private void startContactsBackupJob() {
        final ContactsPreferenceActivity contactsPreferenceActivity = (ContactsPreferenceActivity) getActivity();

        PersistableBundleCompat bundle = new PersistableBundleCompat();
        bundle.putString(ContactsBackupJob.ACCOUNT, contactsPreferenceActivity.getAccount().name);
        bundle.putBoolean(ContactsBackupJob.FORCE, true);

        new JobRequest.Builder(ContactsBackupJob.TAG)
                .setExtras(bundle)
                .setExecutionWindow(3_000L, 10_000L)
                .setRequiresCharging(false)
                .setPersisted(false)
                .setUpdateCurrent(false)
                .build()
                .schedule();

        Snackbar.make(getView().findViewById(R.id.contacts_linear_layout), R.string.contacts_preferences_backup_scheduled,
                Snackbar.LENGTH_LONG).show();
    }

    private void setAutomaticBackup(SwitchCompat backupSwitch, boolean bool) {
        backupSwitch.setChecked(bool);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PREFERENCE_CONTACTS_AUTOMATIC_BACKUP, bool);
        editor.apply();
    }

    private boolean checkAndAskForContactsReadPermission(final int permission) {
        final ContactsPreferenceActivity contactsPreferenceActivity = (ContactsPreferenceActivity) getActivity();

        // check permissions
        if ((PermissionUtil.checkSelfPermission(contactsPreferenceActivity, Manifest.permission.READ_CONTACTS))) {
            return true;
        } else {
            // Check if we should show an explanation
            if (PermissionUtil.shouldShowRequestPermissionRationale(contactsPreferenceActivity,
                    android.Manifest.permission.READ_CONTACTS)) {
                // Show explanation to the user and then request permission
                Snackbar snackbar = Snackbar.make(getView().findViewById(R.id.contacts_linear_layout),
                        R.string.contacts_read_permission,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.common_ok, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                PermissionUtil.requestReadContactPermission(contactsPreferenceActivity, permission);
                            }
                        });

                DisplayUtils.colorSnackbar(contactsPreferenceActivity, snackbar);

                snackbar.show();

                return false;
            } else {
                // No explanation needed, request the permission.
                PermissionUtil.requestReadContactPermission(contactsPreferenceActivity, permission);

                return false;
            }
        }
    }

    @OnClick(R.id.contacts_datepicker)
    public void openDate() {
        final ContactsPreferenceActivity contactsPreferenceActivity = (ContactsPreferenceActivity) getActivity();


        String backupFolderString = getResources().getString(R.string.contacts_backup_folder) + OCFile.PATH_SEPARATOR;
        OCFile backupFolder = contactsPreferenceActivity.getStorageManager().getFileByPath(backupFolderString);

        Vector<OCFile> backupFiles = contactsPreferenceActivity.getStorageManager().getFolderContent(backupFolder,
                false);

        Collections.sort(backupFiles, new Comparator<OCFile>() {
            @Override
            public int compare(OCFile o1, OCFile o2) {
                if (o1.getModificationTimestamp() == o2.getModificationTimestamp()) {
                    return 0;
                }

                if (o1.getModificationTimestamp() > o2.getModificationTimestamp()) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });

        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog.OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                String backupFolderString = getResources().getString(R.string.contacts_backup_folder) + OCFile.PATH_SEPARATOR;
                OCFile backupFolder = contactsPreferenceActivity.getStorageManager().getFileByPath(backupFolderString);
                Vector<OCFile> backupFiles = contactsPreferenceActivity.getStorageManager().getFolderContent(
                        backupFolder, false);

                // find file with modification with date and time between 00:00 and 23:59
                // if more than one file exists, take oldest
                Calendar date = Calendar.getInstance();
                date.set(year, month, dayOfMonth);

                // start
                date.set(Calendar.HOUR, 0);
                date.set(Calendar.MINUTE, 0);
                date.set(Calendar.SECOND, 1);
                date.set(Calendar.MILLISECOND, 0);
                date.set(Calendar.AM_PM, Calendar.AM);
                Long start = date.getTimeInMillis();

                // end
                date.set(Calendar.HOUR, 23);
                date.set(Calendar.MINUTE, 59);
                date.set(Calendar.SECOND, 59);
                Long end = date.getTimeInMillis();

                OCFile backupToRestore = null;

                for (OCFile file : backupFiles) {
                    if (start < file.getModificationTimestamp() && end > file.getModificationTimestamp()) {
                        if (backupToRestore == null) {
                            backupToRestore = file;
                        } else if (backupToRestore.getModificationTimestamp() < file.getModificationTimestamp()) {
                            backupToRestore = file;
                        }
                    }
                }

                if (backupToRestore != null) {
                    Fragment contactListFragment = ContactListFragment.newInstance(backupToRestore,
                            contactsPreferenceActivity.getAccount());

                    FragmentTransaction transaction = contactsPreferenceActivity.getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.frame_container, contactListFragment);
                    transaction.addToBackStack(null);
                    transaction.commit();
                } else {
                    Toast.makeText(contactsPreferenceActivity, R.string.contacts_preferences_no_file_found,
                            Toast.LENGTH_SHORT).show();
                }
            }
        };

        DatePickerDialog datePickerDialog = new DatePickerDialog(contactsPreferenceActivity,
                dateSetListener, year, month, day);
        datePickerDialog.getDatePicker().setMaxDate(backupFiles.lastElement().getModificationTimestamp());
        datePickerDialog.getDatePicker().setMinDate(backupFiles.firstElement().getModificationTimestamp());

        datePickerDialog.show();
    }

}
