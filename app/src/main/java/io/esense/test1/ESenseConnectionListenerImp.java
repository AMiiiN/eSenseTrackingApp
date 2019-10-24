package io.esense.test1;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import io.esense.esenselib.ESenseConnectionListener;
import io.esense.esenselib.ESenseManager;

class ESenseConnectionListenerImp implements ESenseConnectionListener {

    private MainActivity mainAct;
    private Handler handler;

    public void instantiate(MainActivity mainActArg, Handler handlerArg) {
        mainAct = mainActArg;
        handler = handlerArg;
    }

    @Override
    public void onConnected(ESenseManager manager) {

        handler.sendEmptyMessage(mainAct.CONNECTED_ESENSE);
        Log.d("ConnectionListener", "Successfully connected to eSense device.");
    }

    @Override
    public void onDeviceFound(ESenseManager manager) {

        handler.sendEmptyMessage(mainAct.ESENSE_FOUND);
        Log.d("ConnectionListener", "eSense Device has been found!");
    }

    @Override
    public void onDeviceNotFound(ESenseManager manager) {

        handler.sendEmptyMessage(mainAct.ESENSE_NOT_FOUND);
        Log.d("ConnectionListener", "eSense Device has not been found.");
    }

    @Override
    public void onDisconnected(ESenseManager manager) {

        handler.sendEmptyMessage(mainAct.DISCONNECTED_ESENSE);
        Log.d("ConnectionListener", "Successfully disconnected from eSense device.");
    }

}
