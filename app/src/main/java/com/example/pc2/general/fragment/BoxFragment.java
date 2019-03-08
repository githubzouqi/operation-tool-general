package com.example.pc2.general.fragment;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.pc2.general.R;
import com.example.pc2.general.constant.Constants;
import com.example.pc2.general.entity.CarCurrentPathEntity;
import com.example.pc2.general.entity.ChargingPileEntity;
import com.example.pc2.general.entity.ChargingTaskEntity;
import com.example.pc2.general.entity.ErrorCharging;
import com.example.pc2.general.entity.NoMoveTimeoutEntity;
import com.example.pc2.general.entity.PodEntity;
import com.example.pc2.general.entity.RobotCloseConnEntity;
import com.example.pc2.general.entity.RobotEntity;
import com.example.pc2.general.entity.RobotErrorEntity;
import com.example.pc2.general.entity.StorageEntity;
import com.example.pc2.general.entity.WorkStationEntity;
import com.example.pc2.general.utils.DensityUtil;
import com.example.pc2.general.utils.FileUtil;
import com.example.pc2.general.utils.LogUtil;
import com.example.pc2.general.utils.ProgressBarUtil;
import com.example.pc2.general.utils.ScreenUtil;
import com.example.pc2.general.utils.ToastUtil;
import com.example.pc2.general.view.BoxView;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * A simple {@link Fragment} subclass.
 * 格子图主界面
 */
public class BoxFragment extends BaseFragment {

    private static final int WHATMAP = 0;// 地图和pod的初始化
    private static final int WHATCAR = 1;//小车
    private static final int WHAT_CAR_ROUTE = 2;// 小车路径信息查看
    private static final int WHATSTORAGEMAP = 3;// 仓库地图初始化
    private static final int WHAT_CAR_ROUTE_SHOW = 4;// 地图显示小车路径信息
    private static final int WHAT_SHOW_POD = 5;// 显示pod状态
    private static final int WHAT_SHOW_ALL_CAR_CURRENT_PATH = 6;// 显示小车当前路径
    private static final int WHAT_CHARGING_TASK = 7;// 所有充电桩的充电任务
    private static final int WHAT_CLEAR_DATA = 8;// 重选仓库地图时清空原有的数据
    private static final int WHAT_CANCEL_CAR_ALL_CURRENT_PATH = 9;// 取消小车当前路径显示
    private static final int WHAT_CLOSE_CONNECTION = 10;// 关闭连接
    private static final int WHAT_CLEAR_CHARGE_DATA =0x10;// 清空充电任务数据
    private static final int WHAT_ROBOT_ERROR = 0x11;// 小车错误反馈
    private static final int WHAT_ROBOT_NOMOVE_TIMEOUT = 0x12;// 小车位置不改变超时
    private static final int WHAT_REFRESH_ERROR_DATA = 0x13;// 刷新错误反馈数据
    private static final int WHAT_ERROR_CLOSE_CONNECTION = 0x14;// 小车断开连接
    private static final int WHAT_ROBOT_CHARGING_ERROR = 0X15;// 小车充电故障
    private static final int WHAT_CREATE_EMPTY_ROUTE = 0x16;// 生成空车路径
    private static final long DELAY_TIME = 1500;// 延时2秒自动开启小车监控
    private static final int WHAT_CAR_DATA = 0x17;// 开始小车监控
    private static final int WHAT_AUTO_DRAW_MAP = 0x22;// 延迟消息绘制地图
    private static final int WHAT_RELEASE_POD = 0X23;// 延迟消息不间断释放pod
    private static final long RELEASE_POD_TIME = 5000;// 延迟消息不间断释放pod延迟时间
    private static final int WHAT_REBOOT_RESEND = 0X25;// 重启小车，重发任务

    private ConnectionFactory factory = new ConnectionFactory();// 声明ConnectionFactory对象

    @BindView(R.id.boxView)BoxView boxView;// 自定义方格图
    @BindView(R.id.tv_hint)TextView tv_hint;// 地图界面用户信息提示
    @BindView(R.id.btn_drawing)Button btn_drawing;// 进行地图的绘制
    @BindView(R.id.linear_zoomOutIn)LinearLayout linear_zoomOutIn;// 放大、缩小图标所在的线性布局
    @BindView(R.id.linear_map_introduction) LinearLayout linear_map_introduction;// 地图说明图标所在的线性布局
    @BindView(R.id.linear_map_info) LinearLayout linear_map_info;// 地图信息图标所在的线性布局
    @BindView(R.id.linear_map_reset) LinearLayout linear_map_reset;// 地图复位
    @BindView(R.id.linear_map_drawAgain) LinearLayout linear_map_drawAgain;// 地图重选
    @BindView(R.id.linear_map_carLockUnLock) LinearLayout linear_map_carLockUnLock;// 锁格、解锁
    @BindView(R.id.linear_map_carBatteryInfo) LinearLayout linear_map_carBatteryInfo;// 小车电量信息
    @BindView(R.id.linear_map_wcs) LinearLayout linear_map_wcs;// WCS部分（源汇版本 更名为：其他）
    @BindView(R.id.linear_map_rcs) LinearLayout linear_map_rcs;// RCS部分
    @BindView(R.id.linear_operate) LinearLayout linear_operate;// 地图绘制操作的线性布局
    @BindView(R.id.rl_mapView)RelativeLayout rl_mapView;
    @BindView(R.id.btn_connect_rabbitmq) Button btn_connect_rabbitmq;// 连接RabbitMQ按钮
    @BindView(R.id.btn_init_data) Button btn_startCarMonitor;// 开始小车的监控
    @BindView(R.id.tv_showAllCarCurrentPath) TextView tv_showAllCarCurrentPath;// 显示小车锁格、未锁格路径
    @BindView(R.id.tv_cancelAllCarCurrentPath) TextView tv_cancelAllCarCurrentPath;// 取消小车锁格、未锁格路径显示
    @BindView(R.id.view) View view_border;// 显示边线
    @BindView(R.id.linear_lock_unlock) LinearLayout linear_lock_unlock;
    @BindView(R.id.linear_error_tip) LinearLayout linear_error_tip;// 错误反馈提示

    private TextView tv_routeInfo;// 空车路径信息显示

    private int row = 0, column = 0;// 动态设置的行数和列数

    private RequestQueue requestQueue;// volley请求队列

    private ProgressDialog pDialog;

    private List<RobotEntity> carList = new ArrayList<>();// 小车信息集合
    private List<PodEntity> podList = new ArrayList<>();// pod信息集合
    private List<WorkStationEntity> workStationEntityList = new ArrayList<>();// 工作站实体集合
    private List<Long> unWalkedList = new ArrayList<>();// 不可走区域坐标集合
    private List<Integer> workStackList = new ArrayList<>();// 停止点集合，可以用来标识工作栈
    private List<List<Long>> rotateList = new ArrayList<>();// 旋转区坐标集
    private List<Long> storageList = new ArrayList<>();// 存储区坐标集
    private Map<String, String> map_work_site_uuid =  new HashMap<>();
    private List<StorageEntity> storageEntityList = new ArrayList<>();// 创建集合保存所有仓库所有的信息
    private List<ChargingPileEntity> chargingPileList = new ArrayList<>();// 声明集合保存充电桩的数据
    private List<CarCurrentPathEntity> carCurrentPathEntityList = new ArrayList<>();// 小车当前路径数据（锁格和未锁格）
    private List<Object> allLockedAreaList = new ArrayList<>();// 地图上所有的锁格区域地标
    private List<ChargingTaskEntity> chargingTaskEntityList = new ArrayList<>();// 充电桩的充电任务数据
    private List<RobotErrorEntity> robotErrorEntityList = new ArrayList<>();// 小车错误信息
    private List<NoMoveTimeoutEntity> noMoveTimeoutEntityList = new ArrayList<>();// 小车位置不改变超时
    private List<RobotCloseConnEntity> robotCloseConnEntityList = new ArrayList<>();// 小车断开连接
    private List<ErrorCharging> errorChargings = new ArrayList<>();// 充电桩故障

    private List<Long> car_route_list;// 某辆小车的路径
    private Map<Long, List<Long>> carRouteMap = new HashMap<>();;// 小车的路径map集

    private boolean bl_initData = false;// 是否初始化，false表示未进行过初始化
    private boolean bl_initStorageMap = false;// 仓库和地图是否初始化，false表示未进行初始化
    private boolean bl_isShowCarPath = false;// 小车当前路径是否显示，false表示没有显示
    private boolean bl_isSelectLockUnLock = false;// false表示没有选择解锁或者锁格

    // 点击小车弹框
    private View pop_view_carAll;
    private PopupWindow window_carAll;
    // 操作小车
    private View pop_view_car;
    private PopupWindow window_car;
    // pod
    private View pop_view_pod;
    private PopupWindow window_pod;
    // 地图格子(没有小车和pod)
    private View pop_view_box;
    private PopupWindow window_box;
    // 地图说明
    private View pop_view_mapIntroduction;
    private PopupWindow window_mapIntroduction;
    // 地图信息
    private View pop_view_mapInfo;
    private PopupWindow window_mapInfo;
    // 仓库初始化
    private View pop_view_storageInit;
    private PopupWindow window_storageInit;

    private Thread threadMapData = null;// 地图数据消费线程
    private Thread publishThread = null;// 发布消息消费线程
    private Thread subscribeThread_storageMap = null;// 仓库地图消费者线程
    private Thread threadShowAllCarCurrentPath = null;// 所有小车当前路径消费线程
    private Thread subscribeThread = null;// 小车实时数据消费者线程
    private Thread threadChargingTask = null;// 充电桩充电任务消费线程
    private Thread threadProblemFeedback = null;// 小车错误反馈消费线程
    private Thread threadNoMoveTimeout = null;// 小车位置不改变超时线程
    private Thread threadErrorCloseConnection = null;// 小车断开连接线程
    private Thread threadChargingError = null;// 小车充电故障线程

    private Connection connection_map;// 初始化地图数据的连接
    private Connection connection_storageMap;// 初始化仓库地图的连接（消费消息，获取数据）

    private Connection connection_car;// 初始化小车数据的连接
    private Connection connection_showAllCarCurrentPath;// 小车当前路径的连接
    private Connection connection_chargingTask;// 充电桩充电任务的连接
    private Connection connection_problemFeedback;//小车错误反馈的连接（扫不到pod故障）
    private Connection connection_noMoveTimeout;// 小车位置不改变超时
    private Connection connection_errorCloseConnection;// 小车断开连接
    private Connection connection_chargingError;// 小车充电故障

    private int boxSizeChange;// 放大或者缩小地图时需要的参数

    private TextView tv_carPath;// 小车当前路径信息
    private String str_carPath;// 小车的路径信息
    private ScreenUtil screenUtil;
    private int pop_height = 0;

    private RelativeLayout rl_storage;// 仓库选择布局
    private RelativeLayout rl_section_map;// 地图选择布局
    private TextView tv_storageName;// 仓库名称
    private TextView tv_mapName;// 地图名称

    private long sectionRcsId = -1;// 绘制仓库下某个地图时需要用的变量

    private int carRoutePos = 0;
    private List<RobotEntity> carRouteList = null;

    private int lockArea = 0;// 锁格坐标
    private int unLockArea = 0;// 解锁坐标

//    private CharSequence items[] = {"锁格", "解锁"};

    private String strErrorContent = "";// 错误提示内容
    private TextView tv_error_content;// 错误提示内容设置控件

    private Timer timer_clear_charge_data;// 声明Timer对象，用来执行定时任务
    private Timer timer_refresh_error_data;
    private TimerTask task_clear_charge_data = null;// 定时清空充电任务数据的任务
    private TimerTask task_refresh_error_data = null;// 定时刷新错误反馈数据

    private Vibrator vibrator = null;// 震动对象

    private List<Long> emptyRouteList = new ArrayList<>();// 空车路径信息
    private boolean bl_isDriveEmpty = false;// 默认空车路径未生成

    private View view_options = null;// 点击主页面地图上的格子，弹出的对话框的view
    private TextView tv_options_lock_circle, tv_options_unlock_circle, tv_add_point_area, tv_remove_point_area, tv_reboot_resend, tv_agv_action, tv_scram_resend;
    private AlertDialog dialog_options = null;// 点击主页面地图上的格子，弹出的对话框
    private List<Integer> nine_lock_unlock = new ArrayList<>();// 九宫格的地址点位集合

    private boolean STOP_RELEASE_POD = true;// 中断 不间断释放pod功能的开关，true表示中断
    private String strWorkSiteUUID = "";// 点击地图上对应工作站的时候，保存该工作站的UUID，释放pod需要使用该参数

    private int reboot_robotId = -1;// 是否从mq上获取小车的锁格路径信息标识。不为-1就获取小车的锁格路径信息
    private List<Integer> reboot_lock_list = new ArrayList<>();// 保存小车重启后原来锁格的路径信息

    // 处理handler发送的消息，然后进行操作（在主线程）
    @SuppressLint("HandlerLeak")
    private Handler inComingMessageHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case WHATMAP:// 初始化地图数据
                    // 地图绘制完成后，中断地图消费线程
                    interruptThread(threadMapData);

                    byte[] bodyMap = (byte[]) msg.obj;
                    Map<String, Object> mapMap = (Map<String, Object>) toObject(bodyMap);

                    try {
                        FileUtil.createFileWithByte(mapMap.toString().getBytes("utf-8"), "地图返回数据.doc");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }

                    if(mapMap != null && mapMap.size() != 0){
                        row = Integer.parseInt(mapMap.get("row").toString());// 设置行数
                        column = Integer.parseInt(mapMap.get("column").toString());// 设置列数

                        Constants.MAP_ROWS = row;
                        Constants.MAP_COLUMNS = column;

                        if(mapMap.containsKey("sectionUUID")){// 如果map中存在该key，那么获取该key所对应的value
                            sectionId = String.valueOf(mapMap.get("sectionUUID"));
                            Constants.SECTIONID = sectionId;
                            LogUtil.e("SECTIONID",""+sectionId);
                        }
                        setPodData(mapMap);// 设置pod数据（存储区的pod数据）
                        setUnWalkedCellData(mapMap);// 设置不可走区域的坐标数据
                        setStorageData(mapMap);// 设置存储区的坐标数据
                        setWorkStackData(mapMap);// 设置工作栈相关数据，工作栈在停止点的上方，或者下方，或者左方，或者右方
                        setWorkSiteUUID(mapMap);// 设置工作站的uuid。key对应的是停止点的坐标，值就是uuid
                        setChargerData(mapMap);// 设置充电桩的数据
                        setUnStoragePodsData(mapMap);// 设置非存储区货架数据（非存储区的pod数据）
                        setWorkStationAngle(mapMap);// 设置工作站朝向停止点的角度
                    }

                    if((row != 0) && (column != 0)){
                        gone(tv_hint);
                        visibile(boxView);
                        visibile(linear_map_introduction);// 显示地图说明图标
                        visibile(linear_map_info);// 显示地图信息图标
                        visibile(linear_zoomOutIn);// 显示放大和缩小图标
                        visibile(linear_map_reset);// 显示地图重置项
                        visibile(linear_map_drawAgain);// 显示地图重选项
//                        linear_map_carLockUnLock.setVisibility(View.VISIBLE);
                        visibile(linear_map_carBatteryInfo);// 显示小车电量项
                        visibile(linear_map_wcs);// 显示其他项（原来是 wcs部分，现在更名为了 其他）
//                        visibile(linear_map_rcs);
                        // 绘制地图
                        boxView.setRowAndColumn(row, column, boxSizeChange);
                        ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_map_completion));// 提示地图绘制完成
                        btn_drawing.setTextColor(Color.GREEN);
                        bl_initData = true;

                        // 地图上绘制货架、不可走区域、工作栈、旋转区、存储区和充电桩
                        boxView.setPodData(podList, unWalkedList, workStackList,
                                rotateList, storageList, map_work_site_uuid, chargingPileList, workStationEntityList);

                        inComingMessageHandler.sendEmptyMessageDelayed(WHAT_CAR_DATA, DELAY_TIME);// 发送延时消息，自动开启小车监控
                    }

                    break;
                case WHAT_CAR_DATA:// 初始化小车数据
                    initCarData();
                    break;
                case WHATCAR:// 初始化小车数据
                    byte[] body = (byte[]) msg.obj;
                    Map<String, Object> mapCar = (Map<String, Object>) toObject(body);
//                    try {
//                        FileUtil.createFileWithByte(mapCar.toString().getBytes("utf-8"),"Mushiny小车数据文件.doc");
//                        ToastUtil.showToast(getContext(), "文件生成成功");
//                    } catch (UnsupportedEncodingException e) {
//                        e.printStackTrace();
//                    }
                    if(mapCar != null && mapCar.size() != 0){
                        setCarAndPodData(mapCar);// 设置小车的数据
                    }
                    if(carList != null && carList.size() != 0){
                        boxView.setCarAndPodData(carList, podList);// 小车开始绘制并实时更新小车的路径信息
                    }
                    break;
                    /*
                case WHAT_CAR_ROUTE:// 小车路径信息查看
                    if(!TextUtils.isEmpty(str_carPath)){
                        tv_carPath.setText(getResources().getString(R.string.box_car_path) + ":" + str_carPath);
                    }
                    break;
                    */
                case WHATSTORAGEMAP:// 初始化仓库地图
                    ProgressBarUtil.dissmissProgressBar();

                    byte[] bodyStorageMap = (byte[]) msg.obj;
                    Map<String, Object> mapStorageMap = (Map<String, Object>) toObject(bodyStorageMap);
                    JSONObject objectStorageMap = new JSONObject(mapStorageMap);// 将map转为JsonObject结构数据
                    if(objectStorageMap != null){
                        parseStorageMapData(objectStorageMap);// 解析仓库地图数据
                    }
                    // 仓库初始化完成，中断消费线程
                    if(subscribeThread_storageMap != null){
                        subscribeThread_storageMap.interrupt();
                    }

                    new AlertDialog.Builder(getContext())
                            .setIcon(R.mipmap.app_icon)
                            .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                            .setMessage(getResources().getString(R.string.boxfragment_rabbitmq_connection_successful))
                            .setPositiveButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    showPopAboutInitStorage();// 弹框进行仓库地图的初始化
                                }
                            }).create().show();

//                    showPopAboutInitStorage();// 弹框进行仓库地图的初始化

                    break;
                    /*
                case WHAT_CAR_ROUTE_SHOW:// 小车全路径显示（一辆或者多辆）
                    carRouteMap.put(carRouteList.get(carRoutePos).getRobotID(), car_route_list);// 保存小车的路径信息
                    boxView.setCarRouteData(carRouteMap);// 重绘，显示小车的路径

                    // 将该小车的路径显示标志置为true，表示小车的路径已经显示
                    RobotEntity entity = new RobotEntity();
                    entity.setRobotID(carRouteList.get(carRoutePos).getRobotID());
                    entity.setAddressCodeID(carRouteList.get(carRoutePos).getAddressCodeID());
                    entity.setCarRouteIsShow(true);
                    carList.remove(carRoutePos);// 移除对应位置的小车
                    carList.add(carRoutePos, entity);// 在对应位置添加新的小车实体
                    break;
                    */
                case WHAT_SHOW_POD:// 显示pod状态
                    if(TextUtils.isEmpty(podName)){
                        ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_doesnot_have_a_pod));
                    }else{
                        call_releasePod();// 释放pod
                    }
                    break;
                case WHAT_SHOW_ALL_CAR_CURRENT_PATH:// 小车当前路径（实时显示）
                    dissMissDialog();
                    bl_isShowCarPath = true;
                    byte[] bodyShowAllCarCurrentPath = (byte[]) msg.obj;
                    Map<String, Object> mapShow = (Map<String, Object>) toObject(bodyShowAllCarCurrentPath);
                    try {
                        FileUtil.createFileWithByte(mapShow.toString().getBytes("utf-8"), "小车所有路径数据.doc");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    if(mapShow != null && mapShow.size() != 0){
                        setCarCurrentPath(mapShow);// 设置小车当前路径数据
                        boxView.setCarCurrentPath(carCurrentPathEntityList, allLockedAreaList, manualLockList, workSiteIndexList);// 绘制小车的当前路径区域（锁格和未锁格）
                    }
                    break;
                    /*
                case WHAT_CANCEL_CAR_ALL_CURRENT_PATH:// 取消小车当前路径显示
                    bl_isShowCarPath = false;
                    interruptThread(t_cancel_car_all_current_path);
                    carCurrentPathEntityList.clear();
                    boxView.setCarCurrentPath(null, null, null, null);
                    ToastUtil.showToast(getContext(), "小车当前路径已经取消");
                    break;

                case WHAT_CHARGING_TASK:// 充电桩的充电任务
                    byte[] bodyChargingTask = (byte[]) msg.obj;
                    Map<String, Object> mapCharge = (Map<String, Object>) toObject(bodyChargingTask);
//                    try {
//                        FileUtil.createFileWithByte(mapCharge.toString().getBytes("utf-8"), "充电任务数据.doc");
//                    } catch (UnsupportedEncodingException e) {
//                        e.printStackTrace();
//                    }
                    if(mapCharge != null && mapCharge.size() != 0){
                        setChargingTaskData(mapCharge);// 设置充电任务数据
                        boxView.setChargeData(chargingTaskEntityList, sectionId);

                    }
                    break;
                    */
                case WHAT_CLEAR_DATA:// 仓库地图重选，清空所有的数据
                    interruptThread(subscribeThread);
                    interruptThread(t_clear_all_data);
                    interruptThread(threadChargingTask);
                    interruptThread(threadProblemFeedback);
                    interruptThread(threadNoMoveTimeout);
                    interruptThread(threadErrorCloseConnection);
                    interruptThread(threadChargingError);
                    interruptThread(threadShowAllCarCurrentPath);
                    clearData();
                    publishToAMPQ(Constants.MQ_EXCHANGE_STORAGEMAP, Constants.MQ_ROUTINGKEY_STORAGEMAP_REQUEST);// publish消息给请求队列
                    selectStorageMap();
                    break;
                case WHAT_CLOSE_CONNECTION:// 关闭连接
                    interruptThread(threadCloseConnection);
                    carCurrentPathEntityList.clear();
                    allLockedAreaList.clear();
                    boxView.setCarCurrentPath(null, null, null, null);
                    break;
                    /*
                case WHAT_CLEAR_CHARGE_DATA:// 定时清空充电任务数据
                    boxView.setChargeData(null, sectionId);
                    break;
                    */
                case WHAT_ROBOT_ERROR:// 小车错误反馈
                    byte[] bodyProblemFeedback = (byte[]) msg.obj;
                    Map<String, Object> mapProblemFeedback = (Map<String, Object>) toObject(bodyProblemFeedback);
//                    try {
//                        FileUtil.createFileWithByte(mapProblemFeedback.toString().getBytes("utf-8"),"错误反馈信息.doc");
//                    } catch (UnsupportedEncodingException e) {
//                        e.printStackTrace();
//                    }
                    if (robotErrorEntityList.size() == 0){
                        if (vibrator != null){
                            vibrator.vibrate(new long[]{1000,2000}, 0);// 震动
                        }
                    }
                    setRobotErrorData(mapProblemFeedback);
                    visibile(linear_error_tip);
                    break;
                case WHAT_ROBOT_NOMOVE_TIMEOUT:// 小车位置不改变超时
                    byte[] bodyNoMoveTimeout = (byte[]) msg.obj;
                    Map<String, Object> mapNoMoveTimeout = (Map<String, Object>) toObject(bodyNoMoveTimeout);
//                    try {
//                        FileUtil.createFileWithByte(mapNoMoveTimeout.toString().getBytes("utf-8"),"位置不改变超时.doc");
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
                    if (noMoveTimeoutEntityList.size() == 0){
                        if (vibrator != null){
                            vibrator.vibrate(new long[]{1000,2000}, 0);// 震动
                        }
                    }
                    setTimeoutData(mapNoMoveTimeout);
                    visibile(linear_error_tip);
                    break;

                case WHAT_REFRESH_ERROR_DATA:// 刷新错误数据显示
                    strErrorContent = "";
                    getAndSetErrorContent();
                    break;
                case WHAT_ERROR_CLOSE_CONNECTION:// 小车断开连接
                    byte[] bodyErrorCloseConn = (byte[]) msg.obj;
                    Map<String, Object> mapErrorCloseConn = (Map<String, Object>) toObject(bodyErrorCloseConn);
                    if (robotCloseConnEntityList.size() == 0){
                        if (vibrator != null){
                            vibrator.vibrate(new long[]{1000,2000}, 0);// 震动
                        }
                    }
                    setErrorCloseConn(mapErrorCloseConn);
                    visibile(linear_error_tip);
                    break;

                case WHAT_ROBOT_CHARGING_ERROR:// 充电桩故障
                    byte[] bodyChargingError = (byte[]) msg.obj;
//                    JSONObject jbChargingError = (JSONObject) toObject(bodyChargingError);
                    Map<String, Object> mapChargingError = (Map<String, Object>) toObject(bodyChargingError);
                    // 生成文件
                    try {
                        FileUtil.createFileWithByte(mapChargingError.toString().getBytes("utf-8"),"小车充电状态返回数据文件.doc");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (mapChargingError.toString() != null){
                        setChargingErrorData(mapChargingError);// 设置充电故障数据
                    }

                    if (errorChargings.size() != 0){
//                        if (vibrator != null){
//                            vibrator.vibrate(new long[]{1000,2000}, 0);// 震动
//                        }
                        visibile(linear_error_tip);// 故障提示
                    }

                    break;

                case WHAT_AUTO_DRAW_MAP:// 延迟消息绘制地图

                    try {
                        Map<String, Object> message = new HashMap<>();
                        message.put("name", Constants.MQ_ROUTINGKEY_MAP_REQUEST);
                        message.put("requestTime", System.currentTimeMillis());// 系统当前时间
                        message.put("sectionID", sectionRcsId);// 根据该值来确定绘制仓库下的哪个地图
                        queue.putLast(message);// 发送消息到MQ
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    break;

                case WHAT_RELEASE_POD:// 不间断释放pod

                    releasePodForever();// 不间断释放pod

                    break;

                case WHAT_REBOOT_RESEND:// 重启重发，延迟1s执行

                    int robotId = msg.arg1;
                    methodRebootResend(robotId);

                    break;
            }
        }
    };

    /**
     * 不间断释放pod
     */
    private void releasePodForever() {

        // 开启了不间断释放pod功能
        if (!STOP_RELEASE_POD && workSiteUUIDList.size() != 0){
            for (int i = 0;i < workSiteUUIDList.size();i++){
                call_showPod(workSiteUUIDList.get(i));
            }
            inComingMessageHandler.sendEmptyMessageDelayed(WHAT_RELEASE_POD, RELEASE_POD_TIME);
        }

    }

    /**
     * 解析充电桩故障数据
     * @param chargingError
     */
    private void setChargingErrorData(Map<String, Object> chargingError) {
        try{

            /**
             * ｛number=2， sectionID=1, time=1527090817342, type=1, statusName=空闲, statusIndex=2｝
             */

            // 取值
            int number = Integer.parseInt(String.valueOf(chargingError.get("number")));
            int statusIndex = Integer.parseInt(String.valueOf(chargingError.get("statusIndex")));
            String statusName = String.valueOf(chargingError.get("statusName"));// 故障:4 ,空闲:2 ,充电:1
            String type = String.valueOf(chargingError.get("type"));// 充电桩类型
            long time = Long.parseLong(String.valueOf(chargingError.get("time")));

            if (4 == statusIndex){
                // 实体对象设值
                ErrorCharging entity = new ErrorCharging();
                entity.setNumber(number);
                entity.setStatusIndex(statusIndex);
                entity.setStatusName(statusName);
                entity.setType(type);
                entity.setTime(time);

                // list集合中保存实体对象
                if (errorChargings.size() == 0){
                    errorChargings.add(entity);
                }else{
                    boolean bl_isAdd = false;
                    int index = -1;

                    for (int i = 0;i < errorChargings.size();i++){
                        if (number == errorChargings.get(i).getNumber()){
                            bl_isAdd = true;
                            index = i;
                        }
                    }

                    // 集合中已经添加过该实体对象了
                    if (bl_isAdd && index != -1){
                        errorChargings.remove(index);
                        errorChargings.add(index, entity);
                    }else{
                        errorChargings.add(entity);
                    }
                }
            }


        }catch (Exception e){
            e.printStackTrace();
            ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_charging_failure_data_parsing_exceptions) + e.getMessage());
        }
    }

    /**
     * 设置小车断开连接数据
     * @param mapErrorCloseConn
     */
    private void setErrorCloseConn(Map<String, Object> mapErrorCloseConn) {
        try {
            // 取值
            int robotID = Integer.parseInt(String.valueOf(mapErrorCloseConn.get("robotID")));

            int port = -1;
            if (mapErrorCloseConn.containsKey("port")){
                port = Integer.parseInt(String.valueOf(mapErrorCloseConn.get("port")));
            }

            String ip = "";
            if (mapErrorCloseConn.containsKey("ip")){
                ip = String.valueOf(mapErrorCloseConn.get("ip"));
            }

            long time = Long.parseLong(String.valueOf(mapErrorCloseConn.get("time")));

            // 实体对象设值
            RobotCloseConnEntity entity = new RobotCloseConnEntity();
            entity.setRobotID(robotID);
            entity.setPort(port);
            entity.setIp(ip);
            entity.setTime(time);

            // list集合中保存实体对象
            if (robotCloseConnEntityList.size() == 0){
                robotCloseConnEntityList.add(entity);
            }else{
                boolean bl_isAdd = false;
                int index = -1;

                for (int i = 0;i < robotCloseConnEntityList.size();i++){
                    if (robotID == robotCloseConnEntityList.get(i).getRobotID()){
                        bl_isAdd = true;
                        index = i;
                    }
                }

                // 集合中已经添加过该实体对象了
                if (bl_isAdd && index != -1){
                    robotCloseConnEntityList.remove(index);
                    robotCloseConnEntityList.add(index, entity);
                }else{
                    robotCloseConnEntityList.add(entity);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
//            ToastUtil.showToast(getContext(),"小车断开连接数据解析异常");
        }
    }

    /**
     * 设置小车位置不改变超时数据
     * @param mapNoMoveTimeout
     */
    private void setTimeoutData(Map<String, Object> mapNoMoveTimeout) {

        try {
            // 取值
            int robotID = Integer.parseInt(String.valueOf(mapNoMoveTimeout.get("robotID")));
            int port = Integer.parseInt(String.valueOf(mapNoMoveTimeout.get("port")));
            String ip = String.valueOf(mapNoMoveTimeout.get("ip"));
            long currentAddress = Long.parseLong(String.valueOf(mapNoMoveTimeout.get("currentAddress")));

            // 实体对象设值
            NoMoveTimeoutEntity entity = new NoMoveTimeoutEntity();
            entity.setRobotID(robotID);
            entity.setPort(port);
            entity.setIp(ip);
            entity.setCurrentAddress(currentAddress);

            // list集合中保存实体对象
            if (noMoveTimeoutEntityList.size() == 0){
                noMoveTimeoutEntityList.add(entity);
            }else{
                boolean bl_isAdd = false;
                int index = -1;

                for (int i = 0;i < noMoveTimeoutEntityList.size();i++){
                    if (robotID == noMoveTimeoutEntityList.get(i).getRobotID()){
                        bl_isAdd = true;
                        index = i;
                    }
                }

                // 集合中已经添加过该实体对象了
                if (bl_isAdd && index != -1){
                    noMoveTimeoutEntityList.remove(index);
                    noMoveTimeoutEntityList.add(index, entity);
                }else{
                    noMoveTimeoutEntityList.add(entity);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
//            ToastUtil.showToast(getContext(),"小车位置不改变超时数据解析异常");
        }

    }

    /**
     * 设置小车扫不到pod故障数据
     * @param mapRobotError
     */
    private void setRobotErrorData(Map<String, Object> mapRobotError) {
        try {
            // 取值
            int robotID = Integer.parseInt(String.valueOf(mapRobotError.get("robotID")));
            long errorTime = Long.parseLong(String.valueOf(mapRobotError.get("errorTime")));
            String errorID = String.valueOf(mapRobotError.get("errorID"));
            String errorStatus = String.valueOf(mapRobotError.get("errorStatus"));
            int podCodeID = Integer.parseInt(String.valueOf(mapRobotError.get("podCodeID")));
            int curPodID = Integer.parseInt(String.valueOf(mapRobotError.get("curPodID")));
            String sectionID = String.valueOf(mapRobotError.get("sectionID"));

            // 实体对象设置值
            RobotErrorEntity entity = new RobotErrorEntity();
            entity.setRobotID(robotID);
            entity.setErrorTime(errorTime);
            entity.setErrorID(errorID);
            entity.setErrorStatus(errorStatus);
            entity.setPodCodeID(podCodeID);
            entity.setCurPodID(curPodID);
            entity.setSectionID(sectionID);

            // list集合中保存实体对象
            if (robotErrorEntityList.size() == 0){
                robotErrorEntityList.add(entity);
            }else{
                boolean bl_isAdd = false;
                int index = -1;

                for (int i = 0;i < robotErrorEntityList.size();i++){
                    if (robotID == robotErrorEntityList.get(i).getRobotID()){
                        bl_isAdd = true;
                        index = i;
                    }
                }

                // 集合中已经添加过该实体对象了
                if (bl_isAdd && index != -1){
                    robotErrorEntityList.remove(index);
                    robotErrorEntityList.add(index, entity);
                }else{
                    robotErrorEntityList.add(entity);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
//            ToastUtil.showToast(getContext(),"小车扫不到pod故障数据解析异常：" + e.getMessage());
        }
    }

    private Thread t_cancel_car_all_current_path;// 取消小车路径显示
    private Thread t_clear_all_data;// 重选地图清空所有数据
    private Thread threadCloseConnection;
    private List<Object> carLockList = new ArrayList<>();// 保存所有小车的路径坐标（不重复）
    private List<Integer> manualLockList = new ArrayList<>();// 保存手动锁格的区域坐标，不包含小车的锁格坐标
    private String rootAddress = "";

    /**
     * 设置小车当前路径数据
     * @param mapShow
     */
    private void setCarCurrentPath(Map<String, Object> mapShow) {
        try {
            carCurrentPathEntityList.clear();// 清空集合中所有的数据，之后集合变为一个空的集合
            List<Map<String, Object>> list = (List<Map<String, Object>>) mapShow.get("agvList");
            if(list != null && list.size() != 0){
                for (int i = 0;i < list.size();i++){
                    Map<String, Object> map = list.get(i);
                    // 取值
//                    long robotID = (long) map.get("robotID");
                    long robotID = Long.parseLong(String.valueOf(map.get("robotID")));

                    List<Long> longs_lock = (List<Long>) map.get("currentSeriesPath");// 锁格区域
                    List<Long> longs_all = (List<Long>) map.get("currentGlobalSeriesPath");// 锁格和未锁格区域

                    // 将锁格路径集合元素的类型转为int
                    if (reboot_robotId != -1 && reboot_robotId == robotID){

                        reboot_lock_list.clear();
                        for (int j = 0;j < longs_lock.size();j++){
                            int lock_pos = Integer.parseInt(String.valueOf(longs_lock.get(j)));
                            reboot_lock_list.add(lock_pos);
                        }

                        int index = -1;
                        int currentAddressCodeID = Integer.parseInt(String.valueOf(map.get("currentAddressCodeID")));
                        for (int k = 0;k < reboot_lock_list.size();k++){
                            int address = reboot_lock_list.get(k);
                            if (address == currentAddressCodeID){
                                index = k;
                            }
                        }

                        if (index != -1){
                            reboot_lock_list.remove(index);
                        }

                        reboot_robotId = -1;
                    }

                    // 实体对象设值
                    CarCurrentPathEntity entity = new CarCurrentPathEntity();
                    entity.setRobotID(robotID);
                    entity.setLockPath(longs_lock);
                    entity.setAllPath(longs_all);
                    // 添加实体对象，保存到集合
                    carCurrentPathEntityList.add(entity);
                }

                /*carLockList.clear();
                for (int i = 0;i < list.size();i++){
                    Map<String, Object> map = list.get(i);
                    List<Long> longs_lock = (List<Long>) map.get("currentSeriesPath");// 锁格区域
                    if(carLockList == null || carLockList.size() == 0){
                        if(longs_lock != null && longs_lock.size() != 0){
                            for (long l : longs_lock){
                                carLockList.add(l);
                            }
                        }
                    }else {
                        if(longs_lock != null && longs_lock.size() != 0){
                            for (int k = 0;k < longs_lock.size();k++){
                                Object carLockObject = longs_lock.get(k);
                                if(!carLockList.contains(carLockObject)){
                                    carLockList.add(carLockObject);// 添加集合中不存在的锁格坐标，确保锁格坐标不重复
                                }
                            }
                        }

                    }
                }*/

            }

            allLockedAreaList = (List<Object>) mapShow.get("lockedList");// 所有锁格区域地标

//            manualLockList.clear();
//            if(allLockedAreaList != null && allLockedAreaList.size() != 0){
//                for (int i = 0;i < allLockedAreaList.size(); i++){
//                    Object object = allLockedAreaList.get(i);
//                    long long_object = Long.parseLong(object.toString());
//                    if(!carLockList.contains(long_object)){
//                        manualLockList.add(object);
//                    }
//                }
//            }

            // 锁格状态判断
            if(lockArea != 0){
                if(allLockedAreaList.contains(lockArea)){
//                    ToastUtil.showToast(getContext(), "锁格成功");
                    lockArea = 0;
                }
            }

            if(unLockArea != 0){
                if(!allLockedAreaList.contains(unLockArea)){
//                    ToastUtil.showToast(getContext(), "解锁成功");
                    unLockArea = 0;
                }
            }

            // 不显示小车当前路径的情况下，显示手动锁格的区域；如果显示小车当前路径，那么显示所有的锁格区域
//            if(bl_isManualLock){
//                allLockedAreaList.clear();
//                carCurrentPathEntityList.clear();
//            }else {
//                manualLockList.clear();
//            }

        }catch (Exception e){
            e.printStackTrace();
            ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_exception_parsing_for_AGV_current_path_information_data) + e.getMessage());
        }
    }

    /**
     * 设置充电任务数据
     * @param mapCharge
     */
    private void setChargingTaskData(Map<String, Object> mapCharge) {
        try {
            chargingTaskEntityList.clear();// 每次清空集合中的数据
            List<Map<String, Object>> list = (List<Map<String, Object>>) mapCharge.get("chargeList");
            if(list == null || list.size() == 0){
                boxView.setChargeData(null, sectionId);
                return;
            }
            int len = list.size();
            for(int i = 0;i < len;i++){
                Map<String, Object> map = list.get(i);
                ChargingTaskEntity entity = new ChargingTaskEntity();
                // 取值
//                String warehouseId = (String) map.get("warehouseId");
                String warehouseId = String.valueOf(map.get("warehouseId"));
                String sectionUUID = "";
                if(map.containsKey("sectionUUID")){
//                    sectionUUID = (String) map.get("sectionUUID");
                    sectionUUID = String.valueOf(map.get("sectionUUID"));
                }else if(map.containsKey("sectionId")){
//                    sectionUUID = (String) map.get("sectionId");
                    sectionUUID = String.valueOf(map.get("sectionId"));
                }

//                String orderId = (String) map.get("orderId");
                String orderId = String.valueOf(map.get("orderId"));
//                String tripState = (String) map.get("tripState");
                String tripState = String.valueOf(map.get("tripState"));
//                String driveId = (String) map.get("driveId");
                String driveId = String.valueOf(map.get("driveId"));

                String chargeUUID = "";
                if(map.containsKey("chargeUUID")){
//                    chargeUUID = (String) map.get("chargeUUID");
                    chargeUUID = String.valueOf(map.get("chargeUUID"));
                }else if(map.containsKey("chargeId")){
//                    chargeUUID = (String) map.get("chargeId");
                    chargeUUID = String.valueOf(map.get("chargeId"));
                }

                String robotAddressCodeId = "";
                if (map.containsKey("robotAddressCodeId")){
                    robotAddressCodeId = String.valueOf(map.get("robotAddressCodeId"));
                }
                // 实体对象设值
                entity.setWarehouseId(warehouseId);
                entity.setSectionUUID(sectionUUID);
                entity.setOrderId(orderId);
                entity.setTripState(tripState);
                entity.setDriveId(driveId);
                entity.setChargeUUID(chargeUUID);
                entity.setRobotAddressCodeId(robotAddressCodeId);
                // 往集合中添加实体对象
                chargingTaskEntityList.add(entity);
            }
            LogUtil.e("chargingTaskEntityList",chargingTaskEntityList.toString());
        }catch (Exception e){
            e.printStackTrace();
//            ToastUtil.showToast(getContext(),"充电任务数据解析异常");
        }
    }

    private String strStorageMapName = "";// 声明一个变量保存仓库和地图的名称

    /**
     * popupwindow进行仓库地图的初始化选择
     */
    private void showPopAboutInitStorage() {
        // 构建popupwindow的布局
        pop_view_storageInit = getLayoutInflater().inflate(R.layout.popupwindow_storage_init, null);
        int popWidth = screenUtil.getScreenSize(ScreenUtil.WIDTH) - DensityUtil.dp2px(getContext(),40);
        int popHeight = DensityUtil.dp2px(getContext(), 180);// 高度是180dp
        // 构建PopupWindow对象
        window_storageInit = new PopupWindow(pop_view_storageInit, popWidth, popHeight);

        rl_storage = pop_view_storageInit.findViewById(R.id.rl_storage);
        rl_section_map = (RelativeLayout) pop_view_storageInit.findViewById(R.id.rl_section_map);

        tv_storageName = pop_view_storageInit.findViewById(R.id.tv_storageName);
        tv_mapName = pop_view_storageInit.findViewById(R.id.tv_mapName);

        // 设置弹框的动画
        window_storageInit.setAnimationStyle(R.style.pop_anim);
        // 设置背景透明
        window_storageInit.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#00000000")));
        // 设置获取焦点
        window_storageInit.setFocusable(true);
        // 设置触摸区域外可消失
        window_storageInit.setOutsideTouchable(true);
        // 实时更新状态
        window_storageInit.update();
        // 根据偏移量确定在parent view中的显示位置
        window_storageInit.showAtLocation(rl_mapView, Gravity.CENTER, 0, 0);

        // 背景透明度改变，优化用户体验
        bgAlpha(0.618f);
        window_storageInit.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                bgAlpha(1.0f);
            }
        });

        Button btn_storage_init = pop_view_storageInit.findViewById(R.id.btn_storage_init);// 确认按钮

        btn_storage_init.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {// 确认按钮监听
                if(getResources().getString(R.string.str_please_select).equals(tv_storageName.getText().toString())){
                    ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_please_select_a_warehouse));
                    return;
                }else{
                    if(getResources().getString(R.string.str_please_select).equals(tv_mapName.getText().toString())){
                        ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_please_select_a_map));
                        return;
                    }else {
                        if(sectionRcsId != -1){// 仓库下的某张地图存在时
                            bl_initStorageMap = true;// 表示仓库和地图的选择完成，将bl_initStorageMap置为true
                            window_storageInit.dismiss();
//                            ToastUtil.showToast(getContext(),"初始化，仓库和地图选择完成");
                            btn_connect_rabbitmq.setTextColor(Color.GREEN);

                            ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.boxfragment_start_mapping), getResources().getColor(R.color.colorAccent));
                            setUpConnectionFactory();// 连接设置
                            subscribeMapData(inComingMessageHandler);// (这里需要先绑定队列，防止队列接收不到消息)发送地图数据请求到MQ，开始获取地图数据
                            // 发送消息到MQ 从MQ上拿到地图的行和列数据，然后绘制地图
                            publishToAMPQ(Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_MAP_REQUEST);
                            // 这时候发送延迟消息代替手动点击按钮执行绘制地图操作
                            inComingMessageHandler.sendEmptyMessageDelayed(WHAT_AUTO_DRAW_MAP, DELAY_TIME);
                        }
                    }
                }
            }
        });

        chooseStorageMap();// 选择仓库地图
    }

    private int checkedItemStorage = -1;
    private int checkedItemSectionMap = -1;
    private List<StorageEntity.SectionEntity> section_map = null;// 声明集合，表示某个仓库下的地图集
    private String str_storage_map_name = "";// 声明变量保存仓库和地图选择信息
    /**
     * 选择仓库
     */
    private void chooseStorageMap() {
        final List<List<StorageEntity.SectionEntity>> sectionMapList = new ArrayList<>();// 用来保存所有仓库下的地图集
        // 仓库选择监听
        rl_storage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(storageEntityList.size() == 0){
                    ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_the_warehouse_data_is_empty));
                }else {

                    final CharSequence[] items_storage = new CharSequence[storageEntityList.size()];// 保存仓库名称
                    final CharSequence[] items_houseId = new CharSequence[storageEntityList.size()];// 保存仓库ID
                    for(int i = 0;i < storageEntityList.size();i++){
                        String storageName = storageEntityList.get(i).getWarehouseName();// 仓库名称
                        items_storage[i] = storageName;// 赋值仓库名称

                        // 获取warehouseId
                        String warehouseId = storageEntityList.get(i).getWarehouseId();
                        items_houseId[i] = warehouseId;

                        List<StorageEntity.SectionEntity> sectionMap = storageEntityList.get(i).getSectionMap();
                        sectionMapList.add(sectionMap);// 添加某个仓库下的地图数据
                    }

                    new AlertDialog.Builder(getContext()).setSingleChoiceItems(items_storage, checkedItemStorage, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            checkedItemStorage = which;// 赋值选中项的位置
                            tv_storageName.setText(items_storage[which]);
                            section_map = sectionMapList.get(which);// 赋值仓库下的地图数据
                            str_storage_map_name = getResources().getString(R.string.boxfragment_current_warehouse_name) + items_storage[which];
                            // 赋值所选仓库的id
                            Constants.WAREHOUSEID = String.valueOf(items_houseId[which]);
                            LogUtil.e("WAREHOUSEID","" + Constants.WAREHOUSEID);
                            dialog.dismiss();
                        }
                    }).create().show();
                }

            }
        });

        // 仓库下地图的选择监听
        rl_section_map.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(getResources().getString(R.string.str_please_select).equals(tv_storageName.getText().toString())){
                    ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_please_select_the_warehouse_first));
                }else {
                    final CharSequence[] items_sectionMap = new CharSequence[section_map.size()];// 保存地图名称
                    for (int i = 0;i< section_map.size();i++){
                        String mapName = section_map.get(i).getSectionName();// 某个地图名称
                        items_sectionMap[i] = mapName;// 赋值地图名称
                    }

                    new AlertDialog.Builder(getContext()).setSingleChoiceItems(items_sectionMap, checkedItemSectionMap, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            checkedItemSectionMap = which;
                            tv_mapName.setText(items_sectionMap[which]);// 显示地图名称
                            sectionRcsId = section_map.get(which).getSectionRcsId();// 赋值地图sectionRcsId字段的值
                            Constants.EXCHANGE = Constants.exchange_begin + sectionRcsId;// 设置交换机名称
                            Constants.SECTION_RCS_ID = String.valueOf(sectionRcsId);// 设置地图的具体id值（例如：1、2）
                            LogUtil.e("TAG", sectionRcsId + "");
                            strStorageMapName = str_storage_map_name + "\n" + getResources().getString(R.string.boxfragment_current_map_name) + items_sectionMap[which];
                            dialog.dismiss();
                        }
                    }).create().show();
                }
            }
        });

    }

    public BoxFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_box, container, false);
        ButterKnife.bind(this, view);// butterknife的绑定
        init();// 初始化数据

        requestPermission();// 申请权限

        setListener();// 设置监听
        return view;
    }

    @SuppressLint("NewApi")
    private void requestPermission() {
        if(getContext().checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED){
            // 进行授权
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case 1:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){

                }else {
                    ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_read_or_write_file_permissions_not_enabled));
                }
                break;
        }
    }

    private boolean bl_isCarExist = false;// true表示地址格中存在小车-标志
    private boolean bl_isPodExist = false;// true表示地址格中存在pod-标志
    private boolean bl_isUnWalked = false;// true表示地址格是不可走区域-标志
    // 初始化小车和货架在集合中的位置
    private int carPosition = 0, podPosition = 0;

    private List<Integer> lock_unlock_pos = new ArrayList<>();

    private List<Integer> workSiteIndexList = new ArrayList<>();// 保存开启不间断释放pod功能的工作站所在地图的点位
    private List<String> workSiteUUIDList = new ArrayList<>();// 保存开启不间断释放pod功能的工作站的UUID

    private void setListener() {

        // 监听boxView的单击事件
        boxView.setOnClickListener(new BoxView.OnClickListener() {
            @Override
            public void doClick(final int boxNo,
                                final List<RobotEntity> car_List,
                                List<PodEntity> pod_List,
                                List<Long> unWalked_List) {

                // 点击界面，取消震动
                if (vibrator != null){
                    vibrator.cancel();
                }

                bl_isCarExist = false;
                bl_isPodExist = false;
                bl_isUnWalked = false;

                // 初始化小车和货架在集合中的位置
                carPosition = 0;
                podPosition = 0;

                if(unWalked_List != null){// 遍历判断是否是不可走区域
                    for (int i = 0;i < unWalked_List.size();i++){
                        if(boxNo == unWalked_List.get(i)){
                            bl_isUnWalked = true;
                        }
                    }
                }

                if(car_List != null){
                    // 遍历判断格子中是否有小车
                    for(int i = 0;i < car_List.size();i++){
                        if(boxNo == car_List.get(i).getAddressCodeID()){
                            bl_isCarExist = true;// 格子中有小车
                            carPosition = i;
                        }
                    }
                }

                if(pod_List != null){
                    // 遍历判断格子中是否有pod
                    for(int i = 0;i < pod_List.size();i++){
                        if(boxNo == pod_List.get(i).getPodPos()){
                            bl_isPodExist = true;// 格子中有pod
                            podPosition = i;
                        }
                    }
                }

                if(bl_isUnWalked){// 点击了不可走区域
                    ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_the_current_region_is_not_walkable));
                }else {

                    if (!bl_isSelectLockUnLock){

                        // 构建view
                        view_options = getLayoutInflater().from(getContext())
                                .inflate(R.layout.dialog_view_box_options, null);

                        // 创建AlertDialog对象
                        dialog_options = new AlertDialog.Builder(getContext())
                                .setView(view_options)
                                .create();
                        // 获取控件
                        tv_options_lock_circle = view_options.findViewById(R.id.tv_options_lock_circle);// 锁周边（建立安全区域）
                        tv_options_unlock_circle = view_options.findViewById(R.id.tv_options_unlock_circle);// 解锁周边（解除安全区域）
                        tv_add_point_area = view_options.findViewById(R.id.tv_add_point_area);// 建立单点安全区域
                        tv_remove_point_area = view_options.findViewById(R.id.tv_remove_point_area);// 解除单点安全区域
                        tv_reboot_resend = view_options.findViewById(R.id.tv_reboot_resend);// 重启重发
                        tv_agv_action = view_options.findViewById(R.id.tv_agv_action);// agv动作，即上下左右移动一格（已经隐藏）
                        tv_scram_resend = view_options.findViewById(R.id.tv_scram_resend);// 急停重发

                        // 设置dialog所在的窗口的背景为透明，很关键
                        dialog_options.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                        dialog_options.setCancelable(true);
                        dialog_options.show();

                        // 小车重启，重算路径后重发任务
                        tv_reboot_resend.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {

                                if(!bl_isCarExist){
                                    ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_please_click_the_car_on_the_map));
                                    dialog_options.dismiss();
                                    return;
                                }

                                final int robotID = Integer.parseInt(String.valueOf(car_List.get(carPosition).getRobotID()));// 获取小车id

                                // 每次重启重发时都需要清空小车的原锁格路径集合
                                if(reboot_lock_list.size() != 0){
                                    reboot_lock_list.clear();
                                }
                                reboot_robotId = robotID;
                                setUpConnectionFactory();

                                new AlertDialog.Builder(getContext())
                                        .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                                        .setIcon(R.mipmap.app_icon)
                                        .setMessage(getResources().getString(R.string.boxfragment_restart_and_resend_car_no) + robotID + "？")
                                        .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                reboot_robotId = -1;
                                                dialog.dismiss();
                                            }
                                        })
                                        .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                                dialog_options.dismiss();

                                                Message message = inComingMessageHandler.obtainMessage();
                                                message.arg1 = robotID;
                                                message.what = WHAT_REBOOT_RESEND;
                                                inComingMessageHandler.sendMessageDelayed(message, 1000);

                                            }
                                        }).create().show();

                            }
                        });

                        // 急停重发（重发任务）
                        tv_scram_resend.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {

                                if(!bl_isCarExist){
                                    ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_please_click_the_car_on_the_map));
                                    dialog_options.dismiss();
                                    return;
                                }

                                final int robotID = Integer.parseInt(String.valueOf(car_List.get(carPosition).getRobotID()));// 获取小车id
                                methodResendOrder(robotID);// 重发任务

                            }
                        });


                        // agv动作
                        tv_agv_action.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if(!bl_isCarExist){
                                    ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_click_car_first_then_click_the_option));
                                    dialog_options.dismiss();
                                    return;
                                }

                                final int robotID = Integer.parseInt(String.valueOf(car_List.get(carPosition).getRobotID()));// 获取小车id
                                dialog_options.dismiss();
                                CharSequence[] items_agv_action = {getResources().getString(R.string.boxfragment_move_up_one_cell),
                                        getResources().getString(R.string.boxfragment_move_down_one_cell),
                                        getResources().getString(R.string.boxfragment_move_left_one_cell),
                                        getResources().getString(R.string.boxfragment_move_right_one_cell)};
                                new AlertDialog.Builder(getContext())
                                        .setItems(items_agv_action, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                                switch (which){
                                                    case 0:// 上移一格
                                                        call_carMoveUp(robotID);
                                                        break;
                                                    case 1:// 下移一格
                                                        call_carMoveDown(robotID);
                                                        break;
                                                    case 2:// 左移一格
                                                        call_carMoveLeft(robotID);
                                                        break;
                                                    case 3:// 右移一格
                                                        call_carMoveRight(robotID);
                                                        break;
                                                }
                                            }
                                        }).setCancelable(true).create().show();
                            }
                        });

                        // 建立单点安全区域（当前点锁格）
                        tv_add_point_area.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                setUpConnectionFactory();
                                // 给RabbitMQ发送消息
                                publishToAMPQ(Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_LOCK_UNLOCK);
                                ProgressBarUtil.showProgressBar(getContext(),
                                        getResources().getString(R.string.boxfragment_to_establish_a_single_point_safety_area),
                                        getResources().getColor(R.color.colorPrimaryDark));
                                boolean isExist = false;// false表示未添加
                                if (manualLockList.size() == 0){
                                    manualLockList.add(boxNo);
                                }else {
                                    for (int i = 0;i < manualLockList.size();i++){
                                        int point = manualLockList.get(i);
                                        if (point == boxNo){
                                            isExist = true;
                                        }
                                    }

                                    if (!isExist){
                                        manualLockList.add(boxNo);
                                    }
                                }

                                nine_lock_unlock.clear();
                                nine_lock_unlock.add(boxNo);
                                Map<String, Object> message = new HashMap<>();
                                message.put("unAvailableAddressList", nine_lock_unlock);
                                try{
                                    queue.putLast(message);// 发送消息到MQ
                                }catch (Exception e){
                                    e.printStackTrace();
                                    ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_a_single_point_establishing_exception_safety_area) + e.getMessage());
                                }

                                dialog_options.dismiss();
                                ProgressBarUtil.dissmissProgressBar();
                            }
                        });

                        // 解除单点安全区域（当前点解锁）
                        tv_remove_point_area.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                setUpConnectionFactory();
                                // 给RabbitMQ发送消息
                                publishToAMPQ(Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_LOCK_UNLOCK);
                                ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.boxfragment_single_point_safety_area_removed),
                                        getResources().getColor(R.color.colorPrimaryDark));
                                int index = -1;
                                if (manualLockList.size() != 0){
                                    for (int i = 0;i < manualLockList.size();i++){
                                        int point = manualLockList.get(i);
                                        if (point == boxNo){
                                            index = i;
                                        }
                                    }

                                    if (index != -1){
                                        manualLockList.remove(index);
                                    }
                                }

                                nine_lock_unlock.clear();
                                nine_lock_unlock.add(boxNo);

                                Map<String, Object> message = new HashMap<>();
                                message.put("availableAddressList", nine_lock_unlock);
                                try{
                                    queue.putLast(message);// 发送消息到MQ
                                }catch (Exception e){
                                    e.printStackTrace();
                                    ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_single_point_safety_area_unlocks_exception) + e.getMessage());
                                }

                                dialog_options.dismiss();
                                ProgressBarUtil.dissmissProgressBar();
                            }
                        });

                        // 锁周边，即建立安全区域
                        tv_options_lock_circle.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                new AlertDialog.Builder(getContext())
                                        .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                                        .setIcon(R.mipmap.app_icon)
                                        .setMessage(getResources().getString(R.string.boxgragment_careful_peration_1)
                                                + boxNo +
                                                getResources().getString(R.string.boxfragment_create_a_safe_area_around_it))
                                        .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                            }
                                        })
                                        .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                setUpConnectionFactory();
                                                dialog.dismiss();
                                                dialog_options.dismiss();
                                                ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.boxfragment_safety_area_set_up),
                                                        getResources().getColor(R.color.colorPrimaryDark));
                                                // 给RabbitMQ发送消息
                                                publishToAMPQ(Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_LOCK_UNLOCK);
                                                nineLock(boxNo);
                                            }
                                        }).create().show();
                            }
                        });

                        // 解锁周边，即解除安全区域
                        tv_options_unlock_circle.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                new AlertDialog.Builder(getContext())
                                        .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                                        .setIcon(R.mipmap.app_icon)
                                        .setMessage(getResources().getString(R.string.boxgragment_careful_peration_2)
                                                + boxNo +
                                                getResources().getString(R.string.boxfragment_A_circle_around_the_safety_area_to_remove))
                                        .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                            }
                                        })
                                        .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                setUpConnectionFactory();
                                                dialog.dismiss();
                                                dialog_options.dismiss();
                                                ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.boxfragment_Safety_area_to_remove),
                                                        getResources().getColor(R.color.colorPrimaryDark));
                                                // 给RabbitMQ发送消息
                                                publishToAMPQ(Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_LOCK_UNLOCK);
                                                nineUnlock(boxNo);
                                            }
                                        }).create().show();
                            }
                        });


                    }else{

                        // 设置点击了-锁格/解锁-选项后，用户点击地图上的格子后对格子做的标识

                        if(lock_unlock_pos.size() == 0){
                            lock_unlock_pos.add(boxNo);
                        }else {

                            int index = -1;
                            boolean bl_isAdd = false;
                            for(int i = 0;i < lock_unlock_pos.size();i++){
                                int pos = lock_unlock_pos.get(i);
                                if(pos == boxNo){
                                    index = i;
                                    bl_isAdd = true;
                                }
                            }

                            if(bl_isAdd && index != -1){
                                lock_unlock_pos.remove(index);
                            }else {
                                lock_unlock_pos.add(boxNo);
                            }

                        }

                        boxView.setLockUnLockArea(lock_unlock_pos);
                    }

                }

            }

            // 工作站的点击事件监听
            @Override
            public void workSiteClick(final int boxNo, boolean bl_isWorkSite, final String workSiteUUID) {
                try {
                    if(bl_isWorkSite){

//                        strWorkSiteUUID = workSiteUUID;// 保存用户点击工作站的UUID
//                        CharSequence[] items_worksite = {"释放单个pod", "开启不间断释放pod", "停止不间断释放pod"};

                        CharSequence[] items_worksite = {getResources().getString(R.string.boxfragment_Release_the_pod)};

                        // 一个选项是释放工作站的pod、一个选项是采用发送延迟消息的方式不间断的释放pod
//                        new AlertDialog.Builder(getContext())
//                                .setIcon(R.mipmap.app_icon)
//                                .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
//                                .setItems(items_worksite, new DialogInterface.OnClickListener() {
//                                    @Override
//                                    public void onClick(DialogInterface dialog, int which) {
//                                        dialog.dismiss();
//                                        switch (which){
//                                            case 0:// 释放单个pod
//                                                dialog.dismiss();
                                                new AlertDialog.Builder(getContext())
                                                        .setIcon(R.mipmap.app_icon)
                                                        .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                                                        .setMessage(getResources().getString(R.string.boxfragment_Determine_release_POD))
                                                        .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface dialog, int i) {
                                                                dialog.dismiss();
                                                            }

                                                        })
                                                        .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface dialog, int i) {
                                                                call_showPod(workSiteUUID);// 显示pod状态
                                                                dialog.dismiss();
                                                            }
                                                        }).create().show();
//                                                break;
                                            /*
                                            case 1:// 不间断释放pod

                                                new AlertDialog.Builder(getContext())
                                                        .setIcon(R.mipmap.app_icon)
                                                        .setTitle("提示")
                                                        .setMessage("该工作站将开启不间断释放pod功能，但不会影响其他工作站正常工作，请确认是否开启？注：当该设备开启该功能后，停止也需要用该设备停止，或者该设备退出应用也可以停止该功能")
                                                        .setPositiveButton("否", new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface dialog, int which) {

                                                                dialog.dismiss();
                                                                // 不做任何操作

                                                            }
                                                        })
                                                        .setNegativeButton("是", new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface dialog, int which) {
                                                                dialog.dismiss();
                                                                STOP_RELEASE_POD = false;// 不间断释放pod功能开启

                                                                if (!workSiteIndexList.contains(boxNo)){
                                                                    workSiteIndexList.add(boxNo);// 添加开启了不间断释放pod功能的工作站坐标
                                                                    workSiteUUIDList.add(workSiteUUID);// 添加开启了不间断释放pod功能功能的工作站的UUID
                                                                }else{
                                                                    ToastUtil.showToast(getContext(), "该工作站已经开启该功能");
                                                                }

//                                                                LogUtil.e("wsl","workSiteIndexList= "
//                                                                        + workSiteIndexList.toString() + ", workSiteUUIDList= " + workSiteUUIDList.toString());

                                                                inComingMessageHandler.sendEmptyMessageDelayed(WHAT_RELEASE_POD, RELEASE_POD_TIME);
                                                            }
                                                        }).create().show();

                                                break;
                                            case 2:// 停止工作站不间断释放pod功能

                                                new AlertDialog.Builder(getContext())
                                                        .setIcon(R.mipmap.app_icon)
                                                        .setTitle("提示")
                                                        .setMessage("该操作将会停止所有工作站已经开启的不间断释放pod功能，请确认是否继续操作？")
                                                        .setNegativeButton("是", new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface dialog, int which) {
                                                                dialog.dismiss();
                                                                if (workSiteIndexList.size() == 0){
                                                                    ToastUtil.showToast(getContext(), "该设备未开启该功能");
                                                                }else {
                                                                    STOP_RELEASE_POD = true;
                                                                    workSiteIndexList.clear();
                                                                    workSiteUUIDList.clear();
                                                                    ToastUtil.showToast(getContext(), "停止成功");
                                                                }

                                                            }
                                                        })
                                                        .setPositiveButton("否", new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(DialogInterface dialog, int which) {
                                                                dialog.dismiss();
                                                            }
                                                        }).create().show();

                                                break;
                                                */
//                                        }
//                                    }
//                                }).create().show();


                    }
                }catch (Exception e){
                    ToastUtil.showToast(getContext(), e.getMessage());
                }
            }

            // 回调设置地图格子大小
            @Override
            public void setBoxSize(int boxSizeInOut) {
                boxSizeChange = boxSizeInOut;
            }
        });

    }

    /**
     * 小车重启重发任务
     * @param robotID   小车id
     */
    private void methodRebootResend(int robotID) {

        if (reboot_lock_list.size() != 0){
            try{

                // 给RabbitMQ发送消息
                publishToAMPQ(Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_LOCK_UNLOCK);

                Map<String, Object> message = new HashMap<>();
                message.put("availableAddressList", reboot_lock_list);
                queue.putLast(message);// 发送消息到MQ

                methodResendOrder(robotID);

            }catch (Exception e){
                e.printStackTrace();
                ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_The_car_after_the_restart_lock_unlock_anomalies) + e.getMessage());
            }
        }else {
            ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_The_original_lock_path_information_is_empty));
        }

    }

    /**
     * 建立安全区域
     * @param boxNo 工程师点击的地址格
     */
    private void nineLock(int boxNo) {

        try{
            nine_lock_unlock.clear();// 每次都需要清空点位集合数据
            getNineAddressList(boxNo);// 根据点击的地址格，获取周围一圈的地址格集合

            // 工程师建立的安全区域点位集合
            if (manualLockList.size() == 0){
                // 第一次全部添加
                for (int i = 0;i < nine_lock_unlock.size();i++){
                    manualLockList.add(i, nine_lock_unlock.get(i));
                }
            }else {
                // 再次添加，去除重复点位
                for (int j = 0;j < nine_lock_unlock.size();j++){
                    Integer integer = nine_lock_unlock.get(j);
                    if (!manualLockList.contains(integer)){
                        int startIndex = manualLockList.size();
                        manualLockList.add(startIndex, integer);
                    }
                }
            }

            LogUtil.e("manualLockList","lock:"+manualLockList.toString());


            Map<String, Object> message = new HashMap<>();
            message.put("unAvailableAddressList", nine_lock_unlock);
            queue.putLast(message);// 发送消息到MQ
        }catch (Exception e){
            e.printStackTrace();
            ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_Establish_safe_area_exceptions) + e.getMessage());
        }

        ProgressBarUtil.dissmissProgressBar();

    }

    /**
     * 解除安全区域
     * @param boxNo 工程师点击的地址格
     */
    private void nineUnlock(int boxNo) {

        try{
            nine_lock_unlock.clear();// 每次都需要清空点位集合数据
            getNineAddressList(boxNo);// 根据点击的地址格，获取周围一圈的地址格集合

            Map<String, Object> message = new HashMap<>();
            message.put("availableAddressList", nine_lock_unlock);
            queue.putLast(message);// 发送消息到MQ

            // 解除工程师的点位集合，这里需要将解除的点位集合从 manualLockList 中移除
            if (manualLockList.size() != 0){
                for (int i = 0;i < nine_lock_unlock.size();i++){
                    int index = -1;
                    int pos_lock_unlock = nine_lock_unlock.get(i);
                    for (int j = 0;j < manualLockList.size();j++){
                        int pos_manual = manualLockList.get(j);
                        if (pos_lock_unlock == pos_manual){
                            index = j;
                        }
                    }

                    // 移除建立的安全区域点位集
                    if (index != -1){
                        manualLockList.remove(index);
                    }
                }
            }

            LogUtil.e("manualLockList","unlock:"+manualLockList.toString());
        }catch (Exception e){
            e.printStackTrace();
            ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_unlock_safe_zone_exception) + e.getMessage());
        }

        ProgressBarUtil.dissmissProgressBar();

    }

    /**
     * 获取九宫格地址点位集合
     * @param boxNo
     */
    private void getNineAddressList(int boxNo) {

        // boxNo在地图的第一行
        if (boxNo <= column){
            if (boxNo == 1){// 地图左上角
                nine_lock_unlock.add(boxNo + 1);
                nine_lock_unlock.add(boxNo + column);
                nine_lock_unlock.add(boxNo + column + 1);
            }
            if (boxNo == column){// 地图右上角
                nine_lock_unlock.add(boxNo - 1);
                nine_lock_unlock.add(boxNo + column);
                nine_lock_unlock.add(boxNo + column - 1);
            }
            if (boxNo > 1 && boxNo < column){// 地图第一行的中部
                nine_lock_unlock.add(boxNo - 1);
                nine_lock_unlock.add(boxNo + 1);
                nine_lock_unlock.add(boxNo + column);
                nine_lock_unlock.add(boxNo + column - 1);
                nine_lock_unlock.add(boxNo + column + 1);
            }
        }

        // boxNo在地图的最后一行
        else if (boxNo > (row - 1) * column){
            if (boxNo == ((row - 1) * column + 1)){// 地图左下角
                nine_lock_unlock.add((row -2) * column + 1);
                nine_lock_unlock.add((row -2) * column + 2);
                nine_lock_unlock.add((row -1) * column + 2);
            }
            if (boxNo == row * column){// 地图右下角
                nine_lock_unlock.add((row - 1) * column);
                nine_lock_unlock.add((row - 1) * column - 1);
                nine_lock_unlock.add(row * column - 1);
            }
            if (boxNo > ((row - 1) * column + 1) && boxNo < (row * column)){// 地图最后一行的中部
                nine_lock_unlock.add(boxNo - 1);
                nine_lock_unlock.add(boxNo + 1);
                nine_lock_unlock.add(boxNo - column);
                nine_lock_unlock.add(boxNo - column - 1);
                nine_lock_unlock.add(boxNo - column + 1);
            }
        }

        // boxNo在地图的第一列中部
        else if ((boxNo % column) == 1 && (boxNo > 1) && (boxNo < (row -1) * column)){
            nine_lock_unlock.add(boxNo + 1);
            nine_lock_unlock.add(boxNo - column);
            nine_lock_unlock.add(boxNo + column);
            nine_lock_unlock.add(boxNo + 1 - column);
            nine_lock_unlock.add(boxNo + 1 + column);
        }

        // boxNo在地图的最后一列中部
        else if ((boxNo % column) == 0 && (boxNo > column) && (boxNo <= (row - 1) * column)){
            nine_lock_unlock.add(boxNo - 1);
            nine_lock_unlock.add(boxNo - 1 - column);
            nine_lock_unlock.add(boxNo - 1 + column);
            nine_lock_unlock.add(boxNo - column);
            nine_lock_unlock.add(boxNo + column);
        }

        // boxNo在地图的中间部分且不在地图的最外圈
        else {
            nine_lock_unlock.add(boxNo - 1);
            nine_lock_unlock.add(boxNo + 1);
            nine_lock_unlock.add(boxNo + column);
            nine_lock_unlock.add(boxNo - column);
            nine_lock_unlock.add(boxNo + column - 1);
            nine_lock_unlock.add(boxNo + column + 1);
            nine_lock_unlock.add(boxNo - column - 1);
            nine_lock_unlock.add(boxNo - column + 1);
        }

    }

    /**
     * 初始化操作
     */
    private void init() {
        vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);// 震动服务
        screenUtil = new ScreenUtil(getContext());// 创建ScreenUtil对象，获取手机屏幕宽高用
        pop_height = (int) (screenUtil.getScreenSize(ScreenUtil.HEIGHT) * 0.618f);// 黄金比例显示
        requestQueue = Volley.newRequestQueue(getContext());// 创建RequestQueue对象
        pDialog = new ProgressDialog(getContext());// 创建进度对话框
        pDialog.setCanceledOnTouchOutside(true);// 设置触摸进度框外区域可以取消进度框
        boxSizeChange = Constants.DEFAULT_BOX_SIZE;// 赋值初始化地图时格子的默认大小

        rootAddress = Constants.HTTP + Constants.ROOT_ADDRESS;

        // 创建Timer对象
        if (timer_clear_charge_data == null){
            timer_clear_charge_data = new Timer();
        }

        if (timer_refresh_error_data == null){
            timer_refresh_error_data = new Timer();
        }

    }

    /**
     * 创建消费者线程：获取小车的数据
     * @param //handler
     */
    void subscribe(final Handler handler){

        subscribeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(connection_car != null && connection_car.isOpen()){
                        connection_car.close();
                    }
                    connection_car = factory.newConnection();
                    Channel channel = connection_car.createChannel();
                    channel.basicQos(0,1,false);
                    // 创建随机队列，可持续，自动删除
                    String queueName = System.currentTimeMillis() + "QN_CAR";
                    channel.exchangeDeclare(Constants.EXCHANGE, "direct", true);
                    AMQP.Queue.DeclareOk q = channel.queueDeclare(queueName, true, false, true, null);// 声明共享队列
                    LogUtil.e("queueName_car", q.getQueue());
                    channel.queueBind(q.getQueue(), Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_CAR);
//                    channel.queueBind("1518342137798queueNameCar", Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_CAR);

                    Consumer consumer = new DefaultConsumer(channel){
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            super.handleDelivery(consumerTag, envelope, properties, body);

                            // 发消息通知UI更新
                            Message message = handler.obtainMessage();
                            message.obj = body;
                            message.what = WHATCAR;
                            handler.sendMessage(message);
                        }
                    };
                    channel.basicConsume(q.getQueue(), true, consumer);
//                    channel.basicConsume("1518342137798queueNameCar", true, consumer);

                } catch (Exception e){
                    e.printStackTrace();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
        subscribeThread.start();// 开启线程获取RabbitMQ推送消息
    }

    /**
     * 设置小车的实时数据，存入集合中。
     * 同一辆车就进行集合中数据的更新替换，新车数据就添加到集合
     * @param mapCar
     */
    private void setCarAndPodData(Map<String, Object> mapCar) {
        try {
            RobotEntity robotEntity = new RobotEntity();
            long robotID = Long.parseLong(String.valueOf(mapCar.get("robotID")));// 小车id

            float podAngle = Float.parseFloat(String.valueOf(mapCar.get("podCodeInfoTheta")));// pod的角度，0°朝上，90°朝右，依次类推180°和270°

            // 如果为0，则小车没有装载pod；如果非0，则小车装载了该pod。该值表示pod的id
            long podCodeID = Long.parseLong(String.valueOf(mapCar.get("podCodeID")));

            long addressCodeID = Long.parseLong(String.valueOf(mapCar.get("addressCodeID")));// 小车在地图上的坐标

            // 设置小车数据
            robotEntity.setRobotID(robotID);
            robotEntity.setAddressCodeID(addressCodeID);

            // 如果pod位置变化了，也更新pod集合的数据并更新pod的位置显示
            if(podCodeID != 0){// pod的id不为0表示小车上有pod存在
                boolean bl_isExistPod = false;// true表示小车上的pod是初始化地图的pod
                for (int i = 0;i < podList.size();i++){
                    if(podCodeID == podList.get(i).getPodId()){// 小车上的pod是初始化地图数据时存在的pod
                        bl_isExistPod = true;
                        // 获取pod新的位置
                        int newPodId = Integer.parseInt(String.valueOf(podCodeID));
                        int newPodPos = Integer.parseInt(String.valueOf(addressCodeID));
                        // 移除集合中旧的PodEntity对象
                        podList.remove(i);
                        // 设置新的PodEntity对象
                        PodEntity podEntity = new PodEntity();
                        podEntity.setPodId(newPodId);
                        podEntity.setPodPos(newPodPos);
                        podEntity.setPodAngle(Integer.parseInt(String.valueOf(podAngle)));
                        // 往集合中添加这个新的pod
                        podList.add(i, podEntity);
                    }
                }
                if(!bl_isExistPod){// 表示小车此时载的pod是新增加的pod
                    int newPodId = Integer.parseInt(String.valueOf(podCodeID));// 新增pod的id
                    int newPodPos = Integer.parseInt(String.valueOf(addressCodeID));// 新增pod的位置
                    // 设置新的pod对象
                    PodEntity podEntity = new PodEntity();
                    podEntity.setPodId(newPodId);
                    podEntity.setPodPos(newPodPos);
                    podEntity.setPodAngle(Integer.parseInt(String.valueOf(podAngle)));
                    // 直接添加该PodEntity对象到podList集合中
                    podList.add(podEntity);
                }
            }

            boolean bl_isAddCar = false;// false表示集合中未添加过该小车
            int position = 0;
            if(carList == null || carList.size() == 0){
                carList.add(robotEntity);// 第一次添加小车数据，直接添加即可
            }else{
                // 遍历小车信息集合，根据小车的id判断是否添加过该小车
                for(int i = 0;i < carList.size();i++){
                    if(carList.get(i).getRobotID() == robotID){
                        bl_isAddCar = true;
                        position = i;// 拿到小车在集合中的位置
                    }
                }

                if(!bl_isAddCar){
                    carList.add(robotEntity);// 集合中没有添加过该小车
                }else{

                    carList.remove(position);// 移除小车上一次的信息
                    carList.add(position, robotEntity);// 在原来的位置添加新的小车信息
                }

            }

        }catch (Exception e){
            ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_Agv_data_parsing_exceptions) + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 按钮的单击事件
     * @param view
     */
    @OnClick({R.id.btn_connect_rabbitmq, R.id.btn_drawing, R.id.btn_init_data, R.id.iv_zoomOut, R.id.iv_zoomIn
            , R.id.linear_map_introduction, R.id.linear_map_info, R.id.linear_map_reset, R.id.linear_map_drawAgain
            , R.id.tv_showAllCarCurrentPath, R.id.tv_cancelAllCarCurrentPath, R.id.linear_map_carLockUnLock
            , R.id.btn_cancel_lockunlock, R.id.btn_confirm_lockunlock, R.id.linear_map_carBatteryInfo
            , R.id.linear_map_wcs, R.id.linear_map_rcs, R.id.imgBtn_errorTip, R.id.tv_problem_solve_or_not})
    public void doClick(final View view){
        switch (view.getId()){
            case R.id.btn_connect_rabbitmq:// 连接RabbitMQ获取初始化仓库和地图的数据
                initStorageAndMap();
                break;
            /*
            case R.id.btn_drawing:// 初始化地图数据并绘制地图

                if(!bl_initStorageMap){
                    ToastUtil.showToast(getContext(),"请先选择仓库和地图");
                }else {
                    if(!bl_initData){
                        setUpConnectionFactory();// 连接设置
                        subscribeMapData(inComingMessageHandler);// (这里需要先绑定队列，防止队列接收不到消息)发送地图数据请求到MQ，开始获取地图数据
                        // 发送消息到MQ 从MQ上拿到地图的行和列数据，然后绘制地图
                        publishToAMPQ(Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_MAP_REQUEST);
                        // 弹框提示用户操作
                        new AlertDialog.Builder(getContext())
                                .setIcon(R.mipmap.app_icon)
                                .setTitle("提示")
                                .setMessage(getResources().getString(R.string.str_initMapData) + "？")
                                .setPositiveButton("否", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        interruptThread(publishThread);// 中断发布线程
                                        interruptThread(threadMapData);// 中断消费线程
                                        dialog.dismiss();
                                    }
                                }).setNegativeButton("是", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // 弹出进度框提示正在初始化
                                showDialog("初始化...");
                                try {
                                    Map<String, Object> message = new HashMap<>();
                                    message.put("name", Constants.MQ_ROUTINGKEY_MAP_REQUEST);
                                    message.put("requestTime", System.currentTimeMillis());// 系统当前时间
                                    message.put("sectionID", sectionRcsId);// 根据该值来确定绘制仓库下的哪个地图
                                    queue.putLast(message);// 发送消息到MQ
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }).create().show();
                    }else {
                        ToastUtil.showToast(getContext(),"地图已经初始化");
                    }
                }

                break;
            case R.id.btn_init_data:// 初始化小车数据

                // 弹框提示用户操作
                new AlertDialog.Builder(getContext())
                        .setIcon(R.mipmap.app_icon)
                        .setTitle("提示")
                        .setMessage(getResources().getString(R.string.str_initCarData) + "？")
                        .setPositiveButton("否", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).setNegativeButton("是", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        initCarData();

                    }
                }).create().show();

                break;
                */
            case R.id.linear_map_reset:// 复原地图
                if(bl_initData){
                    boxView.reset();// 地图复原，当自定义view触摸不到后调用复原地图
                }else {
//                    ToastUtil.showToast(getContext(), "请先绘制地图");
                    return;
                }

                break;
            case R.id.linear_map_drawAgain:// 重新选择仓库和地图
                interruptThread(publishThread);
                setUpConnectionFactory();// 连接设置
                subscribeStorageMap(inComingMessageHandler);// 先创建队列接收仓库地图的数据
//                publishToAMPQ(Constants.MQ_EXCHANGE_STORAGEMAP, Constants.MQ_ROUTINGKEY_STORAGEMAP_REQUEST);// publish消息给请求队列
                new AlertDialog.Builder(getContext())
                        .setIcon(R.mipmap.app_icon)
                        .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                        .setMessage(getResources().getString(R.string.str_map_drawAgain) + "？")
                        .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                creatThreadCloseConnectionAndClearData();
                            }
                        }).create().show();
                break;
            case R.id.iv_zoomOut:// 地图放大
                if(boxSizeChange != 0 && boxSizeChange > 60){
                    ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_The_map_has_been_zoomed_to_its_maximum_size));
                    return;
                }
                boxSizeChange += 2;
                boxView.zoomInOut(boxSizeChange);
                break;
            case R.id.iv_zoomIn:// 地图缩小
                if(boxSizeChange != 0 && boxSizeChange < 20){
                    ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_The_map_has_been_zoomed_to_its_minimum_size));
                    return;
                }
                boxSizeChange -= 2;
                boxView.zoomInOut(boxSizeChange);
                break;
            case R.id.linear_map_introduction:// 地图说明
                showPopAboutMapIntroduction();
                break;
            case R.id.linear_map_info:// 地图信息
                showDialogAboutMapInfo();
                break;
            /*
            case R.id.tv_showAllCarCurrentPath:// 显示所有小车的当前路径（标识锁格和未锁格状态）
                showCarAllPathInfo();
                break;
            case R.id.tv_cancelAllCarCurrentPath:// 取消小车当前路径显示
                new AlertDialog.Builder(getContext())
                        .setIcon(R.mipmap.app_icon)
                        .setTitle("提示")
                        .setMessage(getResources().getString(R.string.str_cancelAllCarLockUnlockPath) + "？")
                        .setPositiveButton("否", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton("是", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if(!bl_isShowCarPath){
                                    ToastUtil.showToast(getContext(),"请先显示小车锁格、未锁格路径");
                                }else {
                                    gone(linear_lock_unlock);
                                    visibile(linear_map_carLockUnLock);
                                    visibile(linear_map_carBatteryInfo);
                                    // 源汇定制
                                    visibile(linear_map_wcs);
//                                    visibile(linear_map_rcs);
                                    visibile(linear_zoomOutIn);
                                    visibile(linear_map_drawAgain);
                                    visibile(linear_map_reset);
                                    visibile(linear_map_info);
                                    visibile(linear_map_introduction);
                                    interruptThread(threadShowAllCarCurrentPath);
                                    // 创建线程取消小车当前路径
                                    t_cancel_car_all_current_path = new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if(connection_showAllCarCurrentPath != null){
                                                connection_showAllCarCurrentPath.abort();
                                            }
                                            Message message = inComingMessageHandler.obtainMessage();
                                            message.what = WHAT_CANCEL_CAR_ALL_CURRENT_PATH;
                                            inComingMessageHandler.sendMessage(message);
                                        }
                                    });
                                    t_cancel_car_all_current_path.start();
                                }
                            }
                        }).create().show();
                break;
                */
            case R.id.linear_map_carBatteryInfo:// 小车电量
                CarBatteryInfoFragment fragment = new CarBatteryInfoFragment();
                showFragment(BoxFragment.this, fragment);
                break;

                // 源汇版本 更名为：其他
            case R.id.linear_map_wcs:// 跳转到wcs的小车操作界面
                WcsCarOperateFragment wcsCarOperateFragment = new WcsCarOperateFragment();
                showFragment(BoxFragment.this, wcsCarOperateFragment);
                break;
            /*
            case R.id.linear_map_rcs:// 跳转到rcs的小车操作界面
                RcsCarOperateFragment rcsCarOperateFragment = new RcsCarOperateFragment();
                if (errorChargings.size() != 0){
                    String strErrorCharging = "";
                    for (int i = 0;i < errorChargings.size();i++){
                        strErrorCharging = strErrorCharging + "充电桩故障 [ 充电桩类型：" + errorChargings.get(i).getType()
                                + "，充电桩ID：" + errorChargings.get(i).getNumber() + " ]\n";
                    }
                    Bundle bundle = new Bundle();
                    bundle.putString("clear_charging_error", strErrorCharging);
                    rcsCarOperateFragment.setArguments(bundle);
                }
                // 回调监听，清除充电桩故障信息
                rcsCarOperateFragment.setCallBack(new RcsCarOperateFragment.CallBackListener() {
                    @Override
                    public void clearChargingError() {
                        errorChargings.clear();
                    }
                });
                showFragment(BoxFragment.this, rcsCarOperateFragment);
                break;

            case R.id.linear_map_carLockUnLock:// 锁格/解锁
                if(!bl_isShowCarPath){
                    ToastUtil.showToast(getContext(), "请先显示小车当前路径");
//                    return;
                }else {
                    publishToAMPQ(Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_LOCK_UNLOCK);
                    bl_isSelectLockUnLock = true;

                    visibile(linear_lock_unlock);
                    gone(linear_map_carLockUnLock);
                    gone(linear_map_carBatteryInfo);
                    gone(linear_map_wcs);
                    gone(linear_map_rcs);
                    gone(linear_zoomOutIn);
                    gone(linear_map_drawAgain);
                    gone(linear_map_reset);
                    gone(linear_map_info);
                    gone(linear_map_introduction);
                }
                break;
            case R.id.btn_cancel_lockunlock:// 取消解锁或锁格
                gone(linear_lock_unlock);
                visibile(linear_map_carLockUnLock);
                visibile(linear_map_carBatteryInfo);
                // 源汇定制
                visibile(linear_map_wcs);
//                visibile(linear_map_rcs);
                visibile(linear_zoomOutIn);
                visibile(linear_map_drawAgain);
                visibile(linear_map_reset);
                visibile(linear_map_info);
                visibile(linear_map_introduction);
                bl_isSelectLockUnLock = false;
                lock_unlock_pos.clear();
                boxView.setLockUnLockArea(lock_unlock_pos);
                break;
            case R.id.btn_confirm_lockunlock:// 确定锁格或解锁
                if(lock_unlock_pos.size() == 0){
                    ToastUtil.showToast(getContext(),"请选择锁格/解锁区域");
                    return;
                }

                new AlertDialog.Builder(getContext())
                        .setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                LogUtil.e("sure",lock_unlock_pos.toString());
                                if(which == 0){// 点击了锁格
                                    showDialog("锁格...");
                                    try {
                                        Map<String, Object> message = new HashMap<>();
                                        message.put("unAvailableAddressList", lock_unlock_pos);
                                        queue.putLast(message);// 发送消息到MQ
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }else if(which == 1){// 点击了解锁
                                    // 弹出进度框提示正在初始化
                                    showDialog("解锁...");
                                    try {
                                        Map<String, Object> message = new HashMap<>();
                                        message.put("availableAddressList", lock_unlock_pos);
                                        queue.putLast(message);// 发送消息到MQ
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                                dialog.dismiss();

                                lock_unlock_pos.clear();
                                boxView.setLockUnLockArea(null);
                                bl_isSelectLockUnLock = false;
                                gone(linear_lock_unlock);
                                visibile(linear_map_carLockUnLock);
                                visibile(linear_map_carBatteryInfo);
                                // 源汇定制
                                visibile(linear_map_wcs);
//                                visibile(linear_map_rcs);
                                visibile(linear_zoomOutIn);
                                visibile(linear_map_drawAgain);
                                visibile(linear_map_reset);
                                visibile(linear_map_info);
                                visibile(linear_map_introduction);
                            }
                        }).create().show();
                break;
                */
            case R.id.imgBtn_errorTip:// 错误反馈提示
                if (vibrator != null){
                    vibrator.cancel();// 取消震动
                }
                View view_error = getLayoutInflater().inflate(R.layout.view_error, null);
                new AlertDialog.Builder(getContext()).setView(view_error).create().show();
                // 错误提示内容控件
                tv_error_content = view_error.findViewById(R.id.tv_error_tip_content);
                // 获取错误提示内容，赋值给变量
                getAndSetErrorContent();

                // 开启定时任务刷新界面数据
                if (task_refresh_error_data == null){
                    task_refresh_error_data = new TimerTask() {
                        @Override
                        public void run() {
                            Message message = inComingMessageHandler.obtainMessage();
                            message.what = WHAT_REFRESH_ERROR_DATA;
                            inComingMessageHandler.sendMessage(message);
                        }
                    };

                    if (timer_refresh_error_data == null){
                        timer_refresh_error_data = new Timer();
                    }
                    timer_refresh_error_data.schedule(task_refresh_error_data, 2000, 2000);
                }
                break;
            case R.id.tv_problem_solve_or_not:// 问题确认解决后，隐藏反馈提示界面
                new AlertDialog.Builder(getContext())
                        .setIcon(R.mipmap.app_icon)
                        .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                        .setMessage(getResources().getString(R.string.boxfragment_Problem_has_been_solved_cancel_the_error_message))
                        .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // 停止定时任务并清空
                                if (task_refresh_error_data != null){
                                    task_refresh_error_data.cancel();
                                    task_refresh_error_data = null;
                                }
                                // 清空数据并隐藏布局
                                robotErrorEntityList.clear();
                                robotCloseConnEntityList.clear();
                                noMoveTimeoutEntityList.clear();
                                errorChargings.clear();
                                gone(linear_error_tip);
                                dialog.dismiss();
                            }
                        }).create().show();
                break;


        }
    }

    /**
     * 初始化小车数据
     */
    private void initCarData() {

        if(bl_initData){
            // 中断原来存在的消费线程
//                            interruptThread(subscribeThread);
//                            interruptThread(threadChargingTask);
//                            interruptThread(threadShowAllCarCurrentPath);
            setUpConnectionFactory();
            //开启消费者线程  
            subscribe(inComingMessageHandler);
            gone(linear_operate);// 小车监控开始时设置绘制步骤布局不可见
            gone(view_border);// 步骤布局的边线设置不可见

//            tv_cancelAllCarCurrentPath.setVisibility(View.VISIBLE);
//            tv_showAllCarCurrentPath.setVisibility(View.VISIBLE);

//            subscribeChargingTask(inComingMessageHandler);// 充电任务监听
            subscribeChargingError(inComingMessageHandler);// 充电桩故障监听
//            subscribeProblemFeedback(inComingMessageHandler);// 小车错误信息反馈监听（扫不到pod）
//            subscribeNoMoveTimeout(inComingMessageHandler);// 小车位置不改变超时
//            subscribeErrorCloseConnection(inComingMessageHandler);// 小车断开连接

            // 工厂版本 添加安全区域锁格显示
            /**说明：开放小车路径实时监听是为了绘制运维工程师手动锁住的格子。手动建立、解除安全区域的时候会用到*/
            subscribeShowAllCarCurrentPath(inComingMessageHandler);

            bl_isShowCarPath = false;
            bl_isSelectLockUnLock = false;
//          closeConnection(connection_showAllCarCurrentPath);

            gone(linear_lock_unlock);
//            visibile(linear_map_carLockUnLock);
            visibile(linear_map_carBatteryInfo);
            // 工厂版定制
            visibile(linear_map_wcs);
//            visibile(linear_map_rcs);
            visibile(linear_zoomOutIn);
            visibile(linear_map_drawAgain);
            visibile(linear_map_reset);
            visibile(linear_map_info);
            visibile(linear_map_introduction);
//            visibile(linear_error_tip);

            // 显示小车的的锁格和未锁格路径信息
            /*
            if(!bl_isShowCarPath){
                setUpConnectionFactory();// 连接设置
                subscribeShowAllCarCurrentPath(inComingMessageHandler);
            }else {
                ToastUtil.showToast(getContext(),"小车锁格、未锁格路径已经显示");
            }
            */

            // 当地图绘制完成且小车监控开始后，隐藏进度提示开始小车监控
            ProgressBarUtil.dissmissProgressBar();
            ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_Map_drawn_successfully));
        }else {

//            ToastUtil.showToast(getContext(), "请先绘制地图");
            return;
        }

    }

    /**
     * 显示小车的锁格、未锁格路径信息
     */
//    private void showCarAllPathInfo() {
//        new AlertDialog.Builder(getContext())
//                .setIcon(R.mipmap.app_icon)
//                .setTitle("提示")
//                .setCancelable(false)
//                .setMessage(getResources().getString(R.string.str_showAllCarLockUnlockPath) + "？")
//                .setPositiveButton("否", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
//                    }
//                })
//                .setNegativeButton("是", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                        if(!bl_isShowCarPath){
//                            setUpConnectionFactory();// 连接设置
//                            subscribeShowAllCarCurrentPath(inComingMessageHandler);
//                        }else {
//                            ToastUtil.showToast(getContext(),"小车锁格、未锁格路径已经显示");
//                        }
//                    }
//                }).create().show();
//    }

    /**
     * 充电桩故障监听
     * @param handler
     */
    private void subscribeChargingError(final Handler handler) {

        threadChargingError = new Thread(new Runnable() {
            @Override
            public void run() {

                try{
                    if (connection_chargingError != null && connection_chargingError.isOpen()){
                        connection_chargingError.close();
                    }
                    connection_chargingError = factory.newConnection();
                    Channel channel = connection_chargingError.createChannel();
                    channel.basicQos(1);// 一次消费一条消息，消费完再接收下一条消息

                    String queueName = System.currentTimeMillis() + "QN_CHARGE_ERROR";
                    channel.exchangeDeclare(Constants.EXCHANGE, "direct", true);
                    AMQP.Queue.DeclareOk q = channel.queueDeclare(queueName, true, false, true, null);
                    channel.queueBind(q.getQueue(), Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_CHARGING_ERROR);
                    Consumer consumer = new DefaultConsumer(channel){
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            super.handleDelivery(consumerTag, envelope, properties, body);
                            Message message = handler.obtainMessage();
                            message.what = WHAT_ROBOT_CHARGING_ERROR;
                            message.obj = body;
                            handler.sendMessage(message);
                        }
                    };

                    channel.basicConsume(q.getQueue(), true, consumer);

                }catch (Exception e){
                    e.printStackTrace();
                    try {
                        Thread.sleep(5000);
                    }catch (InterruptedException e1){
                        e1.printStackTrace();
                    }
                }

            }
        });

        threadChargingError.start();

    }

    /**
     * 获取错误提示内容并赋值变量
     */
    private void getAndSetErrorContent() {

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//        String date = format.format(new Date(System.currentTimeMillis()));
        /*
        // 扫不到pod错误反馈
        String strPodError = "扫不到pod错误反馈";
        if (robotErrorEntityList.size() != 0){
            for (int  i = 0;i < robotErrorEntityList.size();i++){
                strPodError = strPodError + "\n\n" + "小车id：" + robotErrorEntityList.get(i).getRobotID()
                        + "\n" + "反馈时间：" + format.format(new Date(robotErrorEntityList.get(i).getErrorTime()))
                        + "\n" + "将要扫的pod：" + robotErrorEntityList.get(i).getPodCodeID()
                        + "\n" + "当前扫到的pod：" + robotErrorEntityList.get(i).getCurPodID()
                        + "\n\n" + "*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*";
            }
        }else {
            strPodError = strPodError + "\n\n" + "无" + "\n\n" + "*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*";
        }

        // 小车位置不改变超时
        String strNoMoveTimeout = "小车位置不改变超时";
        if (noMoveTimeoutEntityList.size() != 0){
            for (int i = 0;i < noMoveTimeoutEntityList.size();i++){
                strNoMoveTimeout = strNoMoveTimeout + "\n\n" + "小车id：" + noMoveTimeoutEntityList.get(i).getRobotID()
                        + "\n" + "ip地址：" + noMoveTimeoutEntityList.get(i).getIp()
                        + "\n" + "端口：" + noMoveTimeoutEntityList.get(i).getPort()
                        + "\n\n" + "*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*";
            }
        }else {
            strNoMoveTimeout = strNoMoveTimeout + "\n\n" + "无" + "\n\n" + "*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*";
        }

        // 小车断开连接
        String strCloseConn = "小车断开连接";
        if (robotCloseConnEntityList.size() != 0){
            for (int i = 0;i < robotCloseConnEntityList.size();i++){
                strCloseConn = strCloseConn + "\n\n" + "小车id：" + robotCloseConnEntityList.get(i).getRobotID()
                        + "\n" + "反馈时间：" + format.format(new Date(robotCloseConnEntityList.get(i).getTime()))
                        + "\n" + "ip地址：" + robotCloseConnEntityList.get(i).getIp()
                        + "\n" + "端口：" + robotCloseConnEntityList.get(i).getPort()
                        + "\n\n" + "*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*";
            }
        }else {
            strCloseConn = strCloseConn + "\n\n" + "无" + "\n\n" + "*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*";
        }
        */

        // 充电桩故障
        String strChargingError = getResources().getString(R.string.boxfragment_Charging_pile_fault);
        if (errorChargings.size() != 0){
            for (int i = 0;i < errorChargings.size();i++){
                strChargingError = strChargingError + "\n\n" + getResources().getString(R.string.boxfragment_The_ID_of_the_charging_pile) + errorChargings.get(i).getNumber()
                        + "\n" + "statusIndex：" + errorChargings.get(i).getStatusIndex()
                        + "\n" + getResources().getString(R.string.boxfragment_Description) + errorChargings.get(i).getStatusName()
                        + "\n" + getResources().getString(R.string.boxfragment_time) + format.format(new Date(errorChargings.get(i).getTime()));
            }
        }else {
            strChargingError = strChargingError + "\n\n" + getResources().getString(R.string.boxfragment_Did_not_find_fault) + "\n\n" + "*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*";
        }

//        strErrorContent = strPodError + "\n\n" + strNoMoveTimeout + "\n\n" + strCloseConn + "\n\n" + strChargingError;
        strErrorContent = strChargingError;

        tv_error_content.setText(strErrorContent);
    }

    /**
     * 开启消费线程获取小车断开连接的消息
     * @param handler
     */
    private void subscribeErrorCloseConnection(final Handler handler) {

        threadErrorCloseConnection = new Thread(new Runnable() {
        @Override
        public void run() {

            try{
                if (connection_errorCloseConnection != null && connection_errorCloseConnection.isOpen()){
                    connection_errorCloseConnection.close();
                }
                connection_errorCloseConnection = factory.newConnection();
                Channel channel = connection_errorCloseConnection.createChannel();
                channel.basicQos(1);// 一次消费一条消息，消费完再接收下一条消息

                String queueName = System.currentTimeMillis() + "QN_DISCONNECT_ERROR";
                channel.exchangeDeclare(Constants.EXCHANGE, "direct", true);
                AMQP.Queue.DeclareOk q = channel.queueDeclare(queueName, true, false, true, null);
                channel.queueBind(q.getQueue(), Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_CLOSE_CONNECTION);
                Consumer consumer = new DefaultConsumer(channel){
                    @Override
                    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                        super.handleDelivery(consumerTag, envelope, properties, body);
                        Message message = handler.obtainMessage();
                        message.what = WHAT_ERROR_CLOSE_CONNECTION;
                        message.obj = body;
                        handler.sendMessage(message);
                    }
                };

                channel.basicConsume(q.getQueue(), true, consumer);

            }catch (Exception e){
                e.printStackTrace();
                try {
                    Thread.sleep(5000);
                }catch (InterruptedException e1){
                    e1.printStackTrace();
                }
            }

        }
    });

        threadErrorCloseConnection.start();

}

    /**
     * 开启消费线程获取小车位置不改变超时的消息
     * @param handler
     */
    private void subscribeNoMoveTimeout(final Handler handler) {

        threadNoMoveTimeout = new Thread(new Runnable() {
            @Override
            public void run() {

                try{
                    if (connection_noMoveTimeout != null && connection_noMoveTimeout.isOpen()){
                        connection_noMoveTimeout.close();
                    }
                    connection_noMoveTimeout = factory.newConnection();
                    Channel channel = connection_noMoveTimeout.createChannel();
                    channel.basicQos(1);// 一次消费一条消息，消费完再接收下一条消息

                    String queueName = System.currentTimeMillis() + "QN_NOMOVE_TIMEOUT_ERROR";
                    channel.exchangeDeclare(Constants.EXCHANGE, "direct", true);
                    AMQP.Queue.DeclareOk q = channel.queueDeclare(queueName, true, false, true, null);
                    channel.queueBind(q.getQueue(), Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_NOMOVE_TIMEOUT);
                    Consumer consumer = new DefaultConsumer(channel){
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            super.handleDelivery(consumerTag, envelope, properties, body);
                            Message message = handler.obtainMessage();
                            message.what = WHAT_ROBOT_NOMOVE_TIMEOUT;
                            message.obj = body;
                            handler.sendMessage(message);
                        }
                    };

                    channel.basicConsume(q.getQueue(), true, consumer);

                }catch (Exception e){
                    e.printStackTrace();
                    try {
                        Thread.sleep(5000);
                    }catch (InterruptedException e1){
                        e1.printStackTrace();
                    }
                }

            }
        });

        threadNoMoveTimeout.start();

    }

    /**
     * 开启消费线程获取问题反馈的消息
     * @param handler
     */
    private void subscribeProblemFeedback(final Handler handler) {

        threadProblemFeedback = new Thread(new Runnable() {
            @Override
            public void run() {

                try{
                    if (connection_problemFeedback != null && connection_problemFeedback.isOpen()){
                        connection_problemFeedback.close();
                    }
                    connection_problemFeedback = factory.newConnection();
                    Channel channel = connection_problemFeedback.createChannel();
                    channel.basicQos(1);// 一次消费一条消息，消费完再接收下一条消息

                    String queueName = System.currentTimeMillis() + "QN_NOTSCAN_POD_ERROR";
                    channel.exchangeDeclare(Constants.EXCHANGE, "direct", true);
                    AMQP.Queue.DeclareOk q = channel.queueDeclare(queueName, true, false, true, null);
                    channel.queueBind(q.getQueue(), Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_PROBLEM_FEEDBACK);
                    Consumer consumer = new DefaultConsumer(channel){
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            super.handleDelivery(consumerTag, envelope, properties, body);
                            Message message = handler.obtainMessage();
                            message.what = WHAT_ROBOT_ERROR;
                            message.obj = body;
                            handler.sendMessage(message);
                        }
                    };

                    channel.basicConsume(q.getQueue(), true, consumer);

                }catch (Exception e){
                    e.printStackTrace();
                    try {
                        Thread.sleep(5000);
                    }catch (InterruptedException e1){
                        e1.printStackTrace();
                    }
                }

            }
        });

        threadProblemFeedback.start();

    }

    /**
     * 开启消费线程获取所有充电桩的充电任务数据
     * @param handler
     */
    private void subscribeChargingTask(final Handler handler) {
        threadChargingTask = new Thread(new Runnable() {
            @Override
            public void run() {

                if (task_clear_charge_data == null){
                    task_clear_charge_data = new TimerTask() {
                        @Override
                        public void run() {

                            Message message = inComingMessageHandler.obtainMessage();
                            message.what =  WHAT_CLEAR_CHARGE_DATA;
                            inComingMessageHandler.sendMessage(message);

                        }
                    };
                }

                if (timer_clear_charge_data == null){
                    timer_clear_charge_data = new Timer();
                }

                timer_clear_charge_data.schedule(task_clear_charge_data, 2000, 1000);// 延迟2s,并每隔3s后执行任务

                try {
                    if(connection_chargingTask != null && connection_chargingTask.isOpen()){
                        connection_chargingTask.close();
                    }
                    connection_chargingTask = factory.newConnection();
                    Channel channel = connection_chargingTask.createChannel();
                    channel.basicQos(1);

                    // 创建随机队列，可持续，自动删除
                    String queueName = System.currentTimeMillis() + "QN_CHARGE_TASK";
                    channel.exchangeDeclare(Constants.MQ_EXCHANGE_CHARGINGPILE, "direct", true);
                    AMQP.Queue.DeclareOk q = channel.queueDeclare(queueName, true, false, true, null);// 声明共享队列
                    channel.queueBind(q.getQueue(), Constants.MQ_EXCHANGE_CHARGINGPILE, Constants.MQ_ROUTINGKEY_CHARGINGPILE);

                    Consumer consumer = new DefaultConsumer(channel){
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            super.handleDelivery(consumerTag, envelope, properties, body);

                            // 发消息通知UI更新
                            Message message = handler.obtainMessage();
                            message.obj = body;
                            message.what = WHAT_CHARGING_TASK;
                            handler.sendMessage(message);
                        }
                    };
                    channel.basicConsume(q.getQueue(), true, consumer);

                } catch (Exception e){
                    e.printStackTrace();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });

        threadChargingTask.start();
    }

    /**
     * 开启消费线程获取所有小车当前的路径信息（展示锁格和未锁格状态区域）
     * @param handler
     */
    private void subscribeShowAllCarCurrentPath(final Handler handler) {

        threadShowAllCarCurrentPath = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(connection_showAllCarCurrentPath != null && connection_showAllCarCurrentPath.isOpen()){
                        connection_showAllCarCurrentPath.close();
                    }
                    connection_showAllCarCurrentPath = factory.newConnection();
                    Channel channel = connection_showAllCarCurrentPath.createChannel();
                    channel.basicQos(0,1,false);

                    // 创建随机队列，可持续，自动删除
                    String queueName = System.currentTimeMillis() + "QN_SHOW_CAR_PATH";
                    channel.exchangeDeclare(Constants.EXCHANGE, "direct", true);
                    AMQP.Queue.DeclareOk q = channel.queueDeclare(queueName, true, false, true, null);// 声明共享队列
                    channel.queueBind(q.getQueue(), Constants.EXCHANGE, Constants.MQ_ROUTINGKEY_CARPATH);

                    Consumer consumer = new DefaultConsumer(channel){
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            super.handleDelivery(consumerTag, envelope, properties, body);

                            // 发消息通知UI更新
                            Message message = handler.obtainMessage();
                            message.obj = body;
                            message.what = WHAT_SHOW_ALL_CAR_CURRENT_PATH;
                            handler.sendMessage(message);
                        }
                    };
                    channel.basicConsume(q.getQueue(), true, consumer);

                } catch (Exception e){
                    e.printStackTrace();
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });

        threadShowAllCarCurrentPath.start();
    }

    /**
     * 重选清空数据
     */
    private void clearData() {
        carList.clear();// 小车信息集合
        podList.clear();// pod信息集合
        unWalkedList.clear();// 不可走区域坐标集合
        workStackList.clear();// 停止点集合，可以用来标识工作栈
        rotateList.clear();// 旋转区坐标集
        storageList.clear();// 存储区坐标集
        map_work_site_uuid.clear();
        storageEntityList.clear();// 创建集合保存所有仓库所有的信息
        chargingPileList.clear();// 声明集合保存充电桩的数据
        carRouteMap.clear();// 小车的路径map集
        carCurrentPathEntityList.clear();// 小车路径信息
        chargingTaskEntityList.clear();// 充电桩的充电任务信息
        lock_unlock_pos.clear();

        bl_isShowCarPath = false;

        boxView.setPodData(null, null, null,
                null, null, null, null, null);
        boxView.setCarAndPodData(null, null);
        boxView.setCarRouteData(null);
        boxView.setCarCurrentPath(null, null, null, null);
        boxView.setChargeData(null, null);
        boxView.setLockUnLockArea(null);

        // 停止定时任务
        stopTaskClearChargeData();
        stopTaskRefreshErrorData();

        if (timer_refresh_error_data != null){
            timer_refresh_error_data.cancel();
            timer_refresh_error_data = null;
        }
        if (timer_clear_charge_data != null){
            timer_clear_charge_data.cancel();
            timer_clear_charge_data = null;
        }
    }

    /**
     * 弹出popupwindow显示仓库名称和地图名称
     */
    private void showDialogAboutMapInfo() {

        // 构建布局
        pop_view_mapInfo = getLayoutInflater().inflate(R.layout.popupwindow_map_info, null);
        // 设置数据
        TextView tv_storage_map_name = pop_view_mapInfo.findViewById(R.id.tv_storage_map_name);
        tv_storage_map_name.setText(strStorageMapName);
        // 弹出对话框显示地图信息
        new AlertDialog.Builder(getContext())
                .setView(pop_view_mapInfo)
                .create().show();

    }

    /**
     * 关闭RabbitMQ通信对应的连接
     * @param connection
     */
    private void closeConnection(final Connection connection) {

        threadCloseConnection = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(connection != null && connection.isOpen()){
                        connection.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Message message = inComingMessageHandler.obtainMessage();
                message.what = WHAT_CLOSE_CONNECTION;
                inComingMessageHandler.sendMessage(message);
            }
        });

        threadCloseConnection.start();

    }

    /**
     * 初始化仓库和地图
     */
    private void initStorageAndMap() {
        // 中断小车路径显示线程并关闭对应连接
        interruptThread(threadShowAllCarCurrentPath);
        closeConnection(connection_showAllCarCurrentPath);

        interruptThread(publishThread);// 中断发布消息线程

        // 清空锁格/解锁操作时用户点击地图上的格子点位集合并去除对应标识
        lock_unlock_pos.clear();
        boxView.setLockUnLockArea(null);

        setUpConnectionFactory();// 连接设置
        subscribeStorageMap(inComingMessageHandler);// 先创建队列接收仓库地图的数据
        publishToAMPQ(Constants.MQ_EXCHANGE_STORAGEMAP, Constants.MQ_ROUTINGKEY_STORAGEMAP_REQUEST);// publish消息给请求队列
        new AlertDialog.Builder(getContext())
                .setIcon(R.mipmap.app_icon)
                .setTitle(getResources().getString(R.string.boxfragment_warm_tip))
                .setMessage(getResources().getString(R.string.str_connect_rabbitmq) + "？")
                .setNegativeButton(getResources().getString(R.string.boxfragment_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectStorageMap();
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(getResources().getString(R.string.boxfragment_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        interruptThread(publishThread);// 中断发布线程
                        interruptThread(subscribeThread_storageMap);// 中断消费线程
                        dialog.dismiss();
                    }
                }).create().show();
    }

    /**
     * 初始化，选择仓库和地图
     */
    private void selectStorageMap() {
//        interruptThread(subscribeThread);// 再次初始化仓库情况下，中断小车消费线程
//        interruptThread(threadChargingTask);
//        interruptThread(threadShowAllCarCurrentPath);
        boxView.setVisibility(View.INVISIBLE);// 设置自定义地图view不可见
        tv_hint.setVisibility(View.VISIBLE);// 设置提示内容可见

        gone(linear_lock_unlock);
        gone(linear_map_introduction);// 隐藏地图说明项
        gone(linear_map_info);// 隐藏地图信息项
        gone(linear_zoomOutIn);// 隐藏放大和缩小图标
        gone(linear_map_reset);// 隐藏地图重置项
        gone(linear_map_drawAgain);// 隐藏地图重选项
        gone(linear_map_carLockUnLock);// 隐藏锁格/解锁项
        gone(linear_map_carBatteryInfo);// 隐藏小车电量项
        gone(linear_map_wcs);
        gone(linear_map_rcs);
        gone(linear_error_tip);// 隐藏错误反馈提示
        gone(tv_showAllCarCurrentPath);
        gone(tv_cancelAllCarCurrentPath);

        bl_initStorageMap = false;// 仓库设置状态表示还未初始化仓库地图
        bl_initData = false;// 设置状态表示地图还未绘制

        boxSizeChange = Constants.DEFAULT_BOX_SIZE;// 重置地图格子的大小

        visibile(linear_operate);// 连接RabbitMQ的按钮所在的线性布局
        visibile(view_border);
        // 将按钮的字体颜色恢复初始颜色、将按钮设置为可见
        visibile(btn_connect_rabbitmq);
        btn_connect_rabbitmq.setTextColor(Color.WHITE);

        ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.boxfragment_initializing), getResources().getColor(R.color.colorAccent));
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("requestTime", System.currentTimeMillis());// 系统当前时间
            queue.putLast(message);// publish消息
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
     * 创建消费者线程获取仓库地图数据
     * @param handler
     */
    private void subscribeStorageMap(final Handler handler) {
        subscribeThread_storageMap = new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    if(connection_storageMap != null && connection_storageMap.isOpen()){
                        connection_storageMap.close();
                    }
                    connection_storageMap = factory.newConnection();
                    Channel channel = connection_storageMap.createChannel();
                    channel.basicQos(1);
                    String queueName = System.currentTimeMillis() + "queueNameStorageMap";
                    channel.exchangeDeclare(Constants.MQ_EXCHANGE_STORAGEMAP, "direct", true);
                    AMQP.Queue.DeclareOk q = channel.queueDeclare(queueName, true, false, true, null);
                    channel.queueBind(q.getQueue(), Constants.MQ_EXCHANGE_STORAGEMAP, Constants.MQ_ROUTINGKEY_STORAGEMAP_RESPONSE);
                    Consumer consumer = new DefaultConsumer(channel){
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            super.handleDelivery(consumerTag, envelope, properties, body);

                            Map<String, Object> mapStorageMap = (Map<String, Object>) toObject(body);

//                            ToastUtil.showToast(getContext(), "获取仓库和地图数据成功了" + mapStorageMap.toString());

                            JSONObject objectStorageMap = new JSONObject(mapStorageMap);// 将map转为JsonObject结构数据
                            if(objectStorageMap.toString() != null){
                                connection_storageMap.close();// 获取仓库数据成功了，关闭连接
                                publishThread.interrupt();// 关闭发布消息线程

                                // 发消息通知handler处理，一般用作UI更新
                                Message message = handler.obtainMessage();
                                message.obj = body;
                                message.what = WHATSTORAGEMAP;
                                handler.sendMessage(message);
                            }

                        }
                    };
                    channel.basicConsume(q.getQueue(), true, consumer);
                }catch (Exception e){
                    ProgressBarUtil.dissmissProgressBar();
                    e.printStackTrace();
                    ToastUtil.showToast(getContext(), "Exception：" + e.getMessage());
                    try {
                        Thread.sleep(5000); //sleep and then try again
                    } catch (InterruptedException e1) {
                        ProgressBarUtil.dissmissProgressBar();
                        LogUtil.d("TAG_STORAGEAP_DATA","InterruptedException happened!");
                        e1.printStackTrace();
                        ToastUtil.showToast(getContext(), "Exception：" + "InterruptedException happened!");
                    }
                }
            }
        });
        subscribeThread_storageMap.start();

    }

    /**
     * 解析初始化仓库地图获取的数据
     * @param object 数据
     */
    private void parseStorageMapData(JSONObject object) {
        storageEntityList.clear();
        List<JSONObject> jsonList = new ArrayList<>();
        try {
            Iterator<String> iterator = object.getJSONObject("allWarehouseInfo").keys();
            while (iterator.hasNext()){
                String key = iterator.next();
                jsonList.add(object.getJSONObject("allWarehouseInfo").getJSONObject(key));
            }

//            Iterator<String> iterator = object.keys();
//            while (iterator.hasNext()){
//                String key = iterator.next();
//                if(!"requestTime".equals(key) && !"wcsTime".equals(key)){
//                    jsonList.add(object.getJSONObject(key));
//                }
//            }

            // 将仓库的信息解析存入实体类集合中
            for(int i = 0;i < jsonList.size();i++){
                JSONObject jsonObject = jsonList.get(i);
//                if(!"456e94fe-127d-4861-9948-cc38760801b4".equalsIgnoreCase(jsonObject.optString("warehouseId"))){
//                    continue;
//                }
                // 创建一个仓库信息实体对象
                StorageEntity storageEntity = new StorageEntity();
                storageEntity.setWarehouseId(jsonObject.optString("warehouseId"));// 仓库id
                storageEntity.setWarehouseName(jsonObject.optString("warehouseName"));// 仓库名称

                List<StorageEntity.SectionEntity> list = new ArrayList<>();
                Iterator<String> iteratorMap = jsonObject.getJSONObject("sectionMap").keys();

                LogUtil.e("jsonMap" + i, jsonObject.getJSONObject("sectionMap").toString());

                while (iteratorMap.hasNext()){
                    String key = iteratorMap.next();
                    String sectionName = (String) jsonObject.getJSONObject("sectionMap").getJSONObject(key).opt("sectionName");
                    String sectionUUID = (String) jsonObject.getJSONObject("sectionMap").getJSONObject(key).opt("sectionUUID");
                    String sectionMapId = (String) jsonObject.getJSONObject("sectionMap").getJSONObject(key).opt("sectionMapId");
                    Long sectionRcsId = jsonObject.getJSONObject("sectionMap").getJSONObject(key).optLong("sectionRcsId");

                    StorageEntity.SectionEntity sectionEntity = new StorageEntity.SectionEntity();// 创建一个地图信息实体对象
                    // 实体类对象设置数据
                    sectionEntity.setSectionName(sectionName);
                    sectionEntity.setSectionUUID(sectionUUID);
                    sectionEntity.setSectionMapId(sectionMapId);
                    sectionEntity.setSectionRcsId(sectionRcsId);

                    list.add(sectionEntity);
                }

                storageEntity.setSectionMap(list);// 仓库实体对象设置地图集
                storageEntityList.add(storageEntity);// 往仓库实体类集合中添加一个仓库实体对象
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建popupwindow，介绍说明地图的一些显示区域所代表的内容
     */
    private void showPopAboutMapIntroduction() {
        /*
        // 构建popupwindow的布局
        pop_view_mapIntroduction = getLayoutInflater().inflate(R.view_error.popupwindow_map_introduction, null);
        // 构建PopupWindow对象
//        window_mapIntroduction = new PopupWindow(pop_view_mapIntroduction, ViewGroup.LayoutParams.MATCH_PARENT, pop_height);
        window_mapIntroduction = new PopupWindow(pop_view_mapIntroduction, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        // 设置弹框的动画
        window_mapIntroduction.setAnimationStyle(R.style.pop_anim);
        // 设置背景白色
        window_mapIntroduction.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#ffffff")));
        // 设置获取焦点
        window_mapIntroduction.setFocusable(true);
        // 设置触摸区域外可消失
        window_mapIntroduction.setOutsideTouchable(true);
        // 实时更新状态
        window_mapIntroduction.update();
        // 根据偏移量确定在parent view中的显示位置
        window_mapIntroduction.showAtLocation(rl_mapView, Gravity.BOTTOM, 0, 0);
        bgAlpha(0.618f);// 设置窗口的透明度，提高用户体验
        // 设置popupwindow消失监听
        window_mapIntroduction.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                bgAlpha(1.0f);
            }
        });
        */

        pop_view_mapIntroduction = getLayoutInflater().inflate(R.layout.popupwindow_map_introduction, null);
        new AlertDialog.Builder(getContext())
                .setView(pop_view_mapIntroduction)
                .create().show();

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
     * 开启消费者线程获取地图数据
     * @param handler
     */
    private void subscribeMapData(final Handler handler) {

        threadMapData = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(connection_map != null && connection_map.isOpen()){
                        connection_map.close();
                    }
                    connection_map = factory.newConnection();
                    Channel channel = connection_map.createChannel();
                    channel.basicQos(1);// 一次只发送一个，处理完一个再发送下一个
                    String routingKeyMap = Constants.MQ_ROUTINGKEY_MAP;// 路由键
                    String exchangeMap = Constants.EXCHANGE;// 交换机名称

                    channel.exchangeDeclare(exchangeMap, "direct", true);
                    String queueName = System.currentTimeMillis() + "queueNameMap";// 客户端随机生成队列名称
                    // 声明一个可共享队列（消息不会被该队列所独占，不会被限制在这个连接中）
                    AMQP.Queue.DeclareOk qMap = channel.queueDeclare(queueName, true, false, true, null);

                    LogUtil.e("queueName_map","" + qMap.getQueue());
                    channel.queueBind(qMap.getQueue(), exchangeMap, routingKeyMap);// 将队列绑定到交换机

                    Consumer consumer = new DefaultConsumer(channel){
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            super.handleDelivery(consumerTag, envelope, properties, body);
                            Map<String, Object> mapMap = (Map<String, Object>) toObject(body);
                            if(mapMap.toString() != null){
                                LogUtil.e("TAG_MAP", mapMap.toString());
                                connection_map.close();
                                publishThread.interrupt();// 获取地图数据成功了，就中断地图数据请求线程

                                Message message = handler.obtainMessage();
//                                Bundle bundle = new Bundle();
//                                bundle.putByteArray("body", body);
                                message.obj = body;
                                message.what = WHATMAP;
                                handler.sendMessage(message);
                            }
                        }
                    };
                    channel.basicConsume(qMap.getQueue(), true, consumer);
                } catch (Exception e){
                    e.printStackTrace();
                    try {
                        Thread.sleep(5000); //sleep and then try again
                    } catch (InterruptedException e1) {
                        LogUtil.d("TAG_MAP_DATA","InterruptedException happened!");
                        e1.printStackTrace();
                    }
                }
            }
        });

        threadMapData.start();
    }

    /**
     * 设置工作站朝向停止点的角度，根据这个停止点的坐标和这个角度可以确定工作的坐标
     * @param mapMap
     */
    private void setWorkStationAngle(Map<String, Object> mapMap){

        try{

            if (mapMap.containsKey("WS_TOWARD")){

                Map<String, Object> workStationAngleMap = (Map<String, Object>) mapMap.get("WS_TOWARD");
                if (workStationAngleMap.size() != 0){

                    Iterator iterator = workStationAngleMap.keySet().iterator();
                    while (iterator.hasNext()){
                        Object key = iterator.next();// 工作站对应的停止点坐标
                        int angle = Integer.parseInt(String.valueOf(workStationAngleMap.get(key)));// 工作站朝向停止点的角度

                        WorkStationEntity entity = new WorkStationEntity();
                        entity.setStopPos(Integer.parseInt(String.valueOf(key)));
                        entity.setAngle(angle);
                        workStationEntityList.add(entity);

                    }

                }

            }

        }catch (Exception e){
            e.printStackTrace();
            ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_Workstation_to_Angle_for_exceptions) + e.getMessage());
        }

    }

    /**
     * 设置非存储区货架数据
     * (费舍尔项目中 获取的这部分数据也可能包含是存储区的货架数据，因为储位货架在拣货的时候是也会出现在这部分数据中的)
     * @param mapMap
     */
    private void setUnStoragePodsData(Map<String, Object> mapMap){

        try {

            if (mapMap.containsKey("unStoragePods")){

                List<Map<String, Object>> unStoragePodsList = (List<Map<String, Object>>) mapMap.get("unStoragePods");
                if (unStoragePodsList.size() != 0){
                    for (int i = 0;i < unStoragePodsList.size();i++){

                        Map<String, Object> map = unStoragePodsList.get(i);
                        Iterator iterator = map.keySet().iterator();

                        while (iterator.hasNext()){

                            Object key = iterator.next();// 非存储区货架id
                            int address = Integer.parseInt(String.valueOf(map.get(key)));// 非存储区货架地标

                            // 添加非存储区货架实体对象
                            PodEntity podEntity = new PodEntity();
                            podEntity.setPodId(Integer.parseInt(String.valueOf(key)));
                            podEntity.setPodPos(address);
//                            podEntity.setStoragePod(false);

                            podList.add(podEntity);// 往pod集合中添加非存储区货架实体对象

                        }

                    }
                }

            }

        }catch (Exception e){

            e.printStackTrace();
//            ToastUtil.showToast(getContext(), "非存储区货架数据异常");

        }

    }

    /**
     * 设置充电桩位置的数据
     * @param mapMap
     */
    private void setChargerData(Map<String, Object> mapMap) {
        try{
            List<Map<String, Object>> listCharger = (List<Map<String, Object>>) mapMap.get("chargers");// 充电桩的数据获取
            for(int i = 0;i < listCharger.size();i++){
                Map<String, Object> map = listCharger.get(i);
                ChargingPileEntity entity = new ChargingPileEntity();
                // 取值
                int chargerType = Integer.parseInt(String.valueOf(map.get("chargerType")));
                int toward = Integer.parseInt(String.valueOf(map.get("toward")));
                int chargerID = Integer.parseInt(String.valueOf(map.get("chargerID")));
                String UUID = String.valueOf(map.get("UUID"));
                String addrCodeID = String.valueOf(map.get("addrCodeID"));

                // 实体类设值
                entity.setChargerType(chargerType);
                entity.setToward(toward);
                entity.setChargerID(chargerID);
                entity.setUUID(UUID);
                entity.setAddrCodeID(addrCodeID);
                // 添加充电桩实体对象
                chargingPileList.add(entity);
            }
            LogUtil.e("Chargers = ",chargingPileList.toString());
        }catch (Exception e){
            e.printStackTrace();
            ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_charging_pile_data_parsing_exceptions) + e.getMessage());
        }
    }

    /**
     * 设置工作站的uuid数据：包含所有工作站的uuid的map
     * @param mapMap
     */
    private void setWorkSiteUUID(Map<String, Object> mapMap) {

        try {
            if(mapMap.containsKey("workStationMap")){
                map_work_site_uuid = (Map<String, String>) mapMap.get("workStationMap");// 如果key存在，就获取key对应的值
            }else {
                map_work_site_uuid = null;// 不存在key，这时赋值为null
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * 设置存储区的坐标数据
     * @param mapMap
     */
    private void setStorageData(Map<String, Object> mapMap) {
        storageList.clear();
        if(mapMap.containsKey("storageAddrList")){
            storageList = (List<Long>) mapMap.get("storageAddrList");
        }
    }

    /**
     * 设置工作栈的数据，拿到停止点的坐标，工作栈根据该坐标来进行绘制
     * @param mapMap
     */
    private void setWorkStackData(Map<String, Object> mapMap) {

        Map<String, List<Long>> workStackMap = (Map<String, List<Long>>) mapMap.get("stationAndTurnArea");
        Iterator iterator = workStackMap.keySet().iterator();// 迭代器
        List<Integer> stoplist = new ArrayList<>();
        List<List<Long>> rotateList = new ArrayList<>();
        while (iterator.hasNext()){// 遍历获取key和value
            String key = String.valueOf(iterator.next());

            List<Long> list = workStackMap.get(key);
//            Log.e("key", key + "");
//            Log.e("list", list.toString());
            // 将停止点的集合存入集合中
            stoplist.add(Integer.parseInt(key));
            // 存放旋转区的坐标集合
            rotateList.add(list);
        }
        workStackList = stoplist;
        this.rotateList = rotateList;
    }

    /**
     * 设置不可走区域的坐标集合
     * @param mapMap
     */
    private void setUnWalkedCellData(Map<String, Object> mapMap) {
        unWalkedList = (List<Long>) mapMap.get("unWalkedCell");// 获取不可走区域坐标集合
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


    /**
     * 将byte数组转化为Object对象
     * @return
     */
    private Object toObject(byte[] bytes){
        Object object = null;
        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);// 创建ByteArrayInputStream对象
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);// 创建ObjectInputStream对象
            object = objectInputStream.readObject();// 从objectInputStream流中读取一个对象
            byteArrayInputStream.close();// 关闭输入流
            objectInputStream.close();// 关闭输入流
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return object;// 返回对象
    }

    /**
     * 设置pod数据
     * @param mapMap
     */
    public void setPodData(Map<String, Object> mapMap) {

        Map<Long, Integer> podAngleMap = new HashMap<>();
        if(mapMap.containsKey("podsDirect")){
            podAngleMap = (Map<Long, Integer>) mapMap.get("podsDirect");// 获取初始化pod的角度集合，用来确定pod面的位置
        }

        List<Map<String, Object>> list = (List<Map<String, Object>>) mapMap.get("pods");//{38=227}, {36=254}, {32=251},
        if(list.size() != 0){
            for(int i = 0;i < list.size();i++){
                Map<String, Object> map = list.get(i);
                Iterator iterator = map.keySet().iterator();// 返回该map键值集合的迭代器
                while (iterator.hasNext()){
                    long key = Long.parseLong(String.valueOf(iterator.next()));
                    int podId = Integer.parseInt(String.valueOf(key));// pod的Id

                    String value = String.valueOf(map.get(key));// 根据键值获取value
                    int podPos = Integer.parseInt(value);// pod的地图坐标

                    // 设置pod实体类
                    PodEntity podEntity = new PodEntity();
                    podEntity.setPodId(podId);
                    podEntity.setPodPos(podPos);

                    podList.add(podEntity);// 往pod集合中添加实体对象
                }

            }
        }

        // 往podList集合的对象中添加一个角度属性
        if(podAngleMap.size() != 0){
            Iterator iterator = podAngleMap.keySet().iterator();// 获取迭代器对象
            while (iterator.hasNext()){
                long key = Long.parseLong(String.valueOf(iterator.next()));
                int podId = Integer.parseInt(String.valueOf(key));// pod的id

                int podAngle = Integer.parseInt(String.valueOf(podAngleMap.get(key)));// pod的角度

                if(podList.size() != 0){
                    for(int j = 0;j < podList.size();j++){
                        if(podId == podList.get(j).getPodId()){//如果是同一个pod
                            // 创建新的pod实体并设置相应属性
                            PodEntity entity = new PodEntity();
                            entity.setPodId(podList.get(j).getPodId());
                            entity.setPodPos(podList.get(j).getPodPos());
                            entity.setPodAngle(podAngle);
                            // 移除原来实体
                            podList.remove(j);
                            // 在对应位置添加新的实体
                            podList.add(j, entity);
                        }
                    }
                }
            }
        }
    }

    // car上下左右移一格所需参数
    private String act;
    private int robotId;
    /**
     * 小车上移一格
     */
    private void call_carMoveUp(int robotID){
        robotId = robotID;
        act = "up";
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
    private void call_carMoveDown(int robotID){
        robotId = robotID;
        act = "down";
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
    private void call_carMoveLeft(int robotID){
        robotId = robotID;
        act = "left";
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
    private void call_carMoveRight(int robotID){
        robotId = robotID;
        act = "right";
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

    private String sectionId;// 小车重发路径参数

    /**
     * 小车重发任务
     * @param robotId   小车id
     */
    private void methodResendOrder(final int robotId) {

        ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.boxfragment_Resend_task), getResources().getColor(R.color.colorAccent));

//        String url = rootAddress + getResources().getString(R.string.url_resendOrder)
//                + "sectionId=" + sectionId + "&robotId=" + robotId;

        String url = rootAddress + getResources().getString(R.string.url_resendOrder)
                + "robotId=" + robotId;

        StringRequest request = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        ProgressBarUtil.dissmissProgressBar();

                        ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_Resend_task_successful));
//                        if (!TextUtils.isEmpty(response.toString())){
//
//                            String strRes = response.toString();
//                            if (strRes.contains("未注册")){
//                                ToastUtil.showToast(getContext(), response.toString());
//                                return;
//                            }else {
//                                ToastUtil.showToast(getContext(), strRes);
//                            }
//
//                        }

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ProgressBarUtil.dissmissProgressBar();
//                        ToastUtil.showToast(getContext(), "重发任务over_e");
                    }
                });

        requestQueue.add(request);


    }

    private String workStationId;
    /**
     * 显示当前过来的POD和面
     */
    private void call_showPod(String str_workStationId){
        workStationId = str_workStationId;
        ProgressBarUtil.showProgressBar(getContext(), getResources().getString(R.string.boxfragment_Getting_pod_information), getResources().getColor(R.color.colorAccent));
        String url = rootAddress + getResources().getString(R.string.url_showPod)
                + "sectionId=" + sectionId + "&workStationId=" + workStationId;
        LogUtil.e("url_showPod",url);
        final JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                ProgressBarUtil.dissmissProgressBar();
                try {
                    podName = response.optString("pod");
                    strWorkStationId = response.optString("workstation");

                    // 发消息给handler，执行释放pod操作
                    Message message = inComingMessageHandler.obtainMessage();
                    message.what = WHAT_SHOW_POD;
                    inComingMessageHandler.sendMessage(message);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                ToastUtil.showToast(getContext(),getResources().getString(R.string.boxfragment_Pod_information_retrieval_failed));
                ProgressBarUtil.dissmissProgressBar();
            }
        });
        //将JsonObjectRequest对象添加到RequestQueue里面s
        requestQueue.add(request);

    }

    private String podName;
    private String force = "false";
    private String strWorkStationId;

    /**
     * 释放pod
     */
    private void call_releasePod(){
        showDialog(getResources().getString(R.string.boxfragment_Pod_is_being_released));
//        String url = rootAddress + getResources().getString(R.string.url_releasePod)
//                + "sectionId=" + sectionId + "&podName=" + podName + "&force=" + force + "&workStationId=" + strWorkStationId;

        String url = rootAddress + getResources().getString(R.string.url_releasePod)
                + "sectionId=" + sectionId + "&podName=" + podName + "&workStationId=" + strWorkStationId;
        LogUtil.e("url_releasePod",url);
        StringRequest request = new StringRequest(Request.Method.GET, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                dissMissDialog();
                if(!TextUtils.isEmpty(response)){
                    ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_Workstation_release_pod_success));
                }else {
                    ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_Pod_release_results_returned_empty));
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                ToastUtil.showToast(getContext(), getResources().getString(R.string.boxfragment_Pod_release_failed));
                dissMissDialog();
                error.printStackTrace();
            }
        });
        requestQueue.add(request);// 添加这条请求
    }


    boolean bl_carRouteIsEmpty = false;// false表示小车路径信息不为空
    /**
     *  检查小车的状态（这里有小车的路径信息）
     * @param robotID 小车id
     * @param flag 一个标志 0表示list参数无用，反则表示需要用到list参数
     */
    /*
    private void call_checkCarState(int robotID, final int flag){

        bl_carRouteIsEmpty = false;// 每次获取小车路径信息的时候都需要先重置该值为false
        pDialog.setMessage("获取小车当前路径信息...");
        robotId = robotID;
        String url = rootAddress + getResources().getString(R.string.url_checkCarState)
                + "sectionId=" + sectionId + "&robotId=" + robotId;
        LogUtil.e("url_check", url);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                pDialog.dismiss();// 消失进度框
                if(response.toString() != null){
                    try {
                        if(response.getString("isSuccess").equals("true")){
                            if(!response.getJSONObject("reInfo").isNull("orderPath")){// reInfo字段对应的jsonObject下orderPath存在
                                JSONArray carRouteArray = response.getJSONObject("reInfo").getJSONArray("orderPath");// 路径信息集合
                                List<Long> carRouteList = new ArrayList<>();
                                for(int i = 0;i < carRouteArray.length();i++){
                                    long l = carRouteArray.getLong(i);
                                    carRouteList.add(l);// 添加该路径值
                                }
                                if(flag != 0){// 赋值获取的小车路径,绘制路径要用到该集合
                                    car_route_list = carRouteList;
                                }
                                if(carRouteList.size() != 0){
                                    str_carPath = carRouteList.toString();
                                }else {
                                    bl_carRouteIsEmpty = true;// 此时小车路径信息为空
                                    str_carPath = getResources().getString(R.string.str_carRouteEmpty);
                                }
                            }else{
                                bl_carRouteIsEmpty = true;// 小车路径信息为空
                                str_carPath = getResources().getString(R.string.str_carRouteEmpty);
                            }
                        }else if(response.getString("isSuccess").equals("false")){
                            str_carPath = getResources().getString(R.string.str_carRouteEmpty);
                            bl_carRouteIsEmpty = true;// 小车路径信息为空
                        }

                        if(flag == 0){
                            // 给handler发消息,更新路径信息查看
                            Message message = inComingMessageHandler.obtainMessage();
                            message.what = WHAT_CAR_ROUTE;
                            inComingMessageHandler.sendMessage(message);
                        }else if(flag == 1){

                            if(bl_carRouteIsEmpty){
                                ToastUtil.showToast(getContext(), "当前小车路径信息为空");
                                return;// 返回，不执行下面的操作
                            }
                            // 显示地图上小车的路径信息
                            Message message = inComingMessageHandler.obtainMessage();
                            message.what = WHAT_CAR_ROUTE_SHOW;
                            inComingMessageHandler.sendMessage(message);
                        }
                    }catch (Exception e){
                        ToastUtil.showToast(getContext(),"路径信息数据解析异常");
                        e.printStackTrace();
                    }
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                pDialog.dismiss();// 消失进度框
                error.printStackTrace();
            }
        });
        requestQueue.add(request);//将JsonObjectRequest对象添加到RequestQueue里面
    }
    */

    /**
     * 设置窗口的背景透明度
     * @param f 0.0-1.0
     */
    private  void bgAlpha(float f){
        WindowManager.LayoutParams layoutParams = getActivity().getWindow().getAttributes();
        layoutParams.alpha = f;
        getActivity().getWindow().setAttributes(layoutParams);
    }

    /**
     * 碎片之间的跳转
     * @param f_current
     * @param f_next
     */
    private void showFragment(Fragment f_current, Fragment f_next){
        FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
        if(f_next.isAdded()){
            transaction.hide(f_current)
                    .show(f_next)
                    .addToBackStack(null)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commitAllowingStateLoss();
        }else {
            transaction.hide(f_current)
                    .add(R.id.frame_main_content, f_next)
                    .addToBackStack(null)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commitAllowingStateLoss();
        }
    }

    @Override
    public void onDestroy() {
        LogUtil.e("BoxFragment","onDestroy");
        ToastUtil.showToast(getContext(),"onDestory");
        super.onDestroy();
        // fragment销毁后，也把线程中断
        interruptThread(subscribeThread);
        interruptThread(threadMapData);
        interruptThread(threadShowAllCarCurrentPath);
        interruptThread(publishThread);
        interruptThread(subscribeThread_storageMap);
        interruptThread(threadChargingTask);
        interruptThread(threadProblemFeedback);
        interruptThread(threadNoMoveTimeout);
        interruptThread(threadErrorCloseConnection);
        interruptThread(threadChargingError);
        if(requestQueue != null){
            requestQueue.stop();// 停止缓存和网络调度程序
        }
        // 移除所有的回调和消息，防止Handler泄露
        inComingMessageHandler.removeCallbacksAndMessages(null);

        // 停止定时任务
        stopTaskClearChargeData();
        stopTaskRefreshErrorData();

        if (timer_clear_charge_data != null){
            timer_clear_charge_data.cancel();
            timer_clear_charge_data = null;
        }
        if (timer_refresh_error_data != null){
            timer_refresh_error_data.cancel();
            timer_refresh_error_data = null;
        }
    }

    /**
     * 清除充电桩数据的任务取消
     */
    private void stopTaskClearChargeData(){
        if(task_clear_charge_data != null){
            task_clear_charge_data.cancel();
            task_clear_charge_data = null;
        }
    }

    /**
     * 清除监控错误故障数据的任务取消
     */
    private void stopTaskRefreshErrorData(){
        if(task_refresh_error_data != null){
            task_refresh_error_data.cancel();
            task_refresh_error_data = null;
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

    /**
     * 创建线程终止连接并且清空数据
     */
    private void creatThreadCloseConnectionAndClearData(){
        t_clear_all_data = new Thread(new Runnable() {
            @Override
            public void run() {
                if(connection_car != null){
                    connection_car.abort();// 小车实时包连接终止
                }
                if(connection_chargingTask != null){
                    connection_chargingTask.abort();// 充电任务监听连接终止
                }
                if(connection_showAllCarCurrentPath != null){
                    connection_showAllCarCurrentPath.abort();// 显示小车锁格和未锁格路径信息连接终止
                }
                if (connection_errorCloseConnection != null){
                    connection_errorCloseConnection.abort();// 终止小车断开连接 connection
                }
                if (connection_noMoveTimeout != null){
                    connection_noMoveTimeout.abort();// 终止小车位置不改变超时 connection
                }
                if (connection_problemFeedback != null){
                    connection_problemFeedback.abort();// 终止小车扫不到pod connection
                }
                if (connection_chargingError != null){
                    connection_chargingError.abort();// 终止充电故障监听 connection
                }
                // 给handler发消息清空原有数据
                Message message = inComingMessageHandler.obtainMessage();
                message.what =  WHAT_CLEAR_DATA;
                inComingMessageHandler.sendMessage(message);
            }
        });
        t_clear_all_data.start();
    }

    /**
     * view不可见
     * @param view
     */
    private void gone(View view){
        view.setVisibility(View.GONE);
    }

    /**
     * view可见
     * @param view
     */
    private void visibile(View view){
        view.setVisibility(View.VISIBLE);
    }


    /**
     * fragment的生命周期方法
     * @param hidden true表示当前fragment不可见，false表示当前fragment可见
     */

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        LogUtil.e("Enjoy","hidden = " + hidden);
        if (!hidden){
            boxView.restorePos(false);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LogUtil.e("BoxFragment","onDestroyView");
    }
}
