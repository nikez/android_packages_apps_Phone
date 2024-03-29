/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.phone;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.Handler;
import android.os.Message;
import android.preference.ListPreference;
import android.provider.Settings.Secure;
import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyProperties;

public class CdmaSystemSelectListPreference extends ListPreference {

    private static final String LOG_TAG = "CdmaRoamingListPreference";
    private static final boolean DBG = true;

    private Phone mPhone;
    private MyHandler mHandler = new MyHandler();;

    public CdmaSystemSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mPhone = PhoneApp.getPhone();
        mHandler = new MyHandler();
        mPhone.queryCdmaRoamingPreference(
                mHandler.obtainMessage(MyHandler.MESSAGE_GET_ROAMING_PREFERENCE));
    }

    public CdmaSystemSelectListPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void showDialog(Bundle state) {
        if (Boolean.parseBoolean(
                    SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) {
            // In ECM mode do not show selection options
        } else {
            super.showDialog(state);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult && (getValue() != null)) {
            int buttonCdmaRoamingMode = Integer.valueOf(getValue()).intValue();
            int settingsCdmaRoamingMode =
                    Secure.getInt(mPhone.getContext().getContentResolver(),
                    Secure.CDMA_ROAMING_MODE, Phone.CDMA_RM_HOME);
            if (buttonCdmaRoamingMode != settingsCdmaRoamingMode) {
                //Set the Settings.Secure network mode
                Secure.putInt(mPhone.getContext().getContentResolver(),
                        Secure.CDMA_ROAMING_MODE,
                        buttonCdmaRoamingMode );
                //Set the roaming preference mode
                // Note: buttonCdmaRoamingMode was previously vetted against the
                // device-specific cdma_system_select_values list, and so doesn't need to
                // be coerced again here.
                mPhone.setCdmaRoamingPreference(buttonCdmaRoamingMode, mHandler
                        .obtainMessage(MyHandler.MESSAGE_SET_ROAMING_PREFERENCE));
            }
        } else {
            Log.d(LOG_TAG, String.format("onDialogClosed: positiveResult=%b value=%s -- do nothing",
                    positiveResult, getValue()));
        }
    }

    private class MyHandler extends Handler {

        private static final int MESSAGE_GET_ROAMING_PREFERENCE = 0;
        private static final int MESSAGE_SET_ROAMING_PREFERENCE = 1;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_ROAMING_PREFERENCE:
                    handleQueryCdmaRoamingPreference(msg);
                    break;

                case MESSAGE_SET_ROAMING_PREFERENCE:
                    handleSetCdmaRoamingPreference(msg);
                    break;
            }
        }

        private void handleQueryCdmaRoamingPreference(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if (ar.exception == null) {
                int statusCdmaRoamingMode = ((int[])ar.result)[0];
                int settingsRoamingMode = Secure.getInt(
                        mPhone.getContext().getContentResolver(),
                        Secure.CDMA_ROAMING_MODE, Phone.CDMA_RM_HOME);

                // Ensure that statusCdmaRoamingMode is a value that appears in the
                // cdma_system_select_values list.  Note that this check used to be
                // hardcoded against Phone.CDMA_RM_HOME and Phone.CDMA_RM_ANY, however,
                // some devices (e.g., epicmtd) have radios that support additional roaming
                // options which should be specified in an overlay .xml file.  Should the
                // radio report an option that's _not_ present in that list, fall back to
                // the default roaming mode so that the user doesn't operate in a mode he
                // can't verify.
                if (findIndexOfValue(Integer.toString(statusCdmaRoamingMode)) != -1) {
                    //check changes in statusCdmaRoamingMode and updates settingsRoamingMode
                    if (statusCdmaRoamingMode != settingsRoamingMode) {
                        settingsRoamingMode = statusCdmaRoamingMode;
                        //changes the Settings.Secure accordingly to statusCdmaRoamingMode
                        Secure.putInt(
                                mPhone.getContext().getContentResolver(),
                                Secure.CDMA_ROAMING_MODE,
                                settingsRoamingMode );
                    }
                    //changes the mButtonPreferredNetworkMode accordingly to modemNetworkMode
                    setValue(Integer.toString(statusCdmaRoamingMode));
                }
                else {
                    if(DBG) Log.i(LOG_TAG, "reset cdma roaming mode to default" );
                    resetCdmaRoamingModeToDefault();
                }
            }
        }

        private void handleSetCdmaRoamingPreference(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;

            if ((ar.exception == null) && (getValue() != null)) {
                int cdmaRoamingMode = Integer.valueOf(getValue()).intValue();
                Secure.putInt(mPhone.getContext().getContentResolver(),
                        Secure.CDMA_ROAMING_MODE,
                        cdmaRoamingMode );
            } else {
                mPhone.queryCdmaRoamingPreference(obtainMessage(MESSAGE_GET_ROAMING_PREFERENCE));
            }
        }

        private void resetCdmaRoamingModeToDefault() {
            //set the mButtonCdmaRoam
            setValue(Integer.toString(Phone.CDMA_RM_HOME));
            //set the Settings.System
            Secure.putInt(mPhone.getContext().getContentResolver(),
                        Secure.CDMA_ROAMING_MODE,
                        Phone.CDMA_RM_HOME );
            //Set the Status
            mPhone.setCdmaRoamingPreference(Phone.CDMA_RM_HOME,
                    obtainMessage(MyHandler.MESSAGE_SET_ROAMING_PREFERENCE));
        }
    }

}
