package com.example.pc2.general.activity;

import com.example.pc2.general.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.Window;
import android.widget.TextView;

import java.util.Calendar;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SplashActivity extends Activity {

    @BindView(R.id.tv_mushiny)TextView tv_mushiny;

    private static final int GO_HOME = 0x11;// 跳转到主页面标识
    private static final int DELAY = 2000;// 延时时间

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case GO_HOME:
                    goHome();
                    break;
            }
        }
    };

    /**
     * 主界面跳转
     */
    private void goHome() {

        startActivity(new Intent(SplashActivity.this, MainActivity.class));
        finish();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_splash);
        ButterKnife.bind(this);

        // 设置启动页面标语内容 Copyright @ 2017-2018 MUSHINY
        int year = Calendar.getInstance().get(Calendar.YEAR);
        String logo = getResources().getString(R.string.splash_copyright)
                + "2017-" + year + "MUSHINY";
        tv_mushiny.setText(logo);

        handler.sendEmptyMessageDelayed(GO_HOME, DELAY);

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK){
            return true;// 屏蔽返回按键
        }
        return super.onKeyDown(keyCode, event);
    }
}
