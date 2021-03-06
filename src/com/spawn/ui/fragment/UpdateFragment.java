/*
 * Copyright (C) 2013 OTAPlatform
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.spawn.updater.ui.fragment;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.spawn.updater.R;
import com.spawn.updater.manager.ManagerFactory;
import com.spawn.updater.ui.component.Item;
import com.spawn.updater.ui.component.Item.OnItemClickListener;
import com.spawn.updater.updater.CancelPackage;
import com.spawn.updater.updater.GappsUpdater;
import com.spawn.updater.updater.RomUpdater;
import com.spawn.updater.updater.Updater;
import com.spawn.updater.updater.Updater.PackageInfo;
import com.spawn.updater.util.Constants;

public class UpdateFragment extends Fragment implements RomUpdater.RomUpdaterListener,
        GappsUpdater.GappsUpdaterListener {

    private static PackageInfo mNewRom = null;
    private static boolean mStartup = true;

    private Intent mIntent;
    private ProgressDialog mProgress;
    private ProgressBar mProgressBar;
    private RomUpdater mRomUpdater;
    private GappsUpdater mGappsUpdater;
    private Item mButtonCheckRom;
    private Item mButtonCheckGapps;
    private Item mButtonDownload;
    private Item mButtonDownloadDelta;
    private TextView mNoNewRom;
    private boolean mRomCanUpdate = true;
    private boolean mShouldCheckGapps = true;

    public UpdateFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.update_fragment, container, false);

        mRomUpdater = Updater.getRomUpdater(getActivity(), this, false);

        mGappsUpdater = new GappsUpdater(getActivity(), this, false);

        mRomCanUpdate = mRomUpdater != null && mRomUpdater.canUpdate();

        mProgressBar = (ProgressBar) view.findViewById(R.id.progressBar);
        mButtonCheckRom = (Item) view.findViewById(R.id.button_checkupdates);
        mButtonCheckGapps = (Item) view.findViewById(R.id.button_checkupdatesgapps);
        mButtonDownload = (Item) view.findViewById(R.id.button_download);
        mButtonDownloadDelta = (Item) view.findViewById(R.id.button_download_delta);
        mNoNewRom = (TextView) view.findViewById(R.id.no_new_version);

        mButtonCheckRom.setEnabled(mRomCanUpdate);
        mButtonCheckRom.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onClick(int id) {
                mShouldCheckGapps = true;
                checkRom();
            }
        });

        mButtonCheckGapps.setEnabled(mGappsUpdater.canUpdate());
        mButtonCheckGapps.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onClick(int id) {
                checkGapps();
            }
        });

        mButtonDownload.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onClick(int id) {
                ManagerFactory.getFileManager(getActivity()).download(getActivity(),
                        mNewRom.getPath(), mNewRom.getFilename(), mNewRom.getMd5(), false,
                        Constants.DOWNLOADROM_NOTIFICATION_ID);
            }
        });

        mButtonDownloadDelta.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onClick(int id) {
                ManagerFactory.getFileManager(getActivity()).download(getActivity(),
                        mNewRom.getDeltaPath(), mNewRom.getDeltaFilename(), mNewRom.getDeltaMd5(),
                        true, Constants.DOWNLOADROM_NOTIFICATION_ID);
            }
        });

        mIntent = mStartup ? getActivity().getIntent() : null;
        checkIntent(null);
        mIntent = null;
        mStartup = false;

        return view;
    }

    public void checkIntent(Intent intent) {
        if (intent != null) {
            mIntent = intent;
        }
        if (mIntent != null && mIntent.getExtras() != null
                && mIntent.getExtras().get(Constants.FILE_INFO) != null) {
            if ((mIntent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
                PackageInfo info = ManagerFactory.getFileManager(getActivity()).onNewIntent(
                        getActivity(), mIntent);
                if (!(info instanceof CancelPackage)) {
                    mNewRom = info;
                    if (mNewRom != null && mNewRom.isGapps()) {
                        if (mGappsUpdater == null) {
                            mGappsUpdater = new GappsUpdater(getActivity(), this, false);
                        }
                        mGappsUpdater.versionFound(mNewRom);
                    }
                }
            }
        }

        if (mNewRom != null || !mStartup) {
            if (mNewRom != null && !mNewRom.isGapps()) {
                checkRomCompleted(mNewRom);
            } else if (mRomCanUpdate) {
                if (mStartup) {
                    checkRom();
                } else {
                    checkRomCompleted(mNewRom);
                }
            }
        } else if (mRomCanUpdate) {
            checkRom();
        }
    }

    @Override
    public void checkGappsCompleted(long newVersion) {
        if (mProgress != null) {
            mProgress.dismiss();
            mProgress = null;
        }
        mButtonCheckGapps.setEnabled(mGappsUpdater.canUpdate());
    }

    @Override
    public void checkRomCompleted(PackageInfo info) {
        try {
            mProgressBar.setVisibility(View.GONE);
            if (info == null) {
                mNewRom = null;
                mButtonDownload.setVisibility(View.GONE);
                mButtonDownloadDelta.setVisibility(View.GONE);
                mNoNewRom.setVisibility(View.VISIBLE);
            } else {
                mNewRom = info;
                mNoNewRom.setVisibility(View.GONE);
                mButtonDownload.setVisibility(View.VISIBLE);
                mButtonDownloadDelta.setVisibility(info.isDelta() ? View.VISIBLE : View.GONE);
                mButtonDownload.setSummary(getActivity().getResources().getString(
                        R.string.rom_download_summary, new Object[] { info.getVersion() }));
            }
            mButtonCheckRom.setEnabled(mRomUpdater != null && mRomUpdater.canUpdate());
            if (mShouldCheckGapps && ManagerFactory.getPreferencesManager(getActivity()).getGappsCheck()) {
                checkGapps();
            }
            mShouldCheckGapps = false;
        } catch (Exception ex) {
            // app closed?
        }
    }

    private void checkRom() {
        mNoNewRom.setVisibility(View.GONE);
        mButtonDownload.setVisibility(View.GONE);
        mButtonDownloadDelta.setVisibility(View.GONE);
        mProgressBar.setVisibility(View.VISIBLE);
        mRomUpdater.check();
    }

    private void checkGapps() {
        mProgress = ProgressDialog.show(getActivity(), null, getActivity().getResources()
                .getString(R.string.checking_gapps), true, true);
        mGappsUpdater.check();
    }

}
