package m.tri.facedetectcamera.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import m.tri.facedetectcamera.R;
import m.tri.facedetectcamera.activity.ui.FaceOverlayView;
import m.tri.facedetectcamera.adapter.ImagePreviewAdapter;
import m.tri.facedetectcamera.model.FaceResult;
import m.tri.facedetectcamera.utils.CameraErrorCallback;
import m.tri.facedetectcamera.utils.ImageUtils;
import m.tri.facedetectcamera.utils.Util;
//使用SurfaceHolder.Callback ，可以在该活动中实现预览摄像头视频的相关功能
//使用Camera.PreviewCallback，可以实现在预览视频时，做相关的回调，可以实现视频图像的检测功能
public final class FaceDetectGrayActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    // 摄像头数目.
    private int numberOfCameras;
    public static final String TAG = FaceDetectGrayActivity.class.getSimpleName();
    private Camera mCamera;
    private int cameraId = 0;
    private int mDisplayRotation;
    private int mDisplayOrientation;

    private int previewWidth;
    private int previewHeight;

    // 传入相机图像的 surfaceview
    private SurfaceView mView;

    // 画人脸方框:
    private FaceOverlayView mFaceView;

    // 错误 log记录与打印:
    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();


    private static final int MAX_FACE = 10;         //检测最大 人脸数
    private boolean isThreadWorking = false;
    private Handler handler;
    private FaceDetectThread detectThread = null;  //人脸检测线程类声明
    private int prevSettingWidth;
    private int prevSettingHeight;
    private android.media.FaceDetector fdet;       //Android 自带的图像处理类

    private byte[] grayBuff;
    private int bufflen;
    private int[] rgbs;

    private FaceResult faces[];
    private FaceResult faces_previous[];
    private int Id = 0;

    private String BUNDLE_CAMERA_ID = "camera";


    //RecylerView face image
    private HashMap<Integer, Integer> facesCount = new HashMap<>();
    private RecyclerView recyclerView;          //RecylerView face image
    private ImagePreviewAdapter imagePreviewAdapter;
    private ArrayList<Bitmap> facesBitmap;     //存放人脸图片的arraylist结构


    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * 初始化UI和人连检测器 (face detector).
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.activity_camera_viewer); // 载入人脸检测首页的布局activity_camera_viewer.xml

        mView = (SurfaceView) findViewById(R.id.surfaceview);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //设置OverlayView
        mFaceView = new FaceOverlayView(this); //实现实时追踪人脸，有跟踪的方框

        //调用addContentView 。直接添加到view容器中，有view叠加效果。
        addContentView(mFaceView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        //设置 recyclerView
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);  //获取recyclerView实例
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext()); //使用线性布局
        recyclerView.setLayoutManager(mLayoutManager);                   //给recyclerView设置线性布局
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        //声明线程处理类实例
        handler = new Handler();
        faces = new FaceResult[MAX_FACE];
        faces_previous = new FaceResult[MAX_FACE];
        for (int i = 0; i < MAX_FACE; i++) {
            faces[i] = new FaceResult();
            faces_previous[i] = new FaceResult();
        }


        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("实时人脸识别");   ///设置活动的标题

        if (icicle != null)
            cameraId = icicle.getInt(BUNDLE_CAMERA_ID, 0);
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        //请求相机允许
        SurfaceHolder holder = mView.getHolder();
        holder.addCallback(this);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_camera, menu);

        return true;
    }


    /**
     * 设置菜单栏转换摄像头  前置和后置的转换
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                return true;

            //点击转换镜头菜单时触发，如果只有一个摄像头会弹出提醒
            case R.id.switchCam:

                if (numberOfCameras == 1) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Switch Camera").setMessage("Your device have one camera").setNeutralButton("Close", null);
                    AlertDialog alert = builder.create();
                    alert.show();
                    return true;
                }

                cameraId = (cameraId + 1) % numberOfCameras;
                recreate();

                return true;


            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * 重启摄像头.
     */
    @Override
    protected void onResume() {
        super.onResume();

        Log.i(TAG, "onResume");
        startPreview();
    }

    /**
     * 停止摄像头.
     */
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        resetData();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BUNDLE_CAMERA_ID, cameraId);
    }


    /**
     *  surfaceView创建时会调动该方法，在这里实现获取摄像头图片信息，并将其放入surfaceView中
     */

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        //当surfaceView启动时，获得摄像头数目,获得Camera实例，打开摄像机
         resetData();
        //获得摄像头数目
        numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                if (cameraId == 0) cameraId = i;
            }
        }

        mCamera = Camera.open(cameraId);     //Camera实例，打开摄像机

        Camera.getCameraInfo(cameraId, cameraInfo); //获得摄像头信息参数
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            mFaceView.setFront(true);
        }

        try {
            mCamera.setPreviewDisplay(mView.getHolder());   //设置使用哪个surfaceView来显示摄像头获取的图片
        } catch (Exception e) {
            Log.e(TAG, "Could not preview the image.", e);
        }
    }


    /**
     *  当Surface的状态（大小和格式）发生变化的时候会调用该函数，在surfaceCreated调用后该函数至少会被调用一次。
     *  在这个函数内设摄像头参数, 创建media.FaceDetector实例，开启摄像头取景
     */
    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {

        //surfaceView 安全检查
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        try {
            mCamera.stopPreview();
        } catch (Exception e) {

        }

        configureCamera(width, height);//设置摄像头参数
        setDisplayOrientation();
        setErrorCallback();

        // 创建 media.FaceDetector 实例
        float aspect = (float) previewHeight / (float) previewWidth;
        fdet = new android.media.FaceDetector(prevSettingWidth, (int) (prevSettingWidth * aspect), MAX_FACE);//实例

        bufflen = previewWidth * previewHeight;
        grayBuff = new byte[bufflen];   //灰度图存放数组
        rgbs = new int[bufflen];        //RGB图存放的数组

        // 设置完毕，开始预览取景，这样拍摄的画面就会实时显示在SurfaceView上面
        startPreview();
    }

    private void setErrorCallback() {
        mCamera.setErrorCallback(mErrorCallback);
    }

    private void setDisplayOrientation() {
        // Now set the display orientation:
        mDisplayRotation = Util.getDisplayRotation(FaceDetectGrayActivity.this);
        mDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation, cameraId);

        mCamera.setDisplayOrientation(mDisplayOrientation);

        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(mDisplayOrientation);
        }
    }

    private void configureCamera(int width, int height) {
        Camera.Parameters parameters = mCamera.getParameters();
        // Set the PreviewSize and AutoFocus:
        setOptimalPreviewSize(parameters, width, height);
        setAutoFocus(parameters);
        // And set the parameters:
        mCamera.setParameters(parameters);
    }

    private void setOptimalPreviewSize(Camera.Parameters cameraParameters, int width, int height) {
        List<Camera.Size> previewSizes = cameraParameters.getSupportedPreviewSizes();
        float targetRatio = (float) width / height;
        Camera.Size previewSize = Util.getOptimalPreviewSize(this, previewSizes, targetRatio);
        previewWidth = previewSize.width;
        previewHeight = previewSize.height;

        Log.e(TAG, "previewWidth" + previewWidth);
        Log.e(TAG, "previewHeight" + previewHeight);

        /**
         * Calculate size to scale full frame bitmap to smaller bitmap
         * Detect face in scaled bitmap have high performance than full bitmap.
         * The smaller image size -> detect faster, but distance to detect face shorter,
         * so calculate the size follow your purpose
         */
        if (previewWidth / 4 > 360) {
            prevSettingWidth = 360;
            prevSettingHeight = 270;
        } else if (previewWidth / 4 > 320) {
            prevSettingWidth = 320;
            prevSettingHeight = 240;
        } else if (previewWidth / 4 > 240) {
            prevSettingWidth = 240;
            prevSettingHeight = 160;
        } else {
            prevSettingWidth = 160;
            prevSettingHeight = 120;
        }

        cameraParameters.setPreviewSize(previewSize.width, previewSize.height);

        mFaceView.setPreviewWidth(previewWidth);
        mFaceView.setPreviewHeight(previewHeight);
    }

    private void setAutoFocus(Camera.Parameters cameraParameters) {
        List<String> focusModes = cameraParameters.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
            cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    }

    private void startPreview() {
        if (mCamera != null) {
            isThreadWorking = false;
            mCamera.startPreview();
            mCamera.setPreviewCallback(this);
            counter = 0;
        }
    }


    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mCamera.setPreviewCallbackWithBuffer(null);
        mCamera.setErrorCallback(null);
        mCamera.release();
        mCamera = null;
    }




    // fps detect face (not FPS of camera)
    long start, end;
    int counter = 0;
    double fps;




    /**
     * 一旦程序调用PreviewCallback接口，就会自动调用onPreviewFrame这个函数，这个接口允许我们在SurfaceView进行一些实时检测。
     * onPreviewFrame函数传入的是camera实例和拍摄的byte数据流，在该函数里要将这些数据流恢复为图片之后再进行人脸检测
     * 这里人脸检测是调用另一个线程实现的。
     */

    @Override
    public void onPreviewFrame(byte[] _data, Camera _camera) {
        if (!isThreadWorking) {
            if (counter == 0)
                start = System.currentTimeMillis();

            isThreadWorking = true;
            waitForFdetThreadComplete();
            detectThread = new FaceDetectThread(handler, this);  //创建人脸检测线程类实例
            detectThread.setData(_data);
            detectThread.start();                                //开启人脸检测线程，实时检测
        }
    }

    private void waitForFdetThreadComplete() {
        if (detectThread == null) {
            return;
        }

        if (detectThread.isAlive()) {
            try {
                detectThread.join();
                detectThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }


    /**
     * 人脸检测线程
     * 流程： 1、先检测到人脸，将人脸图装入RecylerView 中；2、然后调用FaceOverlayView在活动上绘制蓝色方框
     * 为内部类
     */
    private class FaceDetectThread extends Thread {
        private Handler handler;
        private byte[] data = null;
        private Context ctx;
        private Bitmap faceCroped;

        public FaceDetectThread(Handler handler, Context ctx) {
            this.ctx = ctx;
            this.handler = handler;
        }

        //获取传入的图像数据流
        public void setData(byte[] data) {
            this.data = data;
        }


        //线程处理函数
        public void run() {
//            Log.i("FaceDetectThread", "running");

            float aspect = (float) previewHeight / (float) previewWidth;
            int w = prevSettingWidth;
            int h = (int) (prevSettingWidth * aspect);

            //将图像byte数据流转化为位图格式，才能进行图像处理，人脸分析
            ByteBuffer bbuffer = ByteBuffer.wrap(data); //传入拍照获得的图像数据byte流数据
            bbuffer.get(grayBuff, 0, bufflen);
            gray8toRGB32(grayBuff, previewWidth, previewHeight, rgbs);   //灰度转RGB三通道
            Bitmap bitmap = Bitmap.createBitmap(rgbs, previewWidth, previewHeight, Bitmap.Config.RGB_565);//生成位图格式
            Bitmap bmp = Bitmap.createScaledBitmap(bitmap, w, h, false); //转化位图大小

            float xScale = (float) previewWidth / (float) prevSettingWidth;
            float yScale = (float) previewHeight / (float) h;

            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, info);
            int rotate = mDisplayOrientation;
            //手机屏幕是否旋转
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT && mDisplayRotation % 180 == 0) {
                if (rotate + 180 > 360) {
                    rotate = rotate - 180;
                } else
                    rotate = rotate + 180;
            }

            switch (rotate) {
                case 90:
                    bmp = ImageUtils.rotate(bmp, 90);       //将图片旋转90度
                    xScale = (float) previewHeight / bmp.getWidth();
                    yScale = (float) previewWidth / bmp.getHeight();
                    break;
                case 180:
                    bmp = ImageUtils.rotate(bmp, 180);     //将图片旋转180度
                    break;
                case 270:
                    bmp = ImageUtils.rotate(bmp, 270);     //将图片旋转270度
                    xScale = (float) previewHeight / (float) h;
                    yScale = (float) previewWidth / (float) prevSettingWidth;
                    break;
            }

            fdet = new android.media.FaceDetector(bmp.getWidth(), bmp.getHeight(), MAX_FACE);    //Android自带图像识别类

            android.media.FaceDetector.Face[] fullResults = new android.media.FaceDetector.Face[MAX_FACE]; //Android自带的Face类，保存有人脸信息

            fdet.findFaces(bmp, fullResults);          //传入照片，寻找人脸，获取人脸信息

            for (int i = 0; i < MAX_FACE; i++) {
                if (fullResults[i] == null) {          //人脸获得为空
                    faces[i].clear();
                } else {
                    PointF mid = new PointF();
                    fullResults[i].getMidPoint(mid);   //获得人脸中点位置

                    mid.x *= xScale;
                    mid.y *= yScale;

                    float eyesDis = fullResults[i].eyesDistance() * xScale;   //获得眼距
                    float confidence = fullResults[i].confidence();           //获得人脸自信度
                    float pose = fullResults[i].pose(android.media.FaceDetector.Face.EULER_Y);
                    int idFace = Id;                                          //获得图像ID

                    Rect rect = new Rect(            //根据眼距设置人脸矩形边框
                            (int) (mid.x - eyesDis * 1.20f),
                            (int) (mid.y - eyesDis * 0.55f),
                            (int) (mid.x + eyesDis * 1.20f),
                            (int) (mid.y + eyesDis * 1.85f));


                    if(rect.height() * rect.width() > 100 * 100) {  //只检测face大于100*100的
                        // Check this face and previous face have same ID?
                        for (int j = 0; j < MAX_FACE; j++) {
                            float eyesDisPre = faces_previous[j].eyesDistance();
                            PointF midPre = new PointF();
                            faces_previous[j].getMidPoint(midPre);

                            RectF rectCheck = new RectF(
                                    (midPre.x - eyesDisPre * 1.5f),
                                    (midPre.y - eyesDisPre * 1.15f),
                                    (midPre.x + eyesDisPre * 1.5f),
                                    (midPre.y + eyesDisPre * 1.85f));

                            if (rectCheck.contains(mid.x, mid.y) && (System.currentTimeMillis() - faces_previous[j].getTime()) < 1000) {
                                idFace = faces_previous[j].getId();
                                break;
                            }
                        }

                        if (idFace == Id) Id++;
                        //设置人脸信息
                        faces[i].setFace(idFace, mid, eyesDis, confidence, pose, System.currentTimeMillis());
                        //将此刻人脸信息保存到faces_previous
                        faces_previous[i].set(faces[i].getId(), faces[i].getMidEye(), faces[i].eyesDistance(), faces[i].getConfidence(), faces[i].getPose(), faces[i].getTime());

                        //
                        // if focus in a face 5 frame -> take picture face display in RecyclerView
                        // because of some first frame have low quality
                        //
                        if (facesCount.get(idFace) == null) {
                            facesCount.put(idFace, 0);
                        } else {
                            int count = facesCount.get(idFace) + 1;
                            if (count <= 5)
                                facesCount.put(idFace, count);

                            //
                            //将人脸显示到RecylerView中去
                            //
                            if (count == 5) {
                                faceCroped = ImageUtils.cropFace(faces[i], bitmap, rotate);//在bitmap上取出相应人脸区域
                                if (faceCroped != null) {
                                    handler.post(new Runnable() {
                                        public void run() {
                                            imagePreviewAdapter.add(faceCroped);  //将人脸照片加到imagePreviewAdapter
                                        }
                                    });
                                }
                            }
                        }
                    }
                }
            }
            //检测完人脸之后，传入人脸信息，准备实时动态方框跟踪人脸
            //handler 用于跟新UI 每次重新绘制方框
            handler.post(new Runnable() {
                public void run() {
                    //send face to FaceView to draw rect
                    mFaceView.setFaces(faces);   //FaceOverlayView实例，传入人脸信息faces，这样FaceOverlayView在调用ondraw方法时就可以绘制人脸方框。

                    //Calculate FPS (Detect Frame per Second)
                    end = System.currentTimeMillis();
                    counter++;
                    double time = (double) (end - start) / 1000;
                    if (time != 0)
                        fps = counter / time;

                    mFaceView.setFPS(fps);   //设置帧率

                    if (counter == (Integer.MAX_VALUE - 1000))
                        counter = 0;

                    isThreadWorking = false;
                }
            });
        }

        private void gray8toRGB32(byte[] gray8, int width, int height, int[] rgb_32s) {
            final int endPtr = width * height;
            int ptr = 0;
            while (true) {
                if (ptr == endPtr)
                    break;

                final int Y = gray8[ptr] & 0xff;
                rgb_32s[ptr] = 0xff000000 + (Y << 16) + (Y << 8) + Y;
                ptr++;
            }
        }
    }

    /**
     * Release Memory
     */
    private void resetData() {
        if (imagePreviewAdapter == null) {
            facesBitmap = new ArrayList<>();
            imagePreviewAdapter = new ImagePreviewAdapter(FaceDetectGrayActivity.this, facesBitmap, new ImagePreviewAdapter.ViewHolder.OnItemClickListener() {
                @Override
                public void onClick(View v, int position) {
                    imagePreviewAdapter.setCheck(position);
                    imagePreviewAdapter.notifyDataSetChanged();
                }
            });
            recyclerView.setAdapter(imagePreviewAdapter);
        } else {
            imagePreviewAdapter.clearAll();
        }
    }
}
