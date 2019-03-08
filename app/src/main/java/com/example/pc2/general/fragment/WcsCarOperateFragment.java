package com.example.pc2.general.fragment;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.pc2.general.R;
import com.example.pc2.general.constant.Constants;
import com.example.pc2.general.utils.LogUtil;
import com.example.pc2.general.utils.ProgressBarUtil;
import com.example.pc2.general.utils.ToastUtil;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * A simple {@link Fragment} subclass.
 * wcs调用接口来实现小车的控制。
 * 现在包含大部分查询的接口，陆续会实现所有涉及的接口
 *
 * 工厂版本 更名为：其他
 * 添加了rcs的清除充电桩故障功能
 *
 * wcs的部分接口如果不需要提供，只需要在res文件夹下的layout布局文件夹的fragment_wcs_car_operate.xml布局中做修改
 * 修改涉及：很简单，找到对应的控件，布局中是button，然后设置该button的visibility属性为gone即可。
 * 反之，需要开放原来有的功能就设置button的visibility属性为visible即可。没有的就需要自己添加了。
 *
 */
public class WcsCarOperateFragment extends BaseFragment{

    private String CURRENT_FRAGMENT_TITLE = "";// 当前fragment的标题声明
    @BindView(R.id.iv_fragment_back)ImageView iv_fragment_back;
    @BindView(R.id.tv_fragment_title)TextView tv_fragment_title;

    private static final int WHAT_CAR_STATUS = 0x10;// 查看小车的状态
    private static final int WHAT_POD_STATUS = 0x11;// 查看pod信息
    private static final int WHAT_ADDR_STATUS = 0x12;// 查看地址状态
    private static final int WHAT_RESEND_ORDER = 0x13;// 重发任务
    private static final int WHAT_OFFLINE = 0x14;// 下线某小车
    private static final int WHAT_DRIVE_POD = 0x15;// 驱动pod去某地
    private RequestQueue requestQueue;// volley请求队列
    private String rootAddress = "";// 请求地址根路径
    private View viewShowContent = null;

    private String sectionId = "";// 地图的sectionId
    private AlertDialog dialog_operate;// 输入信息的弹框
    private AlertDialog dialog_response;// 查看返回结果弹框
    private ProgressDialog pDialog;// 进度框
    private View viewOperate;
    private TextView tv_other_tip_title;

    private ConnectionFactory factory = new ConnectionFactory();// 声明ConnectionFactory对象
    private Thread publishThread = null;// 发布消息消费线程

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case WHAT_CAR_STATUS:// 查看小车的状态
                    ToastUtil.showToast(getContext(),getResources().getString(R.string.other_Information_obtained_successfully));
                    String objCarStatus = (String) msg.obj;
                    int robotId1 = msg.arg1;
                    alertDialogShowCarStatus(objCarStatus, robotId1);
                    break;
                case WHAT_POD_STATUS:// 查看pod信息
                    ToastUtil.showToast(getContext(),getResources().getString(R.string.other_Information_obtained_successfully));
                    String objPodInfo = (String) msg.obj;
                    int podId1 = msg.arg1;
                    alertDialogShowPodStatus(objPodInfo, podId1);
                    break;
                case WHAT_ADDR_STATUS:// 查看地址状态
                    ToastUtil.showToast(getContext(),getResources().getString(R.string.other_Information_obtained_successfully));
                    String objAddr = (String) msg.obj;
                    int addr1 = msg.arg1;
                    alertDialogShowAddrStatus(objAddr, addr1);
                    break;
                case WHAT_RESEND_ORDER:// 重发任务
                    ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_Resend_task_successful));
                    String objResend = (String) msg.obj;
                    int robotId2 = msg.arg1;
                    alertDialogShowResendOrder(objResend, robotId2);
                    break;
                case WHAT_OFFLINE:// 下线小车
                    ToastUtil.showToast(getContext(),getResources().getString(R.string.other_Offline_successfully));
                    break;
                case WHAT_DRIVE_POD:// 驱动POD去某地
                    ToastUtil.showToast(getContext(),getResources().getString(R.string.other_Successfully_drive_pod_back_to_storage));
                    break;
            }
        }
    };

    /**
     * 小车重发任务
     * @param objResend
     * @param robotId
     */
    private void alertDialogShowResendOrder(String objResend, int robotId) {
        viewShowContent = getLayoutInflater().from(getContext()).inflate(R.layout.show_view_content, null);
        TextView tv_showContent = viewShowContent.findViewById(R.id.tv_showContent);

        if (!TextUtils.isEmpty(objResend)){
            tv_showContent.setText(objResend);
        }

        viewShowContent.findViewById(R.id.btn_refresh).setVisibility(View.GONE);
        showResponseDialog("重发任务返回数据");
    }

    /**
     * 查看地址状态
     * @param objAddr
     * @param addr
     */
    private void alertDialogShowAddrStatus(String objAddr, final int addr) {
        viewShowContent = getLayoutInflater().from(getContext()).inflate(R.layout.show_view_content, null);

        TextView tv_showContent = viewShowContent.findViewById(R.id.tv_showContent);
        if (!TextUtils.isEmpty(objAddr)){
            tv_showContent.setText(objAddr);
        }else {
            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Address_does_not_exist));
        }

        viewShowContent.findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                methodCheckAddrStatus(addr);
            }
        });

        showResponseDialog(getResources().getString(R.string.other_Check_address_status));
    }

    /**
     * 显示pod信息
     * @param objPodInfo
     * @param podId1
     */
    private void alertDialogShowPodStatus(String objPodInfo, final int podId1) {
        viewShowContent = getLayoutInflater().from(getContext()).inflate(R.layout.show_view_content, null);

        TextView tv_showContent = viewShowContent.findViewById(R.id.tv_showContent);
        if (!TextUtils.isEmpty(objPodInfo)){
            tv_showContent.setText(objPodInfo);
        }else {
            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_POD_information_is_empty));
        }

        viewShowContent.findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                methodCheckPodStatus(podId1);
            }
        });

        showResponseDialog(getResources().getString(R.string.other_Check_the_POD_information));
    }

    /**
     * 显示小车的状态
     * @param objCarStatus
     * @param robotId1
     */
    private void alertDialogShowCarStatus(String objCarStatus, final int robotId1) {

        viewShowContent = getLayoutInflater().from(getContext()).inflate(R.layout.show_view_content, null);

        TextView tv_showContent = viewShowContent.findViewById(R.id.tv_showContent);
        if (!TextUtils.isEmpty(objCarStatus)){
            tv_showContent.setText(objCarStatus);
        }else {
            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_The_car_information_is_empty));
        }

        viewShowContent.findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                methodCheckCarStatus(robotId1);
            }
        });

        showResponseDialog(getResources().getString(R.string.other_Check_the_status_of_a_car));

    }

    public WcsCarOperateFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wcs_car_operate, container, false);
        ButterKnife.bind(this, view);// 控件绑定
        init();// 初始化
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);// 光标出现的时候，隐藏软键盘
        setListeners();
        setTitleBar();
        return view;
    }

    /**
     * 设置标题栏样式
     */
    private void setTitleBar() {
        tv_fragment_title.setText(CURRENT_FRAGMENT_TITLE);
    }

    /**
     * 初始化数据
     */
    private void init() {
        requestQueue  = Volley.newRequestQueue(getContext());// 创建RequestQueue对象

        rootAddress = Constants.HTTP + Constants.ROOT_ADDRESS;// 请求地址根路径赋值

        sectionId = Constants.SECTIONID;// 地图的sectionId

        pDialog = new ProgressDialog(getContext());
        pDialog.setCanceledOnTouchOutside(true);

        CURRENT_FRAGMENT_TITLE = getResources().getString(R.string.other_other);
    }

    /**
     * 显示加载进度框
     * @param s 描述内容
     */
    private void showDialog(String s) {
        pDialog.setMessage(s);
        pDialog.show();
    }

    /**
     * 消失对话框，将其从屏幕上移除
     */
    private void dissMissDialog(){
        pDialog.dismiss();
    }

    /**
     * 展示返回结果弹框
     * @param message
     */
    private void showResponseDialog(String message){
        if (dialog_response != null && dialog_response.isShowing()){
            dialog_response.dismiss();
        }

        dialog_response = new AlertDialog.Builder(getContext())
                .setMessage(message)
                .setView(viewShowContent)
                .create();

        dialog_response.show();
    }

    /**
     * 设置监听
     */
    private void setListeners() {


    }

    /**
     * 设置dialog展示的view（用户选择操作弹框）
     */
    private void setDialogView(String tipTitle){
        viewOperate = getLayoutInflater().from(getContext()).inflate(R.layout.dialog_wcs_car, null);

        // 设置提示标题
        tv_other_tip_title = viewOperate.findViewById(R.id.tv_other_tip_title);
        tv_other_tip_title.setText(tipTitle);

        dialog_operate = new AlertDialog.Builder(getContext())
                .setView(viewOperate)
                .create();

        dialog_operate.show();
    }

    /**
     * 单击事件监听
     * @param view
     */
    @OnClick({R.id.tv_checkCarStatus, R.id.tv_checkPodStatus, R.id.tv_checkAddrStatus, R.id.tv_resendOrder
    , R.id.btn_robotOffline, R.id.btn_drivePod, R.id.btn_updatePodStatus
    ,R.id.btn_releasePodStatus, R.id.btn_updateAddrState, R.id.btn_robot2Charge, R.id.btn_autoDrivePod
            , R.id.btn_driveRobotCarryPod, R.id.iv_fragment_back, R.id.btn_driveRobot
    ,R.id.btn_clearChargeError, R.id.btn_updatePodOnMap, R.id.btn_updateRobotStatus, R.id.btn_speedlogFinishTripByRobot
    , R.id.btn_speedlogFinishTripByPod})
    public void doClick(View view){

        switch (view.getId()){
            case R.id.iv_fragment_back:// 返回上一界面
                getActivity().getSupportFragmentManager().popBackStack();
                break;

            case R.id.tv_checkCarStatus:// 查看某小车的状态 need

                setDialogView(getResources().getString(R.string.other_Check_the_status_of_a_car));

                final EditText et_carIdInput = viewOperate.findViewById(R.id.et_carIdInput);
                et_carIdInput.setVisibility(View.VISIBLE);
                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String strCarId = et_carIdInput.getText().toString().trim();// 取小车id
                        if (!TextUtils.isEmpty(strCarId)){

                            methodCheckCarStatus(Integer.parseInt(strCarId));

                        }else {
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Please_enter_the_id_of_the_car));
                        }
                    }
                });

                break;

            case R.id.tv_checkPodStatus:// 查看pod信息 need

                setDialogView(getResources().getString(R.string.other_Check_the_POD_information));

                final EditText et_podIdInput = viewOperate.findViewById(R.id.et_podIdInput);
                et_podIdInput.setVisibility(View.VISIBLE);
                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String strPodId = et_podIdInput.getText().toString().trim();// 取pod的id
                        if (!TextUtils.isEmpty(strPodId)){
                            methodCheckPodStatus(Integer.parseInt(strPodId));
                        }else {
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Please_enter_the_pod_id));
                        }
                    }
                });


                break;

            case R.id.tv_checkAddrStatus:// 查看地址状态 need

                setDialogView(getResources().getString(R.string.other_Check_address_status));

                final EditText et_addrInput = viewOperate.findViewById(R.id.et_addrInput);
                et_addrInput.setVisibility(View.VISIBLE);
                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String strAddr = et_addrInput.getText().toString().trim();// 取地址
                        if (!TextUtils.isEmpty(strAddr)){
                            methodCheckAddrStatus(Integer.parseInt(strAddr));
                        }else {
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Please_enter_the_address));
                        }
                    }
                });


                break;

                /*
            case R.id.tv_resendOrder:// 重发任务（急停重发）

                setDialogView("急停重发");

                final EditText et_resendOrder = viewOperate.findViewById(R.id.et_carIdInput);
                et_resendOrder.setVisibility(View.VISIBLE);

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String strCarId = et_resendOrder.getText().toString().trim();// 取小车id
                        if (!TextUtils.isEmpty(strCarId)){

                            new AlertDialog.Builder(getContext())
                                    .setMessage("急停重发？（请谨慎操作！！！）")
                                    .setPositiveButton("否", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .setNegativeButton("是", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            methodResendOrder(Integer.parseInt(strCarId));
                                            dialog.dismiss();
                                        }
                                    })
                                    .create().show();

                        }else {
                            ToastUtil.showToast(getContext(), "请输入小车的id");
                        }
                    }
                });

                break;
                */

            case R.id.btn_robotOffline:// 下线某小车 need

                setDialogView(getResources().getString(R.string.other_Offline_agv));

                final EditText et_robotOffline = viewOperate.findViewById(R.id.et_carIdInput);
                et_robotOffline.setVisibility(View.VISIBLE);

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String strCarId = et_robotOffline.getText().toString().trim();// 取小车id
                        if (!TextUtils.isEmpty(strCarId)){

                            new AlertDialog.Builder(getContext())
                                    .setMessage(getResources().getString(R.string.other_Careful_operation_Confirm_the_offline_AGV))
                                    .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            methodOffline(Integer.parseInt(strCarId));
                                            dialog.dismiss();
                                        }
                                    })
                                    .create().show();

                        }else {
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Please_enter_the_id_of_the_car));
                        }
                    }
                });

                break;

            case R.id.btn_drivePod:// 驱动pod去目标点位（可用存储区） need
                setDialogView(getResources().getString(R.string.other_Drive_the_pod_to_the_target_point));

                final EditText et_podIdInput_drivePod = viewOperate.findViewById(R.id.et_podIdInput);
                final EditText et_addrInput_drivePod = viewOperate.findViewById(R.id.et_addrInput);

                et_podIdInput_drivePod.setVisibility(View.VISIBLE);// pod的id
                et_addrInput_drivePod.setVisibility(View.VISIBLE);// 目标点位

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String strPodId = et_podIdInput_drivePod.getText().toString().trim();// 取pod的id
                        final String strAddr = et_addrInput_drivePod.getText().toString().trim();// 取目标点位

                        if (!TextUtils.isEmpty(strPodId) && !TextUtils.isEmpty(strAddr)){

                            // 非存储区的货架不让调用该接口
                            if ((Constants.unStoragePodsList.size() != 0) &&
                                    Constants.unStoragePodsList.contains(Integer.parseInt(strPodId))){
//                                ToastUtil.showToast(getContext(), "输入货架必须为存储区的货架，请重新输入");
                                return;
                            }else{
                                new AlertDialog.Builder(getContext())
                                        .setMessage(getResources().getString(R.string.other_Drive_pod) + strPodId + getResources().getString(R.string.other_to_target) + strAddr + getResources().getString(R.string.other_Please_operate_carefully))
                                        .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                            }
                                        })
                                        .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                methodDrivePod(strPodId, strAddr);
                                                dialog.dismiss();
                                            }
                                        })
                                        .create().show();
                            }

                        }else {
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Pod_cannot_be_empty_or_target_point_cannot_be_empty));
                        }
                    }
                });

                break;

            case R.id.btn_updatePodStatus:// 更新pod地址 need
                setDialogView(getResources().getString(R.string.other_Update_the_pod_address));

                final EditText et_podIdInput_updatePod = viewOperate.findViewById(R.id.et_podIdInput);
                final EditText et_addrInput_updatePod = viewOperate.findViewById(R.id.et_addrInput);

                et_podIdInput_updatePod.setVisibility(View.VISIBLE);// pod的id
                et_addrInput_updatePod.setVisibility(View.VISIBLE);// 更新地址
                
                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String strPodId = et_podIdInput_updatePod.getText().toString().trim();// 取pod的id
                        final String strAddr = et_addrInput_updatePod.getText().toString().trim();// 取更新地址

                        if (!TextUtils.isEmpty(strPodId) && !TextUtils.isEmpty(strAddr)){
                            new AlertDialog.Builder(getContext())
                                    .setMessage(getResources().getString(R.string.other_update_pod) + strPodId + getResources().getString(R.string.other_to_target_address) + strAddr + getResources().getString(R.string.other_Please_operate_carefully))
                                    .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            methodUpdatePodStatus(strPodId, strAddr);
                                            dialog.dismiss();
                                        }
                                    })
                                    .create().show();
                        }else {
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Pod_cannot_be_empty_or_update_addres_cannot_be_empty));
                        }
                    }
                });

                break;

            case R.id.btn_releasePodStatus:// 释放pod状态 need

                setDialogView(getResources().getString(R.string.other_Release_pod_status));

                final EditText et_podIdInput_releasePod = viewOperate.findViewById(R.id.et_podIdInput);
                et_podIdInput_releasePod.setVisibility(View.VISIBLE);// pod的id

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String strPodId = et_podIdInput_releasePod.getText().toString().trim();// 取pod的id

                        if (!TextUtils.isEmpty(strPodId)){
                            new AlertDialog.Builder(getContext())
                                    .setIcon(R.mipmap.app_icon)
                                    .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                                    .setMessage(getResources().getString(R.string.other_Determine_the_release_pod_status))
                                    .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            methodReleasePodStatus(strPodId);
                                        }
                                    }).create().show();
                        }else {
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Please_enter_the_pod_id));
                        }
                    }
                });
                break;

            case R.id.btn_updateAddrState:// 更新地址状态 need
                setDialogView(getResources().getString(R.string.other_Update_address_state));

                final EditText et_podIdInput_updateAddr = viewOperate.findViewById(R.id.et_addrInput);
                et_podIdInput_updateAddr.setVisibility(View.VISIBLE);// 地址坐标

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String strAddrId = et_podIdInput_updateAddr.getText().toString().trim();// 取地址

                        if (!TextUtils.isEmpty(strAddrId)){
                            new AlertDialog.Builder(getContext())
                                    .setIcon(R.mipmap.app_icon)
                                    .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                                    .setMessage(getResources().getString(R.string.other_Determine_the_update_address_status))
                                    .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            methodUpdateAddrState(strAddrId);
                                        }
                                    }).create().show();
                        }else {
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Address_input_cannot_be_empty));
                        }
                    }
                });
                break;

            case R.id.btn_robot2Charge:// 下发充电任务
                setDialogView("下发充电任务");

                final EditText et_robot2Charge = viewOperate.findViewById(R.id.et_carIdInput);
                et_robot2Charge.setVisibility(View.VISIBLE);// 小车id

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String strRobotId = et_robot2Charge.getText().toString().trim();// 取小车id
                        final int robotId = Integer.parseInt(strRobotId.trim());// 转为整数

                        if (!TextUtils.isEmpty(strRobotId)){
                            new AlertDialog.Builder(getContext())
                                    .setIcon(R.mipmap.app_icon)
                                    .setTitle("提示")
                                    .setMessage("给" + robotId + "号小车下发充电任务？")
                                    .setPositiveButton("否", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .setNegativeButton("是", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            if (robotId == 0){
                                                ToastUtil.showToast(getContext(), "小车id不能为0");
                                                return;
                                            }
                                            method2Charge(robotId);
                                        }
                                    }).create().show();
                        }else {
                            ToastUtil.showToast(getContext(), "地址输入不能为空");
                        }
                    }
                });
                break;

            case R.id.btn_autoDrivePod:// 自动分配POD回存储区

                setDialogView("自动分配pod回存储区");

                final EditText et_podIdInput_autoDrive = viewOperate.findViewById(R.id.et_podIdInput);
                et_podIdInput_autoDrive.setVisibility(View.VISIBLE);// pod的id

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String strPodId = et_podIdInput_autoDrive.getText().toString().trim();// 取pod的id

                        if (!TextUtils.isEmpty(strPodId)){
                            new AlertDialog.Builder(getContext())
                                    .setIcon(R.mipmap.app_icon)
                                    .setTitle("提示")
                                    .setMessage("自动分配POD：" + strPodId + "回存储区？")
                                    .setPositiveButton("否", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .setNegativeButton("是", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            methodAutoDrivePod(strPodId);
                                        }
                                    }).create().show();
                        }else {
                            ToastUtil.showToast(getContext(), "pod不能为空");
                        }
                    }
                });

                break;

            case R.id.btn_driveRobotCarryPod:// 驱动小车驮pod去某地
                setDialogView("驱动小车驮pod去某地");

                final EditText et_robotId = viewOperate.findViewById(R.id.et_carIdInput);// 小车id
                final EditText et_podId = viewOperate.findViewById(R.id.et_podIdInput);// pod的id
                final EditText et_addrId = viewOperate.findViewById(R.id.et_addrInput);// 地址

                et_robotId.setVisibility(View.VISIBLE);
                et_podId.setVisibility(View.VISIBLE);
                et_addrId.setVisibility(View.VISIBLE);

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        final String strRobot = et_robotId.getText().toString().trim();
                        final String strPod = et_podId.getText().toString().trim();
                        final String strAddr = et_addrId.getText().toString().trim();

                        if (!TextUtils.isEmpty(strRobot) &&
                                !TextUtils.isEmpty(strPod) &&
                                !TextUtils.isEmpty(strAddr)){
                            new AlertDialog.Builder(getContext())
                                    .setIcon(R.mipmap.app_icon)
                                    .setTitle("提示")
                                    .setMessage("驱动小车 " + strRobot + "驮pod " + strPod + "去地址 " + strAddr + "?")
                                    .setPositiveButton("否", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .setNegativeButton("是", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            methodDriveRobotCarryPod(strRobot, strPod, strAddr);
                                        }
                                    }).create().show();
                        }else {
                            ToastUtil.showToast(getContext(),"请输入小车id、pod或者地址");
                        }

                    }
                });

                break;

            case R.id.btn_driveRobot:// 驱动小车去某地

                setDialogView("驱动小车去某地");

                final EditText et_robotId_driveRobot = viewOperate.findViewById(R.id.et_carIdInput);// 小车id
                final EditText et_addrId_driveRobot = viewOperate.findViewById(R.id.et_addrInput);// 地址

                et_robotId_driveRobot.setVisibility(View.VISIBLE);
                et_addrId_driveRobot.setVisibility(View.VISIBLE);

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        final String strRobot = et_robotId_driveRobot.getText().toString().trim();
                        final String strAddr = et_addrId_driveRobot.getText().toString().trim();

                        if (!TextUtils.isEmpty(strRobot) &&
                                !TextUtils.isEmpty(strAddr)){
                            new AlertDialog.Builder(getContext())
                                    .setIcon(R.mipmap.app_icon)
                                    .setTitle("提示")
                                    .setMessage("驱动小车 " + strRobot + "去地址 " + strAddr + "?")
                                    .setPositiveButton("否", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .setNegativeButton("是", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            methodDriveRobot(strRobot, strAddr);
                                        }
                                    }).create().show();
                        }else {
                            ToastUtil.showToast(getContext(),"请输入小车id或者地址");
                        }
                    }
                });

                break;

            case R.id.btn_clearChargeError:// 清除充电桩故障 need
                setDialogView(getResources().getString(R.string.other_Clear_the_charging_pile_fault));

                final EditText et_other_chargerId = viewOperate.findViewById(R.id.et_other_chargerId);// 充电桩id
                final EditText et_other_chargerType = viewOperate.findViewById(R.id.et_other_chargerType);// 充电桩类型

                et_other_chargerId.setVisibility(View.VISIBLE);
                et_other_chargerType.setVisibility(View.VISIBLE);

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        final String str_charger_id = et_other_chargerId.getText().toString().trim();
                        final String str_charger_type = et_other_chargerType.getText().toString().trim();

                        if (!TextUtils.isEmpty(str_charger_id) &&
                                !TextUtils.isEmpty(str_charger_type)){

                            setUpConnectionFactory();// 设置连接
                            publishToAMPQ(Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_CHARGING_PILE_CLEAR_ERROR);
                            new AlertDialog.Builder(getContext())
                                    .setIcon(R.mipmap.app_icon)
                                    .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                                    .setMessage(getResources().getString(R.string.other_Dear_engineer_Confirm_to_clear_the_charging_pile_fault))
                                    .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            interruptThread(publishThread);
                                        }
                                    })
                                    .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            try {
                                                Map<String, Object> message = new HashMap<>();
                                                message.put("deviceType", Integer.parseInt(str_charger_type));
                                                message.put("deviceId", Integer.parseInt(str_charger_id));
                                                queue.putLast(message);// 发送消息到MQ
                                                ToastUtil.showToast(getContext(),getResources().getString(R.string.other_The_command_to_clear_the_charging_pile_fault_has_been_issued));

                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                            getActivity().getSupportFragmentManager().popBackStack();
                                            dialog.dismiss();

                                        }
                                    }).create().show();

                        }else {
                            ToastUtil.showToast(getContext(),getResources().getString(R.string.other_Please_enter_the_charging_pile_type_or_charging_id));
                        }
                    }
                });

                break;

            case R.id.btn_updatePodOnMap:// 将通道的空货架更新到地图上
                setDialogView("将通道的空货架更新到地图上");

                final EditText et_other_update_pod = viewOperate.findViewById(R.id.et_podIdInput);// 货架id
                final EditText et_other_update_address = viewOperate.findViewById(R.id.et_addrInput);// 地址码

                et_other_update_pod.setVisibility(View.VISIBLE);// pod的id
                et_other_update_address.setVisibility(View.VISIBLE);// 更新地址

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        final String strPod = et_other_update_pod.getText().toString().trim();
                        final String strAddress = et_other_update_address.getText().toString().trim();

                        if (!TextUtils.isEmpty(strPod) && !TextUtils.isEmpty(strAddress)){
                            setUpConnectionFactory();

                            publishToAMPQ(Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_CHANGING_POD_POSITION);
                            new AlertDialog.Builder(getContext())
                                    .setIcon(R.mipmap.app_icon)
                                    .setTitle("提示")
                                    .setMessage(
                                            "亲爱的工程师！确定将通道的空货架：" + strPod + "更新到目标地址："
                                                    + strAddress + "？"
                                    )
                                    .setPositiveButton("否", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            interruptThread(publishThread);
                                        }
                                    })
                                    .setNegativeButton("是", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            try {
                                                Map<String, Object> message = new HashMap<>();
                                                message.put("podCodeID", Integer.parseInt(strPod));
                                                message.put("addressCodeID", Integer.parseInt(strAddress));
                                                queue.putLast(message);// 发送消息到MQ
                                                ToastUtil.showToast(getContext(),"更新通道空货架到地图上的指令已发布");
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                            getActivity().getSupportFragmentManager().popBackStack();
                                            dialog.dismiss();

                                        }
                                    }).create().show();
                        }else {
                            ToastUtil.showToast(getContext(),"请输入货架ID或者目标地址码");
                        }

                    }
                });

                break;

            case R.id.btn_updateRobotStatus:// 更新小车可用 need

                setDialogView(getResources().getString(R.string.other_Update_the_car_available));
                final EditText et_car_id = viewOperate.findViewById(R.id.et_carIdInput);// 小车的id
                et_car_id.setVisibility(View.VISIBLE);

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String robotId = et_car_id.getText().toString().trim();
                        if (!TextUtils.isEmpty(robotId)){

                            new AlertDialog.Builder(getContext())
                                    .setIcon(R.mipmap.app_icon)
                                    .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                                    .setMessage(getResources().getString(R.string.other_Sure_to_update_the_car_available))
                                    .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            methodUpdateRobotStatus(robotId);
                                        }
                                    }).create().show();
                        }else {
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Please_enter_the_id_of_the_car));
                            return;
                        }
                    }
                });

                break;

            case R.id.btn_speedlogFinishTripByRobot:// speedlog特有接口

                setDialogView(getResources().getString(R.string.wcs_speedlog_finish_trip_by_robot));
                final EditText et_speedlogFinishTripByRobotId = viewOperate.findViewById(R.id.et_carIdInput);// 小车的id
                et_speedlogFinishTripByRobotId.setVisibility(View.VISIBLE);

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String robotId = et_speedlogFinishTripByRobotId.getText().toString().trim();
                        if (!TextUtils.isEmpty(robotId)){

                            new AlertDialog.Builder(getContext())
                                    .setIcon(R.mipmap.app_icon)
                                    .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                                    .setMessage("确定结束任务？")
                                    .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            method2FinishTripByRobotId(robotId);
                                        }
                                    })
                                    .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {

                                        }
                                    }).create().show();

                        }else {
                            ToastUtil.showToast(getContext(), "请输入AGV号码");
                            return;
                        }
                    }
                });

                break;

            case R.id.btn_speedlogFinishTripByPod:// speedlog特有接口

                setDialogView(getResources().getString(R.string.wcs_speedlog_finish_trip_by_pod));
                final EditText et_speedlogFinishTripByPod = viewOperate.findViewById(R.id.et_podIdInput);// 货架id
                et_speedlogFinishTripByPod.setVisibility(View.VISIBLE);

                viewOperate.findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String podId = et_speedlogFinishTripByPod.getText().toString().trim();
                        if (!TextUtils.isEmpty(podId)){

                            new AlertDialog.Builder(getContext())
                                    .setIcon(R.mipmap.app_icon)
                                    .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                                    .setMessage("确定结束任务？")
                                    .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            method2FinishTripByPodId(podId);
                                        }
                                    })
                                    .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {

                                        }
                                    }).create().show();

                        }else {
                            ToastUtil.showToast(getContext(), "请输入POD号码");
                            return;
                        }
                    }
                });

                break;
        }

    }

    /**
     * speedlog中通过输入POD号码来结束任务
     * @param podId
     */
    private void method2FinishTripByPodId(String podId) {

        ProgressBarUtil.showProgressBar(getContext(), "结束任务......");
        String url = rootAddress + getResources().getString(R.string.url_speedlog_finishTripByRobot)
                + "sectionId=" + sectionId + "&podId=" + podId;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {

                        ProgressBarUtil.dissmissProgressBar();
                        try{

                            String result = response.optString("result");

                            if ("success".equals(result)){
                                ToastUtil.showToast(getContext(), "结束任务成功");
                            }else if("failure".equals(result)){
                                ToastUtil.showToast(getContext(), "结束任务失败：" + response.optString("msg"));
                            }
                        }catch (Exception e){
                            ToastUtil.showToast(getContext(), "解析异常：" + e.getMessage());
                        }

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ProgressBarUtil.dissmissProgressBar();
                        ToastUtil.showToast(getContext(), "结束任务异常：" + error.getMessage());
                        return;
                    }
                });

        requestQueue.add(request);
    }

    /**
     * speedlog中通过输入AGV号码来结束任务
     * @param robotId
     */
    private void method2FinishTripByRobotId(String robotId) {

        ProgressBarUtil.showProgressBar(getContext(), "结束任务......");
        String url = rootAddress + getResources().getString(R.string.url_speedlog_finishTripByRobot)
                + "sectionId=" + sectionId + "&robotId=" + robotId;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {

                        ProgressBarUtil.dissmissProgressBar();
                        try{

                            String result = response.optString("result");

                            if ("success".equals(result)){
                                ToastUtil.showToast(getContext(), "结束任务成功");
                            }else if("failure".equals(result)){
                                ToastUtil.showToast(getContext(), "结束任务失败：" + response.optString("msg"));
                            }
                        }catch (Exception e){
                            ToastUtil.showToast(getContext(), "解析异常：" + e.getMessage());
                        }

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ProgressBarUtil.dissmissProgressBar();
                        ToastUtil.showToast(getContext(), "结束任务异常：" + error.getMessage());
                        return;
                    }
                });

        requestQueue.add(request);

    }

    /**
     * 更新小车可用（status为1、available为true、cancelOrder为true）
     * @param robotId
     * need
     */
    private void methodUpdateRobotStatus(final String robotId) {

        ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.other_Update_the_car_available), getResources().getColor(R.color.colorAccent));

        String status = "1";
        boolean available = true;
        boolean cancelOrder = true;

//        String url = rootAddress + getResources().getString(R.string.url_updateRobotStatus)
//                + "sectionId=" + sectionId + "&robotId=" + robotId + "&status=" + status
//                + "&available=" + available + "&cancelOrder=" + cancelOrder;

        String url = rootAddress + getResources().getString(R.string.url_updateRobotStatus)
                + "robotId=" + robotId + "&status=" + status
                + "&available=" + available + "&cancelOrder=" + cancelOrder;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
//                        if (response.contains("小车不在同一个section")){
//                            ToastUtil.showToast(getContext(), "小车不在同一个地图，请输入当前区域下小车 ID");
//                            return;
//                        }

                        ToastUtil.showToast(getContext(), getResources().getString(R.string.other_The_agv_has_been_updated_to_be_available));
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Update_agv_available_failed));
                    }
                });

        requestQueue.add(request);
    }

    /**
     * 驱动小车去某地
     * @param robotId
     * @param addrCodeId
     */
    private void methodDriveRobot(String robotId, String addrCodeId) {

        showDialog("加载中...");

        String url = rootAddress + getResources().getString(R.string.url_driveRobot)
                + "sectionId=" + sectionId + "&addrCodeId=" + addrCodeId
                + "&robotId=" + robotId;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        dissMissDialog();
                        dialog_operate.dismiss();
                        if (TextUtils.isEmpty(response)){
                            ToastUtil.showToast(getContext(), "请检查小车和地址输入是否正确");
                            return;
                        }else if ("ok".equals(response)){
                            ToastUtil.showToast(getContext(),"小车驱动成功");
                        }


                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        dissMissDialog();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), "驱动小车over");
                    }
                });

        requestQueue.add(request);

    }

    /**
     * 小车上移一格
     */
    private void call_carMoveUp(int robotId){
        String act = "up";
        String url = rootAddress + getResources().getString(R.string.url_carMoveOneGrid) + "robotId=" + robotId + "&act=" + act;
        StringRequest request = new StringRequest(url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
        requestQueue.add(request);//将StringRequest对象添加到RequestQueue里面

    }

    /**
     * 小车下移一格
     */
    private void call_carMoveDown(int robotId){
        String act = "down";
        String url = rootAddress + getResources().getString(R.string.url_carMoveOneGrid) + "robotId=" + robotId + "&act=" + act;
        StringRequest request = new StringRequest(url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
        requestQueue.add(request);//将StringRequest对象添加到RequestQueue里面

    }

    /**
     * 小车左移一格
     */
    private void call_carMoveLeft(int robotId){
        String act = "left";
        String url = rootAddress + getResources().getString(R.string.url_carMoveOneGrid) + "robotId=" + robotId + "&act=" + act;
        StringRequest request = new StringRequest(url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
        requestQueue.add(request);//将StringRequest对象添加到RequestQueue里面

    }

    /**
     * 小车右移一格
     */
    private void call_carMoveRight(int robotId){
        String act = "right";
        String url = rootAddress + getResources().getString(R.string.url_carMoveOneGrid) + "robotId=" + robotId + "&act=" + act;
        StringRequest request = new StringRequest(url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
        requestQueue.add(request);//将StringRequest对象添加到RequestQueue里面

    }

    /**
     * 驱动小车驮pod去某地
     * @param robotId
     * @param podId
     * @param addrCodeId
     */
    private void methodDriveRobotCarryPod(String robotId, String podId, String addrCodeId) {

        showDialog("加载中...");

        String url = rootAddress + getResources().getString(R.string.url_driveRobotCarryPod)
                + "sectionId=" + sectionId + "&podId=" + podId + "&addrCodeId=" + addrCodeId
                + "&robotId=" + robotId;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        dissMissDialog();
                        dialog_operate.dismiss();
                        if (TextUtils.isEmpty(response)){
                            ToastUtil.showToast(getContext(), "返回信息为空");
                            return;
                        }else {
                            ToastUtil.showToast(getContext(),"操作成功");
                        }


                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        dissMissDialog();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), "驱动小车驮pod_over");
                    }
                });

        requestQueue.add(request);

    }

    /**
     * 自动分配pod回存储区
     * @param podId
     */
    private void methodAutoDrivePod(String podId) {

        showDialog("加载中...");

        String url = rootAddress + getResources().getString(R.string.url_autoAssignAdnDrivePod)
                + "sectionId=" + sectionId + "&podId=" + podId;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        dissMissDialog();
                        dialog_operate.dismiss();
                        if (TextUtils.isEmpty(response)){
                            ToastUtil.showToast(getContext(), "请检查pod是否存在");
                            return;
                        }else {
                            ToastUtil.showToast(getContext(),"PodRun调度任务已生成");
                        }


                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        dissMissDialog();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), "自动分配pod回存储区over_e");
                    }
                });

        requestQueue.add(request);

    }

    /**
     * 小车下发充电任务
     * @param robotId
     */
    private void method2Charge(int robotId) {
        showDialog("加载中...");

        String url = rootAddress + getResources().getString(R.string.url_robot2Charge)
                + "robotId=" + robotId;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        dissMissDialog();
                        dialog_operate.dismiss();
                        if (TextUtils.isEmpty(response)){
                            ToastUtil.showToast(getContext(), "请检查小车是否存在");
                            return;
                        }else {
                            ToastUtil.showToast(getContext(),"下发充电任务成功");
                            ToastUtil.showToast(getContext(),response);
                        }


                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        dissMissDialog();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), "下发充电任务over_e");
                    }
                });

        requestQueue.add(request);
    }

    /**
     * 更新地址状态
     * @param addrCodeId
     * need
     */
    private void methodUpdateAddrState(String addrCodeId) {

        ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.other_loading), getResources().getColor(R.color.colorAccent));

        String url = rootAddress + getResources().getString(R.string.url_updateAddrState)
                + "sectionId=" + sectionId + "&addrCodeId=" + addrCodeId
                + "&status=" + "Available" + "&lockedby=" + 0;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        if (TextUtils.isEmpty(response)){
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Address_does_not_exist));
                            return;
                        }else {
                            ToastUtil.showToast(getContext(),getResources().getString(R.string.other_Update_address_state_of_success));
//                            ToastUtil.showToast(getContext(),response);
                        }


                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Update_address_status_failed) + error.getMessage());
                    }
                });

        requestQueue.add(request);
    }

    /**
     * 释放pod状态
     * @param podId
     * need
     */
    private void methodReleasePodStatus(String podId) {
        ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.other_loading), getResources().getColor(R.color.colorAccent));

        String url = rootAddress + getResources().getString(R.string.url_releasePodStatus)
                + "sectionId=" + sectionId + "&podId=" + podId + "&lockedBy=" + 0;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        if (TextUtils.isEmpty(response)){
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Check_if_the_pod_exists));
                            return;
                        }else {
                            ToastUtil.showToast(getContext(),getResources().getString(R.string.other_POD_status_release_successfully));
//                            ToastUtil.showToast(getContext(),response);
                        }


                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), getResources().getString(R.string.other_POD_status_release_failed) + error.getMessage());
                    }
                });

        requestQueue.add(request);
    }

    /**
     * 更新pod地址
     * @param podId
     * @param addrCodeId
     * need
     */
    private void methodUpdatePodStatus(String podId, String addrCodeId) {
        ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.other_loading), getResources().getColor(R.color.colorAccent));

        String url = rootAddress + getResources().getString(R.string.url_updatePodPos)
                + "sectionId=" + sectionId + "&podId=" + podId + "&addrCodeId=" + addrCodeId;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        if (TextUtils.isEmpty(response)){
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_POD_or_update_address_fill_out_is_not_correct));
                            return;
                        }else {
                            ToastUtil.showToast(getContext(),response);
                        }


                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Update_the_POD_address_failed) + error.getMessage());
                    }
                });

        requestQueue.add(request);
    }

    /**
     * 驱动pod去目标点位（可用存储区）
     * @param podId
     * @param addrCodeId
     * need
     */
    private void methodDrivePod(String podId, String addrCodeId) {
        ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.other_loading), getResources().getColor(R.color.colorAccent));

        String url = rootAddress + getResources().getString(R.string.url_drivePod)
                + "sectionId=" + sectionId + "&podId=" + podId + "&addrCodeId=" + addrCodeId;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        if (TextUtils.isEmpty(response)){
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_POD_or_target_address_fill_out_is_not_correct));
                            return;
                        }
                        Message message = handler.obtainMessage();
                        message.what = WHAT_DRIVE_POD;
                        message.obj = response;
                        handler.sendMessage(message);

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Failed_to_drive_pod_back_to_storage) + error.getMessage());
                    }
                });

        requestQueue.add(request);
    }

    /**
     * 下线某小车
     * @param robotId
     * need
     */
    private void methodOffline(final int robotId) {
        ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.other_loading), getResources().getColor(R.color.colorAccent));

        String url = rootAddress + getResources().getString(R.string.url_robotOffline)
                + "robotId=" + robotId;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        dissMissDialog();
                        dialog_operate.dismiss();
//                        if (response.contains("没有这辆小车")){
//                            ToastUtil.showToast(getContext(), response);
//                            return;
//                        }
                        Message message = handler.obtainMessage();
                        message.what = WHAT_OFFLINE;
                        message.obj = response;
                        handler.sendMessage(message);

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        dissMissDialog();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Offline_the_agv_failed) + error.getMessage());
                    }
                });

        requestQueue.add(request);
    }

    /**
     * 重发任务（也叫做急停重发）
     * @param robotId
     */
    /*
    private void methodResendOrder(final int robotId) {

        showDialog("加载中...");

//        String url = rootAddress + getResources().getString(R.string.url_resendOrder)
//                + "sectionId=" + sectionId + "&robotId=" + robotId;

        String url = rootAddress + getResources().getString(R.string.url_resendOrder)
                + "robotId=" + robotId;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        dissMissDialog();
                        dialog_operate.dismiss();

                        ToastUtil.showToast(getContext(), "重发任务成功");
//                        if (!TextUtils.isEmpty(response.toString())){
//
//                            String strRes = response.toString();
//                            if (strRes.contains("未注册")){
//                                ToastUtil.showToast(getContext(), response.toString());
//                                return;
//                            }else {
//                                Message message = handler.obtainMessage();
//                                message.what = WHAT_RESEND_ORDER;
//                                message.obj = response.toString();
//                                message.arg1 = robotId;
//                                handler.sendMessage(message);
//                            }
//
//                        }

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        dissMissDialog();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), "重发任务失败");
                    }
                });

        requestQueue.add(request);


    }
    */

    /**
     * 查看地址状态
     * @param addr
     * need
     */
    private void methodCheckAddrStatus(final int addr) {
        ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.other_loading), getResources().getColor(R.color.colorAccent));

        String url = rootAddress + getResources().getString(R.string.url_checkAddrStatus)
                + "sectionId=" + sectionId + "&addrCodeId=" + addr;

        LogUtil.e("url","url = " + url);

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        if (TextUtils.isEmpty(response)){
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Address_does_not_exist));
                            return;
                        }
                        Message message = handler.obtainMessage();
                        message.what = WHAT_ADDR_STATUS;
                        message.obj = response;
                        message.arg1 = addr;
                        handler.sendMessage(message);

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Check_the_address_state_failure) + error.getMessage());
                    }
                });

        requestQueue.add(request);
    }

    /**
     * 查看pod信息
     * @param podId
     * need
     */
    private void methodCheckPodStatus(final int podId) {

        ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.other_loading), getResources().getColor(R.color.colorAccent));

        String url = rootAddress + getResources().getString(R.string.url_checkPodStatus)
                + "sectionId=" + sectionId + "&podId=" + podId;

        LogUtil.e("url","url = " + url);

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        if (TextUtils.isEmpty(response.toString())){
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_POD_information_is_empty));
                            return;
                        }
                        Message message = handler.obtainMessage();
                        message.what = WHAT_POD_STATUS;
                        message.obj = response.toString();
                        message.arg1 = podId;
                        handler.sendMessage(message);

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Check_the_POD_information_failure) + error.getMessage());
                    }
                });

        requestQueue.add(request);

    }

    /**
     * 查看某小车的状态
     * @param robotId 小车的id
     * need
     */
    private void methodCheckCarStatus(final int robotId) {

        ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.other_loading), getResources().getColor(R.color.colorAccent));

        String url = rootAddress + getResources().getString(R.string.url_checkCarState)
                + "sectionId=" + sectionId + "&robotId=" + robotId;

        LogUtil.e("url","url = " + url);

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        if (TextUtils.isEmpty(response.toString())){
                            ToastUtil.showToast(getContext(), getResources().getString(R.string.other_This_agv_is_not_on_the_map));
                            return;
                        }
                        Message message = handler.obtainMessage();
                        message.what = WHAT_CAR_STATUS;
                        message.obj = response.toString();
                        message.arg1 = robotId;
                        handler.sendMessage(message);

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ProgressBarUtil.dissmissProgressBar();
                        dialog_operate.dismiss();
                        ToastUtil.showToast(getContext(), getResources().getString(R.string.other_Failed_to_check_agv_status));
                    }
        });

        requestQueue.add(request);

    }

    /**
     * 连接设置
     */
    private void setUpConnectionFactory() {
        factory.setHost(Constants.MQ_HOST);//主机地址
        factory.setPort(Constants.MQ_PORT);// 端口号
        factory.setUsername(Constants.MQ_USERNAME);// 用户名
        factory.setPassword(Constants.MQ_PASSWORD);// 密码
        factory.setAutomaticRecoveryEnabled(false);
    }

    // 创建BlockingDeque对象
    private BlockingDeque<Map<String, Object>> queue = new LinkedBlockingDeque<>();
    private void publishToAMPQ(final String exchange, final String routingKey) {
        publishThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Connection connection = factory.newConnection();
                        Channel ch = connection.createChannel();
                        ch.confirmSelect();
                        while (true) {
                            Map<String, Object> message = queue.takeFirst();
                            try {
                                ch.basicPublish(exchange, routingKey, null, serialize((Serializable)message));
                                ch.waitForConfirmsOrDie();

                            } catch (Exception e) {
                                queue.putFirst(message);
                                throw e;
                            }

                        }
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        LogUtil.d("TAG_Publish", "Connection broken: " + e.getClass().getName());
                        try {
                            Thread.sleep(5000); //sleep and then try again
                        } catch (InterruptedException e1) {
                            break;

                        }

                    }

                }

            }

        });

        publishThread.start();
    }

    /**
     * 将map对象转换为byte[]
     * @param obj
     * @return
     */
    private byte[] serialize(Serializable obj) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(512);
        serialize(obj, byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * 写对象到输出流
     * @param obj
     * @param outputStream
     */
    private void serialize(Serializable obj, OutputStream outputStream) {
        if(outputStream == null) {
            throw new IllegalArgumentException("The OutputStream must not be null");
        } else {
            ObjectOutputStream out = null;
            try {
                out = new ObjectOutputStream(outputStream);
                out.writeObject(obj);
            } catch (IOException var11) {
                var11.printStackTrace();
            } finally {
                try {
                    if(out != null) {
                        out.close();
                    }
                } catch (IOException var10) {
                    var10.printStackTrace();
                }

            }

        }
    }

    /**
     * 中断线程
     * @param thread
     */
    private void interruptThread(Thread thread) {
        if(thread != null){
            thread.interrupt();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // 移除所有的回调和消息，防止Handler泄露
        handler.removeCallbacksAndMessages(null);
        if(requestQueue != null){
            requestQueue.stop();// 停止缓存和网络调度程序
        }
        interruptThread(publishThread);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden){
            setTitleBar();
        }
    }
}
