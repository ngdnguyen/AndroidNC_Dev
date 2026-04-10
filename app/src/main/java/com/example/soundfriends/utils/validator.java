package com.example.soundfriends.utils;

import static com.google.firebase.FirebaseError.ERROR_INVALID_EMAIL;

import com.google.firebase.auth.FirebaseAuthException;

public class validator {
    public static String validatorMessage(String errorCode){
        String errorMessage = errorCode;
        switch (errorCode){
            case "ERROR_INVALID_EMAIL": errorMessage = "Email không hợp lệ. VD: soundfr@email.com"; break;
            case "ERROR_WEAK_PASSWORD": errorMessage = "Mật khẩu phải có tối thiểu 6 ký tự"; break;
            case "ERROR_EMAIL_ALREADY_IN_USE": errorMessage = "Email đã được sử dụng bởi một tài khoản khác"; break;
            case "ERROR_INVALID_CREDENTIAL": errorMessage = "Email hoặc mật khẩu không chính xác"; break;
            case "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL": errorMessage = "Một tài khoản có cùng email đã tồn tại với các thông tin đăng nhập khác"; break;
            case "ERROR_USER_DISABLED": errorMessage = "Tài khoản đã bị vô hiệu hóa"; break;
            case "ERROR_TOO_MANY_ATTEMPTS_TRY_LATER": errorMessage = "Quá nhiều lần đăng nhập không thành công. Vui lòng thử lại sau"; break;
            case "ERROR_NETWORK_ERROR": errorMessage = "Lỗi mạng. Vui lòng kiểm tra kết nối internet của bạn và thử lại"; break;
            case "ERROR_INTERNAL_ERROR": errorMessage = "Lỗi nội bộ. Vui lòng thử lại sau"; break;
            case "An internal error has occurred. [ INVALID_LOGIN_CREDENTIALS ]": errorMessage = "Sai Email hoặc Mật khẩu"; break;
            case "The user account has been disabled by an administrator.": errorMessage = "Tài khoản đã bị vô hiệu hóa"; break;
            case "A network error (such as timeout, interrupted connection or unreachable host) has occurred.": errorMessage = "Không thể kết nối Internet"; break;
        }

        return errorMessage;
    }
}
