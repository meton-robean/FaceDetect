package m.tri.facedetectcamera.activity;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;

import m.tri.facedetectcamera.R;
import m.tri.facedetectcamera.activity.ui.FaceView;
import m.tri.facedetectcamera.adapter.ImagePreviewAdapter;
import m.tri.facedetectcamera.model.FaceResult;
import m.tri.facedetectcamera.utils.ImageUtils;

/**
 * 通过打开系统相册，选择一张手机照片来做静态人脸识别
 */
public class PhotoDetectActivity extends AppCompatActivity {

    private static final String TAG = PhotoDetectActivity.class.getSimpleName();

    private static final int RC_HANDLE_WRITE_EXTERNAL_STORAGE_PERM = 3;
    private static int PICK_IMAGE_REQUEST = 5;
    private FaceView faceView;
    private RecyclerView recyclerView;
    private ImagePreviewAdapter imagePreviewAdapter;
    private ArrayList<Bitmap> facesBitmap;

    private static final int MAX_FACE = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_viewer);      //导入布局文件

        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("静态人脸识别");       //设置活动的标题

        faceView = (FaceView) findViewById(R.id.faceView);   //获得faceView实例
        //设置recyclerView
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        //权限请求
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            getImage();
        } else {
            requestWriteExternalPermission();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_photo, menu);

        return true;
    }

    //点击相应菜单触发
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                return true;

            case R.id.gallery:

                int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (rc == PackageManager.PERMISSION_GRANTED) {
                    getImage();
                } else {
                    requestWriteExternalPermission();
                }

                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        resetData();
    }


    /**
     * 从相册选择图片之后，返回，开始进行人脸识别 ，调用detectFace(bitmap)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {

            Uri uri = data.getData();
            //获取相册照片
            Bitmap bitmap = ImageUtils.getBitmap(ImageUtils.getRealPathFromURI(this, uri), 2048, 1232);
            if (bitmap != null)
                detectFace(bitmap);
            else
                Toast.makeText(this, "Cann't open this image.", Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_WRITE_EXTERNAL_STORAGE_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Write External permission granted");
            // we have permission
            getImage();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));
    }

    public void getImage() {
        // Create intent to Open Image applications like Gallery
        try {
            Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            // Start the Intent
            startActivityForResult(galleryIntent, PICK_IMAGE_REQUEST);
        } catch (ActivityNotFoundException i) {
            Toast.makeText(PhotoDetectActivity.this, "Your Device can not select image from gallery.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * 静态人脸检测函数，传入照片，绿色方框自动定位人脸
     */
    private void detectFace(Bitmap bitmap) {
        resetData();  //先将空的imagePreviewAdapter放入recyclerView中

        //获得人脸检测类
        android.media.FaceDetector fdet_ = new android.media.FaceDetector(bitmap.getWidth(), bitmap.getHeight(), MAX_FACE);
        //创建人脸信息存放的容器  fullResults
        android.media.FaceDetector.Face[] fullResults = new android.media.FaceDetector.Face[MAX_FACE];
        //开始人脸检测
        fdet_.findFaces(bitmap, fullResults);
        //创建 FaceResult 的实例，FaceResult是将 存放人脸后的各种人脸信息
        ArrayList<FaceResult> faces_ = new ArrayList<>();

        //
        for (int i = 0; i < MAX_FACE; i++) {
            if (fullResults[i] != null) {
                PointF mid = new PointF();
                fullResults[i].getMidPoint(mid);

                float eyesDis = fullResults[i].eyesDistance();
                float confidence = fullResults[i].confidence();
                float pose = fullResults[i].pose(android.media.FaceDetector.Face.EULER_Y);

                Rect rect = new Rect(
                        (int) (mid.x - eyesDis * 1.20f),
                        (int) (mid.y - eyesDis * 0.55f),
                        (int) (mid.x + eyesDis * 1.20f),
                        (int) (mid.y + eyesDis * 1.85f));

                /**
                 * 只要人脸大小 100*100 以上的
                 */
                if (rect.height() * rect.width() > 100 * 100) {
                    FaceResult faceResult = new FaceResult();
                    faceResult.setFace(0, mid, eyesDis, confidence, pose, System.currentTimeMillis());//将人脸信息存入faceResult
                    faces_.add(faceResult);

                    //
                    // 获得人脸图片，放入并显示在recyclerView 中
                    //
                    Bitmap cropedFace = ImageUtils.cropFace(faceResult, bitmap, 0);
                    if (cropedFace != null) {
                        imagePreviewAdapter.add(cropedFace); //将人脸图片放入imagePreviewAdapter，
                                                             //下一步调用 recyclerView.setAdapter(imagePreviewAdapter)就可以显示图片到侧边栏
                    }
                }
            }
        }

        FaceView overlay = (FaceView) findViewById(R.id.faceView);
        overlay.setContent(bitmap, faces_);
    }


    private void requestWriteExternalPermission() {
        Log.w(TAG, "Write External permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_WRITE_EXTERNAL_STORAGE_PERM);
    }



    private void resetData() {

        if (imagePreviewAdapter == null) {
            facesBitmap = new ArrayList<>();
            imagePreviewAdapter = new ImagePreviewAdapter(PhotoDetectActivity.this, facesBitmap, new ImagePreviewAdapter.ViewHolder.OnItemClickListener() {
                @Override
                public void onClick(View v, int position) {
                    imagePreviewAdapter.setCheck(position);
                    imagePreviewAdapter.notifyDataSetChanged();
                }
            });
            recyclerView.setAdapter(imagePreviewAdapter); //将图片显示到recyclerView
        } else {
            imagePreviewAdapter.clearAll();
        }

        faceView.reset();
    }
}
