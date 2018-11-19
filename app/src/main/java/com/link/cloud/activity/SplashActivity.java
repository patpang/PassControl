package com.link.cloud.activity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.widget.Toast;

import com.link.cloud.R;
import com.link.cloud.base.BaseActivity;
import com.link.cloud.base.Constants;
import com.link.cloud.controller.SplashContronller;
import com.link.cloud.network.HttpConfig;
import com.link.cloud.network.bean.APPVersionBean;
import com.link.cloud.network.bean.AllUser;
import com.link.cloud.network.bean.BindUser;
import com.link.cloud.network.bean.CabnetDeviceInfoBean;
import com.link.cloud.network.bean.DeviceInfo;
import com.link.cloud.utils.NettyClientBootstrap;
import com.link.cloud.utils.TTSUtils;
import com.orhanobut.logger.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.reactivex.annotations.NonNull;
import io.realm.Realm;
import io.realm.RealmResults;

public class SplashActivity extends BaseActivity implements SplashContronller.SplashControllerListener{

    private NettyClientBootstrap nettyClientBootstrap;
    int total;
    private SplashContronller splashContronller;
    private DeviceInfo deviceInfo;


    @Override
    protected void initViews() {
        splashContronller = new SplashContronller(this);
        getDeviceInfo();
        showDate();
        TTSUtils.getInstance().speak("");

    }



    private void showDate() {
        if (deviceInfo != null && deviceInfo.getPsw() != null && !TextUtils.isEmpty(deviceInfo.getPsw())) {
            if (deviceInfo.getToken() != null && !TextUtils.isEmpty(deviceInfo.getToken())) {
                HttpConfig.TOKEN = deviceInfo.getToken();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        nettyClientBootstrap = new NettyClientBootstrap(SplashActivity.this, Constants.TCP_PORT, Constants.TCP_URL, "{\"data\":{},\"msgType\":\"HEART_BEAT\",\"token\":\"" + deviceInfo.getToken() + "\"}");
                        nettyClientBootstrap.start();
                    }
                }).start();

                splashContronller.getUser(1);
            } else {
                getToken();
            }
        } else {
            skipActivity(SettingActivity.class);
        }
    }

    private void getDeviceInfo() {
        final RealmResults<DeviceInfo> all = realm.where(DeviceInfo.class).findAll();
        if (!all.isEmpty()) {
            deviceInfo = all.get(0);
        }
    }


    private void getToken() {
        splashContronller.login(deviceInfo.getDeviceId().trim(), deviceInfo.getPsw());
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_splash;
    }


    @Override
    public void onLoginSuccess(final CabnetDeviceInfoBean cabnetDeviceInfoBean) {
        final RealmResults<DeviceInfo> all = realm.where(DeviceInfo.class).findAll();
        realm.executeTransaction(new Realm.Transaction() {
            @Override
            public void execute(Realm realm) {
                DeviceInfo device = all.get(0);
                device.setToken(cabnetDeviceInfoBean.getToken());
                device.setDeviceTypeId(cabnetDeviceInfoBean.getDeviceInfo().getDeviceTypeId());
                deviceInfo = device;
                realm.copyToRealm(device);
            }
        });
        HttpConfig.TOKEN = cabnetDeviceInfoBean.getToken();
        splashContronller.getAppVersion();
        nettyClientBootstrap = new NettyClientBootstrap(this, Constants.TCP_PORT, Constants.TCP_URL, "{\"data\":{},\"msgType\":\"HEART_BEAT\",\"token\":\"" + deviceInfo.getToken() + "\"}");
        nettyClientBootstrap.start();
        splashContronller.getUser(1);
    }

    @Override
    public void onMainErrorCode(String msg) {
        if (msg.equals("400000100000") ) {
            skipActivity(SettingActivity.class);
            TTSUtils.getInstance().speak(getString(R.string.login_fail));
        }else if(msg.equals("400000999102")){
            HttpConfig.TOKEN = "";
            getToken();
        }


    }

    @Override
    public void onMainFail(Throwable e, boolean isNetWork) {
        if(isNetWork){
            skipActivity(EntanceActivity.class);
            TTSUtils.getInstance().speak(getString(R.string.error_net));
        }
    }

    boolean isDeleteAll = false;

    @Override
    public void getUserSuccess(final BindUser data) {
        final RealmResults<AllUser> all = realm.where(AllUser.class).findAll();
        total = data.getTotal();
        if (all.size() != data.getTotal()) {
            if (!isDeleteAll) {
                realm.executeTransaction(new Realm.Transaction() {
                    @Override
                    public void execute(Realm realm) {
                        all.deleteAllFromRealm();
                        isDeleteAll = true;

                    }
                });
                int totalPage = total / Constants.PAGE_NUM + 1;
                ExecutorService executorService = Executors.newFixedThreadPool(totalPage);
                List<Future<Boolean>> futures = new ArrayList();
                if (totalPage >= 2) {
                    for (int i = 2; i <= totalPage; i++) {
                        final int finalI = i;
                        Callable<Boolean> task = new Callable<Boolean>() {
                            @Override
                            public Boolean call() throws Exception {
                                splashContronller.getUser(finalI);
                                return true;
                            }
                        };

                        futures.add(executorService.submit(task));
                    }
                    for (Future<Boolean> future : futures) {
                        try {
                            future.get();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (ExecutionException e) {
                            e.printStackTrace();
                        }
                    }
                    executorService.shutdown();
                }
            }
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    realm.copyToRealm(data.getData());
                }
            });
            showNext();
        } else {
            showNext();
        }

    }

    @Override
    public void getVersionSuccess(APPVersionBean data) {
        String version = data.getVersion();
        try {
            int i = Integer.parseInt(version);
            if(i>getVersion(this)){
                splashContronller.downloadFile();
            }
        }catch (Exception e)
        {

        }

    }
    private static int getVersion(Context context)// 获取版本号
    {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return 0;
        }
    }

    public void showNext() {
        Bundle bundle = new Bundle();
        Constants.CABINET_TYPE=deviceInfo.getDeviceTypeId();
        skipActivity(EntanceActivity.class);
//        if (deviceInfo.getDeviceTypeId() == Constants.REGULAR_CABINET) {
//
//        } else {
//            skipActivity(SettingActivity.class);
//            finish();
//            HttpConfig.TOKEN = "";
//            Toast.makeText(this, getString(R.string.error_type), Toast.LENGTH_LONG).show();
//        }
    }



}