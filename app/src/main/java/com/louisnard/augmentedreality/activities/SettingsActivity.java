package com.louisnard.augmentedreality.activities;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.louisnard.augmentedreality.R;
import com.louisnard.augmentedreality.fragments.SettingsFragment;

/**
 * Augmented reality {@link AppCompatActivity} showing a {@link SettingsFragment}.<br>
 *
 * @author Alexandre Louisnard
 */
public class SettingsActivity extends AppCompatActivity {

    // Tag
    private static final String TAG = SettingsActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set layout
        setContentView(R.layout.activity_toolbar_simple_fragment);

        // Set title
        setTitle(getString(R.string.settings_activity));

        // Set toolbar
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(getDrawable(R.drawable.ic_arrow_drop_down_24dp));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        setSupportActionBar(toolbar);

        // Set fragment
        if (savedInstanceState == null) {
            final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment, new SettingsFragment());
            transaction.commit();
        }
    }
}