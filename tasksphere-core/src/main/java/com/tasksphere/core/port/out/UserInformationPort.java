package com.tasksphere.core.port.out;

import com.tasksphere.core.dto.UserInfo;

public interface UserInformationPort {
    UserInfo getUserInfo(String username);
}