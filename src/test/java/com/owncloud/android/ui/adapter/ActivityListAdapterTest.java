package com.owncloud.android.ui.adapter;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.FieldSetter;

import java.util.ArrayList;

public final class ActivityListAdapterTest {


    @Mock
    private ActivityListAdapter activityListAdapter;

    @Before
    public void setUp() throws NoSuchFieldException {
        MockitoAnnotations.initMocks(this);
        MockitoAnnotations.initMocks(activityListAdapter);
        FieldSetter.setField(activityListAdapter, activityListAdapter.getClass().getDeclaredField("values"), new ArrayList<>());
    }

    @Test
    public void isHeader__ObjectIsHeader_ReturnTrue() {
        Object header = "Hello";
        Object activity = Mockito.mock(com.owncloud.android.lib.resources.activities.model.Activity.class);

        Mockito.when(activityListAdapter.isHeader(0)).thenCallRealMethod();
        Mockito.when(activityListAdapter.getItemViewType(0)).thenCallRealMethod();

        activityListAdapter.values.add(header);
        activityListAdapter.values.add(activity);

        final boolean result = activityListAdapter.isHeader(0);
        Assert.assertTrue(result);
    }

    @Test
    public void isHeader__ObjectIsActivity_ReturnFalse() {
        Object header = "Hello";
        Object activity = Mockito.mock(com.owncloud.android.lib.resources.activities.model.Activity.class);

        Mockito.when(activityListAdapter.isHeader(1)).thenCallRealMethod();
        Mockito.when(activityListAdapter.getItemViewType(1)).thenCallRealMethod();

        activityListAdapter.values.add(header);
        activityListAdapter.values.add(activity);
        Assert.assertFalse(activityListAdapter.isHeader(1));
    }
}
