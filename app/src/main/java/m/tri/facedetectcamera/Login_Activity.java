package m.tri.facedetectcamera;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.isnc.facesdk.SuperID;
import com.isnc.facesdk.common.Cache;
import com.isnc.facesdk.common.SDKConfig;

public class Login_Activity extends Activity{
    private EditText accountEdit;

    private EditText passwordEdit;

    private Button login;
    private  Button face_login;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SuperID.initFaceSDK(this);//一登SDK初始化
        SuperID.setDebugMode(true);
        setContentView(R.layout.activity_login);

        accountEdit = (EditText) findViewById(R.id.account);
        passwordEdit = (EditText) findViewById(R.id.password);
        login = (Button) findViewById(R.id.login);
        face_login=(Button)findViewById(R.id.face_login);
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String account = accountEdit.getText().toString();
                String password = passwordEdit.getText().toString();
                // 如果账号是admin且密码是123456，就认为登录成功
                if (account.equals("admin") && password.equals("123456")) {
                    Intent intent = new Intent(Login_Activity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(Login_Activity.this, "account or password is invalid",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        face_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SuperID.faceLogin(Login_Activity.this);  //刷脸登录功能接入
            }
        });

    }

    // 接口返回
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (resultCode) {
            // 授权成功
            case SDKConfig.AUTH_SUCCESS:
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                finish();
                break;
            // 取消授权
            case SDKConfig.AUTH_BACK:

                break;
            // 找不到该用户
            case SDKConfig.USER_NOTFOUND:

                break;
            // 登录成功
            case SDKConfig.LOGINSUCCESS:
                //System.out.println(Cache.getCached(context, SDKConfig.KEY_APPINFO));
                Intent i = new Intent(this, MainActivity.class);
                startActivity(i);
                finish();
                break;
            // 登录失败
            case SDKConfig.LOGINFAIL:
                break;
            // 网络有误
            case SDKConfig.NETWORKFAIL:
                break;
            // 一登SDK版本过低
            case SDKConfig.SDKVERSIONEXPIRED:
                break;
            default:
                break;
        }
    }

}
