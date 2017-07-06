package com.louisnard.augmentedreality.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.louisnard.augmentedreality.R;

/**
 * Fragment showing the points around the user location using augmented reality over a camera preview.<br>
 *
 * @author Alexandre Louisnard
 */
public class SettingsFragment extends Fragment implements View.OnClickListener {

    // Tag
    private static final String TAG = SettingsFragment.class.getSimpleName();

    // Request codes
    private static final int REQUEST_PICK_GPX_FILE = 1;

    // Views
    private Button mImportGpxFileButton;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Views
        mImportGpxFileButton = (Button) view.findViewById(R.id.import_gpx_file_btn);

        // Listeners
        mImportGpxFileButton.setOnClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    // View.OnClickListener implementation
    @Override
    public void onClick(View v) {
        if (R.id.import_gpx_file_btn == v.getId()) {
            pickGpxFile();
        }
    }

    private void pickGpxFile() {
        final Intent chooseFile;
        final Intent intent;
        chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("application/gpx");
        intent = Intent.createChooser(chooseFile, getString(R.string.settings_pick_a_gpx_file));
        startActivityForResult(intent, REQUEST_PICK_GPX_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (REQUEST_PICK_GPX_FILE == requestCode && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            String filePath = uri.getPath();
            AlertDialogFragment.newInstance("file", filePath).show(getFragmentManager(), "");
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
